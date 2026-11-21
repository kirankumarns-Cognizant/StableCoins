// Read-only snapshot of the existing local stack; never starts or builds processes.
const {containerChecks}=require('./startup-status.cjs');
const {frontendState}=require('./local-frontend.cjs');
const {redact}=require('./failure-diagnostics.cjs');
const componentFor=name=>({payment:'payment-service','compliance-quote':'compliance-quote-service',blockchain:'blockchain-service',ledger:'settlement-ledger-service',reconciliation:'reconciliation-service',visa:'visa-simulator'})[name];

async function stackStatus({config,execute,smoke,fetcher=fetch,onCheck=()=>{}}) {
  const checks=[];
  const record=check=>{checks.push(check);onCheck(check);};
  let containers, inventory, inventoryReason='CONTAINER_COMMAND_FAILED';
  try {
    inventory=await execute('docker',['compose','--profile','services','ps','--all','--format','json'],{timeoutMs:10000});
    if(inventory.code!==0){
      inventoryReason=inventory.timedOut?'CONTAINER_COMMAND_TIMEOUT':'CONTAINER_COMMAND_FAILED';
      throw Error('Container inventory unavailable');
    }
    inventoryReason='CONTAINER_JSON_INVALID';
    containers=containerChecks(inventory.stdout ?? inventory.output);
    record({name:'compose:inventory',status:'PASSED'});
  } catch {
    // Preserve a bounded redacted command error, never raw container JSON or parser input.
    containers=containerChecks('').map(check=>({...check,reason:'CONTAINER_STATUS_UNAVAILABLE'}));
    const detail=inventoryReason==='CONTAINER_JSON_INVALID'
      ? 'Docker stdout was not valid container JSON; stderr warnings are excluded from parsing.'
      : redact(inventory?.stderr || inventory?.output || 'Docker command could not be launched; check Docker installation and access.').slice(0,2000);
    record({name:'compose:inventory',status:'FAILED',reason:'CONTAINER_STATUS_UNAVAILABLE',
      diagnostic:{cause:inventoryReason,exitCode:inventory?.code ?? null,timedOut:Boolean(inventory?.timedOut),message:detail}});
  }
  const backendChecks=await smoke(config,fetcher,record);
  const state=await frontendState(fetcher);
  record({name:'frontend:readiness',status:state==='READY'?'PASSED':'FAILED',reason:state==='READY'?'APPLICATION_AND_PROXY_READY':state});
  const components=containers.map(container=>{
    const failures=backendChecks.filter(check=>componentFor(check.name.split(':')[0])===container.name && check.status==='FAILED');
    return failures.length?{...container,status:'FAILED',reason:failures.map(check=>check.name+':'+(check.reason || 'HTTP_'+check.httpStatus)).join(', ')}:container;
  });
  components.push({name:'react-frontend',status:state==='READY'?'PASSED':'FAILED',reason:state==='READY'?'APPLICATION_AND_PROXY_READY':state});
  return {status:[...checks,...components].every(check=>check.status==='PASSED')?'PASSED':'FAILED',
    scope:'LOCAL_RUNNING_STACK_HEALTH_AND_ACCESS_ONLY',liveAcceptance:false,checks,containers,components};
}
module.exports={stackStatus};
