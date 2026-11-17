const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const {join,resolve,relative,basename}=require('node:path');
const {redact,excerpt,readTail,collectServiceDiagnostics,failureDetails}=require('./failure-diagnostics.cjs');
function fixture(t){
 const base=resolve(__dirname,'../.local/diagnostics-tests');fs.mkdirSync(base,{recursive:true});
 const root=fs.mkdtempSync(join(base,'case-'));
 t.after(()=>{const target=fs.realpathSync(root),part=relative(base,target);assert.equal(part,basename(target));assert.ok(part.startsWith('case-'));fs.rmSync(target,{recursive:true,force:true});});
 const write=(path,text)=>{fs.mkdirSync(require('node:path').dirname(path),{recursive:true});fs.writeFileSync(path,text);return path;};
 return {root,write,directory:join(root,'validation')};
}
test('diagnostics redact configured credentials, headers, URL paths, emails and terminal escapes',()=>{
 const text='ERROR known-value Authorization: Bearer dummy-bearer\npassword="two words" clientSecret=another-value access_key_id=access-value Cookie=session-data\nhttps://user:pass@example.test/private?key=value employee@example.test\n\x1b[31mError: bad connection\x1b[0m';
 const safe=redact(text,{DB_PASSWORD:'known-value'});
 for(const value of ['known-value','dummy-bearer','two words','another-value','access-value','session-data','user:pass','/private','key=value','employee@example.test','\x1b'])assert.ok(!safe.includes(value),value);
 assert.match(safe,/Error: bad connection/);assert.match(safe,/example.test/);
});
test('diagnostics redact key blocks and unlabelled token-shaped values',()=>{
 const key=['sk','proj','x'.repeat(52)].join('-'),privateKey='a'.repeat(64);
 const safe=redact('ERROR '+key+' '+privateKey+'\n-----BEGIN PRIVATE KEY-----\nprivate material\n-----END PRIVATE KEY-----',{});
 for(const value of [key,privateKey,'private material'])assert.ok(!safe.includes(value));
});
test('excerpts retain useful assertions and bound line count and length',()=>{
 const lines=excerpt(Array.from({length:100},(_,i)=>'ERROR '+i+' '+'.'.repeat(1000)).join('\n'),{});
 assert.equal(lines.length,20);assert.ok(lines.every(line=>line.length<=502));assert.match(lines.at(-1),/ERROR 99/);
 const assertion=excerpt('expected: "11155111"\n but was: "84532"',{}).join('\n');
 assert.match(assertion,/11155111/);assert.match(assertion,/84532/);
});
test('tail reader rejects outside paths and stale logs and bounds bytes read',t=>{
 const {root,directory,write}=fixture(t),file=write(join(directory,'java.log'),'x'.repeat(70000)+'\nERROR newest');
 assert.ok(readTail(file,directory).length<=65536);assert.match(readTail(file,directory),/newest/);
 const outside=write(join(root,'private.properties'),'must not display');assert.equal(readTail(outside,directory),null);
 fs.utimesSync(file,new Date(0),new Date(0));assert.equal(readTail(file,directory,Date.now()-1000),null);
});
test('failure details use only failed-stage logs and fresh failed test assertions',t=>{
 const {root,directory,write}=fixture(t),suite='com.example.ConfigurationTest';
 const log=write(join(directory,'java.log'),'ERROR build failed'),passed=write(join(directory,'node.log'),'ERROR must not display passed log');
 write(join(root,'services/payment-service/target/surefire-reports',suite+'.txt'),'expected: 11155111\n but was: 84532');
 const report={action:'verify',status:'FAILED',startedAt:new Date(Date.now()-1000).toISOString(),checks:[{name:'java',status:'FAILED',log},{name:'node',status:'PASSED',log:passed}],java:{suites:[{module:'payment-service',name:suite,failures:4,errors:0}]}};
 const text=failureDetails(report,{root,directory,environment:{}}).join('\n');
 assert.match(text,/build failed/);assert.match(text,/11155111/);assert.ok(!text.includes('passed log'));
 assert.deepEqual(failureDetails({...report,status:'PASSED'},{root,directory}),[]);
});
test('malformed suite paths and missing logs cannot expose arbitrary files',t=>{
 const {root,directory,write}=fixture(t),outside=write(join(root,'private.txt'),'ERROR private data');fs.mkdirSync(directory,{recursive:true});
 const report={action:'verify',status:'FAILED',checks:[{name:'java',status:'FAILED',log:outside}],java:{suites:[{module:'../..',name:'../../private',failures:1}]}};
 const text=failureDetails(report,{root,directory,environment:{}}).join('\n');assert.ok(!text.includes('private data'));assert.match(text,/No matching/);
});
test('startup diagnostics request only allowlisted failed services and redact collected logs',async t=>{
 const {directory}=fixture(t);fs.mkdirSync(directory,{recursive:true});let called;
 const report={action:'start',status:'FAILED',containers:[{name:'payment-service',status:'FAILED'},{name:'postgres',status:'PASSED'},{name:'unknown-service',status:'FAILED'}],components:[{name:'payment-service',status:'FAILED'}]};
 const result=await collectServiceDiagnostics(report,directory,async(exe,args,options)=>{called={exe,args,options};return {code:0,output:'ERROR token=hide-me connection refused'};});
 assert.equal(called.exe,'docker');assert.deepEqual(called.args,['compose','--profile','services','logs','--no-color','--tail','80','payment-service']);assert.equal(called.options.timeoutMs,15000);
 assert.equal(result.status,'COLLECTED');const text=fs.readFileSync(result.log,'utf8');assert.match(text,/connection refused/);assert.ok(!text.includes('hide-me'));
});
test('diagnostic collection is opt-in via caller and cannot mask primary startup failure',async t=>{
 const {directory}=fixture(t);let calls=0;
 for(const report of [{action:'verify',status:'FAILED'},{action:'start',status:'PASSED'},{action:'start',status:'FAILED',components:[]}])assert.equal(await collectServiceDiagnostics(report,directory,async()=>{calls++;}),null);
 assert.equal(calls,0);
 const report={action:'start',status:'FAILED',components:[{name:'postgres',status:'FAILED'}]};
 assert.equal((await collectServiceDiagnostics(report,directory,async()=>{throw Error('private error');})).status,'UNAVAILABLE');assert.equal(report.status,'FAILED');
});
