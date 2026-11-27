'use strict';
const {test} = require('node:test');
const assert = require('node:assert/strict');
const {environment, exportsForBash} = require('../runtime-context.cjs');
const fixture = () => ({
  networkId:'besu-local', chainId:20261125, classification:'PRIVATE_DEMO', feeModel:'EXECUTION_ONLY', gasAsset:'BESU_GAS',
  genesisHash:'0x'+'a'.repeat(64), treasury:'0x'+'1'.repeat(40), beneficiary:'0x'+'2'.repeat(40),
  validators:[3,4,5,6].map(i=>'0x'+String(i).repeat(40)),
  token:{contract:'0x'+'7'.repeat(40),codeHash:'0x'+'b'.repeat(64)}
});
test('exports only verified private identity metadata with no signing/payment gate',()=>{
  const values=environment(fixture()), shell=exportsForBash(values);
  assert.equal(values.BESU_RPC_URL,'http://127.0.0.1:8547');
  assert.equal(values.BESU_READ_ONLY_ACCEPTANCE,'PRIVATE_DEMO');
  assert(!shell.includes('PASSWORD')); assert(!shell.includes('KEYSTORE'));
  assert(!shell.includes('PRIVATE_DEMO_ENABLED'));
  assert(shell.includes("export BESU_GENESIS_HASH='0x"+'a'.repeat(64)+"'"));
});
test('fails closed for public chain, incomplete deployment, repeated validators and shell fragments',()=>{
  for(const mutate of [
    m=>m.chainId=84532,m=>delete m.token,m=>m.validators[0]=m.validators[1],
    m=>m.genesisHash="x'; arbitrary",m=>m.beneficiary=m.treasury
  ]) { const m=fixture();mutate(m);assert.throws(()=>environment(m)); }
  assert.throws(()=>environment(fixture(),'https://arbitrary.invalid'));
  assert.throws(()=>exportsForBash({BESU_RPC_URL:"x'; arbitrary"}));
});
