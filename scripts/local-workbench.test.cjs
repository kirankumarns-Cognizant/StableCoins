const {test}=require('node:test');
const assert=require('node:assert/strict');
const {command,parseSuites,localUrl,probe,doctor,smoke,databaseGate,verificationEnvironment,main}=require('./local-workbench.cjs');
const {readConnections}=require('../services/config/read-connections.cjs');

test('Windows wrapper quotes paths with spaces and rejects shell expansion',async()=>{
  await assert.rejects(main(['start','--offline']),/Usage/);
  const result=command('C:\\project folder\\mvnw.cmd',['-f','services/pom.xml','verify'],'win32');
  assert.equal(result.windowsVerbatimArguments,true);
  assert.equal(result.args[3],'""C:\\project folder\\mvnw.cmd" "-f" "services/pom.xml" "verify""');
  for(const argument of ['%SECRET%','broken"quote','line\nnext','!EXPANSION!'])assert.throws(()=>command('npm.cmd',[argument],'win32'));
  assert.deepEqual(command('node',['--test'],'linux'),{executable:'node',args:['--test']});
});
test('Java report totals preserve skips and failures rather than calling all cases passed',()=>{
  assert.deepEqual(parseSuites('<testsuite tests="12" failures="1" errors="2" skipped="3" name="Suite"></testsuite>'),{tests:12,failures:1,errors:2,skipped:3,passed:6,name:'Suite'});
  assert.throws(()=>parseSuites('<testsuite tests="garbage">'));
  assert.throws(()=>parseSuites('<html>not a report</html>'));
});
test('local probes reject external, credential-bearing and redirect-style origins',()=>{
  assert.equal(localUrl('http://localhost:8080').hostname,'localhost');
  for(const value of ['https://external.test','http://169.254.169.254','http://localhost@external.test','http://localhost:8080/path','http://localhost:8080/?token=secret','http://localhost:8080/#fragment'])assert.throws(()=>localUrl(value));
});
test('health, unauthorized, redirected and failed responses cannot become acceptance evidence',async()=>{
  assert.deepEqual(await probe('http://localhost',[200],async()=>({status:503})),{status:'FAILED',httpStatus:503});
  assert.deepEqual(await probe('http://localhost',[401],async()=>({status:401})),{status:'PASSED',httpStatus:401});
  assert.deepEqual(await probe('http://localhost',[200],async()=>{throw Error('sensitive endpoint detail');}),{status:'FAILED',reason:'UNREACHABLE_OR_TIMEOUT'});
});
test('Docker CLI without reachable server is a blocked prerequisite',async()=>{
  const result=await doctor(async(exe,args)=>args[0]==='-version'?{code:0,output:'openjdk version "21.0.8"'}:args[0]==='version'?{code:1,output:'private credential detail'}:{code:0,output:'2.30.0'});
  assert.equal(result.java21,true);assert.equal(result.docker,false);assert.equal(result.compose,true);
  assert.ok(!JSON.stringify(result).includes('credential'));
});
test('all six services must expose health and reject both missing and forged identity',async()=>{
  const calls=[];
  const checks=await smoke(readConnections({}),async(url,options)=>{
    calls.push({url:String(url),options});
    return {status:url.pathname==='/health'?200:401};
  });
  assert.equal(checks.length,18);assert.ok(checks.every(check=>check.status==='PASSED'));
  assert.equal(calls.filter(call=>call.options.headers.Authorization).length,6);
  assert.ok(calls.every(call=>call.options.redirect==='error'));
});
test('validate all service origins before contacting any of them',async()=>{
  let calls=0;
  await assert.rejects(smoke(readConnections({VISA_SIMULATOR_INTERNAL_URL:'https://private-server.test'}),async()=>{calls++;}),/loopback/);
  assert.equal(calls,0);
});
test('strict database gate requires every module and permits only the named optional live model skip',()=>{
  const suites=['platform-flow','platform-security','payment-service','compliance-quote-service','blockchain-service','settlement-ledger-service','reconciliation-service','visa-simulator'].map(module=>({module,name:'ExecutedTest',skipped:0}));
  assert.equal(databaseGate(suites),true);
  assert.equal(databaseGate(suites.slice(1)),false);
  const live={module:'payment-service',name:'com.cognizant.stablecoins.payment.agent.RoutingAgentLiveTest',skipped:1};
  assert.equal(databaseGate([...suites,live]),true);
  assert.equal(databaseGate([...suites,{...live,name:'SkippedDatabaseTest'}]),false);
});

test('verification removes inherited activation flags without masking profile-guard test inputs',()=>{
  const parent={BLOCKCHAIN_MODE:'REAL_TESTNET',TESTNET_ENABLED:'true',SPRING_PROFILES_ACTIVE:'testnet',RUN_REAL_MODEL_TESTS:'true',JAVA_HOME:'retained-jdk'};
  const safe=verificationEnvironment(parent);
  assert.equal(safe.RUN_REAL_MODEL_TESTS,'false');
  assert.equal(safe.JAVA_HOME,'retained-jdk');
  for(const key of ['BLOCKCHAIN_MODE','TESTNET_ENABLED','SPRING_PROFILES_ACTIVE'])assert.equal(Object.hasOwn(safe,key),false);
  assert.equal(parent.BLOCKCHAIN_MODE,'REAL_TESTNET');
});
