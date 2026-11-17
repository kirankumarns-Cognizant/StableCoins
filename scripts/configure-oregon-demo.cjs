#!/usr/bin/env node
// Public deployment metadata only. Credential values never belong in this file.
const fs = require('node:fs');
const path = require('node:path');
const {parseEnv} = require('node:util');
const {randomUUID} = require('node:crypto');

const pool = 'us-west-2_GHIZSiqQY';
const issuer = `https://cognito-idp.us-west-2.amazonaws.com/${pool}`;
const login = 'https://stablecoins-640237069320-demo-usw2.auth.us-west-2.amazoncognito.com';
const settings = {
  AWS_REGION: 'us-west-2', AI_REGION: 'us-west-2',
  IDENTITY_PROVIDER: 'COGNITO', IDENTITY_ISSUER_URI: issuer,
  IDENTITY_JWK_SET_URI: issuer + '/.well-known/jwks.json',
  IDENTITY_CLIENT_ID: '7u19f3qbnaot6mf8f0tv7295fo',
  IDENTITY_SERVICE_CLIENT_ID: '2168jq8fqbnurpa15frr2j7ljj',
  IDENTITY_SERVICE_SCOPE: 'stablecoins/internal', IDENTITY_AUDIENCE: '',
  IDENTITY_AUTHORIZATION_URI: login + '/oauth2/authorize',
  IDENTITY_TOKEN_URI: login + '/oauth2/token',
  ROUTING_AGENT_ENABLED: 'false',
};
const subjects = [
  ['247824c8-3061-70d6-0d45-6955c0015bb6', '18d1b330-c071-707d-09da-71f04692cbfb', 'a', false],
  ['34888428-6031-70b4-4976-83af1d3c9c8b', 'f84133c0-b0b1-70af-8fa5-916b0ff50c69', 'a', true],
  ['c47804e8-8011-7069-bec0-261bdc17db22', '1871d330-0091-7084-c14f-b9930b0569bd', 'a', true],
  ['64380428-6071-7008-3f64-e174f45151bb', '3821c3e0-a0e1-70e1-cb85-858ea69bfd71', 'b', false],
  ['e40874d8-7061-7043-4ca5-406dfc6d7e71', '18010300-30d1-709b-789a-153387f9257e', 'b', true],
  ['247804d8-60c1-7041-4c1e-29e421671681', '58513350-e0c1-70d5-df55-0cd33ed7ed83', 'b', true],
];

function replaceProperties(text, values, environment = false) {
  const lines = text.split(/\r?\n/).filter(line => {
    const match = line.match(environment ? /^\s*(?:export\s+)?([\w.\-]+)\s*=/ : /^\s*([^#!=\s][^=]*?)\s*=/);
    return !match || !Object.hasOwn(values, match[1].trim());
  });
  while (lines.at(-1) === '') lines.pop();
  return lines.join('\n') + '\n' + Object.entries(values).map(([k, v]) => k + '=' + v).join('\n') + '\n';
}

function plan(envText, configText, sharedText) {
  const env = parseEnv(envText);
  // Reuse the configured HTTPS callback; changing regions must not change the UI host.
  const callback = new URL(env.IDENTITY_REDIRECT_URI || 'invalid:');
  if (callback.protocol !== 'https:' || callback.username || callback.password || callback.search || callback.hash || callback.pathname !== '/auth/callback')
    throw Error('Expected the existing HTTPS /auth/callback setting; no files changed.');
  let config = configText;
  for (const [oldSub, newSub, customer, approver] of subjects) {
    const oldPrefix = `app.access.memberships[${oldSub}].`;
    const newPrefix = `app.access.memberships[${newSub}].`;
    const entries = config.split(/\r?\n/).map(line => line.trim()).filter(line => line.startsWith(oldPrefix) || line.startsWith(newPrefix));
    const expected = [
      `customer-id=${customer === 'a' ? '11111111-1111-1111-1111-111111111111' : '55555555-5555-5555-5555-555555555555'}`,
      'roles[0]=PAYMENT_OPERATOR', ...(approver ? ['roles[1]=PAYMENT_APPROVER'] : []),
    ].sort();
    const actual = entries.map(line => line.slice(line.indexOf('].') + 2)).sort();
    const prefixes = new Set(entries.map(line => line.slice(0, line.indexOf('].') + 2)));
    if (prefixes.size !== 1 || JSON.stringify(actual) !== JSON.stringify(expected))
      throw Error('Demo memberships are missing, duplicated or customized; review privately before migration. No files changed.');
    config = config.split(/\r?\n/).map(line => line.trimStart().startsWith(oldPrefix) ? line.replace(oldPrefix, newPrefix) : line).join('\n');
  }
  // Refresh only the managed placeholders, preserving policies, wallets, TTLs and other settings.
  const keys = ['connections.aws.region', 'connections.ai.region', 'app.agent.enabled',
    ...Object.keys(settings).filter(key => key.startsWith('IDENTITY_')).map(key => ({
      IDENTITY_PROVIDER: 'connections.identity.provider', IDENTITY_ISSUER_URI: 'connections.identity.issuer-uri',
      IDENTITY_JWK_SET_URI: 'connections.identity.jwk-set-uri', IDENTITY_CLIENT_ID: 'connections.identity.client-id',
      IDENTITY_SERVICE_CLIENT_ID: 'connections.identity.service-client-id', IDENTITY_SERVICE_SCOPE: 'connections.identity.service-scope',
      IDENTITY_AUDIENCE: 'connections.identity.audience', IDENTITY_AUTHORIZATION_URI: 'connections.identity.authorization-uri',
      IDENTITY_TOKEN_URI: 'connections.identity.token-uri',
    })[key])];
  const shared = Object.fromEntries(sharedText.split(/\r?\n/).filter(line => line && !line.startsWith('#') && line.includes('=')).map(line => {
    const i = line.indexOf('='); return [line.slice(0, i), line.slice(i + 1)];
  }));
  if (keys.some(key => !key || !Object.hasOwn(shared, key))) throw Error('Shared configuration contract is incomplete.');
  config = replaceProperties(config, Object.fromEntries(keys.map(key => [key, shared[key]])));
  return {env: replaceProperties(envText, settings, true), config};
}

function privatePath(root, file) {
  const target = path.resolve(root, file);
  if (!target.startsWith(path.resolve(root) + path.sep)) throw Error('Configuration path is outside the workspace.');
  let cursor = target;
  while (cursor !== path.resolve(root)) {
    if (fs.existsSync(cursor) && fs.lstatSync(cursor).isSymbolicLink()) throw Error('Configuration symlinks are not supported.');
    cursor = path.dirname(cursor);
  }
  return target;
}

function configure(root, apply = false) {
  const envPath = privatePath(root, '.env');
  const configPath = privatePath(root, '.local/demo-connections.properties');
  const envText = fs.readFileSync(envPath, 'utf8');
  const configured = parseEnv(envText).CONNECTIONS_CONFIG_FILE;
  if (!configured || path.resolve(root, configured) !== configPath)
    throw Error('Expected CONNECTIONS_CONFIG_FILE=./.local/demo-connections.properties. No files changed.');
  const configText = fs.readFileSync(configPath, 'utf8');
  const next = plan(envText, configText, fs.readFileSync(path.join(root, 'services/config/connections.properties'), 'utf8'));
  const changed = next.env !== envText || next.config !== configText;
  if (!apply || !changed) return {mode: apply ? 'APPLIED' : 'PREVIEW', changed, pool, agentEnabled: false};
  const backup = privatePath(root, `.local/backups/oregon-demo-${randomUUID()}`);
  fs.mkdirSync(backup, {recursive: true, mode: 0o700});
  fs.writeFileSync(path.join(backup, 'env.bak'), envText, {flag: 'wx', mode: 0o600});
  fs.writeFileSync(path.join(backup, 'connections.bak'), configText, {flag: 'wx', mode: 0o600});
  try {
    fs.chmodSync(envPath, 0o600);
    fs.writeFileSync(envPath, next.env);
    fs.writeFileSync(configPath, next.config);
  } catch {
    try { fs.writeFileSync(envPath, envText); fs.writeFileSync(configPath, configText); }
    catch { throw Error('Update and rollback failed; restore the two files from ' + path.relative(root, backup)); }
    throw Error('Update failed and original contents were restored. Private backups retained.');
  }
  return {mode: 'APPLIED', changed, pool, agentEnabled: false, backup: path.relative(root, backup)};
}

if (require.main === module) {
  try {
    const args = process.argv.slice(2);
    if (args.length > 1 || args.some(arg => !['--apply', '--preview'].includes(arg))) throw Error('Usage: node scripts/configure-oregon-demo.cjs [--preview|--apply]');
    console.log(JSON.stringify(configure(path.resolve(__dirname, '..'), args[0] === '--apply'), null, 2));
    console.log('No AWS resources changed. Backend/frontend restart and fresh Oregon sign-in are required after applying.');
  } catch (error) {
    // Never echo configuration contents or raw parser/filesystem messages.
    console.error(error.code ? 'Configuration file unavailable; no successful update recorded.' : error instanceof TypeError || error instanceof SyntaxError ? 'Invalid private configuration; no successful update recorded.' : error.message);
    process.exitCode = 1;
  }
}
module.exports = {plan, configure, settings, subjects};
