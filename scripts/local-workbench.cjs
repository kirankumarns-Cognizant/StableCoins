#!/usr/bin/env node
// Local development only. Reuses existing builds, Compose, security and financial runners.
const {spawn} = require('node:child_process');
const {mkdirSync, readFileSync, readdirSync, statSync, writeFileSync, writeSync, existsSync, openSync, closeSync} = require('node:fs');
const {join, resolve, delimiter} = require('node:path');
const {readConnections} = require('../services/config/read-connections.cjs');
const {startFrontend} = require('./local-frontend.cjs');
const {stackStatus} = require('./local-status.cjs');
const {stopApplication}=require('./local-stop.cjs');
const {acquireLifecycle}=require('./lifecycle-lock.cjs');
const {startBackends} = require('./sequential-startup.cjs');
const {containerChecks,startupStage,failureMessages,startupTracker,verificationTracker} = require('./startup-status.cjs');
const {redact,collectServiceDiagnostics,failureDetails}=require('./failure-diagnostics.cjs');
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
    let output = '', stdout = '', stderr = '';
    const child = spawn(spec.executable,spec.args,{cwd:root,env:options.env || process.env,windowsHide:true,windowsVerbatimArguments:spec.windowsVerbatimArguments,
      stdio:options.log && !options.onOutput ? ['ignore',options.log,options.log] : ['ignore','pipe','pipe']});
    const receive=(chunk,stream)=>{
      if(stream==='stdout')stdout=(stdout+chunk).slice(0,262144);
      else stderr=(stderr+chunk).slice(0,65536);
      if(options.log && options.onOutput)writeSync(options.log,chunk);
      if(output.length<32000)output+=chunk;
      options.onOutput?.(chunk);
    };
    child.stdout?.on('data',chunk=>receive(chunk,'stdout'));
    child.stderr?.on('data',chunk=>receive(chunk,'stderr'));
    let timedOut = false;
    const timer = setTimeout(() => {timedOut = true; child.kill();}, options.timeoutMs || 30000);
    child.on('error',() => {clearTimeout(timer); resolveRun({code:127,output:'',stdout:'',stderr:'',timedOut:false});});
    child.on('close',code => {clearTimeout(timer); resolveRun({code:code ?? 1,output,stdout,stderr,timedOut});});
  });
}
function verificationEnvironment(parent=process.env) {
  const environment={...parent,RUN_REAL_MODEL_TESTS:'false'};
  // Verification uses test-owned settings, never a deployed identity, wallet or model.
  // Derive connection variable names from the authoritative shared configuration.
  const names=new Set([...readFileSync(join(root,'services/config/connections.properties'),'utf8').matchAll(/\$\{([A-Z][A-Z0-9_]*)(?=[:}])/g)].map(match=>match[1]));
  for(const key of ['CONNECTIONS_CONFIG','CONNECTIONS_CONFIG_FILE'])names.add(key);
  for(const key of Object.keys(environment))if(names.has(key.toUpperCase()) || key.toUpperCase().startsWith('SPRING_'))delete environment[key];
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
async function smoke(config=readConnections(), fetcher=fetch, onCheck=()=>{}) {
  const timeout=Number(config['local.validation.health-timeout-ms']);
  if(!Number.isSafeInteger(timeout) || timeout<100 || timeout>30000) throw Error('Invalid local health timeout');
  // Validate every destination before making any network request.
  const origins=Object.fromEntries(services.map(name=>[name,localUrl(config[destinations[name]])]));
  const checks=[];
  for(const name of services) {
    const first=checks.length;
    checks.push({name:`${name}:health`,...await probe(new URL('/health',origins[name]),[200],fetcher,timeout)});
    const path=name==='payment'?'/api/v1/payments/workbench':'/api/v1/local-access-probe';
    checks.push({name:`${name}:anonymous-denied`,...await probe(new URL(path,origins[name]),[401],fetcher,timeout)});
    checks.push({name:`${name}:forged-token-denied`,...await probe(new URL(path,origins[name]),[401],fetcher,timeout,{Authorization:'Bearer deliberately-invalid-local-probe'})});
    for(const check of checks.slice(first))onCheck(check);
  }
  return checks;
}
function startupMessages(report, environment=process.env, config) {
  if(!['start','restart'].includes(report.action) || report.status!=='PASSED') return [];
  config=config || readConnections(environment);
  const name=environment.CODESPACE_NAME,domain=environment.GITHUB_CODESPACES_PORT_FORWARDING_DOMAIN;
  const forwarded=typeof name==='string' && /^[a-z0-9-]+$/i.test(name) && typeof domain==='string' && /^[a-z0-9]+(?:[.-][a-z0-9]+)*$/i.test(domain);
  const origin=port=>forwarded?`https://${name}-${port}.${domain}`:`http://localhost:${port}`;
  const labels={payment:'Payment service','compliance-quote':'Compliance / quote service',blockchain:'Blockchain service',ledger:'Settlement / ledger service',reconciliation:'Reconciliation service',visa:'Visa simulator'};
  const healthUrls=services.map(service=>{
    const target=localUrl(config[destinations[service]]);
    return `${labels[service]} health URL: ${forwarded?origin(target.port || 80):target.origin}/health`;
  });
  return [
    report.action==='restart'?'Restart status: RESTARTED':'Startup status: STARTED',
    (report.action==='restart'?'Backend restart completed.':'Server startup completed.')+' You can access the application now. React and all six backend services are ready; access checks passed.',
    `Application URL: ${origin(5173)}/`,
    'Backend health URLs (diagnostics only; use the Application URL for the demo):',
    ...healthUrls,
    'Forward a diagnostic service port privately in Codespaces before opening its browser URL. No browser is opened automatically.',
    'Keep Codespaces ports Private. Sign-in and full payment acceptance are separate checks.',
  ];
}
function validationArguments(args) {
  const action=args[0] || 'doctor',options=args.slice(1);
  const allowed={doctor:[],smoke:[],status:['--show-failures'],start:['--show-failures'],restart:['--show-failures'],stop:['--show-failures'],verify:['--offline','--require-database','--show-failures']};
  if(!Object.hasOwn(allowed,action) || options.some(option=>!allowed[action].includes(option)))throw Error('Usage: node scripts/local-workbench.cjs doctor|smoke|status [--show-failures]|start|restart|stop [--show-failures]|verify [--offline] [--require-database] [--show-failures]');
  return {action,showFailures:options.includes('--show-failures')};
}
async function main(args=process.argv.slice(2),dependencies={}) {
  const {action,showFailures}=validationArguments(args);
  const starting=['start','restart'].includes(action);
  const execute=dependencies.execute || run;
  const directory=join(root,'.local','validation',`${Date.now()}-${process.pid}-${action}`);
  mkdirSync(directory,{recursive:true});
  const file=join(directory,'report.json');
  const report={action,startedAt:new Date().toISOString(),status:'RUNNING',scope:'LOCAL_ONLY',liveAcceptance:false,checks:[]};
  const tracker=starting?startupTracker():null;
  const verification=action==='verify'?verificationTracker():null;
  let activeVerification='prerequisites';
  let releaseLifecycle;
  const stageOptions={progress:()=> (tracker || verification).progress()};
  const componentFor=name=>({payment:'payment-service','compliance-quote':'compliance-quote-service',blockchain:'blockchain-service',ledger:'settlement-ledger-service',reconciliation:'reconciliation-service',visa:'visa-simulator'})[name];
  const save=()=>{if(tracker || verification)report.progress=(tracker || verification).progress();writeFileSync(file,JSON.stringify(report,null,2)+'\n','utf8');};save();
  console.log(`Local ${action}; report: ${file}`);
  if(starting)console.log((action==='restart'?'Restart':'Startup')+' status: STARTING. Checking prerequisites, backend containers, API access and frontend readiness.');
  if(action==='restart')console.log('Rebuild and recreate all six backends; retain PostgreSQL/LocalStack and data volumes. Reuse a healthy React dev server.');
  if(verification)console.log(verification.summary()+' (check-based; not elapsed time or payment acceptance)');
  try {
    if(starting || action==='stop')releaseLifecycle=await (dependencies.acquireLifecycle || acquireLifecycle)(root);
    const config=dependencies.config || (starting?readConnections():undefined);
    if(action==='stop') {
      console.log('Stop order: React > six backend services > LocalStack > PostgreSQL. Containers and volumes are retained.');
      const logPath=join(directory,'compose-stop.log'),log=openSync(logPath,'w');
      try {
        Object.assign(report,await stopApplication({root,execute,log,frontend:dependencies.stopFrontend,onCheck:check=>{
          report.checks.push({...check,log:check.name==='react-frontend'?undefined:logPath});save();
          console.log('['+check.status+'] '+check.name+' | '+(check.reason || 'CHECKED'));
        }}));
        // Keep the log location on checks in the final report for bounded failure diagnostics.
        for(const check of report.checks)if(check.name!=='react-frontend')check.log=logPath;
      }finally{closeSync(log);}
      const stopped=report.components.filter(c=>c.status==='PASSED').length;
      console.log('Stop status: '+(report.status==='PASSED'?'STOPPED':'INCOMPLETE')+' | Components: '+stopped+'/9 stopped');
    }
    if(action==='status') {
      console.log('Checking existing stack only; no build, start, restart or payment commands.');
      const config=dependencies.config || readConnections();
      // Reject external or credential-bearing service origins before inspecting anything.
      for(const name of services)localUrl(config[destinations[name]]);
      Object.assign(report,await stackStatus({config,execute:dependencies.execute || run,smoke,fetcher:dependencies.fetcher || fetch,
        onCheck:check=>{report.checks.push(check);save();console.log('['+check.status+'] '+check.name+' | '+(check.reason || (check.httpStatus!==undefined?'HTTP '+check.httpStatus:'CHECKED')));}}));
      for(const component of report.components)console.log('['+component.status+'] '+component.name+' | '+component.reason);
      const ready=report.components.filter(component=>component.status==='PASSED').length;
      console.log('Stack status: '+(report.status==='PASSED'?'READY':'NOT_READY')+' | Components: '+ready+'/9 ready, '+(9-ready)+' failed or unavailable');
    }
    if(action==='doctor' || action==='verify' || starting) {
      report.prerequisites=tracker || verification?await startupStage('prerequisites',async()=>{
        const prerequisites=await (dependencies.doctor || doctor)(execute);
        const ready=verification?prerequisites.node && prerequisites.java21 && prerequisites.dependencies && (!args.includes('--require-database') || prerequisites.docker && prerequisites.compose):Object.values(prerequisites).every(Boolean);
        return {status:ready?'PASSED':'FAILED',prerequisites};
      },{...stageOptions,successStatus:verification?'CHECKED':'STARTED'}).then(result=>result.prerequisites):await (dependencies.doctor || doctor)(execute);
      console.log('Prerequisites:',JSON.stringify(report.prerequisites));
    }
    if(action==='doctor') report.status=Object.values(report.prerequisites).every(Boolean)?'READY':'BLOCKED';
    if(action==='verify') {
      const p=report.prerequisites;
      if(!p.node || !p.java21 || !p.dependencies || args.includes('--require-database') && (!p.docker || !p.compose)) throw Error('Local verification prerequisites missing; see report');
      verification.update('prerequisites','PASSED');save();
      // This command never opts into real inference or testnet profiles.
      const javaArgs=['-f','services/pom.xml','--no-transfer-progress',...(args.includes('--offline')?['-o']:[]),'verify'];
      const stages=[
        ['node',process.execPath,['--test',...readdirSync(join(root,'frontend/tests')).filter(f=>f.endsWith('.test.mjs')).map(f=>'frontend/tests/'+f),'services/config/read-connections.test.cjs',...readdirSync(join(root,'scripts')).filter(f=>f.endsWith('.test.cjs')).map(f=>'scripts/'+f)]],
        ['frontend-build',process.platform==='win32'?'npm.cmd':'npm',['--prefix','frontend','run','build']],
        ['frontend-lint',process.platform==='win32'?'npm.cmd':'npm',['--prefix','frontend','run','lint']],
        ['cdk-build',process.platform==='win32'?'npm.cmd':'npm',['--prefix','infrastructure','run','build']],
        ['cdk-tests',process.platform==='win32'?'npm.cmd':'npm',['--prefix','infrastructure','test','--','--runInBand']],
        ['java',join(root,'services',process.platform==='win32'?'mvnw.cmd':'mvnw'),javaArgs],
      ];
      for(const [name,executable,stageArgs] of stages) {
        activeVerification=name;
        console.log(`Running ${name}; diagnostic log: ${join(directory,name+'.log')}`);
        const started=Date.now();const logPath=join(directory,name+'.log');const log=openSync(logPath,'w');
        let result;try{result=await startupStage(name,()=>run(executable,stageArgs,{log,timeoutMs:1200000,
          env:verificationEnvironment()}),{...stageOptions,successStatus:'PASSED'});}finally{closeSync(log);}
        report.checks.push({name,status:result.code===0?'PASSED':'FAILED',exitCode:result.code,timedOut:result.timedOut,log:logPath});
        verification.update(name,result.code===0?'PASSED':'FAILED');
        if(name==='java')report.java=freshJavaResults(started);
        save();
        if(result.code!==0)throw Error(`${name} failed; inspect its local log`);
      }
      activeVerification='java-evidence';
      if(!report.java?.totals.tests || report.java.totals.failures || report.java.totals.errors)throw Error('No successful fresh Java test evidence');
      const skips=report.java.suites.filter(suite=>suite.skipped>0);
      // Default offline model evaluation may skip; database cases must execute in the strict gate.
      report.skippedSuites=skips.map(({name,skipped})=>({name,skipped}));
      report.databaseGate=databaseGate(report.java.suites)?'PASSED':'NOT_SATISFIED';
      if(args.includes('--require-database') && report.databaseGate!=='PASSED')throw Error('Required database/integration tests skipped or module reports missing');
      verification.update('java-evidence','PASSED');
      report.status=report.java.totals.skipped?'PASSED_WITH_SKIPS':'PASSED';
    }
    if(starting) {
      if(!Object.values(report.prerequisites).every(Boolean))throw Error('Startup prerequisites missing; see report. No installation or AWS deployment attempted');
      const waitSeconds=Number(config['local.validation.compose-wait-seconds']);
      if(!Number.isSafeInteger(waitSeconds) || waitSeconds<60 || waitSeconds>1200)throw Error('Invalid local Compose startup timeout');
      // Compose defaults keep MOCK/testnet-off. Existing owner migrations and queue initialization are authoritative.
      const logPath=join(directory,'compose.log'),log=openSync(logPath,'w');
      console.log(`Backend build/start log: ${logPath}`);
      console.log(tracker.summary());
      console.log('Startup order: PostgreSQL > LocalStack > payment > compliance/quote > blockchain > ledger > reconciliation > Visa simulator > React');
      let result;try{result=await startBackends({execute,restart:action==='restart',tracker,waitSeconds,log,stageOptions,onCheck:check=>{
        report.checks.push({...check,log:logPath});save();
      }});}finally{closeSync(log);}
      report.checks.push({name:'compose-start',status:result.code===0?'PASSED':'FAILED',exitCode:result.code,reason:result.timedOut?'COMMAND_TIMEOUT':result.code===0?undefined:'BUILD_OR_CONTAINER_READINESS_FAILED',log:logPath});save();
      const inventory=await execute('docker',['compose','--profile','services','ps','--all','--format','json'],{timeoutMs:10000});
      try {
        if(inventory.code!==0)throw Error('Container status unavailable');
        const attempted=new Set(report.checks.filter(check=>check.name.startsWith('compose:')).map(check=>check.name.slice('compose:'.length)));
        report.containers=containerChecks(inventory.stdout ?? inventory.output).filter(check=>attempted.has(check.name));
        for(const check of report.containers) {
          // A still-running old image cannot erase a failed build/start attempt.
          if(check.status==='PASSED' && tracker.snapshot().some(item=>item.name===check.name && item.status==='FAILED'))continue;
          tracker.update(check.name,check.status==='PASSED'?'STARTED':'FAILED',check.reason+(check.exitCode!==undefined?' (exit '+check.exitCode+')':''));
        }
      }catch {report.containerDiagnostics='UNAVAILABLE';}
      save();
      if(result.code!==0)throw Error('Compose startup failed; inspect the local log. Existing volumes were retained');
      if(report.containers?.some(check=>check.status==='FAILED'))throw Error('A container is no longer ready after Compose startup; inspect compose.log and the component status');
      if(!report.containers)for(const component of tracker.snapshot().filter(component=>component.name!=='react-frontend'))tracker.update(component.name,'STARTED','Compose readiness passed; detailed status snapshot unavailable');
    }
    if(action==='smoke' || starting) {
      const checks=starting?await startupStage('backend API health and access checks',async()=>{
        const checks=await smoke(config,dependencies.fetcher || fetch,check=>{
          console.log(`[${check.status==='PASSED'?'READY':'FAILED'}] ${check.name}${check.httpStatus!==undefined?' | HTTP '+check.httpStatus:' | '+check.reason}`);
          if(check.status==='FAILED')tracker.update(componentFor(check.name.split(':')[0]),'FAILED',check.name+' '+(check.reason || 'HTTP '+check.httpStatus));
        });
        return {status:checks.every(check=>check.status==='PASSED')?'PASSED':'FAILED',checks};
      },stageOptions).then(result=>result.checks):await smoke();
      report.checks.push(...checks);
      report.status=report.checks.every(check=>check.status==='PASSED')?'PASSED':'FAILED';
      report.scope='LOCAL_HEALTH_AND_ACCESS_BOUNDARY_ONLY';
      if(starting && report.status==='PASSED') {
        const frontendLog=join(directory,'frontend.log');
        console.log(`Frontend diagnostic log: ${frontendLog}`);
        try {
          report.checks.push(await startupStage('React frontend and API proxy',()=>(dependencies.startFrontend || startFrontend)({root,logPath:frontendLog,paymentUrl:config['connections.services.payment-url'],timeoutMs:Number(config['local.validation.frontend-start-timeout-ms'])}),stageOptions));
          tracker.update('react-frontend','STARTED','Application HTML and API proxy checks passed');
        } catch(error) {
          tracker.update('react-frontend','FAILED','Frontend or API proxy not ready; inspect frontend.log');
          report.checks.push({name:'frontend-start',status:'FAILED',log:frontendLog});
          throw error;
        }
        report.scope='LOCAL_FRONTEND_HEALTH_AND_ACCESS_BOUNDARY_ONLY';
      }
    }
  } catch(error) {report.status='FAILED';report.reason=action==='status'?'Status check could not complete; verify local connection settings':error.message;if(verification)verification.update(activeVerification,'FAILED');}finally{await releaseLifecycle?.();}
  if(tracker){report.components=tracker.snapshot();console.log(tracker.summary());}
  if(verification)console.log(verification.summary());
  if(showFailures && report.status==='FAILED'){
    console.log('Collecting CLI failure diagnostics...');
    report.diagnostics=await collectServiceDiagnostics(report,directory,execute);
  }
  report.completedAt=new Date().toISOString();save();
  console.log(action==='stop'?`Result: ${report.status}. Stop only; containers, volumes and recorded payments were retained.`:`Result: ${report.status}. Full payment, live identity/model and public-testnet acceptance are separate gates.`);
  if(['FAILED','BLOCKED'].includes(report.status))process.exitCode=2;
  for(const message of failureMessages(report))console.error(redact(message));
  if(showFailures)for(const message of failureDetails(report,{root,directory}))console.error(message);
  if(report.status==='FAILED' && !showFailures && ['start','restart','stop','verify','status'].includes(action))console.error('Tip: add --show-failures to print bounded, redacted diagnostics in the CLI.');
  for(const message of startupMessages(report))console.log(message);
  return report;
}
module.exports={run,requiredDatabaseSuites,command,parseSuites,localUrl,probe,doctor,smoke,freshJavaResults,databaseGate,verificationEnvironment,startupMessages,validationArguments,main};
if(require.main===module)main().catch(()=>{console.error('Local validation could not start');process.exitCode=2;});
