'use strict';
const n = require('./network.cjs');

/** Public metadata only: no wallet/key/password reads. Server-owned identity, never user/model RPC input. */
function environment(m, rpcUrl = n.RPC_URL) {
  if (m.networkId !== 'besu-local' || m.chainId !== n.CHAIN_ID || m.classification !== 'PRIVATE_DEMO' ||
      m.feeModel !== 'EXECUTION_ONLY' || m.gasAsset !== 'BESU_GAS' || !m.token)
    n.fail('BESU_DEPLOYED_PRIVATE_CONTEXT_REQUIRED');
  const validators = m.validators?.map(n.address);
  if (!validators || validators.length !== 4 || new Set(validators).size !== 4)
    n.fail('BESU_VALIDATOR_SET_MISMATCH');
  const treasury = n.address(m.treasury), beneficiary = n.address(m.beneficiary);
  if (treasury === beneficiary) n.fail('BESU_PARTICIPANTS_INVALID');
  // Do not accept arbitrary URLs or shell fragments in generated operator exports.
  if (rpcUrl !== n.RPC_URL) n.fail('BESU_LOCAL_READINESS_RPC_REQUIRED');
  return Object.freeze({
    BESU_READ_ONLY_ACCEPTANCE: 'PRIVATE_DEMO',
    BESU_RPC_URL: rpcUrl,
    BESU_GENESIS_HASH: n.hash(m.genesisHash),
    BESU_TOKEN_CONTRACT: n.address(m.token.contract),
    BESU_TOKEN_CODE_HASH: n.hash(m.token.codeHash),
    BESU_TREASURY_ADDRESS: treasury,
    BESU_RECIPIENT_ADDRESS: beneficiary,
    BESU_RECIPIENT_ADDRESSES: beneficiary,
    BESU_VALIDATOR_ADDRESSES: validators.join(',')
  });
}
function exportsForBash(values) {
  return Object.entries(values).map(([key, value]) => {
    if (!/^BESU_[A-Z_]+$/.test(key) || !/^[a-zA-Z0-9_,:/.-]+$/.test(value))
      n.fail('UNSAFE_BESU_RUNTIME_EXPORT');
    return `export ${key}='${value}'`;
  }).join('\n') + '\n';
}
async function main() {
  const m = n.manifest();
  await n.tokenIdentity(m, n.rpcClient()); // Export only an actually verified deployed identity.
  const file = require('node:path').join(n.STATE, 'readiness.env');
  // Re-running is safe: this contains identity metadata only and grants no payment enablement.
  const fs = require('node:fs');
  if (fs.existsSync(file)) n.secureFile(file);
  fs.writeFileSync(file, exportsForBash(environment(m)), {mode: 0o600});
  return {status:'BESU_READ_ONLY_CONTEXT_WRITTEN', file, transactionSubmitted:false,
    applicationExecutionEnabled:false};
}
if (require.main === module) main().then(result => console.log(n.json(result))).catch(() => {
  console.error(n.json({status:'FAILED', code:'BESU_READ_ONLY_CONTEXT_NOT_VERIFIED'}));
  process.exitCode = 1;
});
module.exports = {environment, exportsForBash, main};
