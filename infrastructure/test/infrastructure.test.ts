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
    template.resourceCountIs('AWS::Events::Rule', 15);
  } finally {
    for (const [key, value] of Object.entries(previous)) {
      if (value === undefined) delete process.env[key]; else process.env[key] = value;
    }
  }
});


test('all SQS routes deliver the domain event expected by Java consumers', () => {
 const template=Template.fromStack(new InfrastructureStack(new cdk.App(),'EnvelopeContractTest'));
 const rules=template.findResources('AWS::Events::Rule');
 expect(Object.keys(rules)).toHaveLength(15);
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

test('private observation commands reach only the existing blockchain treasury queue', () => {
 const template=Template.fromStack(new InfrastructureStack(new cdk.App(),'BesuObservationDeliveryTest'));
 const rules=Object.values(template.findResources('AWS::Events::Rule')) as any[];
 const rule=rules.find(r=>r.Properties.EventPattern['detail-type'].includes('SettlementObservationRequested'));
 expect(rule.Properties.EventPattern.source).toEqual(['stablecoins.payment-service']);
 expect(rule.Properties.Targets).toHaveLength(1);
 const queueRef=rule.Properties.Targets[0].Arn['Fn::GetAtt'][0];
 const queues=template.findResources('AWS::SQS::Queue');
 expect(queues[queueRef].Properties.QueueName).toBe('stablecoin-flow-treasury-queue');
 expect(rule.Properties.Targets[0].InputPath).toBe('$.detail');
});

test('private approval events reach only the durable review notification queue', () => {
 const template=Template.fromStack(new InfrastructureStack(new cdk.App(),'BesuApprovalDeliveryTest'));
 const rules=Object.values(template.findResources('AWS::Events::Rule')) as any[];
 const queues=template.findResources('AWS::SQS::Queue');
 for(const type of ["SettlementRecommendationProposed","SettlementQuoteAccepted","SettlementApprovalRecorded","SettlementRecommendationApproved","SettlementRecommendationRejected","SettlementRecommendationExpired","SettlementRecommendationSuperseded"]) {
  const matching=rules.filter(r=>r.Properties.EventPattern['detail-type'].includes(type));
  expect(matching).toHaveLength(1);
  expect(matching[0].Properties.EventPattern.source).toEqual(['stablecoins.payment-service']);
  expect(matching[0].Properties.Targets).toHaveLength(1);
  const target=matching[0].Properties.Targets[0];
  expect(queues[target.Arn['Fn::GetAtt'][0]].Properties.QueueName).toBe('stablecoin-routing-decisions-queue');
  expect(target.InputPath).toBe('$.detail');
 }
});


test('private funding transport defaults off and enabled delivery has its own protected queue and DLQ', () => {
 const keys=['BESU_FUNDING_EVENTS_ENABLED','SQS_BESU_LEDGER_FUNDING_QUEUE_NAME','SQS_BESU_LEDGER_FUNDING_DLQ_NAME'];
 const previous=keys.map(key=>process.env[key]);
 try {
  delete process.env.BESU_FUNDING_EVENTS_ENABLED;
  const disabled=Template.fromStack(new InfrastructureStack(new cdk.App(),'BesuFundingDisabled'));
  expect(Object.values(disabled.findResources('AWS::Events::Rule')).some((r:any)=>r.Properties.EventPattern['detail-type'].includes('BesuFundingAuthorized'))).toBe(false);
  process.env.BESU_FUNDING_EVENTS_ENABLED='true';
  process.env.SQS_BESU_LEDGER_FUNDING_QUEUE_NAME='private-demo-funding';
  process.env.SQS_BESU_LEDGER_FUNDING_DLQ_NAME='private-demo-funding-dlq';
  const enabled=Template.fromStack(new InfrastructureStack(new cdk.App(),'BesuFundingEnabled'));
  enabled.resourceCountIs('AWS::SQS::Queue',34);enabled.resourceCountIs('AWS::Events::Rule',16);
  enabled.hasResourceProperties('AWS::SQS::Queue',{QueueName:'private-demo-funding',SqsManagedSseEnabled:true,VisibilityTimeout:120,RedrivePolicy:{maxReceiveCount:3,deadLetterTargetArn:Match.anyValue()}});
  enabled.hasResourceProperties('AWS::SQS::Queue',{QueueName:'private-demo-funding-dlq',SqsManagedSseEnabled:true,MessageRetentionPeriod:1209600});
  const rules=Object.values(enabled.findResources('AWS::Events::Rule')) as any[];
  const matches=rules.filter(r=>r.Properties.EventPattern['detail-type'].includes('BesuFundingAuthorized'));
  expect(matches).toHaveLength(1);expect(matches[0].Properties.EventPattern).toEqual({source:['stablecoins.payment-service'],'detail-type':['BesuFundingAuthorized'],detail:{eventType:['BesuFundingAuthorized'],eventVersion:['1.0']}});
  const target=matches[0].Properties.Targets;expect(target).toHaveLength(1);expect(target[0].InputPath).toBe('$.detail');
  expect(enabled.findResources('AWS::SQS::Queue')[target[0].Arn['Fn::GetAtt'][0]].Properties.QueueName).toBe('private-demo-funding');
  process.env.SQS_BESU_LEDGER_FUNDING_QUEUE_NAME='stablecoin-ledger-funding-queue';
  expect(()=>new InfrastructureStack(new cdk.App(),'BesuFundingCollision')).toThrow('Dedicated Besu');
 } finally { keys.forEach((key,index)=>{if(previous[index]===undefined)delete process.env[key];else process.env[key]=previous[index];}); }
});
