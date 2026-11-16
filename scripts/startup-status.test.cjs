const {test}=require('node:test');
const assert=require('node:assert/strict');
const {containerChecks,startupStage,failureMessages,startupTracker}=require('./startup-status.cjs');

test('Compose array and newline JSON preserve failed dependency exit codes and missing components',()=>{
  const rows=[{Service:'postgres',State:'exited',ExitCode:255,Environment:'private-value'},{Service:'localstack',State:'running',Health:'healthy'},{Service:'visa-simulator',State:'running',Health:'unhealthy'}];
  const checks=containerChecks(JSON.stringify(rows));
  assert.deepEqual(checks,containerChecks(rows.map(row=>JSON.stringify(row)).join('\n')));
  assert.deepEqual(checks[0],{name:'postgres',status:'FAILED',reason:'EXITED',exitCode:255});
  assert.equal(checks[1].status,'PASSED');
  assert.equal(checks.find(row=>row.name==='visa-simulator').reason,'UNHEALTHY');
  assert.equal(checks.find(row=>row.name==='payment-service').reason,'NOT_CREATED_OR_NOT_REPORTED');
  assert.ok(!JSON.stringify(checks).includes('private-value'));
  assert.throws(()=>containerChecks('not json'));
});

test('streamed component progress handles chunk boundaries, duplicates and private log lines',()=>{
  const lines=[],tracker=startupTracker(line=>lines.push(line));
  tracker.output('build contains private-value\n Container stablecoins-postgres Heal');
  assert.equal(lines.length,0);
  tracker.output('thy\n Container stablecoins-postgres Healthy\n Container stablecoins-unknown Healthy\ncontainer stablecoins-visa-simulator is unhealthy\n');
  assert.equal(lines.length,2);
  assert.match(lines[0],/^\[1\/9\] postgres \| STARTED/);
  assert.match(lines[1],/^\[2\/9\] visa-simulator \| FAILED/);
  assert.equal(tracker.summary(),'Components: 1/9 started, 1 failed, 7 pending');
  assert.ok(!lines.join('\n').includes('private-value'));
  tracker.update('visa-simulator','STARTED','HEALTHY');
  tracker.update('react-frontend','FAILED','API proxy unavailable');
  assert.equal(tracker.summary(),'Components: 2/9 started, 1 failed, 6 pending');
});

test('long startup emits heartbeat and cannot announce successful stage after failure',async()=>{
  const lines=[];
  await startupStage('container readiness',()=>new Promise(resolve=>setTimeout(()=>resolve({code:1}),35)),{write:line=>lines.push(line),intervalMs:5});
  assert.ok(lines.some(line=>line.includes('still working')));
  assert.equal(lines.at(-1),'[FAILED] container readiness');
  const count=lines.length;
  await new Promise(resolve=>setTimeout(resolve,15));
  assert.equal(lines.length,count);
  assert.ok(!lines.some(line=>line.includes('[STARTED]')));
});

test('failed startup summary names components and logs without exposing raw container details',()=>{
  const lines=failureMessages({action:'start',status:'FAILED',prerequisites:{docker:false},checks:[{name:'compose-start',status:'FAILED',reason:'BUILD_OR_CONTAINER_READINESS_FAILED',log:'/local/compose.log'}],containers:[{name:'postgres',status:'FAILED',reason:'EXITED',exitCode:255}],reason:'Compose startup failed'}).join('\n');
  assert.match(lines,/Startup status: FAILED/);
  assert.match(lines,/prerequisite:docker/);
  assert.match(lines,/Failed component: postgres/);
  assert.match(lines,/compose.log/);
  assert.ok(!lines.includes('Application URL:'));
  assert.deepEqual(failureMessages({action:'start',status:'PASSED'}),[]);
});