'use strict';
const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const os=require('node:os');
const {Wallet,keccak256}=require('ethers');
const n=require('../network.cjs');
const {compile}=require('../compile.cjs');
const treasury='0x'+'1'.repeat(40),beneficiary='0x'+'2'.repeat(40);
const genesisHash='0x'+'a'.repeat(64);
const code='0x6001',codeHash=keccak256(code),contract='0x'+'3'.repeat(40);
const extra='0x1234';
function manifest() {return {schemaVersion:1,networkId:'besu-local',chainId:n.CHAIN_ID,
  classification:'PRIVATE_DEMO',feeModel:'EXECUTION_ONLY',gasAsset:'BESU_GAS',treasury,beneficiary,
  genesisHash,genesisExtraData:extra,validators:[treasury,beneficiary],
  token:{contract,codeHash}};}
function fixture(overrides={}) {
  const calls=[];
  const rpc=async(method,params=[])=>{
    calls.push(method);
    if(Object.hasOwn(overrides,method))return typeof overrides[method]==='function'?overrides[method](params):overrides[method];
    switch(method) {
      case 'eth_chainId':return '0x'+n.CHAIN_ID.toString(16);
      case 'eth_getBlockByNumber':return {hash:genesisHash,extraData:extra};
      case 'qbft_getValidatorsByBlockNumber':return [beneficiary,treasury];
      case 'eth_getCode':return code;
      case 'eth_call':{
        const parsed=n.ABI.parseTransaction({data:params[0].data});
        const value={decimals:6n,symbol:'DEMOUSD',totalSupply:n.SUPPLY,balanceOf:20_000000n}[parsed.name];
        return n.ABI.encodeFunctionResult(parsed.name,[value]);
      }
      case 'eth_blockNumber':return calls.filter(x=>x===method).length===1?'0x10':'0x12';
      case 'eth_getBalance':return '0xde0b6b3a7640000';
      case 'eth_gasPrice':return '0x3e8';
      case 'eth_estimateGas':return '0xf424';
      default:throw new Error('UNEXPECTED_TEST_RPC');
    }
  };
  return {rpc,calls};
}
test('genesis has dedicated funded accounts and four generated validators, no tutorial keys',()=>{
  const g=n.genesisConfig(treasury,beneficiary);
  assert.equal(g.genesis.config.chainId,n.CHAIN_ID);
  assert.equal(g.blockchain.nodes.count,4);
  assert.equal(g.genesis.config.londonBlock,0);
  assert.equal(g.genesis.config.zeroBaseFee,true);
  assert.deepEqual(Object.keys(g.genesis.alloc).sort(),[treasury.slice(2),beneficiary.slice(2)].sort());
  assert.ok(!JSON.stringify(g).includes('privateKey'));
  assert.throws(()=>n.genesisConfig(treasury,treasury),/DISTINCT_WALLETS_REQUIRED/);
});
test('all nodes enforce permissions and positive gas; only RPC node exposes bounded namespaces',()=>{
  for(let i=1;i<=5;i++) {
    const config=n.nodeConfig(i);
    assert.match(config,/permissions-nodes-config-file-enabled=true/);
    assert.match(config,/permissions-accounts-config-file-enabled=true/);
    assert.match(config,/min-gas-price=1000/);
    assert.doesNotMatch(config,/ADMIN|DEBUG|PERM|unlock|cors-origins=\["all"\]/);
    assert.match(config,new RegExp('rpc-http-enabled='+(i===5)));
  }
  const compose=fs.readFileSync(path.join(__dirname,'../compose.yml'),'utf8');
  assert.match(compose,/hyperledger\/besu:26\.9\.0/);
  assert.match(compose,/127\.0\.0\.1:8547:8545/);
  assert.match(compose,/internal: true/);
  assert.equal((compose.match(/ipv4_address:/g)||[]).length,5);
});
test('contract compiles for London and exposes no later mint, owner or upgrade capability',()=>{
  const artifact=compile();
  assert.equal(artifact.evmVersion,'london');
  assert.match(artifact.compiler,/^0\.8\.30/);
  assert.ok(artifact.bytecode.length>100);
  assert.match(artifact.runtimeCodeHash,/^0x[0-9a-f]{64}$/);
  const functions=artifact.abi.filter(x=>x.type==='function').map(x=>x.name);
  for(const forbidden of ['mint','burnFrom','owner','upgradeTo','setFee'])assert.ok(!functions.includes(forbidden));
  assert.ok(functions.includes('transfer'));
});
test('wrong chain or reset genesis is rejected before balances or signing',async()=>{
  for(const [method,result,reason] of [
    ['eth_chainId','0x14a34','BESU_WRONG_CHAIN'],
    ['eth_getBlockByNumber',{hash:'0x'+'b'.repeat(64),extraData:extra},'BESU_GENESIS_MISMATCH']
  ]) {
    const f=fixture({[method]:result});
    await assert.rejects(n.identity(manifest(),f.rpc),new RegExp(reason));
    assert.ok(!f.calls.includes('eth_getBalance'));
    assert.ok(!f.calls.includes('eth_sendRawTransaction'));
  }
});
test('wrong validator genesis or validator set is rejected',async()=>{
  await assert.rejects(n.identity(manifest(),fixture({'eth_getBlockByNumber':{hash:genesisHash,extraData:'0x5678'}}).rpc),/BESU_VALIDATOR_GENESIS_MISMATCH/);
  await assert.rejects(n.identity(manifest(),fixture({'qbft_getValidatorsByBlockNumber':[treasury]}).rpc),/BESU_VALIDATOR_SET_MISMATCH/);
});
test('token code, decimals, supply and symbol are verified independently',async()=>{
  await assert.rejects(n.tokenIdentity(manifest(),fixture({'eth_getCode':'0x6002'}).rpc),/BESU_TOKEN_CODE_MISMATCH/);
  for(const [name,wrong] of [['decimals',18n],['symbol','USDC'],['totalSupply',1n]]) {
    const f=fixture({'eth_call':params=>{
      const p=n.ABI.parseTransaction({data:params[0].data});
      const value=p.name===name?wrong:{decimals:6n,symbol:'DEMOUSD',totalSupply:n.SUPPLY}[p.name];
      return n.ABI.encodeFunctionResult(p.name,[value]);
    }});
    await assert.rejects(n.tokenIdentity(manifest(),f.rpc),/BESU_TOKEN_IDENTITY_MISMATCH/);
  }
});
test('read-only preflight checks block progress, gas coverage and both token balances',async()=>{
  const f=fixture();const result=await n.preflight(manifest(),f.rpc,'1',async()=>{});
  assert.equal(result.status,'PRIVATE_CHAIN_PREFLIGHT_READY');
  assert.equal(result.requestedTokenUnits,'1000000');
  assert.equal(result.nativeAsset,'BESU_GAS');
  assert.equal(result.gasLimit,'75000');
  assert.equal(result.requiredNativeWei,'75000000');
  assert.equal(result.transactionSubmitted,false);
  assert.equal(result.applicationIntegration,'NOT_ENABLED');
  assert.ok(!f.calls.some(x=>x.includes('send')||x.includes('sign')));
});
test('halted quorum, insufficient gas and malformed/oversized amounts fail closed',async()=>{
  await assert.rejects(n.preflight(manifest(),fixture({'eth_blockNumber':'0x10'}).rpc,'1',async()=>{}),/BESU_BLOCKS_NOT_PROGRESSING/);
  const result=await n.preflight(manifest(),fixture({'eth_getBalance':'0x1'}).rpc,'1',async()=>{});
  assert.equal(result.status,'BESU_FUNDING_REQUIRED');
  for(const value of ['0','-1','1.0000001','1e6','1000000000000'])assert.throws(()=>n.units(value),/INVALID_DEMOUSD_AMOUNT/);
});
test('RPC errors do not disclose server payloads and transport never retries submission',async()=>{
  let count=0;
  const rpc=n.rpcClient(async()=>{count++;return {ok:true,text:async()=>JSON.stringify({jsonrpc:'2.0',id:1,error:{message:'sensitive server details'}})};});
  await assert.rejects(rpc('eth_sendRawTransaction',['0x00']),/^Error: BESU_RPC_REJECTED$/);
  assert.equal(count,1);
});
test('RPC client validates response identity and HTTP failures',async()=>{
  const wrong=n.rpcClient(async()=>({ok:true,text:async()=>'{"jsonrpc":"2.0","id":99,"result":"0x1"}'}));
  await assert.rejects(wrong('eth_chainId'),/BESU_RPC_REJECTED/);
  const bad=n.rpcClient(async()=>({ok:false}));
  await assert.rejects(bad('eth_chainId'),/BESU_RPC_UNAVAILABLE_OR_INVALID/);
});
test('encrypted keystore checks never print or require the password',()=>{
  const tmp=fs.mkdtempSync(path.join(os.tmpdir(),'stableflow-besu-test-'));
  try {
    const file=path.join(tmp,'wallet.json');
    fs.writeFileSync(file,JSON.stringify({version:3,address:treasury.slice(2),crypto:{ciphertext:'cipher',kdf:'scrypt'}}),{mode:0o600});
    assert.equal(n.walletAddress(file),treasury);
    fs.writeFileSync(file,JSON.stringify({version:3,address:'0'.repeat(40),crypto:{ciphertext:'cipher',kdf:'scrypt'}}));
    assert.throws(()=>n.walletAddress(file),/INVALID_BESU_ADDRESS/);
  } finally {
    assert.equal(path.dirname(path.resolve(tmp)),path.resolve(os.tmpdir()));
    fs.rmSync(tmp,{recursive:true,force:true});
  }
});
test('CLI refuses implicit deployment and unexpected arguments',async()=>{
  const {main}=require('../cli.cjs');
  await assert.rejects(main(['deploy']),/EXPLICIT_PRIVATE_DEMO_DEPLOYMENT_CONFIRMATION_REQUIRED/);
  await assert.rejects(main(['start','--reset']),/UNEXPECTED_BESU_OPTION/);
});
