export interface Identity {clientId:string;redirectUri:string;authorizationUri:string;tokenUri:string;scopes:string}
const slot='stableflow:pkce'
function base64url(bytes:Uint8Array){return btoa(String.fromCharCode(...bytes)).replace(/\+/g,'-').replace(/\//g,'_').replace(/=+$/,'')}
function endpoint(value:string){const url=new URL(value);if(url.protocol!=='https:'||url.username||url.password||url.hash||url.search)throw new Error('Identity endpoint must be configured HTTPS');return url}
export function validateIdentity(config:Identity,origin:string){endpoint(config.authorizationUri);endpoint(config.tokenUri);const redirect=new URL(config.redirectUri);if(redirect.origin!==origin||!config.clientId||redirect.search||redirect.hash)throw new Error('Register a same-origin redirect URI and public identity client')}
export async function beginLogin(config:Identity,storage:Storage,origin:string){
 validateIdentity(config,origin)
 const verifier=base64url(crypto.getRandomValues(new Uint8Array(48)));const state=base64url(crypto.getRandomValues(new Uint8Array(32)))
 const challenge=base64url(new Uint8Array(await crypto.subtle.digest('SHA-256',new TextEncoder().encode(verifier))))
 storage.setItem(slot,JSON.stringify({verifier,state,createdAt:Date.now()}))
 const url=endpoint(config.authorizationUri);url.search=new URLSearchParams({response_type:'code',client_id:config.clientId,redirect_uri:config.redirectUri,scope:config.scopes,code_challenge:challenge,code_challenge_method:'S256',state,prompt:'login'}).toString();return url.href
}
export async function finishLogin(config:Identity,query:string,storage:Storage,origin:string){
 validateIdentity(config,origin);const saved=storage.getItem(slot);storage.removeItem(slot)
 const params=new URLSearchParams(query);const stored=saved?JSON.parse(saved) as {verifier:string;state:string;createdAt:number}:null
 if(!stored||Date.now()-stored.createdAt>600000||Date.now()<stored.createdAt||params.get('state')!==stored.state||!params.get('code')||params.has('error'))throw new Error('Sign-in state is missing, expired or mismatched. Sign in again.')
 const response=await fetch(endpoint(config.tokenUri),{method:'POST',credentials:'omit',redirect:'error',signal:AbortSignal.timeout(20000),headers:{'Content-Type':'application/x-www-form-urlencoded'},body:new URLSearchParams({grant_type:'authorization_code',client_id:config.clientId,redirect_uri:config.redirectUri,code:params.get('code')!,code_verifier:stored.verifier})})
 if(!response.ok)throw new Error('Identity token exchange failed. Sign in again.')
 const token=await response.json() as {access_token?:string;token_type?:string;expires_in?:number}
 if(!token.access_token||token.token_type?.toLowerCase()!=='bearer'||!token.expires_in||token.expires_in<=0)throw new Error('Invalid identity response')
 return {token:token.access_token,expiresAt:Date.now()+token.expires_in*1000}
}
