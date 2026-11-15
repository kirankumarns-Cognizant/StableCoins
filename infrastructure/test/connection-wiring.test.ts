import { readFileSync } from 'node:fs';
import { join } from 'node:path';

interface ServiceConfig {
  environment: Record<string, string | null>;
  volumes: string[];
  ports: string[];
  env_file?: unknown;
}
const { load } = require('js-yaml') as { load: (source: string) => unknown };
const root = join(__dirname, '../..');
const compose = load(readFileSync(join(root, 'docker-compose.yml'), 'utf8')) as {
  services: Record<string, ServiceConfig>;
};
const services: Array<[string, number]> = [
  ['payment-service', 8080], ['compliance-quote-service', 8081],
  ['blockchain-service', 8082], ['settlement-ledger-service', 8083],
  ['reconciliation-service', 8084], ['visa-simulator', 8086],
];

test.each(services)('%s imports shared settings with consistent container ports and scoped signing secrets', (name, port) => {
  const service = compose.services[name];
  expect(service.environment.CONNECTIONS_CONFIG).toBe('file:/app/config/connections.properties');
  expect(service.volumes[0]).toMatch(/:\/app\/config\/connections.properties:ro$/);
  expect(service.environment.BLOCKCHAIN_MODE).toBe('MOCK');
  expect(service.environment.TESTNET_ENABLED).toBe('false');
  expect(service.env_file).toBeUndefined();
  expect(Object.hasOwn(service.environment, 'BLOCKCHAIN_WALLET_PRIVATE_KEY')).toBe(name === 'blockchain-service');
  expect(Object.hasOwn(service.environment, 'IDENTITY_ISSUER_URI')).toBe(true);
  expect(Object.hasOwn(service.environment, 'ROUTING_ASSESSMENT_VERSION')).toBe(true);
  for (const setting of ['IDENTITY_HTTP_TIMEOUT_MS', 'SQS_ROUTING_DECISIONS_QUEUE_NAME', 'SQS_ROUTING_DECISIONS_DLQ_NAME', 'BLOCKCHAIN_RESERVATION_TTL', 'BLOCKCHAIN_READINESS_TTL', 'BLOCKCHAIN_GAS_LIMIT_MULTIPLIER',
    'BLOCKCHAIN_GAS_PRICE_MULTIPLIER', 'BLOCKCHAIN_ADDITIONAL_GAS_RESERVE_ETH', 'BLOCKCHAIN_EXECUTION_API_ENABLED',
    'VISA_SIMULATOR_ALLOWED_PROVIDER_IDS', 'VISA_SIMULATOR_RESERVATION_TTL']) {
    expect(Object.hasOwn(service.environment, setting)).toBe(true);
  }
  expect(service.ports.some(mapping => mapping.endsWith(':' + port))).toBe(true);
  const app = load(readFileSync(join(root, 'services', name, 'src/main/resources/application.yml'), 'utf8')) as {
    spring: { config: { import: string } }; server: { port: string };
  };
  expect(app.spring.config.import).toContain('classpath:connections.properties');
  expect(app.server.port.endsWith(':' + port + '}')).toBe(true);
});


test('local Compose limits host exposure and initializes each owner history without skipping V1', () => {
  for (const [name] of services) {
    expect(compose.services[name].environment.LOCAL_SCHEMA_BOOTSTRAP).toBe('true');
    expect(compose.services[name].ports.every(mapping=>mapping.startsWith('127.0.0.1:'))).toBe(true);
  }
  for (const name of ['postgres','localstack']) expect(compose.services[name].ports.every(mapping=>mapping.startsWith('127.0.0.1:'))).toBe(true);
  const {readConnections}=require('../../services/config/read-connections.cjs');
  expect(readConnections({})['spring.flyway.baseline-on-migrate']).toBe('false');
  expect(readConnections({LOCAL_SCHEMA_BOOTSTRAP:'true'})['spring.flyway.baseline-on-migrate']).toBe('true');
  expect(readConnections({})['spring.flyway.baseline-version']).toBe('0');
});
