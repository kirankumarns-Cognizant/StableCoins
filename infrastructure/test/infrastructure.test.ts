import * as cdk from 'aws-cdk-lib';
import { Template, Match } from 'aws-cdk-lib/assertions';
import { InfrastructureStack } from '../lib/infrastructure-stack';

test('messaging deployment honors configured resource destinations', () => {
  const overrides: Record<string, string> = {
    SQS_ROUTING_DECISIONS_QUEUE_NAME: 'customer-routing-decisions',
    SQS_ROUTING_DECISIONS_DLQ_NAME: 'customer-routing-decisions-dlq',
    EVENTBRIDGE_BUS_NAME: 'customer-payment-bus',
    SQS_PAYMENT_CREATED_QUEUE_NAME: 'customer-created',
    SQS_COMPLIANCE_COMPLETED_QUEUE_NAME: 'customer-completed',
    SQS_PAYMENT_CREATED_DLQ_NAME: 'customer-created-dlq',
    SQS_COMPLIANCE_COMPLETED_DLQ_NAME: 'customer-completed-dlq',
  };
  const previous = Object.fromEntries(Object.keys(overrides).map(key => [key, process.env[key]]));
  try {
    Object.assign(process.env, overrides);
    const template = Template.fromStack(new InfrastructureStack(new cdk.App(), 'ConfiguredConnectionsTest'));
    template.hasResourceProperties('AWS::Events::EventBus', { Name: 'customer-payment-bus' });
    for (const name of Object.entries(overrides).filter(([key]) => key !== 'EVENTBRIDGE_BUS_NAME').map(([, value]) => value)) {
      template.hasResourceProperties('AWS::SQS::Queue', { QueueName: name });
    }
    template.resourceCountIs('AWS::Events::Rule', 14);
  } finally {
    for (const [key, value] of Object.entries(previous)) {
      if (value === undefined) delete process.env[key]; else process.env[key] = value;
    }
  }
});


test('all SQS routes deliver the domain event expected by Java consumers', () => {
 const template=Template.fromStack(new InfrastructureStack(new cdk.App(),'EnvelopeContractTest'));
 const rules=template.findResources('AWS::Events::Rule');
 expect(Object.keys(rules)).toHaveLength(14);
 for(const rule of Object.values(rules) as any[]) for(const target of rule.Properties.Targets) expect(target.InputPath).toBe('$.detail');
});

test('ledger funding, submission and confirmation have durable delivery with DLQs', () => {
 const template=Template.fromStack(new InfrastructureStack(new cdk.App(),'LedgerDeliveryTest'));
 for(const name of ['stablecoin-ledger-funding-queue','stablecoin-blockchain-submitted-queue','stablecoin-blockchain-confirmed-queue']) {
  template.hasResourceProperties('AWS::SQS::Queue',{QueueName:name,VisibilityTimeout:120,RedrivePolicy:{maxReceiveCount:3,deadLetterTargetArn:Match.anyValue()}});
 }
});


test('execution commands fan out to separate owner queues with retry isolation', () => {
 const template=Template.fromStack(new InfrastructureStack(new cdk.App(),'FlowDeliveryTest'));
 for(const name of ['flow-quote','flow-treasury','flow-validation','flow-reservation','flow-submission']) {
  template.hasResourceProperties('AWS::SQS::Queue',{QueueName:`stablecoin-${name}-queue`,VisibilityTimeout:120,RedrivePolicy:{maxReceiveCount:3,deadLetterTargetArn:Match.anyValue()}});
 }
 const rules=Object.values(template.findResources('AWS::Events::Rule')) as any[];
 for(const [type,count] of [['RoutingEvidenceRequested',2],['StablecoinExecutionRequested',3],['BlockchainSubmissionRequested',1]] as const){
  const matched=rules.filter(r=>r.Properties.EventPattern['detail-type'].includes(type));
  for(const rule of matched)expect(rule.Properties.EventPattern.source).toEqual(['stablecoins.payment-service']);
  expect(matched.flatMap(r=>r.Properties.Targets)).toHaveLength(count);
 }
});

test('settlement, fee, payout and reconciliation routes retain owner isolation and DLQs', () => {
 const t=Template.fromStack(new InfrastructureStack(new cdk.App(),'LifecycleDeliveryTest'));
 t.resourceCountIs('AWS::SQS::Queue',32);
 for(const name of ['receipt-observed','settlement-requested','visa-payout','ledger-payout','reconciliation-requested'])t.hasResourceProperties('AWS::SQS::Queue',{QueueName:`stablecoin-${name}-queue`,VisibilityTimeout:120,SqsManagedSseEnabled:true,RedrivePolicy:{maxReceiveCount:3,deadLetterTargetArn:Match.anyValue()}});
 const rules=Object.values(t.findResources('AWS::Events::Rule')) as any[];
 expect(rules.filter(r=>r.Properties.EventPattern['detail-type'].includes('VisaPayoutInitiated'))).toHaveLength(2);
 expect(rules.find(r=>r.Properties.EventPattern['detail-type'].includes('BlockchainReceiptObserved')).Properties.EventPattern.source).toEqual(['stablecoins.blockchain-service']);
});
