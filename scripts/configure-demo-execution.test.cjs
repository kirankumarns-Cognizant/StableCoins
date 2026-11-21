const test=require('node:test'),assert=require('node:assert/strict');
const fs=require('node:fs'),path=require('node:path'),os=require('node:os'),{parseEnv}=require('node:util'),{spawnSync}=require('node:child_process');
const {configureText,configure}=require('./configure-demo-execution.cjs');
const settings=require('../services/config/demo-execution.json');
const {readConnections}=require('../services/config/read-connections.cjs');

test('demo settings enable existing execution gates and preserve unrelated values and comments',()=>{
 const input='# private configuration\r\nDB_PASSWORD="dummy # = value"\r\nBLOCKCHAIN_MODE=MOCK\r\nTESTNET_ENABLED=false\r\nPAYMENT_EXECUTION_ENABLED=false\r\nexport PAYMENT_EXECUTION_ENABLED=false\r\nROUTING_AGENT_ENABLED=false\r\n';
 const output=configureText(input),values=parseEnv(output);
 assert.equal(values.DB_PASSWORD,'dummy # = value');assert.ok(output.startsWith('# private configuration\r\n'));
 for(const [key,value] of Object.entries(settings))assert.equal(values[key],value);
 assert.equal(output.match(/^PAYMENT_EXECUTION_ENABLED=/gm).length,1);
 assert.equal(values.BLOCKCHAIN_MODE,'MOCK');assert.equal(values.TESTNET_ENABLED,'false');assert.equal(values.ROUTING_AGENT_ENABLED,'false');
 assert.equal(configureText(output),output);
 const resolved=readConnections(values);
 for(const key of ['app.flow.enabled','app.flow.execution-enabled','blockchain.execution-api-enabled'])assert.equal(resolved[key],'true');
 assert.equal(readConnections({})['app.flow.execution-enabled'],'false');
});
test('multiline content that resembles an execution assignment is rejected without exposing its value',()=>{
 assert.throws(()=>configureText('PRIVATE="dummy\nPAYMENT_FLOW_ENABLED=secret\nend"\n'),error=>!error.message.includes('secret')&&error.message.includes('no files changed'));
});
test('persistent setup is idempotent and requires an existing regular env file',()=>{
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'stablecoins-demo-config-'));
 try {
  assert.throws(()=>configure(dir));fs.mkdirSync(path.join(dir,'.env'));assert.throws(()=>configure(dir));fs.rmdirSync(path.join(dir,'.env'));
  fs.writeFileSync(path.join(dir,'.env'),'DB_PASSWORD=dummy\n');
  assert.equal(configure(dir).changed,true);assert.equal(configure(dir).changed,false);
  assert.equal(parseEnv(fs.readFileSync(path.join(dir,'.env'),'utf8')).DB_PASSWORD,'dummy');
 } finally {if(fs.existsSync(path.join(dir,'.env')))fs.unlinkSync(path.join(dir,'.env'));fs.rmdirSync(dir);}
});
test('ECS export uses the same non-secret gates and never modifies local configuration',()=>{
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'stablecoins-demo-export-'));
 try {
  const cli=spawnSync(process.execPath,[path.join(__dirname,'configure-demo-execution.cjs'),'--ecs-environment'],{cwd:dir,encoding:'utf8',windowsHide:true});
  assert.equal(cli.status,0,cli.stderr);assert.deepEqual(JSON.parse(cli.stdout),Object.entries(settings).map(([name,value])=>({name,value})));
  assert.deepEqual(fs.readdirSync(dir),[]);
 } finally {if(fs.existsSync(path.join(dir,'.env')))fs.unlinkSync(path.join(dir,'.env'));fs.rmdirSync(dir);}
});
