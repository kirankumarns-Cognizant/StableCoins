#!/usr/bin/env node
// Read-only public-testnet checks. Never loads a signer or submits a transaction.
const {readConnections}=require('../services/config/read-connections.cjs');
const ADDRESS=/^0x[0-9a-fA-F]{40}$/;
const ZERO='0x'+'0'.repeat(40);
const DUMMY='0x7e5f4552091a69125d5dfcb7b8c2659029395bdf';
const OFFICIAL_USDC='0x036cbd53842c5426634e7929541ec2318f3dcf7e';
function fail(code){throw new Error(code);}
function address(value){if(!ADDRESS.test(value||'')||value.toLowerCase()===ZERO)fail('INVALID_WALLET_ADDRESS');return value.toLowerCase();}
function amountUnits(value){
  if(!/^(0|[1-9][0-9]{0,11})(\.[0-9]{1,6})?$/.test(value||''))fail('INVALID_USDC_AMOUNT');
  const [whole,fraction='']=value.split('.');
  const units=BigInt(whole)*1000000n+BigInt(fraction.padEnd(6,'0'));
  if(units<=0n)fail('INVALID_USDC_AMOUNT');return units;
}
function format(units,decimals){const value=units.toString().padStart(decimals+1,'0');return value.slice(0,-decimals)+'.'+value.slice(-decimals);}
function quantity(value){if(typeof value!=='string'||!/^0x[0-9a-fA-F]+$/.test(value)||value.length>66)fail('RPC_INVALID_HEX');return BigInt(value);}
async function preflight(config,{treasury,beneficiary,amount='1'},fetcher=fetch){
  treasury=address(treasury);beneficiary=address(beneficiary);
  if(treasury===beneficiary)fail('SENDER_EQUALS_RECIPIENT');
  if(treasury===DUMMY)fail('PUBLIC_DUMMY_SIGNER_FORBIDDEN');
  const requested=amountUnits(amount);
  if(config.network!=='base-sepolia'||String(config.chainId)!=='84532'||String(config.tokenContract).toLowerCase()!==OFFICIAL_USDC||String(config.decimals)!=='6')fail('BASE_SEPOLIA_USDC_CONFIGURATION_REQUIRED');
  let endpoint;try{endpoint=new URL(config.rpcUrl);}catch{fail('HTTPS_RPC_REQUIRED');}
  if(endpoint.protocol!=='https:'||endpoint.username||endpoint.password||endpoint.hash)fail('HTTPS_RPC_REQUIRED');
  let id=0;
  async function rpc(method,params){
    const requestId=++id;let response;
    try{response=await fetcher(endpoint.href,{method:'POST',redirect:'error',signal:AbortSignal.timeout(20000),headers:{'Content-Type':'application/json'},body:JSON.stringify({jsonrpc:'2.0',id:requestId,method,params})});}
    catch{fail('RPC_UNAVAILABLE');}
    if(!response.ok)fail('RPC_HTTP_ERROR');
    let data;try{const text=await response.text();if(text.length>65536)fail('RPC_RESPONSE_TOO_LARGE');data=JSON.parse(text);}catch{fail('RPC_INVALID_RESPONSE');}
    if(data?.jsonrpc!=='2.0'||data.id!==requestId||data.error||typeof data.result!=='string')fail('RPC_REJECTED_OR_INVALID_RESPONSE');
    return data.result;
  }
  if(quantity(await rpc('eth_chainId',[]))!==84532n)fail('RPC_WRONG_CHAIN');
  const block=await rpc('eth_blockNumber',[]);quantity(block);
  const code=await rpc('eth_getCode',[OFFICIAL_USDC,block]);
  if(!/^0x(?:[0-9a-fA-F]{2})+$/.test(code)||/^0x0+$/.test(code))fail('USDC_CONTRACT_CODE_MISSING');
  const decimals=quantity(await rpc('eth_call',[{to:OFFICIAL_USDC,data:'0x313ce567'},block]));
  if(decimals!==6n)fail('USDC_DECIMALS_MISMATCH');
  const eth=quantity(await rpc('eth_getBalance',[treasury,block]));
  const balanceCall='0x70a08231'+treasury.slice(2).padStart(64,'0');
  const usdc=quantity(await rpc('eth_call',[{to:OFFICIAL_USDC,data:balanceCall},block]));
  const sufficient=eth>0n&&usdc>=requested;
  return {status:sufficient?'RPC_BALANCES_READY':'FUNDING_REQUIRED',network:'base-sepolia',chainId:84532,
    observedBlock:BigInt(block).toString(),treasury,beneficiary,tokenContract:OFFICIAL_USDC,
    requestedUsdc:format(requested,6),testEth:format(eth,18),testUsdc:format(usdc,6),
    hasNativeGasAsset:eth>0n,hasRequestedUsdc:usdc>=requested,
    gasEstimate:'NOT_CHECKED; positive ETH does not establish sufficient complete network fees',
    signer:'NOT_LOADED_OR_VERIFIED',applicationMode:'NOT_INSPECTED',transactionSubmitted:false,
    next:sufficient?'Verify encrypted-wallet signing, explicit runtime mode and application treasury readiness before approval/execution':'Fund treasury with Base Sepolia test ETH and test USDC, then rerun this read-only check'};
}
function options(args){const result={amount:'1'};for(let i=0;i<args.length;i+=2){const name=args[i];if(!['--treasury','--beneficiary','--amount'].includes(name)||!args[i+1]||args[i+1].startsWith('--'))fail('USAGE: --treasury ADDRESS --beneficiary ADDRESS [--amount 1]');const key=name.slice(2);if(Object.hasOwn(result,key)&&key!=='amount')fail('DUPLICATE_OPTION');result[key]=args[i+1];}return result;}
async function main(){
  try{
    const input=options(process.argv.slice(2));
    const p=readConnections();
    const result=await preflight({network:p['blockchain.network'],chainId:p['blockchain.chain-id'],tokenContract:p['blockchain.token-contract'],decimals:p['blockchain.token-decimals'],rpcUrl:p['blockchain.rpc-url']},input);
    console.log(JSON.stringify(result,null,2));if(result.status!=='RPC_BALANCES_READY')process.exitCode=2;
  }catch(error){const message=String(error.message);console.error(JSON.stringify({status:'FAILED',code:/^[A-Z_ :\[\]0-9a-z-]+$/.test(message)&&message.length<160?message:'PREFLIGHT_FAILED',transactionSubmitted:false}));process.exitCode=2;}
}
if(require.main===module)void main();
module.exports={preflight,amountUnits,options};
