const {test}=require('node:test');
const assert=require('node:assert/strict');
const {preflight,amountUnits}=require('./base-sepolia-preflight.cjs');
const config={network:'base-sepolia',chainId:84532,tokenContract:'0x036CbD53842c5426634e7929541eC2318f3dCF7e',decimals:6,rpcUrl:'https://rpc.example.test/private-credential'};
const input={treasury:'0x'+'1'.repeat(40),beneficiary:'0x'+'2'.repeat(40),amount:'1'};
function rpc(values={}){
  const calls=[];
  return {calls,fetcher:async(url,options)=>{
    const request=JSON.parse(options.body);calls.push(request);
    assert.equal(options.redirect,'error');
    const defaults={eth_chainId:'0x14a34',eth_blockNumber:'0x123',eth_getCode:'0x60006000',eth_getBalance:'0x38d7ea4c68000'};
    const key=request.method==='eth_call'?(request.params[0].data==='0x313ce567'?'decimals':'balance'):request.method;
    const result=Object.hasOwn(values,key)?values[key]:(key==='decimals'?'0x6':key==='balance'?'0xf4240':defaults[key]);
    assert.ok(result!==undefined,'Unexpected method: '+request.method);
    return {ok:true,text:async()=>JSON.stringify({jsonrpc:'2.0',id:request.id,result})};
  }};
}
test('verifies actual balances at one block using only a fixed read-only RPC allowlist',async()=>{
  const mock=rpc();const r=await preflight(config,input,mock.fetcher);
  assert.equal(r.status,'RPC_BALANCES_READY');assert.equal(r.testUsdc,'1.000000');assert.equal(r.transactionSubmitted,false);
  assert.equal(r.signer,'NOT_LOADED_OR_VERIFIED');assert.match(r.gasEstimate,/NOT_CHECKED/);
  assert.deepEqual(mock.calls.map(c=>c.method),['eth_chainId','eth_blockNumber','eth_getCode','eth_call','eth_getBalance','eth_call']);
  for(const c of mock.calls.slice(2))assert.equal(c.params.at(-1),'0x123');
});
test('rejects wrong RPC chain before checking balances',async()=>{const m=rpc({eth_chainId:'0x1'});await assert.rejects(preflight(config,input,m.fetcher),/RPC_WRONG_CHAIN/);assert.equal(m.calls.length,1);});
test('insufficient token funding and absent ETH cannot pass',async()=>{
  for(const values of [{balance:'0xf423f'},{eth_getBalance:'0x0'}])assert.equal((await preflight(config,input,rpc(values).fetcher)).status,'FUNDING_REQUIRED');
});
test('rejects missing contract and wrong decimals',async()=>{
  await assert.rejects(preflight(config,input,rpc({eth_getCode:'0x'}).fetcher),/USDC_CONTRACT_CODE_MISSING/);
  await assert.rejects(preflight(config,input,rpc({decimals:'0x12'}).fetcher),/USDC_DECIMALS_MISMATCH/);
});
test('rejects wrong configured token or chain before RPC',async()=>{
  for(const change of [{chainId:1},{tokenContract:'0x'+'3'.repeat(40)},{network:'besu'},{decimals:18}])await assert.rejects(preflight({...config,...change},input,()=>assert.fail('RPC called')),/CONFIGURATION_REQUIRED/);
});
test('rejects zero/dummy/same wallets and invalid amounts before RPC',async()=>{
  for(const change of [{treasury:'0x'+'0'.repeat(40)},{treasury:'0x7e5f4552091a69125d5dfcb7b8c2659029395bdf'},{beneficiary:input.treasury},{amount:'0'},{amount:'1.0000001'},{amount:'1e3'},{beneficiary:'not-an-address'}])await assert.rejects(preflight(config,{...input,...change},()=>assert.fail('RPC called')));
  assert.equal(amountUnits('0.000001'),1n);
});
test('rejects malformed RPC responses and never exposes RPC credentials in network errors',async()=>{
  await assert.rejects(preflight(config,input,async()=>{throw Error(config.rpcUrl)}),/^Error: RPC_UNAVAILABLE$/);
  for(const body of ['bad-json',JSON.stringify({jsonrpc:'2.0',id:99,result:'0x14a34'}),JSON.stringify({jsonrpc:'2.0',id:1,error:{message:config.rpcUrl}})])await assert.rejects(preflight(config,input,async()=>({ok:true,text:async()=>body})),/RPC_INVALID_RESPONSE|RPC_REJECTED_OR_INVALID_RESPONSE/);
});
test('RPC endpoint must be HTTPS without embedded user credentials',async()=>{
  for(const rpcUrl of ['http://rpc.example.test','https://user:secret@rpc.example.test','https://rpc.example.test/#secret'])await assert.rejects(preflight({...config,rpcUrl},input,()=>assert.fail('RPC called')),/HTTPS_RPC_REQUIRED/);
});
