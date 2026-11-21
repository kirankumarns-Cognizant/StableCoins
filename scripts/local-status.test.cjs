const {test}=require('node:test');
const assert=require('node:assert/strict');
const {stackStatus}=require('./local-status.cjs');
const {smoke,main,validationArguments}=require('./local-workbench.cjs');
const {readConnections}=require('../services/config/read-connections.cjs');
const {failureMessages}=require('./startup-status.cjs');
const names=['postgres','localstack','payment-service','compliance-quote-service','blockchain-service','settlement-ledger-service','reconciliation-service','visa-simulator'];
const inventory=()=>names.map(Service=>({Service,State:'running',Health:'healthy'}));
function fetcher({paymentDown=false,proxyStatus=401,frontendDown=false,unsafeAccess=false}={}) {
 return async(input)=>{
  const url=new URL(input);
  if(url.port==='5173') {
   if(frontendDown)throw Error('private frontend failure');
   if(url.pathname==='/__stablecoins_dev_health')return Response.json({application:'stableflow-vite-development'});
   if(url.pathname==='/')return new Response('<title>StableFlow | Stablecoin Payment Workspace</title>');
   return new Response(null,{status:proxyStatus});
  }
  if(paymentDown && url.port==='8080')throw Error('private API failure');
  return new Response(null,{status:url.pathname==='/health'?200:unsafeAccess?200:401});
 };
}
function execute(rows=inventory()) {
 const calls=[];
 const run=async(exe,args)=>{calls.push({exe,args});return {code:0,output:JSON.stringify(rows)};};
 return {calls,run};
}
test('status accepts only the read-only diagnostic option',()=>{
 assert.deepEqual(validationArguments(['status']),{action:'status',showFailures:false});
 assert.deepEqual(validationArguments(['status','--show-failures']),{action:'status',showFailures:true});
 for(const flag of ['--offline','--require-database','--restart'])assert.throws(()=>validationArguments(['status',flag]),/Usage/);
});
test('healthy stack requires eight containers, eighteen access probes and the real frontend marker/proxy',async()=>{
 const docker=execute(),observed=[];
 const result=await stackStatus({config:readConnections({}),execute:docker.run,smoke,fetcher:fetcher(),onCheck:check=>observed.push(check)});
 assert.equal(result.status,'PASSED');assert.equal(result.liveAcceptance,false);
 assert.equal(result.components.length,9);assert.equal(result.checks.length,20);assert.equal(observed.length,20);
 assert.ok(result.components.every(component=>component.status==='PASSED'));
 assert.deepEqual(docker.calls,[{exe:'docker',args:['compose','--profile','services','ps','--all','--format','json']}]);
});
test('healthy containers cannot hide a reset payment API or a frontend proxy returning 502',async()=>{
 const docker=execute();
 const result=await stackStatus({config:readConnections({}),execute:docker.run,smoke,fetcher:fetcher({paymentDown:true,proxyStatus:502})});
 assert.equal(result.status,'FAILED');
 assert.equal(result.components.find(item=>item.name==='payment-service').status,'FAILED');
 assert.equal(result.components.find(item=>item.name==='react-frontend').status,'FAILED');
 assert.ok(!JSON.stringify(result).includes('private'));
});
test('a stopped database or missing service fails even when HTTP probes pass',async()=>{
 const rows=inventory().filter(row=>row.Service!=='visa-simulator');
 Object.assign(rows[0],{State:'exited',Health:'',ExitCode:255});
 const result=await stackStatus({config:readConnections({}),execute:execute(rows).run,smoke,fetcher:fetcher()});
 assert.equal(result.status,'FAILED');
 assert.equal(result.containers[0].reason,'EXITED');assert.equal(result.containers[0].exitCode,255);
 assert.equal(result.containers.at(-1).reason,'NOT_CREATED_OR_NOT_REPORTED');
});
test('unavailable or malformed Docker inventory is unknown, never healthy or assumed stopped',async()=>{
 for(const run of [async()=>({code:127,output:'token=private-docker-value'}),async()=>({code:0,output:'not json'}),async()=>{throw Error('private failure');}]){
  const result=await stackStatus({config:readConnections({}),execute:run,smoke,fetcher:fetcher()});
  assert.equal(result.status,'FAILED');assert.ok(result.containers.every(item=>item.reason==='CONTAINER_STATUS_UNAVAILABLE'));
  assert.ok(!JSON.stringify(result).includes('private'));
 }
});
test('status fails for a missing frontend and for missing access enforcement',async()=>{
 for(const options of [{frontendDown:true},{unsafeAccess:true}]) {
  const result=await stackStatus({config:readConnections({}),execute:execute().run,smoke,fetcher:fetcher(options)});
  assert.equal(result.status,'FAILED');
 }
});
async function invoke(t,args,dependencies) {
 const lines=[],oldLog=console.log,oldError=console.error,oldExit=process.exitCode;
 console.log=(...values)=>lines.push(values.join(' '));console.error=console.log;
 try {const report=await main(args,dependencies);return {report,lines:lines.join('\n'),exitCode:process.exitCode};}
 finally {console.log=oldLog;console.error=oldError;process.exitCode=oldExit;}
}
test('status CLI saves a report and does not start, rebuild, collect logs or run financial operations',async t=>{
 const docker=execute();const {report,lines}=await invoke(t,['status'],{config:readConnections({}),execute:docker.run,fetcher:fetcher()});
 assert.equal(report.status,'PASSED');assert.ok(report.completedAt);
 assert.match(lines,/Stack status: READY.*9\/9 ready/);assert.match(lines,/Result: PASSED/);
 assert.ok(!lines.includes('Server startup completed'));
 assert.equal(docker.calls.length,1);
 const reportFile=lines.match(/Local status; report: (.+)/)[1];
 assert.equal(JSON.parse(require('node:fs').readFileSync(reportFile,'utf8')).status,'PASSED');
});
test('status --show-failures collects only failed service logs, redacts them and retains exit code 2',async t=>{
 const calls=[];
 const run=async(exe,args)=>{calls.push(args);return args.includes('logs')?{code:0,output:'ERROR password=dummy-diagnostic-secret connection refused'}:{code:0,output:JSON.stringify(inventory())};};
 const {report,lines,exitCode}=await invoke(t,['status','--show-failures'],{config:readConnections({}),execute:run,fetcher:fetcher({paymentDown:true,proxyStatus:502})});
 assert.equal(report.status,'FAILED');assert.equal(exitCode,2);
 assert.match(lines,/Stack status: NOT_READY/);assert.match(lines,/connection refused/);assert.ok(!lines.includes('dummy-diagnostic-secret'));
 assert.equal(calls.length,2);assert.deepEqual(calls[1],['compose','--profile','services','logs','--no-color','--tail','80','payment-service']);
 assert.match(failureMessages(report).join('\n'),/payment:health/);
 assert.ok(!require('node:fs').readFileSync(report.diagnostics.log,'utf8').includes('dummy-diagnostic-secret'));
});
test('failed status without the option never collects logs',async t=>{
 const docker=execute();const {exitCode,report}=await invoke(t,['status'],{config:readConnections({}),execute:docker.run,fetcher:fetcher({frontendDown:true})});
 assert.equal(exitCode,2);assert.equal(report.status,'FAILED');assert.equal(docker.calls.length,1);
});
test('status refuses external service origins before Docker or HTTP access',async t=>{
 let calls=0;const {report,exitCode,lines}=await invoke(t,['status','--show-failures'],{
  config:readConnections({PAYMENT_SERVICE_URL:'https://user:private-value@external.test'}),
  execute:async()=>{calls++;},fetcher:async()=>{calls++;}});
 assert.equal(report.status,'FAILED');assert.equal(exitCode,2);assert.equal(calls,0);assert.ok(!lines.includes('private-value'));
});
