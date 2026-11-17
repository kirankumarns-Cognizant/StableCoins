const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const {parseEnv} = require('node:util');
const {plan, configure, subjects} = require('./configure-oregon-demo.cjs');
const {readConnections} = require('../services/config/read-connections.cjs');
const shared = fs.readFileSync(path.join(__dirname, '../services/config/connections.properties'), 'utf8');

function fixture(t) {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'stablecoins-oregon-'));
  t.after(() => fs.rmSync(root, {recursive: true, force: true}));
  fs.mkdirSync(path.join(root, '.local'));
  fs.mkdirSync(path.join(root, 'services/config'), {recursive: true});
  fs.writeFileSync(path.join(root, 'services/config/connections.properties'), shared);
  const env = 'DB_PASSWORD=synthetic-private-value\nCONNECTIONS_CONFIG_FILE=./.local/demo-connections.properties\nIDENTITY_REDIRECT_URI=https://demo-5173.app.github.dev/auth/callback\nAWS_REGION=us-east-1\nROUTING_AGENT_ENABLED=true\nBLOCKCHAIN_READINESS_TTL=PT15M\n';
  let config = shared + fs.readFileSync(path.join(__dirname, '../services/config/hackathon-customer-access.example.properties'), 'utf8');
  for (let i = 0; i < subjects.length; i++) {
    const role = ['operator', 'approver-one', 'approver-two'][i % 3];
    config = config.replaceAll(`replace-${i < 3 ? 'a' : 'b'}-${role}-sub`, subjects[i][0]);
  }
  fs.writeFileSync(path.join(root, '.env'), env, {mode: 0o600});
  fs.writeFileSync(path.join(root, '.local/demo-connections.properties'), config, {mode: 0o644});
  return {root, env, config};
}

test('preview leaves both private files unchanged and never prints their values', t => {
  const f = fixture(t);
  const result = configure(f.root);
  assert.equal(result.mode, 'PREVIEW');
  assert.equal(result.changed, true);
  assert.ok(!JSON.stringify(result).includes('synthetic-private-value'));
  assert.equal(fs.readFileSync(path.join(f.root, '.env'), 'utf8'), f.env);
  assert.equal(fs.readFileSync(path.join(f.root, '.local/demo-connections.properties'), 'utf8'), f.config);
  assert.equal(fs.existsSync(path.join(f.root, '.local/backups')), false);
});

test('apply changes the effective identity and six subjects, preserving policies, passwords and TTLs', t => {
  const f = fixture(t);
  const result = configure(f.root, true);
  const env = parseEnv(fs.readFileSync(path.join(f.root, '.env'), 'utf8'));
  const configPath = path.join(f.root, '.local/demo-connections.properties');
  const actual = readConnections(env, configPath);
  assert.equal(actual['connections.aws.region'], 'us-west-2');
  assert.equal(actual['connections.ai.region'], 'us-west-2');
  assert.equal(actual['connections.identity.client-id'], '7u19f3qbnaot6mf8f0tv7295fo');
  assert.equal(actual['connections.identity.service-client-id'], '2168jq8fqbnurpa15frr2j7ljj');
  assert.equal(actual['connections.identity.issuer-uri'], 'https://cognito-idp.us-west-2.amazonaws.com/us-west-2_GHIZSiqQY');
  assert.equal(actual['app.agent.enabled'], 'false');
  assert.equal(env.DB_PASSWORD, 'synthetic-private-value');
  assert.equal(env.BLOCKCHAIN_READINESS_TTL, 'PT15M');
  assert.equal(env.IDENTITY_REDIRECT_URI, 'https://demo-5173.app.github.dev/auth/callback');
  const policyLines = text => text.split(/\r?\n/).filter(line => line.startsWith('app.routing.policies['));
  assert.deepEqual(policyLines(fs.readFileSync(configPath, 'utf8')), policyLines(f.config));
  for (const [oldSub, newSub] of subjects) {
    assert.ok(!Object.keys(actual).some(key => key.startsWith(`app.access.memberships[${oldSub}]`)));
    assert.equal(actual[`app.access.memberships[${newSub}].roles[0]`], 'PAYMENT_OPERATOR');
  }
  assert.equal(fs.readFileSync(path.join(f.root, result.backup, 'env.bak'), 'utf8'), f.env);
  assert.equal(fs.readFileSync(path.join(f.root, result.backup, 'connections.bak'), 'utf8'), f.config);
  if (process.platform !== 'win32') {
    assert.equal(fs.statSync(path.join(f.root, '.env')).mode & 0o777, 0o600);
    assert.equal(fs.statSync(configPath).mode & 0o777, 0o644);
    assert.equal(fs.statSync(path.join(f.root, result.backup, 'env.bak')).mode & 0o777, 0o600);
  }
  assert.equal(configure(f.root, true).changed, false);
  assert.equal(fs.readdirSync(path.join(f.root, '.local/backups')).length, 1);
});

test('incomplete, duplicate and customized demo memberships fail before private writes', t => {
  const f = fixture(t);
  const prefix = `app.access.memberships[${subjects[0][0]}].`;
  const invalid = [
    f.config.replace(prefix + 'roles[0]=PAYMENT_OPERATOR', '# missing membership role'),
    f.config + '\n' + prefix + 'roles[0]=PAYMENT_OPERATOR\n',
    f.config.replace(prefix + 'roles[0]=PAYMENT_OPERATOR', prefix + 'roles[0]=PAYMENT_APPROVER'),
    f.config + '\n' + prefix.replace(subjects[0][0], subjects[0][1]) + 'roles[0]=PAYMENT_OPERATOR\n',
  ];
  for (const config of invalid) {
    fs.writeFileSync(path.join(f.root, '.local/demo-connections.properties'), config);
    assert.throws(() => configure(f.root, true), /memberships/);
    assert.equal(fs.readFileSync(path.join(f.root, '.env'), 'utf8'), f.env);
    assert.equal(fs.existsSync(path.join(f.root, '.local/backups')), false);
  }
});

test('an unexpected private file target or callback is rejected without mutation', t => {
  const f = fixture(t);
  fs.writeFileSync(path.join(f.root, '.env'), f.env.replace('./.local/demo-connections.properties', '../outside.properties'));
  assert.throws(() => configure(f.root, true), /CONNECTIONS_CONFIG_FILE/);
  for (const callback of ['http://demo.test/auth/callback', 'https://user:synthetic@demo.test/auth/callback', 'https://demo.test/auth/callback?code=synthetic'])
    assert.throws(() => plan(f.env.replace('https://demo-5173.app.github.dev/auth/callback', callback), f.config, shared), /HTTPS/);
});

test('duplicate region variables and literal external identity overrides cannot retain east endpoints', t => {
  const f = fixture(t);
  const next = plan(f.env + 'export AWS_REGION=us-east-1\n', f.config + '\nconnections.identity.issuer-uri=https://old.test/pool\n', shared);
  assert.equal(next.env.match(/^AWS_REGION=/gm).length, 1);
  assert.equal(parseEnv(next.env).AWS_REGION, 'us-west-2');
  assert.ok(!next.config.includes('https://old.test/pool'));
});
