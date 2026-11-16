#!/usr/bin/env node
// Local development only. Reuses existing builds, Compose, security and financial runners.
const {spawn} = require('node:child_process');
const {mkdirSync, readFileSync, readdirSync, statSync, writeFileSync, existsSync, openSync, closeSync} = require('node:fs');
const {join, resolve, delimiter} = require('node:path');
const {readConnections} = require('../services/config/read-connections.cjs');
const {startFrontend} = require('./local-frontend.cjs');
const root = resolve(__dirname, '..');
const modules = ['platform-flow','platform-security','payment-service','compliance-quote-service','blockchain-service','settlement-ledger-service','reconciliation-service','visa-simulator'];
const services = ['payment','compliance-quote','blockchain','ledger','reconciliation','visa'];
const destinations = {'payment':'connections.services.payment-url','compliance-quote':'connections.services.compliance-quote-url','blockchain':'connections.services.blockchain-url','ledger':'connections.services.ledger-url','reconciliation':'connections.services.reconciliation-url','visa':'connections.visa.base-url'};

function command(executable, args, platform = process.platform) {
  if (platform !== 'win32' || !/\.(cmd|bat)$/i.test(executable)) return {executable, args};
  // cmd.exe is necessary for Windows batch wrappers. Accept no shell expansion input.
  for (const value of [executable, ...args]) if (/["%\r\n!]/.test(value)) throw Error('Unsafe batch argument');
  return {executable: process.env.ComSpec || 'cmd.exe', args: ['/d','/s','/c',`""${executable}" ${args.map(arg => `"${arg}"`).join(' ')}"`], windowsVerbatimArguments:true};
}
function run(executable, args, options = {}) {
  if(process.platform==='win32' && /\.(cmd|bat)$/i.test(executable) && !executable.includes('\\') && !executable.includes('/')) {
    const found=(process.env.PATH || '').split(delimiter).map(directory=>join(directory,executable)).find(file=>existsSync(file));
    if(found)executable=found;
  }
  const spec = command(executable,args);
  return new Promise(resolveRun => {
    let output = '';
    const child = spawn(spec.executable,spec.args,{cwd:root,env:options.env || process.env,windowsHide:true,windowsVerbatimArguments:spec.windowsVerbatimArguments,
      stdio:options.log ? ['ignore',options.log,options.log] : ['ignore','pipe','pipe']});
    child.stdout?.on('data',chunk => {if(output.length < 32000) output += chunk;});
    child.stderr?.on('data',chunk => {if(output.length < 32000) output += chunk;});
    let timedOut = false;
    const timer = setTimeout(() => {timedOut = true; child.kill();}, options.timeoutMs || 30000);
    child.on('error',() => {clearTimeout(timer); resolveRun({code:127,output:'',timedOut:false});});
    child.on('close',code => {clearTimeout(timer); resolveRun({code:code ?? 1,output,timedOut});});
  });
}
function verificationEnvironment(parent=process.env) {
  const environment={...parent,RUN_REAL_MODEL_TESTS:'false'};
  for(const key of ['BLOCKCHAIN_MODE','TESTNET_ENABLED','SPRING_PROFILES_ACTIVE']) delete environment[key];
  return environment;
}
function javaExecutable() {return process.env.JAVA_HOME ? join(process.env.JAVA_HOME,'bin',process.platform === 'win32' ? 'java.exe':'java') : 'java';}
function parseSuites(xml) {
  const first = xml.match(/<testsuite\s+([^>]+)>/);
  if(!first) throw Error('Invalid test report');
  const attr = Object.fromEntries([...first[1].matchAll(/([\w-]+)="([^"]*)"/g)].map(match=>[match[1],match[2]]));
  const result = Object.fromEntries(['tests','failures','errors','skipped'].map(key => [key,Number(attr[key] ?? 0)]));
  if(Object.values(result).some(value => !Number.isSafeInteger(value) || value < 0)) throw Error('Invalid test counts');
  return {...result,name:attr.name,passed:result.tests-result.failures-result.errors-result.skipped};
}
function freshJavaResults(since) {
  const suites=[];
  for(const module of modules) for(const folder of ['surefire-reports','failsafe-reports']) {
    const directory=join(root,'services',module,'target',folder);
    if(!existsSync(directory)) continue;
    for(const file of readdirSync(directory)) if(/^TEST-.+\.xml$/.test(file) && statSync(join(directory,file)).mtimeMs >= since) {
      suites.push({module,...parseSuites(readFileSync(join(directory,file),'utf8'))});
    }
  }
  const totals={tests:0,passed:0,failures:0,errors:0,skipped:0};
  for(const suite of suites) for(const key of Object.keys(totals)) totals[key]+=suite[key];
  return {totals,suites};
}
const requiredDatabaseSuites = [
  'com.cognizant.stablecoins.payment.agent.AgentRunPersistenceIT',
  'com.cognizant.stablecoins.payment.api.PaymentApiIT',
  'com.cognizant.stablecoins.payment.approval.RecommendationPersistenceIT',
  'com.cognizant.stablecoins.payment.flow.PaymentFlowPersistenceIT',
  'com.cognizant.stablecoins.payment.integration.PaymentIntentPersistenceIT',
  'com.cognizant.stablecoins.blockchain.integration.TreasuryReservationPersistenceIT',
  'com.cognizant.stablecoins.ledger.integration.LedgerServiceIT',
  'com.cognizant.stablecoins.reconciliation.flow.ReconciliationPersistenceIT',
  'com.cognizant.stablecoins.visa.liquidity.ProviderLiquidityPersistenceIT',
];
function databaseGate(suites) {
  return modules.every(module=>suites.some(suite=>suite.module===module)) &&
    requiredDatabaseSuites.every(name=>suites.some(suite=>suite.name===name && suite.tests>0 && suite.passed===suite.tests && !suite.failures && !suite.errors && !suite.skipped)) &&
    suites.every(suite=>!suite.skipped || suite.name==='com.cognizant.stablecoins.payment.agent.RoutingAgentLiveTest');
}
function localUrl(value) {
  const url=new URL(value);
  if(url.protocol !== 'http:' || !['localhost','127.0.0.1','[::1]'].includes(url.hostname) || url.username || url.password || url.search || url.hash || url.pathname !== '/') throw Error('Local smoke checks require HTTP loopback service origins');
  return url;
}
async function probe(url, expected, fetcher=fetch, timeoutMs=10000, headers={}) {
  try {
    const response=await fetcher(url,{headers,signal:AbortSignal.timeout(timeoutMs),redirect:'error',cache:'no-store'});
    await response.body?.cancel();
    return {status:expected.includes(response.status)?'PASSED':'FAILED',httpStatus:response.status};
  } catch {return {status:'FAILED',reason:'UNREACHABLE_OR_TIMEOUT'};}
}
async function doctor(execute=run) {
  const java=await execute(javaExecutable(),['-version']);
  const docker=await execute('docker',['version','--format','{{.Server.Version}}']);
  const compose=await execute('docker',['compose','version','--short']);
  const [major,minor]=process.versions.node.split('.').map(Number);
  return {node:major>22 || major===22 && minor>=18,java21:java.code===0 && /version "21[.\"]/.test(java.output),docker:docker.code===0,compose:compose.code===0,
    dependencies:existsSync(join(root,'frontend/node_modules')) && existsSync(join(root,'infrastructure/node_modules'))};
}
async function smoke(config=readConnections(), fetcher=fetch) {
  const timeout=Number(config['local.validation.health-timeout-ms']);
  if(!Number.isSafeInteger(timeout) || timeout<100 || timeout>30000) throw Error('Invalid local health timeout');
  // Validate every destination before making any network request.
  const origins=Object.fromEntries(services.map(name=>[name,localUrl(config[destinations[name]])]));
  const checks=[];
  for(const name of services) {
    checks.push({name:`${name}:health`,...await probe(new URL('/health',origins[name]),[200],fetcher,timeout)});
    const path=name==='payment'?'/api/v1/payments/workbench':'/api/v1/local-access-probe';
    checks.push({name:`${name}:anonymous-denied`,...await probe(new URL(path,origins[name]),[401],fetcher,timeout)});
    checks.push({name:`${name}:forged-token-denied`,...await probe(new URL(path,origins[name]),[401],fetcher,timeout,{Authorization:'Bearer deliberately-invalid-local-probe'})});
  }
  return checks;
}
function startupMessages(report, environment=process.env) {
  if(report.action!=='start' || report.status!=='PASSED') return [];
  const name=environment.CODESPACE_NAME,domain=environment.GITHUB_CODESPACES_PORT_FORWARDING_DOMAIN;
  const forwarded=typeof name==='string' && /^[a-z0-9-]+$/i.test(name) && typeof domain==='string' && /^[a-z0-9]+(?:[.-][a-z0-9]+)*$/i.test(domain);
  const origin=port=>forwarded?`https://${name}-${port}.${domain}`:`http://localhost:${port}`;
  return [
    'Server startup completed. You can access the application now. React and all six backend services are ready; access checks passed.',
    `Application URL: ${origin(5173)}/`,
    `Backend health URL: ${origin(8080)}/health`,
    'Keep Codespaces ports Private. Sign-in and full payment acceptance are separate checks.',
  ];
}
async function main(args=process.argv.slice(2)) {
  const action=args[0] || 'doctor';
  if(!['doctor','verify','start','smoke'].includes(action) || action!=='verify' && args.length>1 || args.slice(1).some(arg=>!['--offline','--require-database'].includes(arg))) throw Error('Usage: node scripts/local-workbench.cjs doctor|verify|start|smoke [--offline] [--require-database]');
  const directory=join(root,'.local','validation',`${Date.now()}-${process.pid}-${action}`);
  mkdirSync(directory,{recursive:true});
  const file=join(directory,'report.json');
  const report={action,startedAt:new Date().toISOString(),status:'RUNNING',scope:'LOCAL_ONLY',liveAcceptance:false,checks:[]};
  const save=()=>writeFileSync(file,JSON.stringify(report,null,2)+'\n','utf8');save();
  console.log(`Local ${action}; report: ${file}`);
  try {
    if(action==='doctor' || action==='verify' || action==='start') {
      report.prerequisites=await doctor();
      console.log('Prerequisites:',JSON.stringify(report.prerequisites));
    }
    if(action==='doctor') report.status=Object.values(report.prerequisites).every(Boolean)?'READY':'BLOCKED';
    if(action==='verify') {
      const p=report.prerequisites;
      if(!p.node || !p.java21 || !p.dependencies || args.includes('--require-database') && (!p.docker || !p.compose)) throw Error('Local verification prerequisites missing; see report');
      // This command never opts into real inference or testnet profiles.
      const javaArgs=['-f','services/pom.xml','--no-transfer-progress',...(args.includes('--offline')?['-o']:[]),'verify'];
      const stages=[
        ['node',process.execPath,['--test','frontend/tests/safety.test.mjs','services/config/read-connections.test.cjs',...readdirSync(join(root,'scripts')).filter(f=>f.endsWith('.test.cjs')).map(f=>'scripts/'+f)]],
        ['frontend-build',process.platform==='win32'?'npm.cmd':'npm',['--prefix','frontend','run','build']],
        ['frontend-lint',process.platform==='win32'?'npm.cmd':'npm',['--prefix','frontend','run','lint']],
        ['cdk-build',process.platform==='win32'?'npm.cmd':'npm',['--prefix','infrastructure','run','build']],
        ['cdk-tests',process.platform==='win32'?'npm.cmd':'npm',['--prefix','infrastructure','test','--','--runInBand']],
        ['java',join(root,'services',process.platform==='win32'?'mvnw.cmd':'mvnw'),javaArgs],
      ];
      for(const [name,executable,stageArgs] of stages) {
        console.log(`Running ${name}...`);
        const started=Date.now();const logPath=join(directory,name+'.log');const log=openSync(logPath,'w');
        let result;try{result=await run(executable,stageArgs,{log,timeoutMs:1200000,
          env:verificationEnvironment()});}finally{closeSync(log);}
        report.checks.push({name,status:result.code===0?'PASSED':'FAILED',exitCode:result.code,timedOut:result.timedOut,log:logPath});
        if(name==='java')report.java=freshJavaResults(started);
        save();
        if(result.code!==0)throw Error(`${name} failed; inspect its local log`);
      }
      if(!report.java?.totals.tests || report.java.totals.failures || report.java.totals.errors)throw Error('No successful fresh Java test evidence');
      const skips=report.java.suites.filter(suite=>suite.skipped>0);
      // Default offline model evaluation may skip; database cases must execute in the strict gate.
      report.skippedSuites=skips.map(({name,skipped})=>({name,skipped}));
      report.databaseGate=databaseGate(report.java.suites)?'PASSED':'NOT_SATISFIED';
      if(args.includes('--require-database') && report.databaseGate!=='PASSED')throw Error('Required database/integration tests skipped or module reports missing');
      report.status=report.java.totals.skipped?'PASSED_WITH_SKIPS':'PASSED';
    }
    if(action==='start') {
      if(!Object.values(report.prerequisites).every(Boolean))throw Error('Startup prerequisites missing; see report. No installation or AWS deployment attempted');
      const waitSeconds=Number(readConnections()['local.validation.compose-wait-seconds']);
      if(!Number.isSafeInteger(waitSeconds) || waitSeconds<60 || waitSeconds>1200)throw Error('Invalid local Compose startup timeout');
      // Compose defaults keep MOCK/testnet-off. Existing owner migrations and queue initialization are authoritative.
      const logPath=join(directory,'compose.log'),log=openSync(logPath,'w');
      let result;try{result=await run('docker',['compose','--profile','services','up','-d','--build','--wait','--wait-timeout',String(waitSeconds)],{log,timeoutMs:1800000});}finally{closeSync(log);}
      report.checks.push({name:'compose-start',status:result.code===0?'PASSED':'FAILED',exitCode:result.code,log:logPath});save();
      if(result.code!==0)throw Error('Compose startup failed; inspect the local log. Existing volumes were retained');
    }
    if(action==='smoke' || action==='start') {
      report.checks.push(...await smoke());
      report.status=report.checks.every(check=>check.status==='PASSED')?'PASSED':'FAILED';
      report.scope='LOCAL_HEALTH_AND_ACCESS_BOUNDARY_ONLY';
      if(action==='start' && report.status==='PASSED') {
        const config=readConnections();
        const frontendLog=join(directory,'frontend.log');
        try {
          report.checks.push(await startFrontend({root,logPath:frontendLog,paymentUrl:config['connections.services.payment-url'],timeoutMs:Number(config['local.validation.frontend-start-timeout-ms'])}));
        } catch(error) {
          report.checks.push({name:'frontend-start',status:'FAILED',log:frontendLog});
          throw error;
        }
        report.scope='LOCAL_FRONTEND_HEALTH_AND_ACCESS_BOUNDARY_ONLY';
      }
    }
  } catch(error) {report.status='FAILED';report.reason=error.message;}
  report.completedAt=new Date().toISOString();save();
  console.log(`Result: ${report.status}. Full payment, live identity/model and public-testnet acceptance are separate gates.`);
  if(['FAILED','BLOCKED'].includes(report.status))process.exitCode=2;
  for(const message of startupMessages(report))console.log(message);
  return report;
}
module.exports={requiredDatabaseSuites,command,parseSuites,localUrl,probe,doctor,smoke,freshJavaResults,databaseGate,verificationEnvironment,startupMessages,main};
if(require.main===module)main().catch(()=>{console.error('Local validation could not start');process.exitCode=2;});
