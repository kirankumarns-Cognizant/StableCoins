const {test}=require('node:test');
const assert=require('node:assert/strict');
const app=require('../application.cjs');
const {environment}=require('../runtime-context.cjs');
const fixture=()=>({networkId:'besu-local',chainId:20261125,classification:'PRIVATE_DEMO',feeModel:'EXECUTION_ONLY',gasAsset:'BESU_GAS',
 genesisHash:'0x'+'a'.repeat(64),treasury:'0x'+'1'.repeat(40),beneficiary:'0x'+'2'.repeat(40),validators:[3,4,5,6].map(i=>'0x'+String(i).repeat(40)),token:{contract:'0x'+'7'.repeat(40),codeHash:'0x'+'b'.repeat(64)}});
const values=()=>({...environment(fixture()),BESU_APP_PUBLIC_MODE:'testnet',...Object.fromEntries(app.FLAGS.map(k=>[k,'true']))});
test('compose plan preserves the selected public overlay and attaches only the application overlay',()=>{
 const args=app.composeArgs('start',values());assert(args.some(a=>a.endsWith('docker-compose.testnet.yml')));
 assert(args.some(a=>a.endsWith('application.compose.yml')));assert(!args.some(a=>a.endsWith('/besu/compose.yml')));
 assert.deepEqual(args.slice(-app.SERVICES.length),app.SERVICES);
 const mock=app.composeArgs('config',{BESU_APP_PUBLIC_MODE:'mock'});assert(!mock.some(a=>a.endsWith('docker-compose.testnet.yml')));assert.deepEqual(mock.slice(-2),['config','--quiet']);
 assert.throws(()=>app.composeArgs('start',{}));assert.throws(()=>app.composeArgs('down',values()));
});
test('stop preserves infrastructure, chain, volumes and saved transactions',()=>{
 const args=app.composeArgs('stop',values());assert.deepEqual(args.slice(-app.SERVICES.length-1),['stop',...app.SERVICES]);
 for(const forbidden of ['down','-v','--volumes','postgres','localstack','besu-rpc'])assert(!args.includes(forbidden));
});
test('operator file serialization rejects interpolation, newlines and shell fragments',()=>{
 assert.equal(app.serialize({BESU_HOST_FILE:'C:\\safe folder\\wallet.json'}),'BESU_HOST_FILE="C:/safe folder/wallet.json"\n');
 for(const bad of ['line\nsecret','${SECRET}','`command`','quote"'])assert.throws(()=>app.serialize({BESU_HOST_FILE:bad}));
});
test('generated server registry pins both token and native identity without custody material',()=>{
 const text=app.properties(fixture(),'11111111-1111-1111-1111-111111111111');
 assert(text.includes('native-asset.chain.genesis-hash=0x'+'a'.repeat(64)));assert(text.includes('token.symbol=DEMOUSD'));
 assert(!/password|keystore|private-key/i.test(text));assert.throws(()=>app.properties(fixture(),'injected\nsetting=true'));
});
function harness(action,{verifyFailure=false}={}){
 const calls=[],v=values();
 return {calls,deps:{load:()=>v,environment:(v,parent,a)=>app.runtimeEnvironment(v,parent,a,{getuid:()=>1000,getgid:()=>1000}),
  manifest:fixture,chain:a=>calls.push('chain:'+a),verify:async()=>{calls.push('verify');if(verifyFailure)throw Error('WRONG_CHAIN');},
  run:(args,env)=>calls.push({args,env}),startFrontend:async()=>calls.push('frontend:start'),stopFrontend:async()=>calls.push('frontend:stop')}};
}
test('startup verifies existing chain before application mutation and starts frontend last',async()=>{
 const {calls,deps}=harness();await app.main(['start'],deps);
 assert.equal(calls[0],'chain:start');assert.equal(calls[1],'verify');assert(calls[2].args.includes('--quiet'));assert(calls[3].args.includes('up'));assert.equal(calls[4],'frontend:start');
 const commands=calls.filter(c=>typeof c==='object').flatMap(c=>c.args);
 assert(!commands.includes('deploy'));assert(!commands.includes('init'));
});
test('failed identity prevents starting application workers or frontend',async()=>{
 const {calls,deps}=harness('start',{verifyFailure:true});await assert.rejects(app.main(['start'],deps),/WRONG_CHAIN/);assert.deepEqual(calls,['chain:start','verify']);
});
test('resume suppresses first submission and new reservations in the actual compose environment',async()=>{
 const {calls,deps}=harness();const result=await app.main(['resume'],deps);
 for(const call of calls.filter(c=>typeof c==='object')){assert.equal(call.env.BESU_WORKER_SUBMISSION_ENABLED,'false');assert.equal(call.env.BESU_FUNDING_WORKERS_ENABLED,'false');}
 assert.equal(result.firstSubmissionEnabled,false);
});
test('CLI refuses reset, deployment, unknown commands and extra arguments before reading files',async()=>{
 for(const args of [['reset'],['deploy'],['start','--reset'],['prepare']])await assert.rejects(app.main(args,{load:()=>{throw Error('SHOULD_NOT_READ');}}),e=>!e.message.includes('SHOULD_NOT_READ'));
});

test('actual environment enforces explicit booleans, file precedence and non-root Linux identity',()=>{
 const identity={getuid:()=>1001,getgid:()=>1002};const v=values();v.BESU_APPLICATION_API_ENABLED='false';
 const env=app.runtimeEnvironment(v,{BESU_APPLICATION_API_ENABLED:'true',UNRELATED:'retained'},'resume',identity);
 assert.equal(env.BESU_APPLICATION_API_ENABLED,'false');assert.equal(env.BESU_WORKER_SUBMISSION_ENABLED,'false');
 assert.equal(env.BESU_FUNDING_WORKERS_ENABLED,'false');assert.equal(env.BESU_LOCAL_UID,'1001');assert.equal(env.TESTNET_LOCAL_GID,'1002');assert.equal(env.UNRELATED,'retained');
 assert.throws(()=>app.runtimeEnvironment({...v,BESU_APPLICATION_API_ENABLED:'yes'},{},'start',identity));
 assert.throws(()=>app.runtimeEnvironment(v,{},'start',{}));assert.throws(()=>app.runtimeEnvironment(v,{},'start',{getuid:()=>0}));
});
test('unrecognized frontend is preserved while backend workers are still stopped',async()=>{
 const {calls,deps}=harness();deps.stopFrontend=async()=>{throw Error('PORT_OCCUPIED');};
 await assert.rejects(app.main(['stop'],deps),/BACKENDS_STOPPED_FRONTEND_REQUIRES_REVIEW/);
 assert.equal(calls.length,1);assert(calls[0].args.includes('stop'));
});
test('build is explicit and does not start chain, containers or frontend',async()=>{
 const {calls,deps}=harness();await app.main(['build'],deps);assert.equal(calls.length,1);assert(calls[0].args.includes('build'));assert(!calls[0].args.includes('up'));
});
