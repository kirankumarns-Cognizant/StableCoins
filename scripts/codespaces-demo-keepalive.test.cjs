const {test}=require('node:test');
const assert=require('node:assert/strict');
const {startKeepAlive,main}=require('./codespaces-demo-keepalive.cjs');
test('keep-alive refuses non-Codespaces execution before scheduling activity',()=>{
  for(const environment of [{},{CODESPACES:'false',CODESPACE_NAME:'demo'},{CODESPACES:'true'},{CODESPACES:'true',CODESPACE_NAME:'invalid/name'}])assert.throws(()=>startKeepAlive({environment,write:()=>assert.fail('unexpected output'),schedule:()=>assert.fail('unexpected timer')}),/inside a GitHub Codespaces terminal/);
});
test('connected demo emits 28-minute activity and stops cleanly without reporting health',()=>{
  const lines=[];let tick,delay,cancelled=0;
  const stop=startKeepAlive({environment:{CODESPACES:'true',CODESPACE_NAME:'demo-123'},write:line=>lines.push(line),now:()=> '2026-11-17T12:00:00.000Z',schedule:(callback,ms)=>{tick=callback;delay=ms;return 42;},cancel:timer=>{assert.equal(timer,42);cancelled++;}});
  assert.equal(delay,28*60*1000);assert.equal(lines.filter(line=>line.includes('DEMO KEEP-ALIVE')).length,1);
  tick();assert.equal(lines.filter(line=>line.includes('DEMO KEEP-ALIVE')).length,2);
  assert.ok(lines.some(line=>line.includes('application health is not checked')));
  stop();const count=lines.length;tick();stop();assert.equal(lines.length,count);assert.equal(cancelled,1);
});
test('unknown arguments cannot silently start a keep-alive timer',()=>assert.throws(()=>main(['--background']),/Usage:/));
