const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const {join}=require('node:path');
const {main,validationArguments,startupMessages}=require('./local-workbench.cjs');
const {backendOrder}=require('./sequential-startup.cjs');
const {failureMessages}=require('./startup-status.cjs');
const {failureDetails}=require('./failure-diagnostics.cjs');
const {readConnections}=require('../services/config/read-connections.cjs');

function fixture(failure) {
 const calls=[];let frontends=0;
 const dependencies={acquireLifecycle:async()=>()=>{},config:readConnections({}),doctor:async()=>({node:true,java21:true,docker:true,compose:true,dependencies:true}),
  execute:async(exe,args)=>{
   calls.push(args);
   if(args.includes('ps'))return {code:0,stdout:JSON.stringify(backendOrder.map(Service=>({Service,State:'running',Health:'healthy'})))};
   if(args.includes('logs'))return {code:0,output:'ERROR controlled rebuild failure'};
   return {code:args.at(-1)===failure?1:0};
  },
  fetcher:async url=>({status:url.pathname==='/health'?200:401}),
  startFrontend:async()=>{frontends++;return {name:'frontend-start',status:'PASSED',reused:true};}
 };
 return {calls,dependencies,get frontends(){return frontends;}};
}
async function quiet(task) {
 const log=console.log,error=console.error,exitCode=process.exitCode,lines=[];
 console.log=console.error=(...args)=>lines.push(args.join(' '));
 try{return await task(lines);}finally{console.log=log;console.error=error;process.exitCode=exitCode;}
}

test('restart accepts diagnostics but rejects unrelated and destructive options',()=>{
 assert.deepEqual(validationArguments(['restart','--show-failures']),{action:'restart',showFailures:true});
 for(const option of ['--offline','--require-database','--volumes','--unknown'])assert.throws(()=>validationArguments(['restart',option]),/Usage/);
});

test('restart orchestrates six recreations, health/access checks and frontend before announcing success',async()=>quiet(async lines=>{
 const f=fixture();const report=await main(['restart'],f.dependencies);
 assert.equal(report.action,'restart');assert.equal(report.status,'PASSED');
 assert.equal(report.liveAcceptance,false);assert.equal(report.progress.percent,100);
 assert.equal(report.components.length,9);assert.equal(f.frontends,1);
 assert.equal(report.checks.filter(c=>c.name.endsWith(':health')).length,6);
 assert.equal(report.checks.filter(c=>c.name.endsWith('-denied')).length,12);
 assert.equal(f.calls.filter(args=>args.includes('--force-recreate')).length,6);
 assert.ok(lines.some(line=>line==='Restart status: RESTARTED'));
 assert.ok(lines.some(line=>line.startsWith('Application URL:')));
}));

test('start continues to reuse unchanged backends without force recreation',async()=>quiet(async()=>{
 const f=fixture();const report=await main(['start'],f.dependencies);
 assert.equal(report.status,'PASSED');assert.equal(f.calls.filter(args=>args.includes('--force-recreate')).length,0);
 assert.equal(f.calls.filter(args=>args.includes('--build')).length,6);
}));

test('failed restart retains failure despite healthy old inventory, provides diagnostics and no success URL',async()=>quiet(async lines=>{
 const f=fixture('payment-service');const report=await main(['restart','--show-failures'],f.dependencies);
 assert.equal(report.status,'FAILED');assert.equal(process.exitCode,2);
 assert.equal(report.components.find(c=>c.name==='payment-service').status,'FAILED');
 assert.equal(f.frontends,0);assert.equal(f.calls.filter(args=>args.includes('up')).length,3);
 assert.ok(f.calls.some(args=>args.includes('logs')));
 assert.ok(lines.some(line=>line==='Restart status: FAILED'));
 assert.ok(!lines.some(line=>line.startsWith('Application URL:') || line==='Restart status: RESTARTED'));
}));

test('restart prerequisite failure runs no container commands',async()=>quiet(async()=>{
 const f=fixture();f.dependencies.doctor=async()=>({node:true,java21:false,docker:true,compose:true,dependencies:true});
 const report=await main(['restart'],f.dependencies);
 assert.equal(report.status,'FAILED');assert.equal(f.calls.length,0);assert.equal(f.frontends,0);
}));

test('restart frontend failure prevents successful readiness announcement',async()=>quiet(async lines=>{
 const f=fixture();f.dependencies.startFrontend=async()=>{throw Error('Frontend not ready');};
 const report=await main(['restart'],f.dependencies);
 assert.equal(report.status,'FAILED');assert.equal(report.components.at(-1).status,'FAILED');
 assert.deepEqual(startupMessages(report),[]);assert.ok(!lines.some(line=>line.startsWith('Application URL:')));
}));

test('restart backend access failure prevents frontend start and successful readiness',async()=>quiet(async()=>{
 const f=fixture();f.dependencies.fetcher=async()=>({status:200});
 const report=await main(['restart'],f.dependencies);
 assert.equal(report.status,'FAILED');assert.equal(f.frontends,0);
 assert.equal(report.checks.filter(c=>c.status==='FAILED').length,12);
}));

test('restart failure includes retained-volume summary and bounded diagnostic excerpts',t=>{
 const directory=fs.mkdtempSync(join(__dirname,'../.local/restart-diagnostics-'));
 const log=join(directory,'compose.log');fs.writeFileSync(log,'ERROR rebuild failed');
 t.after(()=>{fs.unlinkSync(log);fs.rmdirSync(directory);});
 const report={action:'restart',status:'FAILED',checks:[{name:'compose:payment-service',status:'FAILED',log}]};
 assert.match(failureMessages(report).join('\n'),/Existing data volumes were retained/);
 assert.match(failureDetails(report,{root:join(__dirname,'..'),directory,environment:{}}).join('\n'),/rebuild failed/);
});
