#!/usr/bin/env node
'use strict';
const n=require('./network.cjs');
async function main(args) {
  const [command,...rest]=args;
  if(!['init','start','stop','status','pin','deploy','preflight'].includes(command))n.fail('USAGE_BESU_INIT_START_STOP_STATUS_PIN_DEPLOY_PREFLIGHT');
  if(command==='deploy') {if(rest.length!==1||rest[0]!=='--confirm-private-demo')n.fail('EXPLICIT_PRIVATE_DEMO_DEPLOYMENT_CONFIRMATION_REQUIRED');}
  else if(command==='preflight') {if(rest.length>1)n.fail('USAGE_PREFLIGHT_OPTIONAL_AMOUNT');}
  else if(rest.length)n.fail('UNEXPECTED_BESU_OPTION');
  if(command==='init')return n.init();
  if(['start','stop','status'].includes(command))return n.compose(command);
  const m=n.manifest(),rpc=n.rpcClient();
  if(command==='pin')return n.pin(m,rpc);
  if(command==='deploy')return require('./deploy.cjs').deploy(m,rpc,process.env.BESU_PASSWORD_FILE);
  return n.preflight(m,rpc,rest[0]||'1');
}
if(require.main===module)main(process.argv.slice(2)).then(result=>{
  console.log(n.json(result));
  if(result.status==='BESU_FUNDING_REQUIRED'||result.status==='DEPLOYMENT_PENDING_OR_UNKNOWN')process.exitCode=2;
}).catch(error=>{
  // RPC/KDF/library errors must never print password, signer, signed bytes or endpoint details.
  const code=String(error.message);
  console.error(n.json({status:'FAILED',code:/^[A-Z][A-Z0-9_]{1,120}$/.test(code)?code:'BESU_SETUP_FAILED',
    next:'Preserve local state. If deployment.json exists, inspect that transaction before any further deployment.'}));
  process.exitCode=1;
});
module.exports={main};
