'use strict';
const fs = require('node:fs');
const path = require('node:path');

/** Existence/permissions only: never read a wallet or password or contact RPC. */
function status(root = path.resolve(__dirname, '../..')) {
  const expected = {
    BESU_KEYSTORE_FILE: '.local/besu-wallets/treasury-wallet.json',
    BESU_BENEFICIARY_FILE: '.local/besu-wallets/beneficiary-wallet.json',
    BESU_PASSWORD_FILE: '.local/secrets/besu-treasury-password'
  };
  const files = Object.fromEntries(Object.entries(expected).map(([key, relative]) => {
    const file = path.join(root, relative);
    let info;
    try { info = fs.lstatSync(file); }
    catch (e) { if (e.code !== 'ENOENT') throw e; }
    const parents = [path.join(root, '.local'), path.dirname(file)];
    const symlink = parents.some(p => { try { return fs.lstatSync(p).isSymbolicLink(); } catch { return false; } });
    const regularFile = Boolean(info?.isFile()) && !symlink;
    const ownerOnly = process.platform === 'win32' ? null : Boolean(regularFile && (info.mode & 0o777) === 0o600);
    return [key, {path: relative, exists: Boolean(info), regularFile, ownerOnly}];
  }));
  return {status: Object.values(files).every(f => f.regularFile && f.ownerOnly === true)
    ? 'BESU_CREDENTIAL_FILES_PRESENT' : process.platform === 'win32' && Object.values(files).every(f => f.regularFile) ? 'BESU_CREDENTIAL_FILES_PRESENT_ACL_CHECK_REQUIRED' : 'BESU_CREDENTIAL_SETUP_REQUIRED',
    BESU_RPC_URL: 'http://127.0.0.1:8547', rpcReachabilityChecked: false,
    files, passwordVerified: false, applicationExecutionEnabled: false, transactionSubmitted: false};
}
if (require.main === module) {
  try { console.log(JSON.stringify(status(), null, 2)); }
  catch { console.error('BESU_CREDENTIAL_STATUS_FAILED'); process.exitCode = 1; }
}
module.exports = {status};