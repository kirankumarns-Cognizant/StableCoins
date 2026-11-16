const {test}=require('node:test');
const assert=require('node:assert/strict');
const {EventEmitter}=require('node:events');
const {frontendEnvironment,frontendState,startFrontend}=require('./local-frontend.cjs');

test('frontend child gets a loopback proxy and exact Codespaces host without mutating parent',()=>{
  const parent={CODESPACE_NAME:'demo-123',GITHUB_CODESPACES_PORT_FORWARDING_DOMAIN:'app.github.dev',FRONTEND_DEV_ALLOWED_HOSTS:'another.test'};
  const result=frontendEnvironment(parent,'http://localhost:8080');
  assert.equal(result.PAYMENT_SERVICE_URL,'http://localhost:8080');
  assert.equal(result.FRONTEND_DEV_ALLOWED_HOSTS,'another.test,demo-123-5173.app.github.dev');
  assert.equal(parent.PAYMENT_SERVICE_URL,undefined);
  for(const url of ['https://external.test','http://localhost@external.test','http://localhost:8080/?key=secret']) assert.throws(()=>frontendEnvironment(parent,url));
  assert.equal(frontendEnvironment({CODESPACE_NAME:'bad\nhost',GITHUB_CODESPACES_PORT_FORWARDING_DOMAIN:'app.github.dev'},'http://localhost').FRONTEND_DEV_ALLOWED_HOSTS,'');
});
test('frontend readiness requires its own marker, rendered app and denied anonymous proxied API',async()=>{
  const fetcher=status=>async url=>url.endsWith('__stablecoins_dev_health')?Response.json({application:'stableflow-vite-development'}):url.endsWith('/workbench')?new Response('',{status}):new Response('<title>StableFlow | Stablecoin Payment Workspace</title>');
  assert.equal(await frontendState(fetcher(401)),'READY');
  for(const status of [200,403,404,502]) assert.equal(await frontendState(fetcher(status)),'NOT_READY');
  assert.equal(await frontendState(async()=>Response.json({application:'other'})),'OCCUPIED');
  assert.equal(await frontendState(async()=>{throw Error('connection refused');}),'UNREACHABLE');
});
const options={root:'.',logPath:'unused.log',paymentUrl:'http://localhost:8080',environment:{}};
test('existing healthy frontend is reused without spawning a duplicate',async()=>{
  const result=await startFrontend(options,{state:async()=> 'READY',launch:()=>assert.fail('must not spawn')});
  assert.equal(result.reused,true);
});
test('existing unrelated or unready port owner is never replaced or killed',async()=>{
  for(const status of ['OCCUPIED','NOT_READY']) await assert.rejects(startFrontend(options,{state:async()=>status,launch:()=>assert.fail('must not spawn')}),/Port 5173/);
});
test('owned child persists after readiness and failures terminate only that child',async()=>{
  function child() {const value=new EventEmitter();value.pid=123;value.unref=()=>{};value.killed=false;value.kill=()=>{value.killed=true;};return value;}
  let calls=0;const successful=child();
  const result=await startFrontend(options,{state:async()=>++calls===1?'UNREACHABLE':'READY',launch:()=>successful});
  assert.equal(result.pid,123);assert.equal(successful.killed,false);
  calls=0;const failed=child();
  await assert.rejects(startFrontend(options,{state:async()=> 'UNREACHABLE',launch:()=>failed,pause:async()=>{throw Error('fixture deadline');}}),/fixture deadline/);
  assert.equal(failed.killed,true);
  await assert.rejects(startFrontend({...options,timeoutMs:0}),/Invalid/);
});
