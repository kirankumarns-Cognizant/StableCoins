// Console diagnostics for local startup. Never print container environment or raw logs.
const componentNames=['postgres','localstack','payment-service','compliance-quote-service','blockchain-service','settlement-ledger-service','reconciliation-service','visa-simulator'];

function progressOf(items,basis) {
  const total=items.length,completed=items.filter(item=>item.status!=='PENDING').length;
  return {basis,completed,total,percent:Math.floor(completed*100/total),failed:items.filter(item=>item.status==='FAILED').length};
}
function verificationTracker(write=console.log) {
  const stages=new Map(['prerequisites','node','frontend-build','frontend-lint','cdk-build','cdk-tests','java','java-evidence'].map(name=>[name,{name,status:'PENDING'}]));
  const progress=()=>progressOf([...stages.values()],'verification-stages');
  function update(name,status) {
    if(!stages.has(name) || !['PASSED','FAILED'].includes(status))throw Error('Invalid verification progress update');
    if(stages.get(name).status===status)return;
    stages.set(name,{name,status});
    const p=progress();
    write('['+p.completed+'/'+p.total+' | '+p.percent+'%] '+name+' | '+status);
  }
  const summary=()=>{const p=progress();return 'Verification progress: '+p.percent+'% | '+p.completed+'/'+p.total+' stages finished, '+p.failed+' failed';};
  return {update,progress,summary};
}

function startupTracker(write=console.log) {
  const components=new Map([...componentNames,'react-frontend'].map(name=>[name,{name,status:'PENDING',reason:'NOT_CHECKED'}]));
  const progress=()=>progressOf(snapshot(),'startup-components');
  let partial='';
  const snapshot=()=>[...components.values()];
  function update(name,status,reason) {
    const previous=components.get(name);
    if(!previous || previous.status===status && (status==='STARTED' || previous.reason===reason))return;
    components.set(name,{name,status,reason});
    const complete=snapshot().filter(item=>item.status!=='PENDING').length;
    write(`[${complete}/9 | ${progress().percent}%] ${name} | ${status} | ${reason}`);
  }
  function output(chunk) {
    partial+=String(chunk);
    const lines=partial.split(/\r?\n/);partial=lines.pop();
    for(const line of lines) {
      const match=line.trim().match(/^Container stablecoins-([a-z-]+)\s+Healthy$/i);
      if(match)update(match[1],'STARTED','Container health check passed');
      const failure=line.trim().match(/^container stablecoins-([a-z-]+) is unhealthy$/i);
      if(failure)update(failure[1],'FAILED','Container unhealthy; inspect compose.log');
    }
    // Discard exceptionally long build lines; only allowlisted readiness events are printed.
    if(partial.length>4096)partial='';
  }
  function summary() {
    const items=snapshot();
    return `Startup progress: ${progress().percent}% | Components: ${items.filter(item=>item.status==='STARTED').length}/9 started, ${items.filter(item=>item.status==='FAILED').length} failed, ${items.filter(item=>item.status==='PENDING').length} pending`;
  }
  return {update,output,snapshot,summary,progress};
}

function containerChecks(output) {
  const text=output.trim();
  const rows=text.startsWith('[')?JSON.parse(text):text.split(/\r?\n/).filter(Boolean).map(line=>JSON.parse(line));
  if(!Array.isArray(rows))throw Error('Invalid container status response');
  return componentNames.map(name=>{
    const row=rows.find(row=>row.Service===name);
    if(!row)return {name,status:'FAILED',reason:'NOT_CREATED_OR_NOT_REPORTED'};
    const state=String(row.State || '').toLowerCase(),health=String(row.Health || '').toLowerCase();
    if(state==='running' && health==='healthy')return {name,status:'PASSED',reason:'HEALTHY'};
    if(row.ExitCode!==undefined && Number.isInteger(Number(row.ExitCode)) && state==='exited')return {name,status:'FAILED',reason:'EXITED',exitCode:Number(row.ExitCode)};
    return {name,status:'FAILED',reason:health==='unhealthy'?'UNHEALTHY':state==='running'?'NOT_YET_HEALTHY':state==='restarting'?'RESTARTING':'NOT_RUNNING'};
  });
}

async function startupStage(name,task,{write=console.log,intervalMs=15000,progress,successStatus='STARTED'}={}) {
  const started=Date.now();
  const suffix=()=>progress?` | ${progress().percent}% complete`: '';
  write(`[STARTING] ${name}${suffix()}`);
  const timer=setInterval(()=>write(`[STARTING] ${name}: still working (${Math.floor((Date.now()-started)/1000)}s elapsed)${suffix()}`),intervalMs);
  try {
    const result=await task();
    write(`[${result?.status==='FAILED' || result?.code!==undefined && result.code!==0?'FAILED':successStatus}] ${name}${suffix()}`);
    return result;
  } catch(error) {write(`[FAILED] ${name}${suffix()}`);throw error;}
  finally {clearInterval(timer);}
}

function failureMessages(report) {
  if(!['start','restart','stop','verify','status'].includes(report.action) || report.status!=='FAILED')return [];
  const label={start:'Startup',restart:'Restart',stop:'Stop',verify:'Verification',status:'Stack'}[report.action];
  const lines=[label+' status: FAILED'];
  for(const [name,ready] of Object.entries(report.prerequisites || {}))if(!ready)lines.push(`Failed component: prerequisite:${name} | Reason: MISSING_OR_UNAVAILABLE`);
  for(const check of [...(report.checks || []),...(report.containers || [])])if(check.status==='FAILED') {
    lines.push(`Failed component: ${check.name} | Reason: ${check.reason || (check.httpStatus!==undefined?'UNEXPECTED_HTTP_'+check.httpStatus:check.exitCode!==undefined?'EXIT_CODE_'+check.exitCode:'READINESS_CHECK_FAILED')}${check.exitCode!==undefined?' | Exit code: '+check.exitCode:''}`);
    if(check.log)lines.push(`Diagnostic log: ${check.log}`);
  }
  for(const suite of report.java?.suites || [])if(suite.failures || suite.errors)lines.push(`Failed test suite: ${suite.name} | Failures: ${suite.failures} | Errors: ${suite.errors}`);
  if(report.databaseGate==='NOT_SATISFIED')lines.push('Database gate: NOT_SATISFIED; required integration evidence is missing, failed or skipped.');
  if(report.reason)lines.push(`${label} reason: ${report.reason}`);
  if(report.containerDiagnostics==='UNAVAILABLE')lines.push('Container status unavailable; inspect compose.log for the build/start failure.');
  const pending=(report.components || []).filter(component=>component.status==='PENDING').map(component=>component.name);
  if(pending.length)lines.push('Pending / not verified: '+pending.join(', '));
  if(['start','restart'].includes(report.action))lines.push('Application readiness was not established. Existing data volumes were retained.');
  return lines;
}
module.exports={containerChecks,startupStage,failureMessages,startupTracker,verificationTracker};
