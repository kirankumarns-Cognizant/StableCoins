import {test} from 'node:test'
import assert from 'node:assert/strict'
import {beginLogin,clearPendingLogin,cognitoLogoutUrl,finishLogin,resumeFreshLogin,refreshSession,returnRoute} from '../src/lib/auth.ts'
import {SessionKeeper} from '../src/lib/session.ts'
const identity={clientId:'public-client',redirectUri:'https://ui.test/auth/callback',authorizationUri:'https://id.test/oauth2/authorize',tokenUri:'https://id.test/oauth2/token',scopes:'openid profile',autoRefreshEnabled:true}
class Storage {values=new Map();getItem(k){return this.values.get(k)??null}setItem(k,v){this.values.set(k,v)}removeItem(k){this.values.delete(k)}}
function harness(renew,enabled=true){
 let now=1000,expired=0;const changes=[],timers=new Set()
 const runtime={now:()=>now,schedule:(callback,delay)=>{const timer={callback,at:now+delay};timers.add(timer);return timer},cancel:timer=>timers.delete(timer),renew}
 const keeper=new SessionKeeper({...identity,autoRefreshEnabled:enabled},session=>changes.push(session),()=>expired++,runtime)
 return {keeper,changes,timers,get expired(){return expired},setTime:value=>{now=value},fire:async()=>{for(const timer of [...timers])if(timer.at<=now){timers.delete(timer);timer.callback()}await Promise.resolve();await Promise.resolve()}}
}
test('PKCE restores only an allowlisted same-app route; refresh token stays out of storage',async()=>{
 const storage=new Storage(),route='#/payments/PAY-1/approvals?review=review-1'
 const url=new URL(await beginLogin(identity,storage,'https://ui.test',route))
 const original=globalThis.fetch
 globalThis.fetch=async()=>Response.json({access_token:'synthetic-access',refresh_token:'synthetic-refresh',token_type:'Bearer',expires_in:3600})
 try{const session=await finishLogin(identity,'?code=code&state='+url.searchParams.get('state'),storage,'https://ui.test');assert.equal(session.returnHash,route);assert.equal(session.refreshToken,'synthetic-refresh');assert.equal(storage.values.size,0)}finally{globalThis.fetch=original}
 for(const unsafe of ['https://elsewhere.test/','#//elsewhere.test/','#/unknown','#/payments/PAY-1/routing?redirect=https://elsewhere.test'])assert.equal(returnRoute(unsafe),'#/overview')
})
test('refresh grant omits cookies/client secret and supports rotating or retained refresh tokens',async()=>{
 const original=globalThis.fetch;let rotate=true
 globalThis.fetch=async(url,options)=>{assert.equal(String(url),identity.tokenUri);assert.equal(options.credentials,'omit');assert.equal(options.redirect,'error');assert.equal(options.body.get('grant_type'),'refresh_token');assert.equal(options.body.get('client_id'),'public-client');assert.equal(options.body.get('refresh_token'),'synthetic-refresh');assert.equal(options.body.has('client_secret'),false);return Response.json({access_token:'renewed-access',token_type:'Bearer',expires_in:3600,...(rotate?{refresh_token:'rotated-refresh'}:{})})}
 try{const session={token:'old',expiresAt:0,refreshToken:'synthetic-refresh'};assert.equal((await refreshSession(identity,session)).refreshToken,'rotated-refresh');rotate=false;assert.equal((await refreshSession(identity,session)).refreshToken,'synthetic-refresh')}finally{globalThis.fetch=original}
})
test('invalid-grant error is sanitized and malformed lifetimes are rejected',async()=>{
 const original=globalThis.fetch,session={token:'old',expiresAt:0,refreshToken:'synthetic-refresh'}
 try{globalThis.fetch=async()=>Response.json({error:'invalid_grant',error_description:'SENSITIVE_PROVIDER_BODY'},{status:400});await assert.rejects(refreshSession(identity,session),error=>error.message==='SESSION_RENEWAL_REJECTED');for(const expires_in of [-1,0,'3600',2147484]){globalThis.fetch=async()=>Response.json({access_token:'new',token_type:'Bearer',expires_in});await assert.rejects(refreshSession(identity,session),/Invalid identity response/)}}finally{globalThis.fetch=original}
})
test('proactive refresh changes token without ending session or adding an application idle limit',async()=>{
 let calls=0;const h=harness(async()=>{calls++;return {token:'new-'+calls,refreshToken:'refresh',expiresAt:100000+calls*100000}})
 h.keeper.start({token:'old',refreshToken:'refresh',expiresAt:11000});assert.equal([...h.timers][0].at,10000)
 h.setTime(10000);await h.fire();assert.equal(await h.keeper.validToken(),'new-1');assert.equal(h.expired,0);assert.equal(h.changes.length,2)
 h.setTime(185000);await h.fire();assert.equal(await h.keeper.validToken(),'new-2');assert.equal(h.expired,0);h.keeper.clear()
})
test('concurrent reads after tab suspension wait for one refresh before receiving a token',async()=>{
 let resolve,calls=0;const h=harness(()=>{calls++;return new Promise(done=>{resolve=done})})
 h.keeper.start({token:'expired',refreshToken:'refresh',expiresAt:11000});h.setTime(20000)
 const first=h.keeper.validToken(),second=h.keeper.validToken();assert.equal(calls,1)
 resolve({token:'renewed',refreshToken:'rotated',expiresAt:100000});assert.deepEqual(await Promise.all([first,second]),['renewed','renewed']);h.keeper.clear()
})
test('signout cancels refresh and ignores its late result; no old user is restored',async()=>{
 let resolve,signal;const h=harness((_,__,abort)=>{signal=abort;return new Promise(done=>{resolve=done})})
 h.keeper.start({token:'old',refreshToken:'refresh',expiresAt:11000});h.setTime(20000);const pending=h.keeper.validToken();h.keeper.clear();assert.equal(signal.aborted,true)
 h.keeper.start({token:'different-user',expiresAt:100000});resolve({token:'late-old-user',refreshToken:'refresh',expiresAt:100000});await assert.rejects(pending);assert.equal(await h.keeper.validToken(),'different-user');assert.equal(h.changes.length,2);h.keeper.clear()
})
test('provider rejection clears session immediately and no expired access token is returned',async()=>{
 const h=harness(async()=>{throw Error('SESSION_RENEWAL_REJECTED')});h.keeper.start({token:'old',refreshToken:'refresh',expiresAt:11000});h.setTime(10000);await h.fire();assert.equal(h.expired,1);assert.equal(h.timers.size,0);await assert.rejects(h.keeper.validToken())
})
test('transient failure retries only while access is valid, then ends session',async()=>{
 const h=harness(async()=>{throw Error('SESSION_RENEWAL_UNAVAILABLE')});h.keeper.start({token:'old',refreshToken:'refresh',expiresAt:11000});h.setTime(10000);await h.fire();assert.equal(h.expired,0);assert.equal([...h.timers][0].at,11000);h.setTime(12000);await h.fire();assert.equal(h.expired,1);await assert.rejects(h.keeper.validToken())
})
test('disabled renewal and pasted access tokens retain provider expiry; no fake unlimited token',async()=>{
 for(const [enabled,refreshToken] of [[false,'refresh'],[true,undefined]]){let calls=0;const h=harness(async()=>{calls++;throw Error('must not refresh')},enabled);h.keeper.start({token:'manual',expiresAt:11000,refreshToken});h.setTime(11000);await h.fire();assert.equal(calls,0);assert.equal(h.expired,1);await assert.rejects(h.keeper.validToken())}
})

const cognito={...identity,authorizationUri:'https://demo.auth.us-west-2.amazoncognito.com/oauth2/authorize',tokenUri:'https://demo.auth.us-west-2.amazoncognito.com/oauth2/token'}
test('Cognito sign-out clears hosted UI session through the configured domain and registered root',()=>{
 const url=new URL(cognitoLogoutUrl(cognito,'https://ui.test'))
 assert.equal(url.origin,'https://demo.auth.us-west-2.amazoncognito.com');assert.equal(url.pathname,'/logout')
 assert.deepEqual([...url.searchParams],[['client_id','public-client'],['logout_uri','https://ui.test/']])
 assert.equal(url.hash,'');assert.equal(url.searchParams.has('token'),false)
})
test('hosted sign-out refuses unsafe or mismatched identity configuration',()=>{
 for(const authorizationUri of ['http://demo.auth.us-west-2.amazoncognito.com/oauth2/authorize','https://user:secret@demo.auth.us-west-2.amazoncognito.com/oauth2/authorize','https://demo.auth.us-west-2.amazoncognito.com/oauth2/authorize?logout_uri=https://elsewhere.test'])assert.throws(()=>cognitoLogoutUrl({...cognito,authorizationUri},'https://ui.test'))
 assert.throws(()=>cognitoLogoutUrl({...cognito,redirectUri:'https://elsewhere.test/auth/callback'},'https://ui.test'))
})
test('non-Cognito providers and lookalike domains do not receive a guessed sign-out request',()=>{
 assert.equal(cognitoLogoutUrl(identity,'https://ui.test'),null)
 for(const authorizationUri of ['https://demo.auth.us-west-2.amazoncognito.com.attacker.test/oauth2/authorize','https://demo.auth.us-west-2.amazoncognito.com/login'])assert.equal(cognitoLogoutUrl({...cognito,authorizationUri},'https://ui.test'),null)
 assert.equal(cognitoLogoutUrl({...cognito,tokenUri:identity.tokenUri},'https://ui.test'),null)
})


test('classic Cognito login clears hosted cookie before authorize and retains PKCE and payment route',async()=>{
 const storage=new Storage(),route='#/payments/PAY-1/approvals?review=review-1'
 const first=new URL(await beginLogin(cognito,storage,'https://ui.test',route))
 assert.equal(first.pathname,'/logout');assert.equal(first.searchParams.get('logout_uri'),'https://ui.test/')
 assert.equal(first.searchParams.size,2);assert.equal(first.searchParams.has('code_challenge'),false)
 const authorize=new URL(await resumeFreshLogin(cognito,storage,'https://ui.test','/',''))
 assert.equal(authorize.pathname,'/oauth2/authorize');assert.equal(authorize.searchParams.get('prompt'),'login')
 const saved=JSON.parse(storage.getItem('stableflow:pkce'))
 assert.equal(authorize.searchParams.get('state'),saved.state)
 assert.equal(authorize.searchParams.get('code_challenge'),Buffer.from(await crypto.subtle.digest('SHA-256',new TextEncoder().encode(saved.verifier))).toString('base64url'))
 assert.equal(await resumeFreshLogin(cognito,storage,'https://ui.test','/',''),null)
 const original=globalThis.fetch
 globalThis.fetch=async(_,options)=>{assert.equal(options.body.get('code_verifier'),saved.verifier);return Response.json({access_token:'different-user',token_type:'Bearer',expires_in:3600})}
 try{const session=await finishLogin(cognito,'?code=code&state='+saved.state,storage,'https://ui.test');assert.equal(session.returnHash,route);assert.equal(session.token,'different-user');assert.equal(storage.values.size,0)}finally{globalThis.fetch=original}
})

test('pending fresh login rejects wrong return path, query, configuration, state and malformed or expired data',async()=>{
 const variants=[
  {path:'/auth/callback'}, {query:'?return=https://elsewhere.test'}, {config:{...cognito,clientId:'other'}},
  {change:p=>({...p,state:'wrong'})}, {change:p=>({...p,createdAt:Date.now()-600001})},
  {change:p=>({...p,createdAt:Date.now()+60000})}, {change:p=>({...p,createdAt:'today'})},
  {change:p=>({...p,verifier:'bad'})}, {raw:'invalid-json'}, {raw:'null'}
 ]
 for(const variant of variants){
  const storage=new Storage();await beginLogin(cognito,storage,'https://ui.test')
  if(variant.change)storage.setItem('stableflow:pkce',JSON.stringify(variant.change(JSON.parse(storage.getItem('stableflow:pkce')))))
  if(variant.raw)storage.setItem('stableflow:pkce',variant.raw)
  await assert.rejects(resumeFreshLogin(variant.config??cognito,storage,'https://ui.test',variant.path??'/',variant.query??''),/Fresh sign-in could not resume/)
  assert.equal(storage.values.size,0)
  assert.equal(await resumeFreshLogin(cognito,storage,'https://ui.test','/',''),null)
 }
})

test('explicit signout clears pending login without deleting retained financial command keys',async()=>{
 const storage=new Storage();storage.setItem('retained-command','keep');await beginLogin(cognito,storage,'https://ui.test')
 clearPendingLogin(storage)
 assert.deepEqual([...storage.values],[['retained-command','keep']])
 assert.equal(await resumeFreshLogin(cognito,storage,'https://ui.test','/',''),null)
 await assert.rejects(finishLogin(cognito,'?code=old&state=old',storage,'https://ui.test'))
})

test('starting another login invalidates the previous callback and binds exchange to current config',async()=>{
 const storage=new Storage();const first=new URL(await beginLogin(identity,storage,'https://ui.test'))
 const second=new URL(await beginLogin(identity,storage,'https://ui.test'))
 assert.notEqual(first.searchParams.get('state'),second.searchParams.get('state'))
 await assert.rejects(finishLogin(identity,'?code=old&state='+first.searchParams.get('state'),storage,'https://ui.test'))
 await beginLogin(identity,storage,'https://ui.test');const pending=JSON.parse(storage.getItem('stableflow:pkce'))
 await assert.rejects(finishLogin({...identity,clientId:'other'},'?code=code&state='+pending.state,storage,'https://ui.test'))
 assert.equal(storage.values.size,0)
})
