'use strict';
const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const {status} = require('../credential-status.cjs');

test('missing credentials report setup required without changing files', t => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'besu-credential-status-'));
  t.after(() => fs.rmSync(root, {recursive:true,force:true}));
  const result = status(root);
  assert.equal(result.status, 'BESU_CREDENTIAL_SETUP_REQUIRED');
  assert.equal(result.files.BESU_KEYSTORE_FILE.exists, false);
  assert.equal(result.passwordVerified, false);
  assert.equal(result.rpcReachabilityChecked, false);
  assert.equal(result.transactionSubmitted, false);
  assert.deepEqual(fs.readdirSync(root), []);
});
test('status never reads password contents or claims password verification', t => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'besu-credential-status-'));
  t.after(() => fs.rmSync(root, {recursive:true,force:true}));
  fs.mkdirSync(path.join(root,'.local/besu-wallets'), {recursive:true,mode:0o700});
  fs.mkdirSync(path.join(root,'.local/secrets'), {recursive:true,mode:0o700});
  for (const file of ['besu-wallets/treasury-wallet.json','besu-wallets/beneficiary-wallet.json','secrets/besu-treasury-password'])
    fs.writeFileSync(path.join(root,'.local',file), 'SENSITIVE_FIXTURE_NOT_FOR_OUTPUT', {mode:0o600});
  const result = status(root);
  assert.equal(result.files.BESU_PASSWORD_FILE.exists, true);
  assert.equal(result.passwordVerified, false);
  assert.ok(!JSON.stringify(result).includes('SENSITIVE_FIXTURE'));
  assert.equal(result.status, process.platform === 'win32' ? 'BESU_CREDENTIAL_FILES_PRESENT_ACL_CHECK_REQUIRED' : 'BESU_CREDENTIAL_FILES_PRESENT');
});
test('Linux group-readable password does not pass readiness', {skip:process.platform==='win32'}, t => {
  const root=fs.mkdtempSync(path.join(os.tmpdir(),'besu-credential-status-'));
  t.after(()=>fs.rmSync(root,{recursive:true,force:true}));
  fs.mkdirSync(path.join(root,'.local/secrets'),{recursive:true});
  const file=path.join(root,'.local/secrets/besu-treasury-password');
  fs.writeFileSync(file,'fixture');fs.chmodSync(file,0o644);
  assert.equal(status(root).files.BESU_PASSWORD_FILE.ownerOnly,false);
  assert.equal(status(root).status,'BESU_CREDENTIAL_SETUP_REQUIRED');
});