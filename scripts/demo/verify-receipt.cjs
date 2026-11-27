#!/usr/bin/env node
// Read-only Base Sepolia USDC receipt verification. No signer, credentials or submission.
const {amountUnits}=require('../base-sepolia-preflight.cjs');
const TOKEN='0x036cbd53842c5426634e7929541ec2318f3dcf7e';
const TRANSFER='0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef';
const HASH=/^0x[0-9a-fA-F]{64}$/;
function requireThat(ok,code){if(!ok)throw Error(code);}
function address(v){requireThat(/^0x[0-9a-fA-F]{40}$/.test(v||'')&&!/^0x0{40}$/i.test(v),'INVALID_ADDRESS');return v.toLowerCase();}
function hex(v){requireThat(typeof v==='string'&&/^0x[0-9a-fA-F]+$/.test(v)&&v.length<=66,'INVALID_RPC_QUANTITY');return BigInt(v);}
function options(args){
 const result={amount:'1',confirmations:'1'};
 for(let i=0;i<args.length;i+=2){
  const key=args[i]?.replace(/^--/,'');
  requireThat(args[i]==='--'+key&&['hash','treasury','beneficiary','amount','confirmations'].includes(key)&&args[i+1]&&!args[i+1].startsWith('--'),'INVALID_OPTIONS');
  requireThat(!Object.hasOwn(result,key)||['amount','confirmations'].includes(key),'DUPLICATE_OPTION');
  result[key]=args[i+1];
 }
 return result;
}
async function verify(input,fetcher=fetch){
 requireThat(HASH.test(input.hash||''),'INVALID_TRANSACTION_HASH');
 const hash=input.hash.toLowerCase(),treasury=address(input.treasury),beneficiary=address(input.beneficiary);
 requireThat(treasury!==beneficiary,'SENDER_EQUALS_RECIPIENT');
 const amount=amountUnits(input.amount||'1');
 requireThat(/^[1-9][0-9]{0,3}$/.test(String(input.confirmations||'1')),'INVALID_CONFIRMATION_REQUIREMENT');
 const required=BigInt(input.confirmations||'1');
 let endpoint;
 try{endpoint=new URL(input.rpcUrl||'https://sepolia.base.org');}catch{throw Error('HTTPS_RPC_REQUIRED');}
 requireThat(endpoint.protocol==='https:'&&!endpoint.username&&!endpoint.password&&!endpoint.hash,'HTTPS_RPC_REQUIRED');
 let id=0;
 async function rpc(method,params){
  const requestId=++id;let response,data;
  try{
   response=await fetcher(endpoint.href,{method:'POST',redirect:'error',signal:AbortSignal.timeout(20000),headers:{'Content-Type':'application/json'},body:JSON.stringify({jsonrpc:'2.0',id:requestId,method,params})});
   requireThat(response.ok,'RPC_HTTP_ERROR');
   const body=await response.text();requireThat(body.length<=1048576,'RPC_RESPONSE_TOO_LARGE');data=JSON.parse(body);
  }catch{throw Error('RPC_UNAVAILABLE_OR_INVALID_RESPONSE');}
  requireThat(data?.jsonrpc==='2.0'&&data.id===requestId&&!data.error,'RPC_REJECTED_OR_INVALID_RESPONSE');
  return data.result;
 }
 requireThat(hex(await rpc('eth_chainId',[]))===84532n,'WRONG_CHAIN');
 const receipt=await rpc('eth_getTransactionReceipt',[hash]);
 requireThat(receipt!=null,'RECEIPT_PENDING_OR_NOT_FOUND');
 requireThat(receipt.transactionHash?.toLowerCase()===hash&&hex(receipt.status)===1n,'RECEIPT_FAILED_OR_HASH_MISMATCH');
 requireThat(address(receipt.from)===treasury&&address(receipt.to)===TOKEN,'WRONG_RECEIPT_SENDER_OR_CONTRACT');
 requireThat(HASH.test(receipt.blockHash||''),'INVALID_RECEIPT_BLOCK_HASH');
 const blockNumber=hex(receipt.blockNumber);
 const block=await rpc('eth_getBlockByNumber',[receipt.blockNumber,false]);
 requireThat(block?.hash?.toLowerCase()===receipt.blockHash.toLowerCase()&&hex(block.number)===blockNumber,'NONCANONICAL_OR_MISSING_BLOCK');
 const head=hex(await rpc('eth_blockNumber',[]));
 requireThat(head>=blockNumber,'HEAD_BEHIND_RECEIPT');
 const confirmations=head-blockNumber+1n;
 requireThat(confirmations>=required,'INSUFFICIENT_CONFIRMATIONS');
 const topic=a=>'0x'+a.slice(2).padStart(64,'0');
 requireThat(Array.isArray(receipt.logs),'INVALID_RECEIPT_LOGS');
 const events=receipt.logs.filter(log=>log.address?.toLowerCase()===TOKEN&&log.topics?.[0]?.toLowerCase()===TRANSFER);
 requireThat(events.length===1,'EXPECTED_ONE_USDC_TRANSFER');
 const event=events[0];
 requireThat(event.removed!==true&&event.topics.length===3&&event.topics[1]?.toLowerCase()===topic(treasury)&&event.topics[2]?.toLowerCase()===topic(beneficiary)&&HASH.test(event.data||'')&&hex(event.data)===amount,'TRANSFER_BINDING_MISMATCH');
 const gasUsed=hex(receipt.gasUsed),gasPrice=hex(receipt.effectiveGasPrice);
 const l1Fee=receipt.l1Fee==null?null:hex(receipt.l1Fee);
 return {status:'VERIFIED',chainId:84532,transactionHash:hash,blockNumber:blockNumber.toString(),blockHash:receipt.blockHash,
  treasury,beneficiary,tokenContract:TOKEN,amountSmallestUnits:amount.toString(),
  confirmations:confirmations.toString(),requiredConfirmations:required.toString(),
  finality:'L2 inclusion/depth checked; Ethereum finality NOT checked',
  gasUsed:gasUsed.toString(),effectiveGasPriceWei:gasPrice.toString(),l2ExecutionFeeWei:(gasUsed*gasPrice).toString(),
  l1FeeWei:l1Fee?.toString()??null,
  feeScope:'Raw receipt components only; compare application total_fee_wei and fee_components_complete for accounting',
  transactionSubmitted:false};
}
async function main(){
 try{
  const value=await verify({...options(process.argv.slice(2)),rpcUrl:process.env.TESTNET_RPC_URL||process.env.BLOCKCHAIN_RPC_URL});
  console.log(JSON.stringify(value,null,2));
 }catch(error){
  console.error(JSON.stringify({status:'FAILED',code:/^[A-Z_]+$/.test(error.message)?error.message:'RECEIPT_VERIFICATION_FAILED',transactionSubmitted:false}));
  process.exitCode=2;
 }
}
if(require.main===module)void main();
module.exports={verify,options};
