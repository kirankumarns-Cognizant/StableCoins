'use strict';
const fs = require('node:fs');
const path = require('node:path');
const solc = require('solc');
const {keccak256} = require('ethers');
const ROOT = path.resolve(__dirname, '../..');
function compile() {
  const input = {
    language: 'Solidity',
    sources: {'BesuDemoUSD.sol': {content: fs.readFileSync(path.join(__dirname, 'contracts/BesuDemoUSD.sol'), 'utf8')}},
    settings: {optimizer: {enabled: true, runs: 200}, evmVersion: 'london',
      outputSelection: {'*': {'*': ['abi', 'evm.bytecode.object', 'evm.deployedBytecode.object']}}}
  };
  const output = JSON.parse(solc.compile(JSON.stringify(input), {import(name) {
    // Resolve only the pinned library, never arbitrary filesystem imports.
    if (!name.startsWith('@openzeppelin/contracts/') || name.includes('..')) return {error: 'IMPORT_FORBIDDEN'};
    return {contents: fs.readFileSync(path.join(__dirname, 'node_modules', name), 'utf8')};
  }}));
  if (output.errors?.some(e => e.severity === 'error')) throw new Error('CONTRACT_COMPILATION_FAILED');
  const result = output.contracts['BesuDemoUSD.sol'].BesuDemoUSD;
  return {schemaVersion: 1, compiler: solc.version(), evmVersion: 'london', library: '@openzeppelin/contracts@5.4.0',
    abi: result.abi, bytecode: '0x' + result.evm.bytecode.object,
    runtimeCodeHash: keccak256('0x' + result.evm.deployedBytecode.object)};
}
if (require.main === module) {
  try {
    const dir = path.join(ROOT, '.local/besu-build');
    fs.mkdirSync(dir, {recursive: true, mode: 0o700});
    fs.writeFileSync(path.join(dir, 'BesuDemoUSD.json'), JSON.stringify(compile(), null, 2) + '\n', {mode: 0o600});
    console.log('BESU_DEMOUSD_COMPILED; no deployment or wallet operation');
  } catch { console.error('CONTRACT_COMPILATION_FAILED'); process.exitCode = 1; }
}
module.exports = {compile};
