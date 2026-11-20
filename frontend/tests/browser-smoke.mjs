// Synthetic UI contract rehearsal only. No live identity, model, database or blockchain.
import assert from 'node:assert/strict'
import {createServer as createHttpServer} from 'node:http'
import {spawn} from 'node:child_process'
import {mkdir,readFile,writeFile} from 'node:fs/promises'
import {resolve} from 'node:path'
import {fileURLToPath} from 'node:url'
import {createServer} from 'vite'
const root=fileURLToPath(new URL('../',import.meta.url))
const artifacts=resolve(root,'../.local/step9-ui');await mkdir(artifacts,{recursive:true})
await writeFile(resolve(artifacts,'browser-results.json'),JSON.stringify({status:'RUNNING',synthetic:true,startedAt:new Date().toISOString()}));
const profile=resolve(artifacts,'edge-'+Date.now());const stamp=new Date().toISOString();const future=new Date(Date.now()+3600000).toISOString()
const pay='PAY-UI-SYNTHETIC',customer='11111111-1111-1111-1111-111111111111',reviewId='66666666-6666-4666-8666-666666666666',quoteId='77777777-7777-4777-8777-777777777777',hash='0x'+'a'.repeat(64)
let blocked=false;let created=false,prepared=false,agentRun=false,executed=false,expire=false,chainMode='MOCK';const approvals=new Set();const accepted=new Set();const calls=[];let firstCreate=true
let holdCatalog=false,catalogFailure=false,catalogReads=0;const heldReads=[]
const candidate={candidate:{candidateId:'synthetic-provider-a',providerId:'SIMULATED-A',rail:'STABLECOIN',outcome:'USDC_WALLET',network:{name:'base-sepolia',chainId:84532}},eligibility:'ELIGIBLE',totalCostUsd:'1.50',deliveryMillis:6000,successRate:'0.99',fraudScore:'0.01',findings:[],itemizedCosts:[{component:'GAS',nativeAmount:'0.00001',nativeAsset:'ETH',comparisonAmountUsd:'0.02'}],evidenceModes:['MOCK','SIMULATED'],evidence:['SUCCESS_RATE','LATENCY','TRANSACTION_COST','FRAUD_RISK','RAIL_AVAILABILITY','LIQUIDITY','EXTERNAL_DATA'].map((signalType,index)=>({signalId:'signal-'+index,signalType,sourceId:'synthetic-fixture',evidenceMode:'SIMULATED',quality:'ACCEPTABLE',observedAt:stamp,expiresAt:future}))}
const eligibleReview=()=>({recommendationId:reviewId,version:1,state:executed?'EXECUTED':approvals.size>=2?'APPROVED':'PROPOSED',approvalCount:approvals.size,requiredApprovals:2,createdAt:stamp,expiresAt:future,document:{rationale:'SYNTHETIC UI TEST',quote:{quoteId,version:1,providerId:'SIMULATED-A',sourceAmountUsd:'10000.00',transferAmountUsdc:'10000',payoutAmounts:{},totalFeeUsd:'1.50',expiresAt:future,simulated:true},assessment:{status:'ASSESSED',preferredCandidateId:'synthetic-provider-a',assessedAt:stamp,expiresAt:future,candidates:[candidate]},evidence:{observations:candidate.evidence},intent:{}}})
const review=()=>{const value=eligibleReview();if(blocked){value.document.quote=null;value.document.rationale='No approvable route: NO_ELIGIBLE_ROUTE';value.document.assessment.status='NO_ELIGIBLE_ROUTE';value.document.assessment.preferredCandidateId=null;value.document.assessment.candidates=[{...candidate,eligibility:'INELIGIBLE',findings:[{code:'FEE_LIMIT_EXCEEDED',signalType:'TRANSACTION_COST'}]}]}return value}
const payment=()=>({paymentId:pay,correlationId:'CORR-UI-SYNTHETIC',amount:'10000.00',sourceCurrency:'USD',status:executed?'RECONCILED':'COMPLIANCE_APPROVED',intent:{instruction:{requiredOutcome:'USDC_WALLET',deliverBy:future,maximumFee:'25.00',priority:'LOWEST_COST'},policy:{requiredApprovals:2}}})
const available=data=>({status:'AVAILABLE',data});const pending={status:'PENDING',data:null}
const evidence=()=>({paymentId:pay,preparations:prepared?[{request_id:'synthetic-preparation',status:'READY',recommendation_id:reviewId,created_at:stamp}]:[],recommendations:prepared?[{recommendation_id:reviewId,version:1,state:review().state,created_at:stamp,expires_at:future}]:[],decisions:[...accepted].map(actor_id=>({recommendation_id:reviewId,actor_id,action:'QUOTE_ACCEPTED',decided_at:stamp})).concat([...approvals].map(actor_id=>({recommendation_id:reviewId,actor_id,action:'APPROVED',decided_at:stamp}))),agentRuns:agentRun?[{run_id:'synthetic-run',recommendation_id:reviewId,status:'COMPLETED'}]:[],execution:executed?[{execution_id:'synthetic-execution',status:'CONFIRMED',transaction_id:'synthetic-tx'}]:[],closure:executed?[{phase:'COMPLETED'}]:[],treasury:prepared?available({status:'READY',document:{evidenceMode:'MOCK',network:'base-sepolia',chainId:84532,walletAddress:'0x'+'1'.repeat(40),tokenContract:'0x'+'2'.repeat(40),totalUsdc:'20000',reservedUsdc:'0',availableUsdc:'20000',totalEth:'1',reservedEth:'0',availableEth:'1',estimatedGas:'65000',gasLimit:'78000',gasPriceWei:'10000000',requiredGasEth:'0.00001',observedAt:stamp,expiresAt:future,reasons:[]}}):pending,blockchain:executed?available({transactionId:'synthetic-tx',network:'base-sepolia',chainId:84532,tokenContract:'0x'+'2'.repeat(40),sender:'0x'+'1'.repeat(40),recipient:'0x'+'3'.repeat(40),amount:'10000',hash,status:'CONFIRMED',blockNumber:123456,gasUsed:'65000',effectiveGasPrice:'10000000',additionalFeeWei:'100',totalFeeWei:'650000000100',feeComplete:true,mode:chainMode,confirmations:3}):pending,journals:available(executed?[{journalId:'synthetic-journal',type:'NETWORK_FEE',status:'POSTED',reference:hash,evidence:{mode:'MOCK',source:'SYNTHETIC UI TEST'},entries:[{accountCode:'NETWORK_FEE_EXPENSE',asset:'ETH',debit:'0.000000650000000100',credit:'0'},{accountCode:'TREASURY_ETH',asset:'ETH',debit:'0',credit:'0.000000650000000100'}]}]:[]),payout:pending,reconciliation:executed?available({status:'MATCHED',sourceHash:'synthetic-source-fingerprint',findings:[],note:'SYNTHETIC UI TEST'}):pending})
const fixture=createHttpServer(async(req,res)=>{
 const actor=(req.headers.authorization??'').replace('Bearer ','');const url=new URL(req.url,'http://fixture');const path=url.pathname.replace('/api/v1','');let raw='';for await(const chunk of req)raw+=chunk
 const reply=(value,status=200)=>{res.writeHead(status,{'Content-Type':'application/json','Cache-Control':'no-store'});res.end(JSON.stringify(value))}
 if(expire)return reply({code:'AUTHENTICATION_REQUIRED'},401)
 if(!actor.startsWith('synthetic-'))return reply({code:'CUSTOMER_ACCESS_DENIED'},403)
 if(req.method==='GET' && path==='/payments/workbench'){
  catalogReads++
  if(catalogFailure)return reply({code:'HTTP_502'},502)
  if(holdCatalog)await new Promise(resolve=>heldReads.push(resolve))
  if(res.destroyed)return
 }
 if(req.method==='POST'){
  calls.push({path,actor,key:req.headers['idempotency-key'],raw})
  if(path==='/payments'){created=true;if(firstCreate){firstCreate=false;return reply({code:'SYNTHETIC_LOST_ACK'},503)}return reply({paymentId:pay},201)}
  if(path.endsWith('/recommendation-preparations')){prepared=true;return reply({requestId:'synthetic-preparation'},202)}
  if(path.endsWith('/agent-runs')){agentRun=true;return reply({runId:'synthetic-run'},202)}
  if(path.endsWith('/quote-acceptance')){accepted.add(actor);return reply({})}
  if(path.endsWith('/approvals')){if(actor==='synthetic-operator'||!accepted.has(actor))return reply({code:'APPROVAL_DENIED'},403);approvals.add(actor);return reply({})}
  if(path.endsWith('/execution')){if(approvals.size<2)return reply({code:'APPROVALS_REQUIRED'},409);executed=true;return reply({executionId:'synthetic-execution'},202)}
  return reply({code:'UNSUPPORTED_FIXTURE_COMMAND'},404)
 }
 if(path==='/payments/workbench')return reply({customerId:customer,actorId:actor,actorUsername:actor.replace('synthetic-','demo-'),roles:actor==='synthetic-operator'?['PAYMENT_OPERATOR']:['PAYMENT_OPERATOR','PAYMENT_APPROVER'],policy:{version:'synthetic-policy',allowedOutcomes:['USDC_WALLET','SIMULATED_BANK_USD','SIMULATED_BANK_INR'],maximumFeeUsd:'25.00',requiredApprovals:2},accounts:[{account_id:'22222222-2222-2222-2222-222222222222',currency:'USD',balance:'100000.00',status:'ACTIVE'}],beneficiaries:[{beneficiary_id:'33333333-3333-3333-3333-333333333333',name:'SYNTHETIC UI TEST supplier',currency:'USD'}],payments:created?[{payment_id:pay,amount:'10000.00',status:payment().status,created_at:stamp,deliver_by:future},{payment_id:'PAY-OLDER',amount:'100.00',status:'CREATED',created_at:'2020-01-01T00:00:00Z',deliver_by:null}]:[],paymentWindowLimit:50,catalogLimit:100})
 if(path==='/payments/'+pay)return reply(payment())
 if(path.endsWith('/evidence'))return reply(evidence())
 if(path.endsWith('/journey'))return reply({currentStatus:payment().status,steps:[{fromStatus:'CREATED',toStatus:payment().status,reason:'SYNTHETIC UI TEST',actorService:'synthetic-fixture',timestamp:stamp}]})
 if(path.endsWith('/agent-runs/synthetic-run'))return reply({runId:'synthetic-run',status:'COMPLETED',configuredModel:'synthetic-model',deadlineAt:future,report:{inferenceProvider:'SYNTHETIC UI FIXTURE | NO MODEL CALL',providerModel:'synthetic-model',promptVersion:'test',expiresAt:future,explanation:['SYNTHETIC UI TEST explanation: candidate is eligible on the supplied fixture evidence.'],answer:{outcome:'RECOMMENDED',selectedCandidateId:'synthetic-provider-a',clarificationCodes:[],intentDraft:null},tools:[{name:'readEvidence',sequence:1}],estimatedInferenceCostUsd:'0',inputTokens:0,outputTokens:0}})
 if(path.endsWith('/recommendations/'+reviewId))return reply(review())
 return reply({code:'NOT_FOUND'},404)
})
await new Promise(resolve=>fixture.listen(0,'127.0.0.1',resolve));process.env.PAYMENT_SERVICE_URL=`http://127.0.0.1:${fixture.address().port}`
// Intercept a synthetic Cognito domain: never contact real AWS or use real credentials.
const provider='https://synthetic.auth.us-west-2.amazoncognito.com'
Object.assign(process.env,{IDENTITY_AUTHORIZATION_URI:provider+'/oauth2/authorize',IDENTITY_TOKEN_URI:provider+'/oauth2/token',IDENTITY_CLIENT_ID:'synthetic-public',IDENTITY_REDIRECT_URI:'http://127.0.0.1:5190/auth/callback',IDENTITY_ISSUER_URI:'https://cognito-idp.us-west-2.amazonaws.com/synthetic',PUBLIC_API_BASE_URL:'/api/v1'})
let providerUser='synthetic-operator',logoutCount=0,authorization=null
let vite,edge,socket;const checks=[]
try{
 vite=await createServer({root,configFile:resolve(root,'vite.config.ts'),server:{port:5190,host:'127.0.0.1',strictPort:true}});await vite.listen()
 const executable=process.env.EDGE_PATH??'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
 edge=spawn(executable,['--headless=new','--disable-gpu','--no-first-run','--disable-extensions','--disable-background-networking','--remote-debugging-port=0','--user-data-dir='+profile,'about:blank'],{windowsHide:true,stdio:['ignore','ignore','pipe']})
 edge.on('error',error=>console.error('Edge startup failed:',error.message));edge.stderr.on('data',data=>console.error(String(data).slice(0,1000)));console.log('STAGE Edge started pid='+edge.pid)
 const delay=ms=>new Promise(resolve=>setTimeout(resolve,ms));const until=async(fn,label)=>{for(let i=0;i<100;i++){if(await fn())return;await delay(150)}throw new Error('Timed out: '+label)}
 let port;await until(async()=>{try{port=(await readFile(resolve(profile,'DevToolsActivePort'),'utf8')).split('\n')[0];return Boolean(port)}catch{return false}},'Edge DevTools')
 const targets=await(await fetch(`http://127.0.0.1:${port}/json/list`)).json();socket=new WebSocket(targets.find(t=>t.type==='page').webSocketDebuggerUrl);await new Promise((resolve,reject)=>{socket.onopen=resolve;socket.onerror=reject})
 const inflight=new Map();let sequence=0;const exceptions=[]
 socket.onmessage=event=>{const response=JSON.parse(event.data);if(response.method==='Fetch.requestPaused'){void identityRequest(response.params).catch(error=>exceptions.push(String(error)));return}if(response.method==='Runtime.exceptionThrown')exceptions.push(response.params.exceptionDetails);if(response.id){const waiter=inflight.get(response.id);inflight.delete(response.id);if(response.error)waiter.reject(new Error(JSON.stringify(response.error)));else waiter.resolve(response.result)}}
 const send=(method,params={})=>new Promise((resolve,reject)=>{const id=++sequence;const timer=setTimeout(()=>{inflight.delete(id);reject(new Error('CDP timeout '+method))},15000);inflight.set(id,{resolve:value=>{clearTimeout(timer);resolve(value)},reject:error=>{clearTimeout(timer);reject(error)}});socket.send(JSON.stringify({id,method,params}))})
 const identityRequest=async({requestId,request})=>{
  const url=new URL(request.url)
  const fulfill=(responseCode,text='',headers={})=>send('Fetch.fulfillRequest',{requestId,responseCode,responseHeaders:Object.entries({'Cache-Control':'no-store',...headers}).map(([name,value])=>({name,value})),body:Buffer.from(text).toString('base64')})
  const redirect=location=>fulfill(302,'',{Location:location})
  if(url.pathname==='/favicon.ico')return fulfill(204)
  if(url.pathname==='/logout'){assert.equal(url.searchParams.get('client_id'),'synthetic-public');assert.equal(url.searchParams.get('logout_uri'),'http://127.0.0.1:5190/');providerUser=null;logoutCount++;return redirect(url.searchParams.get('logout_uri'))}
  if(url.pathname==='/oauth2/authorize'){
   authorization=url;assert.equal(url.searchParams.get('code_challenge_method'),'S256')
   // Model classic hosted UI: prompt=login alone does not suppress its retained session.
   if(providerUser)return redirect(url.searchParams.get('redirect_uri')+'?code='+providerUser+'&state='+url.searchParams.get('state'))
   const links=['synthetic-operator','synthetic-approver-a','synthetic-approver-b'].map(actor=>'<a href="'+provider+'/choose?actor='+actor+'">Choose '+actor+'</a>').join('<br>')
   return fulfill(200,'<!doctype html><title>Synthetic identity</title><p>SYNTHETIC Cognito sign-in form</p>'+links,{'Content-Type':'text/html'})
  }
  if(url.pathname==='/choose'){providerUser=url.searchParams.get('actor');return redirect(authorization.searchParams.get('redirect_uri')+'?code='+providerUser+'&state='+authorization.searchParams.get('state'))}
  if(url.pathname==='/oauth2/token'){
   const body=new URLSearchParams(request.postData)
   assert.equal(body.get('grant_type'),'authorization_code');assert.equal(body.has('client_secret'),false)
   assert.equal(Buffer.from(await crypto.subtle.digest('SHA-256',new TextEncoder().encode(body.get('code_verifier')))).toString('base64url'),authorization.searchParams.get('code_challenge'))
   return fulfill(200,JSON.stringify({access_token:body.get('code'),token_type:'Bearer',expires_in:3600}),{'Content-Type':'application/json','Access-Control-Allow-Origin':'http://127.0.0.1:5190'})
  }
  throw Error('Unexpected synthetic identity request '+url.pathname)
 }
 const ev=async expression=>{const result=await send('Runtime.evaluate',{expression,awaitPromise:true,returnByValue:true});if(result.exceptionDetails)throw new Error(JSON.stringify(result.exceptionDetails));return result.result.value}
 const body=()=>ev("document.body?.innerText??''");const has=async text=>(await body()).includes(text)
 const click=async label=>{await until(()=>ev(`(()=>{const element=[...document.querySelectorAll('button,a,summary')].find(e=>e.textContent.trim()===${JSON.stringify(label)});return Boolean(element&&!element.disabled)})()`),'enabled '+label);const clicked=await ev(`(()=>{const element=[...document.querySelectorAll('button,a,summary')].find(e=>e.textContent.trim()===${JSON.stringify(label)});if(!element||element.disabled)return false;element.click();return true})()`);assert.ok(clicked,'Clickable: '+label)}
 const input=async(selector,value)=>{await ev(`(()=>{const element=document.querySelector(${JSON.stringify(selector)});Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(element,${JSON.stringify(value)});element.dispatchEvent(new Event('input',{bubbles:true}));element.dispatchEvent(new Event('change',{bubbles:true}))})()`)}
 const navigate=async tab=>{await ev(`location.hash=${JSON.stringify('/payments/'+pay+'/'+tab)}`);await until(()=>has('PAY-UI-SYNTHETIC'),'payment '+tab)}
 const signIn=async actor=>{await click('Rehearsal: use an existing access token');await input('input[type=password]',actor);await click('Connect with access token');await until(()=>has(actor),'signed in '+actor)}
 const check=(name,condition)=>{assert.ok(condition,name);checks.push(name);console.log('PASS '+name)}
 const screenshot=async name=>{const result=await send('Page.captureScreenshot',{format:'png',captureBeyondViewport:false});await writeFile(resolve(artifacts,name+'.png'),Buffer.from(result.data,'base64'))}
 await send('Emulation.setTimezoneOverride',{timezoneId:'Asia/Kolkata'});await send('Runtime.enable');await send('Page.enable');await send('Fetch.enable',{patterns:[{urlPattern:provider+'/*',requestStage:'Request'}]});await send('Emulation.setDeviceMetricsOverride',{width:1440,height:1000,deviceScaleFactor:1,mobile:false});await send('Page.navigate',{url:'http://127.0.0.1:5190/'})
 await until(()=>has('Sign in with your organization'),'sign in page');await signIn('synthetic-operator');await until(()=>has('Corporate funding accounts'),'overview');check('customer catalog loaded without a browser role selector',!(await ev('Boolean(document.querySelector("[name=role]"))')))
 check('header displays login name below signout with server roles',await ev("(()=>{const account=document.querySelector('.signed-in-account');const button=account.querySelector('button'),label=account.querySelector('.signed-in-user');return label.textContent.includes('Signed in as demo-operator')&&label.textContent.includes('payment operator')&&!label.textContent.includes('payment approver')&&label.getBoundingClientRect().top>=button.getBoundingClientRect().bottom})()"))
 holdCatalog=true;await click('Refresh');await until(()=>heldReads.length===1,'held catalog refresh');const readsWhileHeld=catalogReads
 check('refresh shows progress and prevents repeated clicks',await has('Refreshing application evidence...')&&await ev('[...document.querySelectorAll("button")].filter(e=>e.textContent==="Refreshing...").every(e=>e.disabled)'))
 await delay(5500);check('five-second polling does not abort or duplicate a slow refresh',catalogReads===readsWhileHeld)
 holdCatalog=false;heldReads.splice(0).forEach(resolve=>resolve());await until(()=>has('Evidence refreshed at'),'refresh completion')
 catalogFailure=true;await click('Refresh');await until(()=>has('Evidence refresh failed (HTTP 502)'),'failed refresh feedback');check('failed refresh retains last successful data and explains unavailable API',await has('Corporate funding accounts')&&await has('last successful refresh time is unchanged'))
 catalogFailure=false;await click('Refresh evidence');await until(async()=>await has('Evidence refreshed at')&&!await has('Evidence refresh failed'),'refresh recovery');check('Refresh evidence recovers reads without submitting financial commands',calls.length===0)
 await click('New payment');await until(()=>has('Supplier payment instruction'),'new payment');await ev('document.querySelector("input[type=checkbox]").click()');await click('Create payment intent');await until(()=>has('SYNTHETIC_LOST_ACK'),'uncertain response');await delay(400);check('mutation was not automatically retried',calls.filter(c=>c.path==='/payments').length===1)
 await click('Refresh');await delay(250);check('successful reads retain unresolved command warning',await has('SYNTHETIC_LOST_ACK'));await click('Retry original instruction');await until(()=>has(pay),'created payment');const creates=calls.filter(c=>c.path==='/payments');check('uncertain create retried exact body and original key',creates.length===2&&creates[0].key===creates[1].key&&creates[0].raw===creates[1].raw)
 await click('Prepare / refresh recommendation');await until(()=>has('Deterministic provider comparison'),'prepared');check('no fabricated model recommendation before a run',await has('No model run is recorded'))
 await click('Run reasoning agent');await until(()=>has('SYNTHETIC UI TEST explanation'),'agent result');await screenshot('routing-synthetic');await ev("document.querySelector('.agent-result').closest('.card').scrollIntoView({block:'start'})");await screenshot('agent-recommendation-synthetic');await ev('window.scrollTo(0,0)');check('actual agent record fields and seven signals rendered',await has('SYNTHETIC UI FIXTURE')&&await has('seven-signal evidence'))
 await navigate('treasury');await until(()=>has('USDC available'),'treasury');check('treasury gas estimate carries captured mock evidence',await has('MOCK')&&await has('Required ETH estimate'))
 await navigate('approvals');await until(()=>has('Approve this version'),'approval');check('operator cannot click approve',await ev('[...document.querySelectorAll("button")].find(e=>e.textContent==="Approve this version").disabled'))
 for(const actor of ['synthetic-approver-a','synthetic-approver-b']){await click('Sign out / switch user');await until(()=>has('Sign in with your organization'),'hosted logout return');check('signout clears customer evidence '+actor,!(await has(pay)));check('signout removes header identity '+actor,await ev("!document.querySelector('.signed-in-user')"));await signIn(actor);await navigate('approvals');await until(()=>has('Accept this quote'),'approver page');await click('Accept this quote');await until(()=>has('Command acknowledged'),'acceptance response');await delay(200);await click('Approve this version');await until(()=>has(actor)&&has('Command acknowledged'),'approval response');await delay(250)}
 await navigate('journey');await until(()=>has('Execute approved USDC payment'),'journey');await ev('document.querySelector("input[type=checkbox]").click()');await click('Execute approved USDC payment');await until(()=>has('RECONCILED'),'closure');check('one execution requested after distinct approvals',approvals.size===2&&calls.filter(c=>c.path.endsWith('/execution')).length===1)
 await navigate('blockchain');await until(()=>has('Total fee (wei)'),'chain evidence');await screenshot('blockchain-mock-synthetic');check('mock chain is not advertised as public proof',await has('MOCK - NOT PUBLIC CHAIN PROOF')&&!await has('Independently inspect public transaction'))
 await navigate('ledger');await until(()=>has('BALANCED'),'ledger');await screenshot('ledger-synthetic');check('ledger renders exact 18-decimal fee and per-asset result',await has('0.000000650000000100')&&await has('ETH 0.000000000000000000'))
 await navigate('payout');await until(()=>has('A fiat payout is not required'),'wallet outcome');check('wallet-only instruction does not invent a bank payout',await has('SIMULATED FIAT DELIVERY'))
 check('access tokens absent from browser storage',!(await ev('JSON.stringify({...localStorage,...sessionStorage})')).includes('synthetic-operator'))
 chainMode='LIVE';await navigate('blockchain');await until(()=>has('Independently inspect public transaction'),'live fixture link');check('public explorer link targets supported testnet safely',await ev(`document.querySelector('a[target="_blank"]').href===${JSON.stringify('https://sepolia.basescan.org/tx/'+hash)}`));chainMode='MOCK'
 await send('Emulation.setDeviceMetricsOverride',{width:390,height:844,deviceScaleFactor:1,mobile:true});await ev('location.hash="/overview"');await until(()=>has('Corporate funding accounts'),'mobile');await screenshot('mobile-synthetic');check('mobile layout has no document horizontal overflow',await ev('document.documentElement.scrollWidth<=innerWidth'))
 expire=true;await click('Refresh');await until(()=>has('Sign in with your organization'),'expired session');check('401 clears the entire customer view',!(await has(pay))&&!(await has('100000.00')))
 expire=false
 // Pretend a previous user still has a hosted cookie, then exercise secure login.
 providerUser='synthetic-operator';const beforeLogout=logoutCount
 await click('Sign in securely');await until(()=>has('SYNTHETIC Cognito sign-in form'),'fresh hosted login')
 check('secure login clears retained provider session before showing account entry',logoutCount===beforeLogout+1&&providerUser===null)
 await click('Choose synthetic-approver-a');await until(()=>has('Signed in as demo-approver-a'),'secure first identity')
 check('secure PKCE callback loads selected approver and removes one-time login data',await ev("!sessionStorage.getItem('stableflow:pkce')&&!sessionStorage.getItem('stableflow:fresh-login')&&!location.search"))
 await click('Sign out / switch user');await until(()=>has('Sign in with your organization'),'secure logout')
 check('hosted logout removes old identity and evidence',providerUser===null&&!(await has('Signed in as'))&&!(await has('100000.00')))
 await click('Sign in securely');await until(()=>has('SYNTHETIC Cognito sign-in form'),'second hosted login')
 await click('Choose synthetic-approver-b');await until(()=>has('Signed in as demo-approver-b'),'secure second identity')
 check('second secure login uses a different identity without restoring the previous user',!(await has('Signed in as demo-approver-a'))&&providerUser==='synthetic-approver-b')
 await ev('location.hash="/overview"');await until(()=>has('Recent customer payments'),'payment list deadlines')
 check('payment list shows separate created and delivery columns with explicit zones',await ev("(()=>{const table=[...document.querySelectorAll('table')].find(t=>t.textContent.includes('Created ?'));const cells=table.querySelector('tbody tr').cells;return table.textContent.includes('Deliver by')&&cells[3].textContent.includes('GMT+05:30')&&cells[4].textContent.includes('GMT+05:30')&&/Asia\\/(Calcutta|Kolkata)/.test(cells[4].textContent)})()"))
 check('recent payments retain newest-first order and missing legacy deadline is explicit',await ev("(()=>{const rows=[...document.querySelectorAll('tbody tr')];return rows[0].cells[0].textContent==='PAY-UI-SYNTHETIC'&&rows[1].cells[0].textContent==='PAY-OLDER'&&rows[1].cells[4].textContent==='No confirmed deadline'})()"))
 blocked=true;executed=false;approvals.clear();accepted.clear();agentRun=false
 await navigate('routing');await until(()=>has('NO_ELIGIBLE_ROUTE'),'blocked routing review');await click('synthetic-provider-a: seven-signal evidence, findings and itemized fees');await click('Eligibility findings and costs')
 check('review with no selected quote renders routing findings instead of crashing',await has('FEE_LIMIT_EXCEEDED')&&!(await has('Application evidence could not be displayed')))
 await navigate('approvals');await until(()=>has('No approvable quote'),'blocked quote')
 check('blocked review explains missing quote and cannot accept or approve',await has('NO_ELIGIBLE_ROUTE')&&await ev('[...document.querySelectorAll("button")].filter(e=>["Accept this quote","Approve this version"].includes(e.textContent)).every(e=>e.disabled)'))
 await navigate('journey');await until(()=>has('Execute approved USDC payment'),'blocked execution')
 check('blocked review cannot execute and opening other tabs sends no commands',await ev('[...document.querySelectorAll("button")].find(e=>e.textContent==="Execute approved USDC payment").disabled')&&calls.filter(c=>c.path.endsWith('/execution')).length===1)
 if(exceptions.length)console.error('SYNTHETIC BROWSER EXCEPTIONS',JSON.stringify(exceptions))
 check('no browser runtime exceptions',exceptions.length===0)
 await writeFile(resolve(artifacts,'browser-results.json'),JSON.stringify({status:'PASSED',completedAt:new Date().toISOString(),synthetic:true,checks:checks.length,passed:checks,liveSystemsExecuted:false},null,2));console.log(`BROWSER CHECKS ${checks.length}/${checks.length}; SYNTHETIC ONLY; screenshots: ${artifacts}`)
}catch(error){console.error('BROWSER FAILURE',error);await writeFile(resolve(artifacts,'browser-results.json'),JSON.stringify({status:'FAILED',synthetic:true,checks:checks.length,passed:checks,error:String(error)}));process.exitCode=1}finally{fixture.closeAllConnections();if(socket){try{socket.close()}catch{}}if(edge)edge.kill();if(vite)await Promise.race([vite.close(),new Promise(resolve=>setTimeout(resolve,2000))]);fixture.close();setTimeout(()=>process.exit(process.exitCode??0),1000).unref()}
