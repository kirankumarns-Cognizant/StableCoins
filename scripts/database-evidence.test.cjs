const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const os=require('node:os');
const path=require('node:path');
const {requiredDatabaseSuites,databaseGate,databaseEvidence,freshJavaResults,parseSuites}=require('./local-workbench.cjs');
const root=path.resolve(__dirname,'..');
const modules=['platform-flow','platform-security','payment-service','compliance-quote-service','blockchain-service','settlement-ledger-service','reconciliation-service','visa-simulator'];
const besuSuites=[
 'SettlementBindingPersistenceIT','SettlementPreparationPersistenceIT','SettlementExecutionClaimPersistenceIT',
 'SettlementFundingPersistenceIT','SettlementApprovalClaimPersistenceIT','BesuLiquidityPersistenceIT',
 'BesuAccountingPersistenceIT','BesuAccountingWorkReadsIT','BesuReconciliationPersistenceIT'
];
function owner(name) {
 return name.includes('.ledger.')?'settlement-ledger-service':name.includes('.reconciliation.')?'reconciliation-service':name.includes('.visa.')?'visa-simulator':name.includes('.blockchain.')?'blockchain-service':'payment-service';
}
function passedEvidence() {
 return [...modules.map(module=>({module,name:module+'.UnitTest',reportKind:'unit',tests:2,passed:2,failures:0,errors:0,skipped:0})),
 ...requiredDatabaseSuites.map(name=>({module:owner(name),name,reportKind:'integration',tests:3,passed:3,failures:0,errors:0,skipped:0}))];
}
for(const simple of besuSuites) test('strict verification cannot omit '+simple,()=>{
 const name=requiredDatabaseSuites.find(name=>name.endsWith('.'+simple));
 assert.ok(name,'Besu persistence suite must be mandatory');
 const evidence=passedEvidence().filter(suite=>suite.name!==name);
 assert.equal(databaseGate(evidence),false);
 assert.deepEqual(databaseEvidence(evidence).required.find(suite=>suite.name===name).reasons,['MISSING_FRESH_REPORT']);
});
test('every non-live persistence IT is registered; exclusions are explicit',()=>{
 const excluded=new Map([
  ['com.cognizant.stablecoins.blockchain.besu.BesuReadinessIT','Explicit read-only private-chain profile; no Docker database test.'],
  ['com.cognizant.stablecoins.blockchain.integration.BlockchainRealTestnetIT','Explicit public-testnet profile; may sign and submit.'],
  ['com.cognizant.stablecoins.blockchain.integration.BlockchainServiceIT','Legacy disabled Spring smoke fixture, excluded by existing default POM; public treasury DB coverage is required separately.']
 ]);
 const discovered=[];
 function walk(directory) {
  if(!fs.existsSync(directory))return;
  for(const entry of fs.readdirSync(directory,{withFileTypes:true})) {
   const file=path.join(directory,entry.name);
   if(entry.isDirectory())walk(file);
   else if(entry.name.endsWith('IT.java')) {
    const source=fs.readFileSync(file,'utf8');
    const pkg=/^package\s+([\w.]+);/m.exec(source);
    assert.ok(pkg,file);
    discovered.push(pkg[1]+'.'+entry.name.slice(0,-5));
   }
  }
 }
 for(const module of modules)walk(path.join(root,'services',module,'src/test/java'));
 assert.deepEqual(discovered.filter(name=>!excluded.has(name)).sort(),[...requiredDatabaseSuites].sort());
 for(const name of excluded.keys())assert.ok(discovered.includes(name),name);
});
test('ordinary blockchain verify includes both DB suites and keeps live profiles separate',()=>{
 const pom=fs.readFileSync(path.join(root,'services/blockchain-service/pom.xml'),'utf8');
 const ordinary=pom.split('<profiles>')[0];
 const required=requiredDatabaseSuites.filter(name=>owner(name)==='blockchain-service');
 for(const name of required)assert.ok(ordinary.includes('**/'+name.split('.').at(-1)+'.java'),name);
 assert.ok(!ordinary.includes('<include>**/BesuReadinessIT.java</include>'));
 assert.ok(!ordinary.includes('<include>**/*RealTestnetIT.java</include>'));
});
test('all healthy evidence passes; wrong module, unit-only or duplicate evidence fails',()=>{
 const valid=passedEvidence(),name=requiredDatabaseSuites[0];
 assert.equal(databaseGate(valid),true);
 for(const replacement of [{module:'blockchain-service'},{reportKind:'unit'}]) {
  const bad=valid.map(suite=>suite.name===name?{...suite,...replacement}:suite);
  assert.equal(databaseGate(bad),false);
  assert.ok(databaseEvidence(bad).required.find(suite=>suite.name===name).reasons.includes('WRONG_REPORT_ORIGIN'));
 }
 assert.equal(databaseGate([...valid,valid.find(suite=>suite.name===name)]),false);
});
test('zero, skipped, failed and errored database suites cannot pass',()=>{
 const valid=passedEvidence(),name=requiredDatabaseSuites.at(-1);
 for(const counts of [{tests:0,passed:0},{passed:2,skipped:1},{passed:2,failures:1},{passed:2,errors:1}]) {
  const bad=valid.map(suite=>suite.name===name?{...suite,...counts}:suite);
  assert.equal(databaseGate(bad),false);
  assert.ok(databaseEvidence(bad).required.find(suite=>suite.name===name).reasons.includes('NOT_FULLY_EXECUTED_AND_PASSED'));
 }
 assert.equal(databaseGate([...valid,{module:'payment-service',name:'OtherIT',tests:1,errors:1,skipped:0}]),false);
});
test('fresh report collection rejects stale success and records report provenance',()=>{
 const temporary=fs.mkdtempSync(path.join(os.tmpdir(),'stablecoins-db-evidence-'));
 try {
  const since=Date.now()-10000;
  const emit=(folder,name,when)=>{
   const directory=path.join(temporary,'services/payment-service/target',folder);
   fs.mkdirSync(directory,{recursive:true});
   const file=path.join(directory,'TEST-'+name+'.xml');
   fs.writeFileSync(file,`<testsuite name="${name}" tests="2" failures="0" errors="0" skipped="0"></testsuite>`);
   fs.utimesSync(file,when/1000,when/1000);
  };
  emit('failsafe-reports',requiredDatabaseSuites[0],since-60000);
  emit('failsafe-reports','FreshIntegration',Date.now());
  emit('surefire-reports','FreshUnit',Date.now());
  const result=freshJavaResults(since,temporary);
  assert.equal(result.suites.length,2);
  assert.equal(result.totals.passed,4);
  assert.ok(!result.suites.some(suite=>suite.name===requiredDatabaseSuites[0]));
  assert.equal(result.suites.find(suite=>suite.name==='FreshIntegration').reportKind,'integration');
  assert.equal(result.suites.find(suite=>suite.name==='FreshUnit').reportKind,'unit');
  assert.equal(databaseGate(result.suites),false);
 } finally {fs.rmSync(temporary,{recursive:true,force:true});}
});
test('impossible test report counts and missing identity fail parsing',()=>{
 for(const xml of ['<testsuite tests="1" failures="1" errors="1" skipped="0" name="Bad">',
  '<testsuite tests="2" failures="0" errors="0" skipped="0">',
  '<testsuite tests="NaN" failures="0" errors="0" skipped="0" name="Bad">'])assert.throws(()=>parseSuites(xml),/Invalid test counts/);
});
