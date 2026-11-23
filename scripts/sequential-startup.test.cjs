const {test}=require('node:test');
const assert=require('node:assert/strict');
const {backendOrder,startBackends}=require('./sequential-startup.cjs');
const {startupTracker}=require('./startup-status.cjs');
function context(execute) {
  const lines=[],checks=[],tracker=startupTracker(line=>lines.push(line));
  return {execute,tracker,waitSeconds:360,log:42,onCheck:check=>checks.push(check),stageOptions:{write:line=>lines.push(line),progress:tracker.progress},lines,checks};
}
test('Compose targets are strictly sequential, infrastructure first, without implicit dependency starts',async()=>{
  let active=0,maximum=0;const calls=[];
  const ctx=context(async(executable,args,options)=>{
    active++;maximum=Math.max(maximum,active);calls.push({executable,args,options});
    await new Promise(resolve=>setImmediate(resolve));active--;return {code:0,timedOut:false};
  });
  assert.equal((await startBackends(ctx)).code,0);
  assert.equal(maximum,1);
  assert.deepEqual(calls.map(call=>call.args.at(-1)),backendOrder);
  assert.deepEqual(backendOrder.slice(0,2),['postgres','localstack']);
  for(const [index,call] of calls.entries()) {
    assert.equal(call.executable,'docker');assert.ok(call.args.includes('--no-deps'));
    assert.equal(call.args.includes('--build'),index>=2);
    assert.equal(call.args[call.args.indexOf('--wait-timeout')+1],'360');
    assert.ok(call.args.includes('--wait'));assert.equal(call.options.log,42);
  }
  assert.equal(ctx.checks.length,8);assert.ok(ctx.checks.every(check=>check.status==='PASSED'));
  assert.equal(ctx.tracker.progress().percent,88); // React still pending.
  assert.equal(ctx.tracker.snapshot().at(-1).status,'PENDING');
  assert.ok(ctx.lines.some(line=>line.includes('payment-service: build, start and health check')));
});
test('a component failure stops later builds and retains successful components and pending React',async()=>{
  const calls=[];
  const ctx=context(async(executable,args)=>{calls.push(args.at(-1));return {code:calls.length===3?1:0,timedOut:false};});
  assert.equal((await startBackends(ctx)).code,1);
  assert.deepEqual(calls,['postgres','localstack','payment-service']);
  const states=ctx.tracker.snapshot();
  assert.ok(states.slice(0,2).every(item=>item.status==='STARTED'));
  assert.equal(states[2].status,'FAILED');assert.ok(states.slice(3).every(item=>item.status==='PENDING'));
  assert.equal(ctx.checks.at(-1).reason,'BUILD_OR_CONTAINER_READINESS_FAILED');
  assert.ok(calls.every(name=>!['down','rm','restart'].includes(name)));
});
test('a timeout is reported against the active component and does not continue',async()=>{
  let calls=0;const ctx=context(async()=>{calls++;return {code:1,timedOut:true};});
  await startBackends(ctx);assert.equal(calls,1);
  assert.equal(ctx.checks[0].reason,'COMMAND_TIMEOUT');
  assert.equal(ctx.tracker.snapshot()[0].reason,'COMMAND_TIMEOUT');
});
test('a command exception becomes a safe component failure without leaking exception content',async()=>{
  const ctx=context(async()=>{throw Error('private credential detail');});
  const result=await startBackends(ctx);
  assert.equal(result.code,127);assert.equal(ctx.checks.length,1);
  assert.equal(ctx.tracker.snapshot()[0].status,'FAILED');
  assert.ok(!JSON.stringify({lines:ctx.lines,checks:ctx.checks}).includes('credential'));
});


test('restart rebuilds and recreates only the six backends, preserves infrastructure and waits sequentially',async()=>{
 const calls=[];const ctx=context(async(exe,args)=>{calls.push(args);return {code:0};});
 await startBackends({...ctx,restart:true});
 assert.deepEqual(calls.map(args=>args.at(-1)),backendOrder);
 for(const [index,args] of calls.entries()) {
  assert.equal(args.includes('--force-recreate'),index>=2);
  assert.equal(args.includes('--build'),index>=2);
  assert.ok(args.includes('--wait') && args.includes('--no-deps'));
  assert.ok(!args.some(arg=>['down','rm','--volumes','-v','restart'].includes(arg)));
 }
 assert.ok(ctx.lines.some(line=>line.includes('build, recreate and health check')));
});

test('restart stops on rebuild failure without replacing later services',async()=>{
 const calls=[];const ctx=context(async(exe,args)=>{calls.push(args);return {code:calls.length===4?1:0};});
 assert.equal((await startBackends({...ctx,restart:true})).code,1);
 assert.deepEqual(calls.map(args=>args.at(-1)),backendOrder.slice(0,4));
 assert.equal(ctx.tracker.snapshot()[3].status,'FAILED');
 assert.ok(ctx.tracker.snapshot().slice(4).every(item=>item.status==='PENDING'));
});
