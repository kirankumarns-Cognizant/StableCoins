import * as cdk from 'aws-cdk-lib';
import { Construct } from 'constructs';
import * as sqs from 'aws-cdk-lib/aws-sqs';
import * as events from 'aws-cdk-lib/aws-events';
import * as targets from 'aws-cdk-lib/aws-events-targets';

const { readConnections } = require('../../services/config/read-connections.cjs') as {
  readConnections: () => Record<string, string>;
};

/**
 * Stablecoin Payment Orchestration POC — AWS Infrastructure (Iteration 3)
 *
 * Defines: EventBridge custom bus, SQS queues, DLQs, and routing rules.
 * See ADR-004 (EventBridge + SQS), docs/11-EVENT-CATALOG.md.
 *
 * NOTE: This stack is defined only — do not run `cdk deploy` during the POC
 * unless explicitly instructed. Use `cdk synth` to validate.
 */
export class InfrastructureStack extends cdk.Stack {
  constructor(scope: Construct, id: string, props?: cdk.StackProps) {
    super(scope, id, props);
    const connections = readConnections();

    // ── Dead-Letter Queues ────────────────────────────────────────────────────

    const paymentCreatedDlq = new sqs.Queue(this, 'PaymentCreatedDlq', {
      queueName: connections['app.sqs.payment-created-dlq-name'],
      retentionPeriod: cdk.Duration.days(14),
    });

    const complianceCompletedDlq = new sqs.Queue(this, 'ComplianceCompletedDlq', {
      queueName: connections['app.sqs.compliance-completed-dlq-name'],
      retentionPeriod: cdk.Duration.days(14),
    });

    // ── SQS Queues ────────────────────────────────────────────────────────────

    // compliance-quote-service listens here for PaymentCreated
    const paymentCreatedQueue = new sqs.Queue(this, 'PaymentCreatedQueue', {
      queueName: connections['app.sqs.payment-created-queue-name'],
      visibilityTimeout: cdk.Duration.seconds(60),
      deadLetterQueue: {
        queue: paymentCreatedDlq,
        maxReceiveCount: 3,
      },
    });

    // payment-service listens here for ComplianceCompleted
    const complianceCompletedQueue = new sqs.Queue(this, 'ComplianceCompletedQueue', {
      queueName: connections['app.sqs.compliance-completed-queue-name'],
      visibilityTimeout: cdk.Duration.seconds(60),
      deadLetterQueue: {
        queue: complianceCompletedDlq,
        maxReceiveCount: 3,
      },
    });

    // ── EventBridge Custom Bus ────────────────────────────────────────────────

    const paymentBus = new events.EventBus(this, 'StablecoinPaymentBus', {
      eventBusName: connections['app.eventbridge.bus-name'],
    });

    // ── Routing Rules ─────────────────────────────────────────────────────────

    // Durable review notifications; this queue has no payment-execution consumer.
    const routingDecisionsDlq = new sqs.Queue(this, 'RoutingDecisionsDlq', {
      queueName: connections['app.sqs.routing-decisions-dlq-name'],
      retentionPeriod: cdk.Duration.days(14),
    });
    const routingDecisionsQueue = new sqs.Queue(this, 'RoutingDecisionsQueue', {
      queueName: connections['app.sqs.routing-decisions-queue-name'],
      retentionPeriod: cdk.Duration.days(14),
      deadLetterQueue: { queue: routingDecisionsDlq, maxReceiveCount: 3 },
    });
    new events.Rule(this, 'RoutingDecisionsRule', {
      eventBus: paymentBus,
      eventPattern: {
        source: ['stablecoins.payment-service'],
        detailType: ['RoutingRecommendationProposed', 'RoutingQuoteAccepted', 'RoutingApprovalRecorded',
          'RoutingRecommendationApproved', 'RoutingRecommendationRejected',
          'RoutingRecommendationExpired', 'RoutingRecommendationSuperseded', 'RoutingRecommendationExecuted', 'SettlementCompleted', 'ReconciliationCompleted'],
      },
      targets: [new targets.SqsQueue(routingDecisionsQueue, { message: events.RuleTargetInput.fromEventPath('$.detail') })],
    });

    // PaymentCreated → stablecoin-payment-created-queue
    new events.Rule(this, 'PaymentCreatedRule', {
      eventBus: paymentBus,
      ruleName: 'stablecoin-route-payment-created',
      description: 'Routes PaymentCreated from payment-service to compliance-quote-service',
      eventPattern: {
        source: ['stablecoins.payment-service'],
        detailType: ['PaymentCreated'],
      },
      targets: [
        new targets.SqsQueue(paymentCreatedQueue, {
          message: events.RuleTargetInput.fromEventPath('$.detail'),
        }),
      ],
    });

    // ComplianceCompleted → stablecoin-compliance-completed-queue
    new events.Rule(this, 'ComplianceCompletedRule', {
      eventBus: paymentBus,
      ruleName: 'stablecoin-route-compliance-completed',
      description: 'Routes ComplianceCompleted from compliance-quote-service to payment-service',
      eventPattern: {
        source: ['stablecoins.compliance-quote-service'],
        detailType: ['ComplianceCompleted'],
      },
      targets: [
        new targets.SqsQueue(complianceCompletedQueue, {
          message: events.RuleTargetInput.fromEventPath('$.detail'),
        }),
      ],
    });

    // ── Outputs ───────────────────────────────────────────────────────────────

    // Every ledger consumer receives its own durable queue and domain-event body.
    const ledgerRoutes = [
      { id: 'LedgerFunding', source: 'stablecoins.payment-service', type: 'StablecoinFundingCompleted', queue: 'ledger-funding' },
      { id: 'BlockchainSubmitted', source: 'stablecoins.blockchain-service', type: 'BlockchainTransactionSubmitted', queue: 'blockchain-submitted' },
      { id: 'BlockchainConfirmed', source: 'stablecoins.blockchain-service', type: 'BlockchainTransactionConfirmed', queue: 'blockchain-confirmed' },
    ];
    for (const route of ledgerRoutes) {
      const deadLetterQueue = new sqs.Queue(this, `${route.id}Dlq`, {
        queueName: connections[`app.sqs.${route.queue}-dlq-name`],
        retentionPeriod: cdk.Duration.days(14), encryption: sqs.QueueEncryption.SQS_MANAGED,
        enforceSSL: true,
      });
      const queue = new sqs.Queue(this, `${route.id}Queue`, {
        queueName: connections[`app.sqs.${route.queue}-queue-name`],
        visibilityTimeout: cdk.Duration.seconds(120),
        deadLetterQueue: { queue: deadLetterQueue, maxReceiveCount: 3 },
        encryption: sqs.QueueEncryption.SQS_MANAGED, enforceSSL: true,
      });
      new events.Rule(this, `${route.id}Rule`, {
        eventBus: paymentBus, eventPattern: { source: [route.source], detailType: [route.type] },
        targets: [new targets.SqsQueue(queue, { message: events.RuleTargetInput.fromEventPath('$.detail') })],
      });
    }

    // Fanout commands have one durable queue per owner consumer. Partial delivery is safe to replay.
    const flowRoutes = [
      { id: 'FlowEvidence', type: 'RoutingEvidenceRequested', queues: ['flow-quote', 'flow-treasury'] },
      { id: 'FlowExecution', type: 'StablecoinExecutionRequested', queues: ['flow-validation', 'flow-reservation'] },
      { id: 'FlowSubmission', type: 'BlockchainSubmissionRequested', queues: ['flow-submission'] },
    ];
    for (const route of flowRoutes) {
      const destinations = route.queues.map((name, index) => {
        const dlq = new sqs.Queue(this, `${route.id}${index}Dlq`, {
          queueName: connections[`app.sqs.${name}-dlq-name`], retentionPeriod: cdk.Duration.days(14),
          encryption: sqs.QueueEncryption.SQS_MANAGED, enforceSSL: true,
        });
        const queue = new sqs.Queue(this, `${route.id}${index}Queue`, {
          queueName: connections[`app.sqs.${name}-queue-name`], visibilityTimeout: cdk.Duration.seconds(120),
          deadLetterQueue: { queue: dlq, maxReceiveCount: 3 },
          encryption: sqs.QueueEncryption.SQS_MANAGED, enforceSSL: true,
        });
        return new targets.SqsQueue(queue, { message: events.RuleTargetInput.fromEventPath('$.detail') });
      });
      new events.Rule(this, `${route.id}Rule`, {
        eventBus: paymentBus, eventPattern: { source: ['stablecoins.payment-service'], detailType: [route.type] },
        targets: destinations,
      });
    }

    // Step 8 owner queues: no cross-service financial writes.
    const lifecycle = (id: string, name: string) => {
      const dlq = new sqs.Queue(this, `${id}Dlq`, { queueName: connections[`app.sqs.${name}-dlq-name`], retentionPeriod: cdk.Duration.days(14), encryption: sqs.QueueEncryption.SQS_MANAGED, enforceSSL: true });
      return new sqs.Queue(this, `${id}Queue`, { queueName: connections[`app.sqs.${name}-queue-name`], encryption: sqs.QueueEncryption.SQS_MANAGED, enforceSSL: true, visibilityTimeout: cdk.Duration.seconds(120), deadLetterQueue: { queue: dlq, maxReceiveCount: 3 } });
    };
    const receiptQueue = lifecycle('ReceiptObserved', 'receipt-observed');
    const settlementQueue = lifecycle('SettlementRequested', 'settlement-requested');
    const visaQueue = lifecycle('VisaPayout', 'visa-payout');
    const payoutLedgerQueue = lifecycle('LedgerPayout', 'ledger-payout');
    const reconQueue = lifecycle('ReconciliationRequested', 'reconciliation-requested');
    for (const [id, source, types, queues] of [
      ['ReceiptObservedRule', 'stablecoins.blockchain-service', ['BlockchainReceiptObserved'], [receiptQueue]],
      ['SettlementRequestedRule', 'stablecoins.payment-service', ['SettlementRequested'], [settlementQueue]],
      ['VisaPayoutRule', 'stablecoins.payment-service', ['StablecoinExecutionRequested', 'VisaPayoutInitiated'], [visaQueue]],
      ['LedgerPayoutRule', 'stablecoins.payment-service', ['VisaPayoutInitiated', 'VisaPayoutCompleted'], [payoutLedgerQueue]],
      ['ReconciliationRequestedRule', 'stablecoins.payment-service', ['ReconciliationRequested'], [reconQueue]],
    ] as [string, string, string[], sqs.Queue[]][]) {
      new events.Rule(this, id, { eventBus: paymentBus, eventPattern: { source: [source], detailType: types }, targets: queues.map(queue => new targets.SqsQueue(queue, { message: events.RuleTargetInput.fromEventPath('$.detail') })) });
    }

    new cdk.CfnOutput(this, 'PaymentCreatedQueueUrl', {
      value: paymentCreatedQueue.queueUrl,
      description: 'SQS queue URL for PaymentCreated events',
    });
    new cdk.CfnOutput(this, 'ComplianceCompletedQueueUrl', {
      value: complianceCompletedQueue.queueUrl,
      description: 'SQS queue URL for ComplianceCompleted events',
    });
    new cdk.CfnOutput(this, 'EventBusArn', {
      value: paymentBus.eventBusArn,
      description: 'EventBridge custom bus ARN',
    });
  }
}
