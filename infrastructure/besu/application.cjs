#!/usr/bin/env node
'use strict';
const fs=require('node:fs');
const path=require('node:path');
const {parseEnv}=require('node:util');
const {spawnSync}=require('node:child_process');
const n=require('./network.cjs');
const {environment}=require('./runtime-context.cjs');
const ENV=path.join(n.STATE,'application.env');
const PROPERTIES=path.join(n.STATE,'application.properties');
const SERVICES=['payment-service','compliance-quote-service','blockchain-service','settlement-ledger-service','reconciliation-service','visa-simulator'];
const FLAGS=['BESU_PRIVATE_DEMO_ENABLED','BESU_APPLICATION_API_ENABLED','BESU_OBSERVATION_API_ENABLED',
 'BESU_FUNDING_ENABLED','BESU_FUNDING_WORKERS_ENABLED','BESU_WORKERS_ENABLED','BESU_WORKER_SUBMISSION_ENABLED',
 'BESU_ACCOUNTING_ENABLED','BESU_FUNDING_EVENTS_ENABLED','BESU_ACCOUNTING_WORKERS_ENABLED',
 'BESU_CLOSURE_ENABLED','BESU_CLOSURE_WORKERS_ENABLED','BESU_RECONCILIATION_ENABLED','BESU_RECOVERY_ENABLED','BESU_RECOVERY_WORKERS_ENABLED'];
function serialize(values){
 return Object.entries(values).map(([key,value])=>{
  if(!/^[A-Z][A-Z0-9_]+$/.test(key)||/[\r\n\0"$`]/.test(String(value)))n.fail('UNSAFE_APPLICATION_ENVIRONMENT');
  return `${key}="${String(value).replaceAll('\\','/')}"`;
 }).join('\n')+'\n';
}
function properties(m,customer){
 if(!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(customer))n.fail('BESU_CUSTOMER_UUID_REQUIRED');
 const v=environment(m),root='app.settlement.networks[0].context';
 return Object.entries({
  [`${root}.token.chain.network-id`]:'besu-local',[`${root}.token.chain.chain-id`]:n.CHAIN_ID,
  [`${root}.token.chain.genesis-hash`]:v.BESU_GENESIS_HASH,[`${root}.token.chain.classification`]:'PRIVATE_DEMO',
  [`${root}.token.contract`]:v.BESU_TOKEN_CONTRACT,[`${root}.token.symbol`]:'DEMOUSD',[`${root}.token.decimals`]:6,
  [`${root}.token.code-hash`]:v.BESU_TOKEN_CODE_HASH,[`${root}.native-asset.chain.network-id`]:'besu-local',
  [`${root}.native-asset.chain.chain-id`]:n.CHAIN_ID,[`${root}.native-asset.chain.genesis-hash`]:v.BESU_GENESIS_HASH,
  [`${root}.native-asset.chain.classification`]:'PRIVATE_DEMO',[`${root}.native-asset.symbol`]:'BESU_GAS',[`${root}.native-asset.decimals`]:18,
  [`${root}.treasury`]:v.BESU_TREASURY_ADDRESS,[`${root}.fee-model`]:'EXECUTION_ONLY',[`${root}.policy-version`]:'besu-demo-v1',
  'app.settlement.networks[0].enabled-for-new-work':true,[`app.settlement.allowed-networks[${customer}][0]`]:'besu-local',
  'app.besu.quote.terms.version':'besu-simulated-quote-v1','app.besu.quote.terms.provider-id':'BESU-SIMULATED-PROVIDER',
  'app.besu.quote.terms.simulated-fee-usd':'0.10','app.besu.quote.terms.validity-seconds':900,
  'app.besu.quote.terms.native-reserve-multiplier':'1.2'
 }).map(([k,v])=>`${k}=${v}`).join('\n')+'\n';
}
function composeArgs(action,values){
 if(!['config','status','build','start','resume','stop'].includes(action))n.fail('INVALID_APPLICATION_ACTION');
 if(!['mock','testnet'].includes(values.BESU_APP_PUBLIC_MODE))n.fail('EXPLICIT_PUBLIC_MODE_REQUIRED');
 const args=['compose','--project-directory',n.ROOT,'--env-file',path.join(n.ROOT,'.env'),'--env-file',ENV,'-f',path.join(n.ROOT,'docker-compose.yml')];
 if(values.BESU_APP_PUBLIC_MODE==='testnet')args.push('-f',path.join(n.ROOT,'docker-compose.testnet.yml'));
 args.push('-f',path.join(__dirname,'application.compose.yml'),'--profile','services');
 if(action==='build')args.push('build',...SERVICES);
 if(action==='config')args.push('config','--quiet');
 if(action==='status')args.push('ps','--all');
 if(action==='stop')args.push('stop',...SERVICES); // Preserve DB, messaging, chain and every volume.
 if(action==='start'||action==='resume')args.push('up','-d','--wait',...SERVICES);
 return args;
}
function runDocker(args,env){
 const r=spawnSync('docker',args,{cwd:n.ROOT,env,shell:false,stdio:'inherit',windowsHide:true});
 if(r.error||r.status!==0)n.fail('BESU_APPLICATION_COMPOSE_FAILED');
}
async function prepare(mode,customer){
 if(!['mock','testnet'].includes(mode))n.fail('PREPARE_REQUIRES_EXPLICIT_MOCK_OR_TESTNET');
 const m=n.manifest();await n.tokenIdentity(m,n.rpcClient());
 const values={...environment(m),BESU_APP_PUBLIC_MODE:mode,BESU_CUSTOMER_ID:customer,
  BESU_APPLICATION_PROPERTIES:PROPERTIES,BESU_HOST_KEYSTORE_FILE:path.join(n.WALLETS,'treasury-wallet.json'),
  BESU_HOST_PASSWORD_FILE:path.join(n.ROOT,'.local/secrets/besu-treasury-password')};
 n.secureFile(values.BESU_HOST_PASSWORD_FILE);
 if(n.walletAddress(values.BESU_HOST_KEYSTORE_FILE)!==values.BESU_TREASURY_ADDRESS)n.fail('BESU_TREASURY_FILE_MISMATCH');
 if(mode==='testnet'){
  values.TESTNET_KEYSTORE_FILE=path.join(n.ROOT,'.local/wallets/treasury-wallet.json');
  values.TESTNET_PASSWORD_FILE=path.join(n.ROOT,'.local/secrets/treasury-password');
  n.secureFile(values.TESTNET_PASSWORD_FILE);values.TESTNET_TREASURY_ADDRESS=n.walletAddress(values.TESTNET_KEYSTORE_FILE);
  if(values.TESTNET_TREASURY_ADDRESS===values.BESU_TREASURY_ADDRESS)n.fail('BESU_PUBLIC_TREASURY_REUSE_REJECTED');
 }
 for(const flag of FLAGS)values[flag]='false';
 const docs=[[PROPERTIES,properties(m,customer)],[ENV,serialize(values)]];
 for(const [file,content] of docs)if(fs.existsSync(file)){n.secureFile(file);if(fs.readFileSync(file,'utf8')!==content)n.fail('EXISTING_APPLICATION_CONFIGURATION_PRESERVED');}
 for(const [file,content] of docs)if(!fs.existsSync(file))n.write(file,content);
 return {status:'BESU_APPLICATION_CONFIG_PREPARED',file:ENV,privateCapabilitiesEnabled:false,newPaymentsEnabled:false};
}
function load(){n.secureFile(ENV);n.secureFile(PROPERTIES);return parseEnv(fs.readFileSync(ENV,'utf8'));}
function runtimeEnvironment(values,parent,action,identity=process){
 const v={...parent,...values};
 for(const flag of FLAGS)if(!['true','false'].includes(v[flag]))n.fail('EXPLICIT_BESU_BOOLEAN_REQUIRED');
 // Resume observes saved work only. It never acquires new holds or first-broadcasts.
 if(action==='resume'){v.BESU_WORKER_SUBMISSION_ENABLED='false';v.BESU_FUNDING_WORKERS_ENABLED='false';}
 // UID/GID are recalculated, not copied between Windows and Codespaces.
 if(typeof identity.getuid!=='function'||identity.getuid()===0)n.fail('BESU_APPLICATION_REQUIRES_NONROOT_LINUX');
 v.BESU_LOCAL_UID=v.TESTNET_LOCAL_UID=String(identity.getuid());v.BESU_LOCAL_GID=v.TESTNET_LOCAL_GID=String(identity.getgid());
 return v;
}
async function main(args,deps={}){
 const [action,...rest]=args;
 if(action==='prepare'){if(rest.length!==2)n.fail('USAGE_PREPARE_PUBLIC_MODE_CUSTOMER_UUID');return prepare(rest[0],rest[1]);}
 if(!['config','status','build','start','resume','stop'].includes(action)||rest.length)n.fail('USAGE_APPLICATION_PREPARE_CONFIG_STATUS_BUILD_START_STOP_RESUME');
 const values=(deps.load||load)();const env=(deps.environment||runtimeEnvironment)(values,process.env,action);const run=deps.run||runDocker;
 let frontendFailure;
 if(action==='stop')try{await (deps.stopFrontend||require('../../scripts/local-stop.cjs').stopFrontend)({root:n.ROOT});}catch(error){frontendFailure=error;}
 if(action==='start'||action==='resume'){
  const m=(deps.manifest||n.manifest)();
  const expected=environment(m);
  for(const key of ['BESU_GENESIS_HASH','BESU_TOKEN_CONTRACT','BESU_TOKEN_CODE_HASH','BESU_TREASURY_ADDRESS'])
   if(values[key]!==expected[key])n.fail('BESU_APPLICATION_IDENTITY_CHANGED');
  (deps.chain||n.compose)('start'); // Existing chain only: no init, pin, deploy or reset.
  await (deps.verify||n.tokenIdentity)(m,n.rpcClient());
  run(composeArgs('config',values),env);
 }
 run(composeArgs(action,values),env);
 if(frontendFailure)n.fail('BESU_BACKENDS_STOPPED_FRONTEND_REQUIRES_REVIEW');
 if(action==='start'||action==='resume'){
  const log=path.join(n.ROOT,'.local/validation/besu-frontend.log');fs.mkdirSync(path.dirname(log),{recursive:true});
  await (deps.startFrontend||require('../../scripts/local-frontend.cjs').startFrontend)({root:n.ROOT,logPath:log,paymentUrl:'http://127.0.0.1:8080',environment:env});
 }
 let frontend;
 if(action==='status')frontend=await (deps.frontendState||require('../../scripts/local-frontend.cjs').frontendState)();
 return {status:'BESU_APPLICATION_'+action.toUpperCase(),privateNewPaymentsEnabled:false,...(frontend?{frontend}:{}),
  firstSubmissionEnabled:action==='resume'?false:env.BESU_WORKER_SUBMISSION_ENABLED==='true'};
}
if(require.main===module)main(process.argv.slice(2)).then(v=>console.log(n.json(v))).catch(error=>{
 const code=/^[A-Z][A-Z0-9_]{1,120}$/.test(String(error.message))?error.message:'BESU_APPLICATION_OPERATION_FAILED';
 console.error(n.json({status:'FAILED',code,next:'Preserve state and inspect prerequisites/configuration. No automatic reset or deployment is performed.'}));process.exitCode=1;
});
module.exports={ENV,PROPERTIES,FLAGS,SERVICES,serialize,properties,composeArgs,runtimeEnvironment,main};
