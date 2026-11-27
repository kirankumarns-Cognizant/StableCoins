const {test}=require('node:test');
const assert=require('node:assert/strict');
const {verify}=require('./verify-receipt.cjs');
const hash='0x'+'a'.repeat(64),blockHash='0x'+'b'.repeat(64);
const treasury='0x'+'1'.repeat(40),beneficiary='0x'+'2'.repeat(40);
const token='0x036cbd53842c5426634e7929541ec2318f3dcf7e';
const topic=a=>'0x'+a.slice(2).padStart(64,'0');
const transfer='0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef';
const input={hash,treasury,beneficiary,amount:'1',confirmations:'2'};
function fixture(change={}){
 const event={address:token,topics:[transfer,topic(treasury),topic(beneficiary)],data:'0x'+(1000000n).toString(16).padStart(64,'0')};
 const receipt={transactionHash:hash,status:'0x1',from:treasury,to:token,blockNumber:'0x10',blockHash,logs:[event],gasUsed:'0xf2c3',effectiveGasPrice:'0x6ddd00',l1Fee:'0x1ea',...change.receipt};
 const calls=[];
 return {calls,fetcher:async(url,init)=>{
  const q=JSON.parse(init.body);calls.push(q.method);
  const values={eth_chainId:change.chain??'0x14a34',eth_getTransactionReceipt:change.pending?null:receipt,
   eth_getBlockByNumber:{number:'0x10',hash:change.blockHash??blockHash},eth_blockNumber:change.head??'0x12'};
  assert.ok(Object.hasOwn(values,q.method),'Unexpected RPC method');assert.equal(init.redirect,'error');
  return {ok:true,text:async()=>JSON.stringify({jsonrpc:'2.0',id:q.id,result:values[q.method]})};
 }};
}
test('verifies canonical successful exact USDC transfer with required depth using read-only RPC',async()=>{
 const f=fixture();const r=await verify(input,f.fetcher);
 assert.equal(r.status,'VERIFIED');assert.equal(r.confirmations,'3');assert.equal(r.transactionSubmitted,false);
 assert.equal(r.l1FeeWei,'490');assert.match(r.finality,/NOT checked/);
 assert.deepEqual(f.calls,['eth_chainId','eth_getTransactionReceipt','eth_getBlockByNumber','eth_blockNumber']);
});
test('rejects wrong chain, reverted/pending receipts, hash and canonical block mismatches',async()=>{
 for(const change of [{chain:'0x1'},{receipt:{status:'0x0'}},{pending:true},{receipt:{transactionHash:blockHash}},{blockHash:hash}])
  await assert.rejects(verify(input,fixture(change).fetcher));
});
test('rejects wrong sender, contract, recipient, token amount, removed or absent transfer',async()=>{
 const base={address:token,topics:[transfer,topic(treasury),topic(beneficiary)],data:'0x'+(1000000n).toString(16).padStart(64,'0')};
 for(const receipt of [{from:beneficiary},{to:treasury},{logs:[]},{logs:[{...base,address:treasury}]},
  {logs:[{...base,topics:[transfer,topic(treasury),topic(treasury)]}]},{logs:[{...base,data:'0x'+(2n).toString(16).padStart(64,'0')}]},
  {logs:[{...base,removed:true}]},{logs:[base,base]}])
  await assert.rejects(verify(input,fixture({receipt}).fetcher));
});
test('rejects insufficient depth and head behind receipt',async()=>{
 for(const head of ['0x10','0xf'])await assert.rejects(verify(input,fixture({head}).fetcher));
});
test('missing L1 fee remains unknown instead of being invented as zero',async()=>{
 const result=await verify(input,fixture({receipt:{l1Fee:undefined}}).fetcher);assert.equal(result.l1FeeWei,null);
});
test('invalid addresses/hash/amount/endpoints are rejected without RPC and transport errors hide credentials',async()=>{
 for(const change of [{hash:'bad'},{amount:'0'},{beneficiary:treasury},{treasury:'bad'},{confirmations:'0'},{rpcUrl:'http://rpc.test'},{rpcUrl:'https://user:secret@rpc.test'}])
  await assert.rejects(verify({...input,...change},()=>assert.fail('RPC invoked')));
 await assert.rejects(verify(input,async()=>{throw Error('secret endpoint');}),/^Error: RPC_UNAVAILABLE_OR_INVALID_RESPONSE$/);
});
