// Read-only snapshot of the existing local stack; never starts or builds processes.
const {containerChecks}=require('./startup-status.cjs');
const {frontendState}=require('./local-frontend.cjs');
const componentFor=name=>({payment:'payment-service','compliance-quote':'compliance-quote-service',blockchain:'blockchain-service',ledger:'settlement-ledger-service',reconciliation:'reconciliation-service',visa:'visa-simulator'})[name];

async function stackStatus({config,execute,smoke,fetcher=fetch,onCheck=()=>{}}) {
  const checks=[];
  const record=check=>{checks.push(check);onCheck(check);};
  let containers;
  try {
    const inventory=await execute('docker',['compose','--profile','services','ps','--all','--format','json'],{timeoutMs:10000});
    if(inventory.code!==0)throw Error('Container inventory unavailable');
    containers=containerChecks(inventory.output);
    record({name:'compose:inventory',status:'PASSED'});
  } catch {
    // Do not echo Docker output or claim that unobservable containers are stopped.
    containers=containerChecks('').map(check=>({...check,reason:'CONTAINER_STATUS_UNAVAILABLE'}));
    record({name:'compose:inventory',status:'FAILED',reason:'CONTAINER_STATUS_UNAVAILABLE'});
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
