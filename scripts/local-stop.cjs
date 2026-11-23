// Local shutdown only. Keep containers, networks, images, volumes and financial records.
const net=require('node:net');
const {nodeProcesses,ownsFrontend}=require('./local-processes.cjs');
const backends=Object.freeze(['payment-service','compliance-quote-service','blockchain-service','settlement-ledger-service','reconciliation-service','visa-simulator']);
const infrastructure=Object.freeze(['localstack','postgres']);
function portOpen() {
 return new Promise(resolve=>{
  const socket=net.connect({host:'127.0.0.1',port:5173});
  const finish=value=>{socket.destroy();resolve(value);};
  socket.once('connect',()=>finish(true));socket.once('error',error=>finish(error.code!=='ECONNREFUSED'));
  socket.setTimeout(1000,()=>finish(true));
 });
}
async function stopFrontend({root,timeoutMs=20000},{list=nodeProcesses,signal=(pid)=>process.kill(pid,'SIGTERM'),isOpen=portOpen,pause=()=>new Promise(r=>setTimeout(r,250)),now=Date.now}={}) {
 if(!Number.isSafeInteger(timeoutMs)||timeoutMs<100||timeoutMs>60000)throw Error('Invalid frontend shutdown timeout');
 const owned=(await list()).filter(row=>ownsFrontend(row,root));
 for(const row of owned) {
  // Recheck process start identity immediately before signaling, protecting against stale PID reuse.
  const current=(await list()).find(value=>value.pid===row.pid);
  if(!current || current.started!==row.started || !ownsFrontend(current,root))continue;
  try {await signal(row.pid);}catch(error){if(error.code!=='ESRCH')throw Error('Owned frontend process could not be stopped');}
 }
 const deadline=now()+timeoutMs;
 do {
  const alive=(await list()).some(row=>ownsFrontend(row,root));
  const open=await isOpen();
  if(!alive&&!open)return {name:'react-frontend',status:'PASSED',state:'STOPPED',reason:owned.length?'STOPPED':'ALREADY_STOPPED'};
  if(!alive&&open)throw Error('Port 5173 is still occupied by an unrecognized process; it was not terminated');
  await pause();
 }while(now()<deadline);
 throw Error('Frontend stop timed out; application shutdown is incomplete');
}
function inventoryRows(output) {
 const text=String(output || '').trim();
 const rows=text.startsWith('[')?JSON.parse(text):text?text.split(/\r?\n/).filter(Boolean).map(line=>JSON.parse(line)):[];
 if(!Array.isArray(rows)||rows.some(row=>!row||typeof row.Service!=='string'||typeof row.State!=='string'))throw Error('Invalid shutdown inventory');
 return rows;
}
function stopped(rows,name) {return rows.filter(row=>row.Service===name).every(row=>['exited','created','dead','stopped','removed'].includes(row.State.toLowerCase()));}
async function stopApplication({root,execute,log,onCheck=()=>{},frontend=stopFrontend}) {
 const checks=[],components=[];
 const record=(check,component=false)=>{checks.push(check);if(component)components.push(check);onCheck(check);};
 const snapshot=async()=>{
  const result=await execute('docker',['compose','--profile','services','ps','--all','--format','json'],{timeoutMs:10000});
  if(result.code!==0)throw Error('Container shutdown inventory unavailable');
  return inventoryRows(result.stdout ?? result.output);
 };
 try {record(await frontend({root}),true);}catch(error){record({name:'react-frontend',status:'FAILED',reason:error.message},true);}
 let rows;
 try {rows=await snapshot();record({name:'compose:stop-inventory',status:'PASSED'});}
 catch {record({name:'compose:stop-inventory',status:'FAILED',reason:'CONTAINER_STATUS_UNAVAILABLE'});}
 const stopOne=async name=>{
  if(!rows){record({name,status:'FAILED',reason:'CONTAINER_STATUS_UNAVAILABLE'},true);return;}
  const already=stopped(rows,name);let result={code:0};
  if(!already) {
   console.log('[STOPPING] '+name+' | allowing up to 45 seconds for container shutdown');
   try {result=await execute('docker',['compose','--profile','services','stop','--timeout','45',name],{log,timeoutMs:60000});}
   catch {result={code:127};}
  }
  try {rows=await snapshot();}catch {rows=null;}
  const passed=result.code===0&&rows&&stopped(rows,name);
  record({name,status:passed?'PASSED':'FAILED',state:passed?'STOPPED':'UNKNOWN',reason:passed?(already?'ALREADY_STOPPED':'STOPPED'):result.timedOut?'COMMAND_TIMEOUT':!rows?'CONTAINER_STATUS_UNAVAILABLE':'CONTAINER_STOP_NOT_CONFIRMED',exitCode:result.code},true);
 };
 for(const name of backends)await stopOne(name);
 // Recheck the whole worker group before removing its database/messaging dependencies.
 try {rows=await snapshot();}catch {rows=null;record({name:'compose:dependency-stop-inventory',status:'FAILED',reason:'CONTAINER_STATUS_UNAVAILABLE'});}
 const workersStopped=rows&&backends.every(name=>stopped(rows,name)&&components.some(c=>c.name===name&&c.status==='PASSED'));
 for(const name of infrastructure) {
  if(workersStopped)await stopOne(name);
  else record({name,status:'FAILED',state:'RETAINED',reason:'BACKEND_STOP_NOT_CONFIRMED; dependency retained'},true);
 }
 // Final inventory catches workers or infrastructure which restarted during shutdown.
 try {
  const final=await snapshot();
  for(const component of components.filter(c=>c.name!=='react-frontend'))if(!stopped(final,component.name)){component.status='FAILED';component.state='RUNNING_OR_UNKNOWN';component.reason='CONTAINER_STILL_ACTIVE';}
 }catch {record({name:'compose:final-stop-inventory',status:'FAILED',reason:'CONTAINER_STATUS_UNAVAILABLE'});}
 try {const final=await frontend({root});if(final.status!=='PASSED')throw Error(final.reason||'Frontend shutdown not confirmed');}catch(error){const ui=components.find(c=>c.name==='react-frontend');ui.status='FAILED';ui.reason=error.message;}
 return {status:checks.every(c=>c.status==='PASSED')?'PASSED':'FAILED',scope:'LOCAL_APPLICATION_SHUTDOWN',checks,components};
}
module.exports={backends,infrastructure,portOpen,stopFrontend,inventoryRows,stopped,stopApplication};
