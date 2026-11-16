const {test}=require('node:test');
const assert=require('node:assert/strict');
const {command,parseSuites,localUrl,probe,doctor,smoke,databaseGate,verificationEnvironment,startupMessages,main}=require('./local-workbench.cjs');
const {readConnections}=require('../services/config/read-connections.cjs');

test('successful unified startup prints current Codespaces frontend and backend health URLs',()=>{
  const messages=startupMessages({action:'start',status:'PASSED'},{CODESPACE_NAME:'demo-space-123',GITHUB_CODESPACES_PORT_FORWARDING_DOMAIN:'app.github.dev'});
  assert.ok(messages.includes('Application URL: https://demo-space-123-5173.app.github.dev/'));
  assert.ok(messages.includes('Payment service health URL: https://demo-space-123-8080.app.github.dev/health'));
  for(const port of [8080,8081,8082,8083,8084,8086])assert.ok(messages.some(message=>message.endsWith(`-${port}.app.github.dev/health`)));
  assert.equal(messages.filter(message=>message.includes(' health URL: ')).length,6);
  const custom=startupMessages({action:'start',status:'PASSED'},{CODESPACE_NAME:'demo-space-123',GITHUB_CODESPACES_PORT_FORWARDING_DOMAIN:'app.github.dev'},readConnections({PAYMENT_SERVICE_URL:'http://127.0.0.1:9080'}));
  assert.ok(custom.includes('Payment service health URL: https://demo-space-123-9080.app.github.dev/health'));
  assert.match(messages.join('\n'),/React and all six backend services are ready/);
});
test('startup URLs use loopback outside Codespaces and never render malformed forwarding variables',()=>{
  for(const environment of [{},{CODESPACE_NAME:'demo',GITHUB_CODESPACES_PORT_FORWARDING_DOMAIN:'https://user:secret@external.test'},{CODESPACE_NAME:'demo\nforged message',GITHUB_CODESPACES_PORT_FORWARDING_DOMAIN:'app.github.dev'}]) {
    const messages=startupMessages({action:'start',status:'PASSED'},environment).join('\n');
    assert.match(messages,/Application URL: http:\/\/localhost:5173\//);
    assert.match(messages,/Payment service health URL: http:\/\/localhost:8080\/health/);
    assert.ok(!messages.includes('secret') && !messages.includes('forged'));
  }
});
test('failed, blocked and unrelated actions never announce successful startup or application URLs',()=>{
  for(const report of [{action:'start',status:'FAILED'},{action:'start',status:'BLOCKED'},{action:'start',status:'RUNNING'},{action:'smoke',status:'PASSED'},{action:'verify',status:'PASSED'}]) assert.deepEqual(startupMessages(report,{}),[]);
});

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
  assert.equal(databaseGate(suites),false); // Unit suites alone cannot satisfy database acceptance.
  for(const name of require('./local-workbench.cjs').requiredDatabaseSuites) {
    const module=name.includes('.blockchain.')?'blockchain-service':name.includes('.ledger.')?'settlement-ledger-service':name.includes('.reconciliation.')?'reconciliation-service':name.includes('.visa.')?'visa-simulator':'payment-service';
    suites.push({module,name,tests:1,passed:1,failures:0,errors:0,skipped:0});
  }
  assert.equal(databaseGate(suites),true);
  assert.equal(databaseGate(suites.slice(0,-1)),false);
  assert.equal(databaseGate(suites.map(suite=>suite.name.endsWith('TreasuryReservationPersistenceIT')?{...suite,tests:0,passed:0}:suite)),false);
  assert.equal(databaseGate(suites.map(suite=>suite.name.endsWith('PaymentApiIT')?{...suite,passed:0,errors:1}:suite)),false);
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


test('verification isolates deployed connections while retaining Java and Docker tooling',()=>{
  const parent={
    IDENTITY_ISSUER_URI:'https://cognito.example.test/real-pool', BLOCKCHAIN_CHAIN_ID:'84532',
    CONNECTIONS_CONFIG:'file:/private/demo.properties', CONNECTIONS_CONFIG_FILE:'./.local/demo.properties',
    AI_MODEL:'deployed-model',ROUTING_AGENT_ENABLED:'true',DB_PASSWORD:'dummy-secret',
    AWS_ACCESS_KEY_ID:'dummy-access',AWS_SECRET_ACCESS_KEY:'dummy-secret',
    SPRING_APPLICATION_JSON:'{}',SPRING_DATASOURCE_URL:'jdbc:postgresql://deployed-db/app',
    JAVA_HOME:'test-jdk',JAVA_TOOL_OPTIONS:'-Dapi.version=1.44',PATH:'test-path',
    DOCKER_HOST:'unix:///var/run/docker.sock',TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE:'/var/run/docker.sock'
  };
  const original={...parent},safe=verificationEnvironment(parent);
  for(const key of ['IDENTITY_ISSUER_URI','BLOCKCHAIN_CHAIN_ID','CONNECTIONS_CONFIG','CONNECTIONS_CONFIG_FILE','AI_MODEL','ROUTING_AGENT_ENABLED','DB_PASSWORD','AWS_ACCESS_KEY_ID','AWS_SECRET_ACCESS_KEY','SPRING_APPLICATION_JSON','SPRING_DATASOURCE_URL'])assert.equal(Object.hasOwn(safe,key),false,key);
  for(const key of ['JAVA_HOME','JAVA_TOOL_OPTIONS','PATH','DOCKER_HOST','TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE'])assert.equal(safe[key],parent[key]);
  assert.deepEqual(parent,original);
  assert.equal(safe.RUN_REAL_MODEL_TESTS,'false');
});
