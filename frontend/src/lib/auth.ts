export interface Identity {clientId:string;redirectUri:string;authorizationUri:string;tokenUri:string;scopes:string;autoRefreshEnabled?:boolean}
export interface Session {token:string;expiresAt:number;refreshToken?:string}
const slot='stableflow:pkce'
function base64url(bytes:Uint8Array){return btoa(String.fromCharCode(...bytes)).replace(/\+/g,'-').replace(/\//g,'_').replace(/=+$/,'')}
function endpoint(value:string){const url=new URL(value);if(url.protocol!=='https:'||url.username||url.password||url.hash||url.search)throw new Error('Identity endpoint must be configured HTTPS');return url}
export function validateIdentity(config:Identity,origin:string){endpoint(config.authorizationUri);endpoint(config.tokenUri);const redirect=new URL(config.redirectUri);if(redirect.origin!==origin||!config.clientId||redirect.search||redirect.hash)throw new Error('Register a same-origin redirect URI and public identity client')}
export function returnRoute(hash:string){return /^#\/(overview|payments\/new|payments\/[^/?#]+\/(routing|treasury|approvals|journey|blockchain|ledger|payout)(\?review=[^&#]+)?)$/.test(hash)&&hash.length<=1000?hash:'#/overview'}
export async function beginLogin(config:Identity,storage:Storage,origin:string,hash='#/overview'){
 validateIdentity(config,origin)
 const verifier=base64url(crypto.getRandomValues(new Uint8Array(48)));const state=base64url(crypto.getRandomValues(new Uint8Array(32)))
 const challenge=base64url(new Uint8Array(await crypto.subtle.digest('SHA-256',new TextEncoder().encode(verifier))))
 storage.setItem(slot,JSON.stringify({verifier,state,createdAt:Date.now(),returnHash:returnRoute(hash)}))
 const url=endpoint(config.authorizationUri);url.search=new URLSearchParams({response_type:'code',client_id:config.clientId,redirect_uri:config.redirectUri,scope:config.scopes,code_challenge:challenge,code_challenge_method:'S256',state,prompt:'login'}).toString();return url.href
}
export async function finishLogin(config:Identity,query:string,storage:Storage,origin:string){
 validateIdentity(config,origin);const saved=storage.getItem(slot);storage.removeItem(slot)
 const params=new URLSearchParams(query);const stored=saved?JSON.parse(saved) as {verifier:string;state:string;createdAt:number;returnHash?:string}:null
 if(!stored||Date.now()-stored.createdAt>600000||Date.now()<stored.createdAt||params.get('state')!==stored.state||!params.get('code')||params.has('error'))throw new Error('Sign-in state is missing, expired or mismatched. Sign in again.')
 const response=await fetch(endpoint(config.tokenUri),{method:'POST',credentials:'omit',redirect:'error',signal:AbortSignal.timeout(20000),headers:{'Content-Type':'application/x-www-form-urlencoded'},body:new URLSearchParams({grant_type:'authorization_code',client_id:config.clientId,redirect_uri:config.redirectUri,code:params.get('code')!,code_verifier:stored.verifier})})
 if(!response.ok)throw new Error('Identity token exchange failed. Sign in again.')
 return {...tokenSession(await response.json()),returnHash:returnRoute(stored.returnHash??'#/overview')}
}
function tokenSession(value:unknown,previousRefreshToken?:string):Session{
 const token=value as {access_token?:string;token_type?:string;expires_in?:number;refresh_token?:string}|null
 if(!token||typeof token.access_token!=='string'||!token.access_token||token.token_type?.toLowerCase()!=='bearer'||typeof token.expires_in!=='number'||!Number.isFinite(token.expires_in)||token.expires_in<=0||token.expires_in>2147483)throw new Error('Invalid identity response')
 if(token.refresh_token!==undefined&&(typeof token.refresh_token!=='string'||!token.refresh_token))throw new Error('Invalid identity response')
 return {token:token.access_token,expiresAt:Date.now()+token.expires_in*1000,refreshToken:token.refresh_token??previousRefreshToken}
}
export async function refreshSession(config:Identity,session:Session,signal?:AbortSignal):Promise<Session>{
 if(!session.refreshToken)throw new Error('Sign in again to renew this session')
 const response=await fetch(endpoint(config.tokenUri),{method:'POST',credentials:'omit',redirect:'error',signal:signal?AbortSignal.any([signal,AbortSignal.timeout(20000)]):AbortSignal.timeout(20000),headers:{'Content-Type':'application/x-www-form-urlencoded'},body:new URLSearchParams({grant_type:'refresh_token',client_id:config.clientId,refresh_token:session.refreshToken})})
 if(!response.ok)throw new Error(response.status===400||response.status===401?'SESSION_RENEWAL_REJECTED':'SESSION_RENEWAL_UNAVAILABLE')
 return tokenSession(await response.json(),session.refreshToken)
}
