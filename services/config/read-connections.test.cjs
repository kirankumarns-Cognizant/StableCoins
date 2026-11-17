const { test } = require('node:test');
const assert = require('node:assert/strict');
const { readConnections, publicConnections } = require('./read-connections.cjs');

test('shared defaults and nested DB placeholders resolve', () => {
  const config = readConnections({ DB_HOST: 'tenant-db', DB_PORT: '6432', DB_NAME: 'tenant' });
  assert.equal(config['spring.datasource.url'], 'jdbc:postgresql://tenant-db:6432/tenant');
  assert.equal(config['app.eventbridge.bus-name'], 'stablecoin-payment-bus');
  assert.equal(config['blockchain.mode'], 'MOCK');
  assert.equal(config['testnet.enabled'], 'false');
  assert.equal(config['connections.aws.region'], 'us-west-2');
  assert.equal(config['connections.ai.region'], 'us-west-2');
  const override = readConnections({AWS_REGION: 'us-east-2'});
  assert.equal(override['connections.aws.region'], 'us-east-2');
  assert.equal(override['connections.ai.region'], 'us-east-2');
});

test('deployment overrides use the same messaging names as services', () => {
  const config = readConnections({ EVENTBRIDGE_BUS_NAME: 'tenant-bus', SQS_PAYMENT_CREATED_QUEUE_NAME: 'tenant-created' });
  assert.equal(config['app.eventbridge.bus-name'], 'tenant-bus');
  assert.equal(config['app.sqs.payment-created-queue-name'], 'tenant-created');
});

test('browser allowlist exposes identity and gateway, never private connections', () => {
  const config = readConnections({ DB_PASSWORD: 'test-only-password', BLOCKCHAIN_RPC_URL: 'https://rpc.test/private-token',
    AWS_SECRET_ACCESS_KEY: 'test-only-secret', IDENTITY_ISSUER_URI: 'https://id.test/pool', IDENTITY_CLIENT_ID: 'client',
    PUBLIC_API_BASE_URL: 'https://gateway.test/api/v1' });
  const publicConfig = publicConnections(config);
  assert.equal(publicConfig.identity.issuerUri, 'https://id.test/pool');
  assert.equal(publicConfig.apiBaseUrl, 'https://gateway.test/api/v1');
  assert.deepEqual(Object.keys(publicConfig).sort(), ['apiBaseUrl', 'blockchain', 'identity']);
  for (const secret of ['test-only-password', 'private-token', 'test-only-secret']) {
    assert.ok(!JSON.stringify(publicConfig).includes(secret));
  }
});

test('legacy Cognito and explorer aliases resolve', () => {
  const config = readConnections({ COGNITO_ISSUER_URI: 'https://legacy.test', BLOCKCHAIN_EXPLORER_URL: 'https://explorer.test' });
  assert.equal(config['public.identity-issuer-uri'], 'https://legacy.test');
  assert.equal(config['blockchain.explorer-base-url'], 'https://explorer.test');
});

test('assessment settings are configurable and remain server-only', () => {
  const config = readConnections({ ROUTING_ASSESSMENT_VERSION: 'customer-v2', ROUTING_MINIMUM_SAMPLE_SIZE: '250' });
  assert.equal(config['app.routing.assessment.version'], 'customer-v2');
  assert.equal(config['app.routing.assessment.minimum-sample-size'], '250');
  assert.equal(config['app.routing.assessment.latency-percentile'], '95');
  assert.ok(!JSON.stringify(publicConnections(config)).includes('customer-v2'));
});


test('treasury controls and simulated providers are configurable and server-only', () => {
  const config = readConnections({ BLOCKCHAIN_RESERVATION_TTL: 'PT2M', BLOCKCHAIN_GAS_PRICE_MULTIPLIER: '1.5',
    VISA_SIMULATOR_ALLOWED_PROVIDER_IDS: 'CUSTOMER-SIMULATED-A', VISA_SIMULATOR_RESERVATION_TTL: 'PT3M' });
  assert.equal(config['blockchain.reservation-ttl'], 'PT2M');
  assert.equal(config['blockchain.gas-price-multiplier'], '1.5');
  assert.equal(config['blockchain.execution-api-enabled'], 'false');
  assert.equal(config['visa.simulator.allowed-provider-ids'], 'CUSTOMER-SIMULATED-A');
  assert.equal(config['visa.simulator.reservation-ttl'], 'PT3M');
  assert.ok(!JSON.stringify(publicConnections(config)).includes('CUSTOMER-SIMULATED-A'));
});

test('identity timeout and decision destinations are shared and server-only', () => {
  const config = readConnections({ IDENTITY_HTTP_TIMEOUT_MS: '3000', SQS_ROUTING_DECISIONS_QUEUE_NAME: 'tenant-decisions' });
  assert.equal(config['connections.identity.http-timeout-ms'], '3000');
  assert.equal(config['app.sqs.routing-decisions-queue-name'], 'tenant-decisions');
  assert.ok(!JSON.stringify(publicConnections(config)).includes('tenant-decisions'));
});

test('agent deployment controls and secret references stay outside browser config', () => {
  const config=readConnections({AI_MODEL:'configured-model',AI_API_KEY_SECRET_NAME:'private-agent-key',ROUTING_AGENT_MAXIMUM_ROUNDS:'16'});
  assert.equal(config['app.agent.enabled'],'false');
  assert.equal(config['app.agent.maximum-rounds'],'16');
  assert.equal(config['connections.ai.model'],'configured-model');
  assert.equal(config['app.agent.input-usd-per-million'],'0');
  for(const value of ['private-agent-key','configured-model'])assert.ok(!JSON.stringify(publicConnections(config)).includes(value));
});

test('OpenAI and Bedrock runtime selection uses the shared private configuration', () => {
 const c=readConnections({AI_PROVIDER:'BEDROCK',AI_AUTH_MODE:'AWS_SIGV4',AI_REGION:'us-east-2',AI_BASE_URL:'https://bedrock-runtime.us-east-2.amazonaws.com/openai/v1'});
 assert.equal(c['connections.ai.provider'],'BEDROCK');assert.equal(c['connections.ai.auth-mode'],'AWS_SIGV4');assert.equal(c['connections.ai.region'],'us-east-2');
 assert.ok(!JSON.stringify(publicConnections(c)).includes('bedrock-runtime'));
});


test('approved accounting mappings and manual-write controls stay server-only', () => {
 const c=readConnections({LEDGER_ACCOUNT_PROVIDER_INR:'149999',LEDGER_ACCOUNTING_POLICY_VERSION:'approved-test-v2'});
 assert.equal(c['app.ledger.accounting.accounts.provider-inr'],'149999');
 assert.equal(c['app.ledger.accounting.accounts.gas-clearing-eth'],'130001');
 assert.equal(c['app.ledger.manual-api-enabled'],'false');
 assert.ok(!JSON.stringify(publicConnections(c)).includes('approved-test-v2'));
});


test('local startup waits are configurable and stay outside browser configuration',()=>{
 const config=readConnections({LOCAL_START_WAIT_SECONDS:'700',LOCAL_FRONTEND_START_TIMEOUT_MS:'40000'});
 assert.equal(config['local.validation.compose-wait-seconds'],'700');
 assert.equal(config['local.validation.frontend-start-timeout-ms'],'40000');
 const exposed=JSON.stringify(publicConnections(config));
 assert.ok(!exposed.includes('700') && !exposed.includes('40000'));
});

test('internal identity client and scope never enter browser configuration', () => {
 const c=readConnections({IDENTITY_SERVICE_CLIENT_ID:'privateInternalClient',IDENTITY_SERVICE_SCOPE:'tenant/internal'});
 assert.equal(c['connections.identity.service-client-id'],'privateInternalClient');
 assert.equal(c['connections.identity.service-scope'],'tenant/internal');
 assert.ok(!JSON.stringify(publicConnections(c)).includes('privateInternalClient'));
 assert.ok(!JSON.stringify(publicConnections(c)).includes('tenant/internal'));
});


test('workflow gates, simulation assumptions and command destinations remain private', () => {
 const config=readConnections({PAYMENT_FLOW_ENABLED:'true',PAYMENT_EXECUTION_ENABLED:'false',SQS_FLOW_SUBMISSION_QUEUE_NAME:'customer-submit',FLOW_SIMULATED_FEE_USD:'3.50'});
 assert.equal(config['app.flow.enabled'],'true');assert.equal(config['app.flow.execution-enabled'],'false');
 assert.equal(config['app.sqs.flow-submission-queue-name'],'customer-submit');assert.equal(config['app.flow.simulation.fee-usd'],'3.50');
 const exposed=JSON.stringify(publicConnections(config));assert.ok(!exposed.includes('customer-submit'));assert.ok(!exposed.includes('simulation-v1'));
});

test('payout simulation controls and lifecycle queues stay in private shared configuration',()=>{
 const c=readConnections({FLOW_SIMULATED_FX_INR:'84.25',VISA_SIMULATOR_OPERATIONS_ENABLED:'true',SQS_LEDGER_PAYOUT_QUEUE_NAME:'private-payout-ledger'});
 assert.equal(c['app.flow.simulation.fx-inr'],'84.25');assert.equal(c['visa.simulator.operations-enabled'],'true');assert.equal(c['app.sqs.ledger-payout-queue-name'],'private-payout-ledger');
 assert.equal(c['visa.simulator.payout-scenario'],'SUCCEEDED');assert.ok(!JSON.stringify(publicConnections(c)).includes('private-payout-ledger'));assert.ok(!JSON.stringify(publicConnections(c)).includes('84.25'));
});


test('browser identity endpoints are public while the development allowlist stays server-only',()=>{
 const config=readConnections({IDENTITY_AUTHORIZATION_URI:'https://id.test/oauth2/authorize',IDENTITY_TOKEN_URI:'https://id.test/oauth2/token',FRONTEND_DEV_ALLOWED_HOSTS:'private-codespace.test'});
 const exposed=publicConnections(config);
 assert.equal(exposed.identity.authorizationUri,'https://id.test/oauth2/authorize');
 assert.equal(exposed.identity.tokenUri,'https://id.test/oauth2/token');
 assert.equal(exposed.blockchain.chainId,84532);
 assert.equal(exposed.blockchain.explorerBaseUrl,'https://sepolia.basescan.org');
 assert.ok(!JSON.stringify(exposed).includes('private-codespace.test'));
});

test('browser token renewal is public and configurable, without exposing credentials',()=>{
 assert.equal(publicConnections(readConnections({})).identity.autoRefreshEnabled,true);
 assert.equal(publicConnections(readConnections({IDENTITY_AUTO_REFRESH_ENABLED:'false'})).identity.autoRefreshEnabled,false);
});
