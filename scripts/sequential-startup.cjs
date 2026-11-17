// One Compose target at a time; dependencies are explicitly started first.
const {startupStage}=require('./startup-status.cjs');
const backendOrder=Object.freeze(['postgres','localstack','payment-service','compliance-quote-service','blockchain-service','settlement-ledger-service','reconciliation-service','visa-simulator']);

async function startBackends({execute,tracker,waitSeconds,log,onCheck=()=>{},stageOptions={}}) {
  for(const [index,name] of backendOrder.entries()) {
    const args=['compose','--profile','services','up','-d','--no-deps',...(index<2?[]:['--build']),'--wait','--wait-timeout',String(waitSeconds),name];
    let result;
    try {
      result=await startupStage(name+': '+(index<2?'start and health check':'build, start and health check'),async()=>{
        const outcome=await execute('docker',args,{log,timeoutMs:1800000});
        tracker.update(name,outcome.code===0?'STARTED':'FAILED',outcome.code===0?'Compose health check passed':outcome.timedOut?'COMMAND_TIMEOUT':'BUILD_OR_CONTAINER_READINESS_FAILED');
        return outcome;
      },stageOptions);
    } catch {
      tracker.update(name,'FAILED','COMPONENT_START_COMMAND_FAILED');
      result={code:127,timedOut:false};
    }
    onCheck({name:'compose:'+name,status:result.code===0?'PASSED':'FAILED',exitCode:result.code,reason:result.timedOut?'COMMAND_TIMEOUT':result.code===0?undefined:'BUILD_OR_CONTAINER_READINESS_FAILED'});
    // Leave successful containers/data in place and later components pending.
    if(result.code!==0)return result;
  }
  return {code:0,timedOut:false};
}
module.exports={backendOrder,startBackends};
