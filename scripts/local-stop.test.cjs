const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const {join}=require('node:path');
const {stopApplication,stopFrontend,inventoryRows,stopped,backends,infrastructure}=require('./local-stop.cjs');
const {ownsFrontend,windowsArguments}=require('./local-processes.cjs');
const {main,validationArguments}=require('./local-workbench.cjs');
const {acquireLifecycle}=require('./lifecycle-lock.cjs');

const ui=()=>({name:'react-frontend',status:'PASSED',state:'STOPPED'});
function stack(options={}) {
 const calls=[],names=[...backends,...infrastructure];
 const rows=names.map(Service=>({Service,State:options.already?'exited':'running'}));
 let reads=0;
 const execute=async(exe,args)=>{
  calls.push(args);
  if(args.includes('logs'))return {code:0,output:'ERROR controlled shutdown failure'};
  if(args.includes('ps')) {
   reads++;
   if(options.inventoryFailure)return {code:1};
   if(options.malformed)return {code:0,stdout:'not json'};
   if(options.onInventory)options.onInventory(rows,reads);
   return {code:0,stdout:JSON.stringify(rows),stderr:'Compose version warning'};
  }
  const name=args.at(-1);
  if(name===options.failure)return {code:1,timedOut:!!options.timeout};
  if(name!==options.stuck)rows.find(row=>row.Service===name).State='exited';
  return {code:0};
 };
 return {calls,rows,execute};
}
async function quiet(task) {
 const log=console.log,error=console.error,exit=process.exitCode,lines=[];
 console.log=console.error=(...args)=>lines.push(args.join(' '));
 try{return await task(lines);}finally{console.log=log;console.error=error;process.exitCode=exit;}
}
function ownedFixture(t) {
 const root=fs.mkdtempSync(join(__dirname,'../.local/stop-process-'));
 const folders=['frontend','frontend/node_modules','frontend/node_modules/vite','frontend/node_modules/vite/bin'];
 for(const dir of folders)fs.mkdirSync(join(root,dir));
 const entry=join(root,'frontend/node_modules/vite/bin/vite.js');fs.writeFileSync(entry,'// test-owned entrypoint');
 t.after(()=>{fs.unlinkSync(entry);for(const dir of folders.reverse())fs.rmdirSync(join(root,dir));fs.rmdirSync(root);});
 const row={pid:process.pid+100000,exe:process.execPath,args:[process.execPath,entry],cwd:join(root,'frontend'),started:'123'};
 return {root,row,entry};
}

test('stop parses array and newline inventory, including absent containers',()=>{
 const row={Service:'postgres',State:'exited'};
 assert.deepEqual(inventoryRows(JSON.stringify([row])),[row]);
 assert.equal(inventoryRows(JSON.stringify(row)+'\n'+JSON.stringify(row)).length,2);
 assert.ok(stopped(inventoryRows(''),'postgres'));
 assert.ok(!stopped([{...row,State:'restarting'}],'postgres'));
 assert.ok(!stopped([row,{...row,State:'running'}],'postgres'));
 for(const value of ['broken','null','[{}]'])assert.throws(()=>inventoryRows(value));
});

test('stop frontend then all workers then dependencies, with no destructive/build commands',async()=>quiet(async()=>{
 const f=stack();let first=true;
 const report=await stopApplication({root:__dirname,execute:f.execute,frontend:async()=>{if(first){assert.equal(f.calls.length,0);first=false;}return ui();}});
 assert.equal(report.status,'PASSED');assert.equal(report.components.length,9);
 assert.deepEqual(f.calls.filter(args=>args.includes('stop')).map(args=>args.at(-1)),[...backends,...infrastructure]);
 assert.ok(f.calls.every(args=>!args.some(arg=>['up','down','rm','--build','-v','--volumes'].includes(arg))));
 assert.ok(f.calls.filter(args=>args.includes('stop')).every(args=>args.includes('45')));
}));

test('stop is idempotent when all components are already stopped',async()=>quiet(async()=>{
 const f=stack({already:true});const report=await stopApplication({execute:f.execute,frontend:async()=>ui()});
 assert.equal(report.status,'PASSED');assert.equal(f.calls.filter(args=>args.includes('stop')).length,0);
 assert.ok(report.components.slice(1).every(c=>c.reason==='ALREADY_STOPPED'));
}));

for(const options of [{failure:'payment-service'},{stuck:'blockchain-service'},{failure:'visa-simulator',timeout:true}]) {
 test('backend shutdown failure retains dependencies: '+JSON.stringify(options),async()=>quiet(async()=>{
  const f=stack(options),report=await stopApplication({execute:f.execute,frontend:async()=>ui()});
  assert.equal(report.status,'FAILED');
  assert.equal(f.calls.filter(args=>args.includes('stop')).length,6);
  assert.ok(report.components.filter(c=>infrastructure.includes(c.name)).every(c=>c.state==='RETAINED'||c.status==='FAILED'));
  assert.ok(!f.calls.some(args=>args.includes('stop')&&infrastructure.includes(args.at(-1))));
 }));
}
for(const options of [{inventoryFailure:true},{malformed:true}]) {
 test('unavailable inventory cannot claim shutdown or stop database: '+JSON.stringify(options),async()=>quiet(async()=>{
  const f=stack(options),report=await stopApplication({execute:f.execute,frontend:async()=>ui()});
  assert.equal(report.status,'FAILED');assert.ok(!f.calls.some(args=>args.includes('stop')));
 }));
}

test('worker restart before dependency shutdown is detected and dependencies retained',async()=>quiet(async()=>{
 const f=stack({onInventory:(rows,count)=>{if(count===8)rows.find(r=>r.Service==='payment-service').State='running';}});
 const report=await stopApplication({execute:f.execute,frontend:async()=>ui()});
 assert.equal(report.status,'FAILED');assert.ok(!f.calls.some(args=>args.includes('stop')&&infrastructure.includes(args.at(-1))));
 assert.equal(report.components.find(c=>c.name==='payment-service').reason,'CONTAINER_STILL_ACTIVE');
}));

test('late inventory and frontend failures prevent successful shutdown',async()=>quiet(async()=>{
 const f=stack({onInventory:(rows,count)=>{if(count===11)rows.find(r=>r.Service==='postgres').State='running';}});
 let count=0;
 const report=await stopApplication({execute:f.execute,frontend:async()=>{if(++count===2)throw Error('Frontend reappeared');return ui();}});
 assert.equal(report.status,'FAILED');assert.equal(report.components[0].reason,'Frontend reappeared');
 assert.equal(report.components.find(c=>c.name==='postgres').status,'FAILED');
}));

test('frontend process ownership requires exact checkout entrypoint and process identity',t=>{
 const {root,row}=ownedFixture(t);assert.ok(ownsFrontend(row,root));
 for(const change of [{pid:process.pid},{pid:1},{started:undefined},{exe:'other.exe'},{args:[process.execPath,'other.js']},{cwd:root},{cwd:undefined,args:[process.execPath,'vite.js']}])assert.equal(ownsFrontend({...row,...change},root),false);
 assert.deepEqual(windowsArguments('"C:\\Program Files\\node.exe" "C:\\my app\\vite.js" --port 5173'),['C:\\Program Files\\node.exe','C:\\my app\\vite.js','--port','5173']);
});

test('frontend stop signals only its owned process and confirms process/port exit',async t=>{
 const {root,row}=ownedFixture(t);let alive=true;const signals=[];
 const other={...row,pid:row.pid+1,args:[process.execPath,join(root,'other.js')]};
 const report=await stopFrontend({root},{list:async()=>alive?[row,other]:[other],signal:async pid=>{signals.push(pid);alive=false;},isOpen:async()=>false});
 assert.equal(report.status,'PASSED');assert.deepEqual(signals,[row.pid]);
});

test('frontend stop refuses unknown port owner without signaling it',async()=>{
 let signals=0;await assert.rejects(stopFrontend({root:__dirname},{list:async()=>[],isOpen:async()=>true,signal:async()=>signals++}),/unrecognized process/);
 assert.equal(signals,0);
});

test('frontend stop detects reused PID and leaves changed process untouched',async t=>{
 const {root,row}=ownedFixture(t);let reads=0,signals=0,time=0;
 await assert.rejects(stopFrontend({root,timeoutMs:100},{list:async()=>[{...row,started:++reads===1?'123':'new'}],signal:async()=>signals++,isOpen:async()=>true,now:()=>time,pause:async()=>{time+=100;}}),/timed out/);
 assert.equal(signals,0);
});

test('frontend stop validates timeout before signaling and reports signal denial',async t=>{
 const {root,row}=ownedFixture(t);let signals=0;
 await assert.rejects(stopFrontend({root,timeoutMs:0},{list:async()=>[row],signal:async()=>signals++}),/Invalid/);assert.equal(signals,0);
 await assert.rejects(stopFrontend({root},{list:async()=>[row],signal:async()=>{throw Object.assign(Error('denied'),{code:'EPERM'});}}),/could not be stopped/);
});

test('lifecycle mutex rejects concurrent commands and releases for subsequent command',async t=>{
 const root=fs.mkdtempSync(join(__dirname,'../.local/stop-lock-'));t.after(()=>fs.rmdirSync(root));
 const release=await acquireLifecycle(root);t.after(release);
 await assert.rejects(acquireLifecycle(root),/Another start, restart or stop/);
 await release();await release();const again=await acquireLifecycle(root);await again();
});

test('stop CLI rejects destructive flags and does not require Java, build deps or API health',async()=>quiet(async lines=>{
 assert.deepEqual(validationArguments(['stop','--show-failures']),{action:'stop',showFailures:true});
 for(const option of ['--offline','--volumes','--require-database'])assert.throws(()=>validationArguments(['stop',option]),/Usage/);
 const f=stack();let releases=0;
 const report=await main(['stop'],{execute:f.execute,stopFrontend:async()=>ui(),acquireLifecycle:async()=>()=>{releases++;},doctor:()=>{throw Error('must not run');},fetcher:()=>{throw Error('must not run');}});
 assert.equal(report.status,'PASSED');assert.equal(report.scope,'LOCAL_APPLICATION_SHUTDOWN');assert.equal(report.components.length,9);assert.equal(releases,1);
 assert.ok(lines.some(line=>line==='Stop status: STOPPED | Components: 9/9 stopped'));
 assert.ok(!lines.some(line=>line.startsWith('Application URL:')));
}));

test('stop CLI failure retains exit code and provides failure diagnostics',async()=>quiet(async lines=>{
 const f=stack({failure:'payment-service'});let releases=0;
 const report=await main(['stop','--show-failures'],{execute:f.execute,stopFrontend:async()=>ui(),acquireLifecycle:async()=>()=>{releases++;}});
 assert.equal(report.status,'FAILED');assert.equal(process.exitCode,2);assert.equal(releases,1);
 assert.ok(lines.some(line=>line.includes('Stop status: INCOMPLETE')));
 assert.ok(lines.some(line=>line.includes('controlled shutdown failure')));
}));

test('lifecycle lock refusal prevents all stop operations',async()=>quiet(async()=>{
 let called=false;
 const report=await main(['stop'],{acquireLifecycle:async()=>{throw Error('Another start, restart or stop command is active');},execute:async()=>{called=true;},stopFrontend:async()=>{called=true;}});
 assert.equal(report.status,'FAILED');assert.equal(called,false);
}));

test('native process inspection and shutdown terminate only a test-owned frontend child',async t=>{
 const {fork}=require('node:child_process');
 const {once}=require('node:events');
 const {root,entry}=ownedFixture(t);
 fs.writeFileSync(entry,"setInterval(()=>{},1000);process.send('ready');");
 const env={...process.env};delete env.NODE_TEST_CONTEXT;
 const child=fork(entry,[],{cwd:join(root,'frontend'),execArgv:[],env,silent:true,windowsHide:true});
 const exited=once(child,'exit');
 t.after(async()=>{if(child.exitCode===null&&child.signalCode===null){child.kill();await exited;}});
 await once(child,'message',{signal:AbortSignal.timeout(10000)});
 const report=await stopFrontend({root},{isOpen:async()=>false});
 await exited;assert.equal(report.status,'PASSED');
});

test('loss of final inventory cannot establish successful shutdown',async()=>quiet(async()=>{
 const f=stack();let reads=0;
 const execute=async(exe,args)=>args.includes('ps')&&++reads===11?{code:1}:f.execute(exe,args);
 const report=await stopApplication({execute,frontend:async()=>ui()});
 assert.equal(report.status,'FAILED');assert.ok(report.checks.some(c=>c.name==='compose:final-stop-inventory'&&c.status==='FAILED'));
}));

test('an unrecognized frontend remains a failure while backend shutdown can proceed',async()=>quiet(async()=>{
 const f=stack();const report=await stopApplication({execute:f.execute,frontend:async()=>{throw Error('Port 5173 has an unrecognized process');}});
 assert.equal(report.status,'FAILED');assert.equal(report.components[0].status,'FAILED');
 assert.equal(f.calls.filter(args=>args.includes('stop')).length,8);
}));
