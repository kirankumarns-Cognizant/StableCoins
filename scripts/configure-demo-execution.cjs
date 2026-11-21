#!/usr/bin/env node
// Explicit local demo setup. Does not start services, submit payments, or deploy AWS.
const fs = require('node:fs');
const path = require('node:path');
const {parseEnv} = require('node:util');
const settings = Object.freeze(require('../services/config/demo-execution.json'));

function configureText(text) {
  const before = parseEnv(text), newline = text.includes('\r\n') ? '\r\n' : '\n';
  const lines = text.split(/\r?\n/).filter(line => {
    const match = line.match(/^\s*(?:export\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*=/);
    return !match || !Object.hasOwn(settings, match[1]);
  });
  while (lines.at(-1) === '') lines.pop();
  const result = lines.join(newline) + newline + Object.entries(settings).map(([key,value]) => key+'='+value).join(newline) + newline;
  const after = parseEnv(result);
  // Reject unusual multiline input if line replacement would affect unrelated values.
  for (const key of new Set([...Object.keys(before), ...Object.keys(after)])) {
    if (after[key] !== (Object.hasOwn(settings,key) ? settings[key] : before[key]))
      throw Error('Environment layout requires private manual review; no files changed.');
  }
  return result;
}
function configure(directory = path.resolve(__dirname,'..')) {
  const file = path.join(directory,'.env');
  if (!fs.existsSync(file) || !fs.lstatSync(file).isFile())
    throw Error('An existing regular .env file is required; no files changed.');
  const before = fs.readFileSync(file,'utf8'), after = configureText(before);
  if (before !== after) fs.writeFileSync(file,after,{mode:0o600});
  return {changed:before!==after, settings};
}
if (require.main === module) {
  try {
    if (process.argv.length === 3 && process.argv[2] === '--ecs-environment') {
      console.log(JSON.stringify(Object.entries(settings).map(([name,value]) => ({name,value})),null,2));
      return;
    }
    if (process.argv.length !== 2) throw Error('Invalid setup arguments');
    const result = configure();
    console.log('Demo execution configuration '+(result.changed?'saved':'already configured')+' in .env.');
    for (const [key,value] of Object.entries(result.settings)) console.log(key+'='+value);
    console.log('Restart with: node --env-file=.env scripts/local-workbench.cjs start --show-failures');
    console.log('Existing identity, policy, approval, expiry and blockchain-mode checks still apply. No AWS deployment or payment was executed.');
  } catch { console.error('Demo setup failed. Check that .env is a regular file and its layout is valid. Usage: node scripts/configure-demo-execution.cjs'); process.exitCode=2; }
}
module.exports = {configureText,configure};
