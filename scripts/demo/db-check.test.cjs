const {test}=require('node:test');
const assert=require('node:assert/strict');
const {spawnSync}=require('node:child_process');
const fs=require('node:fs'),os=require('node:os'),path=require('node:path');
const root=path.resolve(__dirname,'../..');
const bash=process.env.DEMO_TEST_BASH||(process.platform==='win32'?'C:/Program Files/Git/bin/bash.exe':'bash');
function run(args,extra={}){
 const directory=fs.mkdtempSync(path.join(os.tmpdir(),'stableflow-demo-query-'));
 try{
  fs.writeFileSync(path.join(directory,'docker'),'#!/usr/bin/env bash\nprintf "DOCKER_CALL\\n"\nprintf "%s\\n" "$@"\ncat\nexit "${DEMO_FAKE_EXIT:-0}"\n',{mode:0o700});
  return spawnSync(bash,['-c','if command -v cygpath >/dev/null 2>&1; then export PATH="$(cygpath -u "$DEMO_MOCK_BIN"):$PATH"; else export PATH="$DEMO_MOCK_BIN:$PATH"; fi; bash scripts/demo/db-check.sh "$@"','demo-query',...args],{
   cwd:root,encoding:'utf8',timeout:30000,env:{...process.env,DEMO_CUSTOMER_ID:'11111111-1111-1111-1111-111111111111',DEMO_MOCK_BIN:directory,...extra}});
 }finally{
  const full=path.resolve(directory),prefix=path.join(path.resolve(os.tmpdir()),'stableflow-demo-query-');
  if(!full.startsWith(prefix))throw Error('Unexpected temporary directory');
  fs.rmSync(full,{recursive:true,force:true});
 }
}
test('all phases pass quoted IDs to psql and stream only the intended read-only SQL files',()=>{
 const r=run(['all','PAY-EXAMPLE123']);
 assert.equal(r.error,undefined);assert.equal(r.status,0,r.stderr);
 assert.equal((r.stdout.match(/DOCKER_CALL/g)||[]).length,6);
 assert.equal((r.stdout.match(/BEGIN READ ONLY;/g)||[]).length,6);
 assert.equal((r.stdout.match(/COMMIT;/g)||[]).length,6);
 assert.match(r.stdout,/DEMO_PAYMENT_ID=PAY-EXAMPLE123/);
 assert.ok(r.stdout.includes('-v payment_id="$DEMO_PAYMENT_ID"'));
 assert.doesNotMatch(r.stdout,/\b(?:INSERT INTO|UPDATE\s+\w+\s+SET|DELETE FROM|DROP TABLE|CREATE TABLE)\b/i);
});
test('bad phase, payment or customer cannot invoke Docker',()=>{
 for(const [args,env] of [[['bogus'],{}],[['execution',"PAY-x'; DROP TABLE payment; --"],{}],[['all'],{}],[['catalog'],{DEMO_CUSTOMER_ID:'invalid'}]]){
  const r=run(args,env);assert.equal(r.status,2,r.stderr);assert.doesNotMatch(r.stdout,/DOCKER_CALL/);
 }
});
test('psql/docker failure stops all before subsequent phases',()=>{
 const r=run(['all','PAY-EXAMPLE123'],{DEMO_FAKE_EXIT:'7'});
 assert.equal(r.status,7);assert.equal((r.stdout.match(/DOCKER_CALL/g)||[]).length,1);
});
test('catalog uses sender-account ownership and works without a payment ID',()=>{
 const r=run(['catalog']);assert.equal(r.status,0,r.stderr);
 assert.match(r.stdout,/JOIN account a ON a.account_id = p.sender_account_id/);
 assert.match(r.stdout,/a.customer_id = :'customer_id'/);assert.doesNotMatch(r.stdout,/p.customer_id/);
});
test('all walkthrough Bash blocks parse and all relative file links exist',()=>{
 const file=path.join(root,'docs/38-COPY-PASTE-DEMO-WALKTHROUGH.md'),text=fs.readFileSync(file,'utf8');
 const blocks=[...text.matchAll(/~~~bash\n([\s\S]*?)\n~~~/g)];assert.ok(blocks.length>=20);
 for(const [i,block] of blocks.entries()){
  const r=spawnSync(bash,['-n'],{input:block[1],encoding:'utf8',timeout:15000});assert.equal(r.status,0,'Bash block '+(i+1)+': '+r.stderr);
 }
 for(const match of text.matchAll(/\]\(([^)]+)\)/g)){
  const target=match[1].split('#')[0];if(!target||/^https?:/.test(target))continue;
  assert.ok(fs.existsSync(path.resolve(path.dirname(file),target)),target);
 }
});
