// Console diagnostics for local startup. Never print container environment or raw logs.
const componentNames=['postgres','localstack','payment-service','compliance-quote-service','blockchain-service','settlement-ledger-service','reconciliation-service','visa-simulator'];

function startupTracker(write=console.log) {
  const components=new Map([...componentNames,'react-frontend'].map(name=>[name,{name,status:'PENDING',reason:'NOT_CHECKED'}]));
  let partial='';
  const snapshot=()=>[...components.values()];
  function update(name,status,reason) {
    const previous=components.get(name);
    if(!previous || previous.status===status && (status==='STARTED' || previous.reason===reason))return;
    components.set(name,{name,status,reason});
    const complete=snapshot().filter(item=>item.status!=='PENDING').length;
    write(`[${complete}/9] ${name} | ${status} | ${reason}`);
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
    return `Components: ${items.filter(item=>item.status==='STARTED').length}/9 started, ${items.filter(item=>item.status==='FAILED').length} failed, ${items.filter(item=>item.status==='PENDING').length} pending`;
  }
  return {update,output,snapshot,summary};
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

async function startupStage(name,task,{write=console.log,intervalMs=15000}={}) {
  const started=Date.now();
  write(`[STARTING] ${name}`);
  const timer=setInterval(()=>write(`[STARTING] ${name}: still working (${Math.floor((Date.now()-started)/1000)}s elapsed)`),intervalMs);
  try {
    const result=await task();
    write(`[${result?.status==='FAILED' || result?.code!==undefined && result.code!==0?'FAILED':'STARTED'}] ${name}`);
    return result;
  } catch(error) {write(`[FAILED] ${name}`);throw error;}
  finally {clearInterval(timer);}
}

function failureMessages(report) {
  if(report.action!=='start' || report.status!=='FAILED')return [];
  const lines=['Startup status: FAILED'];
  for(const [name,ready] of Object.entries(report.prerequisites || {}))if(!ready)lines.push(`Failed component: prerequisite:${name} | Reason: MISSING_OR_UNAVAILABLE`);
  for(const check of [...report.checks,...(report.containers || [])])if(check.status==='FAILED') {
    lines.push(`Failed component: ${check.name} | Reason: ${check.reason || (check.httpStatus!==undefined?'UNEXPECTED_HTTP_'+check.httpStatus:check.exitCode!==undefined?'EXIT_CODE_'+check.exitCode:'READINESS_CHECK_FAILED')}${check.exitCode!==undefined?' | Exit code: '+check.exitCode:''}`);
    if(check.log)lines.push(`Diagnostic log: ${check.log}`);
  }
  if(report.reason)lines.push(`Startup reason: ${report.reason}`);
  if(report.containerDiagnostics==='UNAVAILABLE')lines.push('Container status unavailable; inspect compose.log for the build/start failure.');
  const pending=(report.components || []).filter(component=>component.status==='PENDING').map(component=>component.name);
  if(pending.length)lines.push('Pending / not verified: '+pending.join(', '));
  lines.push('Application readiness was not established. Existing data volumes were retained.');
  return lines;
}
module.exports={containerChecks,startupStage,failureMessages,startupTracker};
