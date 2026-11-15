// Opt-in Codespaces demonstration against running services. No embedded tokens or wallet secrets.
const { readConnections } = require('../services/config/read-connections.cjs');
const { createHash } = require('node:crypto');
const { writeFileSync } = require('node:fs');
const TRANSFER_TOPIC = '0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef';
function requireValue(env,key){if(!env[key])throw new Error('Missing '+key);return env[key];}
function keyFor(runId,phase){const bytes=createHash('sha256').update(runId+'/'+phase).digest().subarray(0,16);bytes[6]=(bytes[6]&15)|80;bytes[8]=(bytes[8]&63)|128;const h=bytes.toString('hex');return `${h.slice(0,8)}-${h.slice(8,12)}-${h.slice(12,16)}-${h.slice(16,20)}-${h.slice(20)}`;}
function units(value,decimals){const match=/^(0|[1-9][0-9]*)(?:\.([0-9]+))?$/.exec(String(value));if(!match||(match[2]||'').length>decimals)throw new Error('Invalid decimal evidence');return BigInt(match[1]+(match[2]||'').padEnd(decimals,'0'));}
function verifyLedger(ledger,paymentId,correlationId,hash){
 const expected=['FUNDING','CONVERSION_USD','CONVERSION_USDC','BLOCKCHAIN_SUBMISSION','BLOCKCHAIN_CONFIRMATION'];
 if(ledger.paymentId!==paymentId)throw new Error('Ledger payment mismatch');
 for(const type of expected){const matches=ledger.journals.filter(j=>j.journalType===type);if(matches.length!==1)throw new Error('Missing/duplicate journal '+type);const j=matches[0];
  if(j.status!=='POSTED'||j.paymentId!==paymentId||j.correlationId!==correlationId)throw new Error('Unbound ledger journal');
  if(type.startsWith('BLOCKCHAIN_')&&j.reference.toLowerCase()!==hash.toLowerCase())throw new Error('Ledger hash mismatch');
  const expectedAsset=['FUNDING','CONVERSION_USD'].includes(type)?'USD':'USDC';
  const totals=new Map();for(const line of j.entries){if(line.asset!==expectedAsset)throw new Error('Ledger does not balance per asset');const total=totals.get(line.asset)||{debit:0n,credit:0n};total.debit+=units(line.exactDebit??line.debit??0,18);total.credit+=units(line.exactCredit??line.credit??0,18);totals.set(line.asset,total);}
  if(!totals.size||[...totals.values()].some(t=>t.debit<=0n||t.debit!==t.credit))throw new Error('Ledger does not balance per asset');
 }
}
function verifyReceipt(tx,receipt,head,chainId,required){
 if(![84532,11155111].includes(Number(chainId))||Number(chainId)!==Number(tx.chainId))throw new Error('Wrong public testnet');
 if(!receipt||receipt.transactionHash.toLowerCase()!==tx.txHash.toLowerCase()||BigInt(receipt.status)!==1n||BigInt(receipt.blockNumber)!==BigInt(tx.blockNumber)||BigInt(receipt.gasUsed)<=0n)throw new Error('Invalid public-testnet receipt');
 if(BigInt(head)-BigInt(receipt.blockNumber)+1n<BigInt(required))throw new Error('Insufficient confirmations');
 const from='0x'+tx.fromAddress.slice(2).toLowerCase().padStart(64,'0'),to='0x'+tx.toAddress.slice(2).toLowerCase().padStart(64,'0');
 const logs=receipt.logs.filter(l=>!l.removed&&l.address.toLowerCase()===tx.tokenContract.toLowerCase()&&l.topics[0]?.toLowerCase()===TRANSFER_TOPIC&&l.topics[1]?.toLowerCase()===from&&l.topics[2]?.toLowerCase()===to&&BigInt(l.data)===units(tx.amount,6));
 if(logs.length!==1)throw new Error('Matching public-testnet USDC Transfer log required');
}
function verifyLifecycleLedger(ledger,paymentId,correlationId,tx,payout){
 verifyLedger(ledger,paymentId,correlationId,tx.txHash);
 if(payout&&(payout.status!=='SUCCEEDED'||payout.mode!=='SIMULATED'||!['USD','INR'].includes(payout.currency)))throw new Error('Successful simulated bank payout required');
 const extra=[{type:'NETWORK_FEE',asset:'ETH',scale:18,amount:BigInt(tx.totalFeeWei||0),reference:tx.txHash}];
 if(!tx.feeComponentsComplete||BigInt(tx.totalFeeWei||0)<=0n)throw new Error('Complete actual fee evidence required');
 if(payout)extra.push({type:'OFFRAMP_USDC',asset:'USDC',scale:6,amount:units(tx.amount,6),reference:payout.reference},...['INITIATION','COMPLETION'].map(phase=>({type:'PAYOUT_'+phase+'_'+payout.currency,asset:payout.currency,scale:2,amount:units(payout.amount,2),reference:payout.reference})));
 if(ledger.journals.length!==5+extra.length)throw new Error('Unexpected lifecycle journal count');
 for(const expected of extra){
  const found=ledger.journals.filter(j=>j.journalType===expected.type);if(found.length!==1)throw new Error('Missing/duplicate '+expected.type);
  const j=found[0];if(j.status!=='POSTED'||j.paymentId!==paymentId||j.correlationId!==correlationId||j.reference!==expected.reference)throw new Error('Lifecycle journal binding mismatch');
  let debit=0n,credit=0n;for(const line of j.entries){if(line.asset!==expected.asset)throw new Error('Lifecycle journal asset mismatch');debit+=units(line.exactDebit??line.debit??0,expected.scale);credit+=units(line.exactCredit??line.credit??0,expected.scale);}
  if(debit!==expected.amount||credit!==expected.amount||expected.amount<=0n)throw new Error('Lifecycle journal amount mismatch');
 }
}
function verifyPublicFee(tx,receipt){
 if(!receipt)throw new Error('Public receipt required for fee verification');
 let additional=0n;if(Number(tx.chainId)===84532){
  if(receipt.l1Fee===undefined)throw new Error('Public L1 fee missing');additional=BigInt(receipt.l1Fee);
  if(receipt.operatorFee!==undefined)additional+=BigInt(receipt.operatorFee);
  else if(BigInt(receipt.operatorFeeScalar||0)>0n||BigInt(receipt.operatorFeeConstant||0)>0n)throw new Error('Public operator fee incomplete');
 }
 const total=BigInt(receipt.gasUsed)*BigInt(receipt.effectiveGasPrice)+additional;
 if(!tx.feeComponentsComplete||BigInt(tx.totalFeeWei)!==total||BigInt(tx.additionalFeeWei)!==additional)throw new Error('Public receipt fee mismatch');
}
async function run(env=process.env,fetchImpl=globalThis.fetch){
 const config=readConnections(env),runId=requireValue(env,'DEMO_RUN_ID');if(!/^[0-9a-f-]{36}$/i.test(runId))throw new Error('DEMO_RUN_ID must be a stable UUID');
 const operator=requireValue(env,'DEMO_OPERATOR_TOKEN'),approvers=JSON.parse(requireValue(env,'DEMO_APPROVER_TOKENS'));
 if(!Array.isArray(approvers)||approvers.some(t=>typeof t!=='string'||!t))throw new Error('DEMO_APPROVER_TOKENS must be a JSON token array');
 const mode=env.DEMO_EVIDENCE_MODE||'MOCK';if(!['MOCK','TESTNET'].includes(mode))throw new Error('Expected MOCK or TESTNET');
 if(mode==='TESTNET')requireValue(env,'DEMO_RPC_URL');
 const bases={payment:config['connections.services.payment-url'],chain:config['connections.services.blockchain-url'],ledger:config['connections.services.ledger-url'],reconciliation:config['connections.services.reconciliation-url'],visa:config['connections.visa.base-url']};
 for(const base of Object.values(bases)){const u=new URL(base);if(u.protocol!=='https:'&&!(u.protocol==='http:'&&['localhost','127.0.0.1','payment-service','blockchain-service','settlement-ledger-service','reconciliation-service','visa-simulator'].includes(u.hostname)))throw new Error('HTTPS or a local service URL required');}
 const api=async(service,path,method='GET',body,token=operator,key)=>{const headers={Authorization:'Bearer '+token};if(body!==undefined)headers['Content-Type']='application/json';if(key)headers['Idempotency-Key']=key;const response=await fetchImpl(bases[service].replace(/\/$/,'')+'/api/v1'+path,{method,headers,body:body===undefined?undefined:JSON.stringify(body),signal:AbortSignal.timeout(30000)});if(!response.ok)throw new Error(`Demo API failed: ${response.status} ${method} ${path}`);return response.json();};
 const timeout=Number(env.DEMO_TIMEOUT_MS||600000);if(!Number.isInteger(timeout)||timeout<1000||timeout>1800000)throw new Error('Invalid demo timeout');
 const poll=async(read,done)=>{const end=Date.now()+timeout;while(Date.now()<end){const value=await read();if(['REVIEW_REQUIRED','FAILED','EXPIRED','REJECTED','COMPLIANCE_REJECTED'].includes(value.status||value.state))throw new Error('Flow requires investigation: '+(value.failureCode||value.status||value.state));if(done(value))return value;await new Promise(resolve=>setTimeout(resolve,1000));}throw new Error('Demo timed out; inspect the SAME payment/execution before retrying');};
 const outcome=env.DEMO_OUTCOME||'USDC_WALLET';if(!['USDC_WALLET','SIMULATED_BANK_USD','SIMULATED_BANK_INR'].includes(outcome))throw new Error('Invalid beneficiary outcome');
 const instruction={senderAccountId:requireValue(env,'DEMO_SENDER_ACCOUNT_ID'),beneficiaryId:requireValue(env,'DEMO_BENEFICIARY_ID'),amount:'10000.00',currency:'USD',purpose:'SUPPLIER_INVOICE',requestedRail:'AUTO',intent:{requiredOutcome:outcome,deliverBy:requireValue(env,'DEMO_DELIVER_BY'),maximumFee:env.DEMO_MAXIMUM_FEE_USD||'25.00',feeCurrency:'USD',priority:'LOWEST_COST',allowedAlternatives:[],confirmed:true}};
 if(!Number.isFinite(Date.parse(instruction.intent.deliverBy)))throw new Error('DEMO_DELIVER_BY must be a fixed ISO timestamp, retained for replays');
 const payment=await api('payment','/payments','POST',instruction,operator,keyFor(runId,'create')),path='/payments/'+encodeURIComponent(payment.paymentId);
 await poll(()=>api('payment',path),p=>!['CREATED','COMPLIANCE_PENDING'].includes(p.status));
 const preparation=await api('payment',path+'/recommendation-preparations','POST',undefined,operator,keyFor(runId,'prepare'));
 const ready=await poll(()=>api('payment',path+'/recommendation-preparations/'+preparation.requestId),p=>p.status==='READY');
 const reviewPath=path+'/recommendations/'+ready.recommendationId;let recommendation=await api('payment',reviewPath),agent;
 if(env.DEMO_RUN_AGENT==='true'&&recommendation.state==='PROPOSED'){
  agent=await api('payment',reviewPath+'/agent-runs','POST',{instruction:'Compare the captured eligible stablecoin route evidence, explain the seven signals, costs, provenance and any uncertainty. Do not execute the payment.'},operator,keyFor(runId,'agent'));
  agent=await poll(()=>api('payment',reviewPath+'/agent-runs/'+agent.runId),a=>a.status==='COMPLETED');if(!agent.report||!agent.configuredModel)throw new Error('Actual validated agent report required');
 }
 const acceptance={expectedVersion:recommendation.version,quoteId:recommendation.document.quote.quoteId,quoteVersion:recommendation.document.quote.version};
 if(recommendation.state==='PROPOSED'){
  if(approvers.length<recommendation.requiredApprovals)throw new Error('Insufficient independently authorized approver tokens');
  for(const token of approvers){await api('payment',reviewPath+'/quote-acceptance','POST',acceptance,token);recommendation=await api('payment',reviewPath+'/approvals','POST',acceptance,token);if(recommendation.state==='APPROVED')break;}
 }
 if(!['APPROVED','EXECUTED'].includes(recommendation.state))throw new Error('Recommendation is not approved');
 const execution=await api('payment',reviewPath+'/execution','POST',acceptance,operator,keyFor(runId,'execute'));
 const complete=await poll(()=>api('payment',path+'/execution'),e=>e.status==='CONFIRMED');
 if(complete.executionId!==execution.executionId)throw new Error('Execution changed');
 let tx=await api('chain','/blockchain/payments/'+encodeURIComponent(payment.paymentId)+'/transaction');
 if(tx.status!=='CONFIRMED'||tx.paymentId!==payment.paymentId||tx.correlationId!==payment.correlationId||tx.transactionId!==complete.transactionId||tx.tokenSymbol!=='USDC'||units(tx.amount,6)!==10000000000n||tx.evidenceMode!==(mode==='TESTNET'?'LIVE':'MOCK'))throw new Error('Unbound/mislabeled chain evidence');
 let ledger=await api('ledger',path+'/ledger');verifyLedger(ledger,payment.paymentId,payment.correlationId,tx.txHash);
 let receipt;
 if(mode==='TESTNET'){
  let id=0;const rpc=async(method,params)=>{const response=await fetchImpl(env.DEMO_RPC_URL,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({jsonrpc:'2.0',id:++id,method,params}),signal:AbortSignal.timeout(30000)});if(!response.ok)throw new Error('RPC evidence request failed');const result=await response.json();if(result.error)throw new Error('RPC evidence response failed');return result.result;};
  receipt=await rpc('eth_getTransactionReceipt',[tx.txHash]);verifyReceipt(tx,receipt,await rpc('eth_blockNumber',[]),await rpc('eth_chainId',[]),Number(config['blockchain.confirmations-required']));
 }
 let payout,reconciliation,finalPayment;
 if(env.DEMO_COMPLETE_LIFECYCLE==='true'){
  finalPayment=await poll(async()=>{
   const closure=await api('payment',path+'/closure');
   if(closure.phase==='REVIEW_REQUIRED'||closure.failureCode==='PAYOUT_UNKNOWN_PINNED')throw new Error('Lifecycle requires investigation: '+(closure.failureCode||closure.phase));
   return await api('payment',path);
  },p=>p.status==='RECONCILED');
  ledger=await api('ledger',path+'/ledger');reconciliation=await api('reconciliation',path+'/reconciliation');
  if(reconciliation.status!=='MATCHED'||reconciliation.paymentId!==payment.paymentId||reconciliation.correlationId!==payment.correlationId||reconciliation.executionId!==complete.executionId)throw new Error('Matching owner reconciliation required');
  if(outcome!=='USDC_WALLET'){
   payout=await api('visa',path+'/payout');
   if(payout.status!=='SUCCEEDED'||payout.mode!=='SIMULATED'||payout.paymentId!==payment.paymentId||payout.correlationId!==payment.correlationId||payout.executionId!==complete.executionId||payout.currency!==(outcome.endsWith('INR')?'INR':'USD')||units(payout.amount,2)!==units(recommendation.document.quote.payoutAmounts[outcome],2))throw new Error('Bound simulated payout required');
  }
  const finalTx=await api('chain','/blockchain/payments/'+encodeURIComponent(payment.paymentId)+'/transaction');
  verifyLifecycleLedger(ledger,payment.paymentId,payment.correlationId,finalTx,payout);
  if(mode==='TESTNET')verifyPublicFee(finalTx,receipt);
  tx=finalTx;
 }
 const replay=await api('payment',reviewPath+'/execution' ,'POST',acceptance,operator,keyFor(runId,'execute'));if(replay.executionId!==complete.executionId||replay.transactionId!==tx.transactionId)throw new Error('Execution replay changed financial operation');
 const evidence={runId,paymentId:payment.paymentId,correlationId:payment.correlationId,executionId:complete.executionId,mode,transaction:tx,ledger,agent,receipt,payout,reconciliation,finalPayment,journey:await api('payment',path+'/journey'),verifiedAt:new Date().toISOString(),scope:env.DEMO_COMPLETE_LIFECYCLE==='true'?'Reconciled application lifecycle. Fiat funding/conversion, provider signals and optional Visa/bank credit are simulated.':'Wallet confirmation only; full lifecycle verification was not requested.'};
 if(env.DEMO_EVIDENCE_FILE)writeFileSync(env.DEMO_EVIDENCE_FILE,JSON.stringify(evidence,null,2),{flag:'wx'});
 console.log(JSON.stringify({paymentId:evidence.paymentId,executionId:evidence.executionId,mode,txHash:tx.txHash,status:finalPayment?.status||complete.status}));return evidence;
}
module.exports={keyFor,units,verifyLedger,verifyReceipt,verifyLifecycleLedger,verifyPublicFee,run};
if(require.main===module)run().catch(error=>{console.error(error.message);process.exitCode=1;});
