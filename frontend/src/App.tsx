import {time} from './lib/format'
import {tabs} from './lib/navigation'
import {useCallback,useEffect,useRef,useState} from 'react'
import {connections} from './config/connections'
import {ApiError,apiRoot,request} from './lib/api'
import {clearPendingLogin,cognitoLogoutUrl,finishLogin,resumeFreshLogin} from './lib/auth'
import type {Session} from './lib/auth'
import {SessionKeeper} from './lib/session'
import {clearOperation,operation,parseRoute,paymentPath} from './lib/safety'
import {privatePayment} from './lib/settlement'
import type {AgentRun,Catalog,Evidence,Journey,Payment,Recommendation,PrivateEvidence} from './lib/model'
import {Badge,Card} from './components/common'
import {SignIn,Overview,NewPayment} from './components/Workspace'
import {PaymentViews} from './components/PaymentViews'
import './App.css'

export default function App(){
 const [session,setSession]=useState<Session|null>(null)
 const [redirecting,setRedirecting]=useState(()=>Boolean(sessionStorage.getItem('stableflow:fresh-login'))&&!new URLSearchParams(location.search).has('code')&&!new URLSearchParams(location.search).has('error'))
 const [catalog,setCatalog]=useState<Catalog|null>(null);const [payment,setPayment]=useState<Payment|null>(null)
 const [privateEvidence,setPrivateEvidence]=useState<PrivateEvidence|null>(null)
 const [evidence,setEvidence]=useState<Evidence|null>(null);const [review,setReview]=useState<Recommendation|null>(null)
 const [agent,setAgent]=useState<AgentRun|null>(null);const [journey,setJourney]=useState<Journey|null>(null)
 const [hash,setHash]=useState(window.location.hash);const route=parseRoute(hash)
 const [error,setError]=useState('');const [commandError,setCommandError]=useState('');const [notice,setNotice]=useState('');const [busy,setBusy]=useState(false);const [updated,setUpdated]=useState('')
 const [refreshing,setRefreshing]=useState(false);const [refreshResult,setRefreshResult]=useState('')
 const keeper=useRef<SessionKeeper|null>(null)
 const knownHash=useRef(window.location.hash)
 const epoch=useRef(0);const active=useRef<AbortController|null>(null);const callbackStarted=useRef(false)
 const base=apiRoot(connections.apiBaseUrl,location.origin)
 const reset=useCallback(()=>{keeper.current?.clear();epoch.current++;active.current?.abort();setSession(null);setCatalog(null);setPayment(null);setEvidence(null);setPrivateEvidence(null);setReview(null);setAgent(null);setJourney(null);setUpdated('');setBusy(false);setRefreshing(false);setRefreshResult('');setCommandError('');setNotice('')},[])
 useEffect(()=>{
  const instance=new SessionKeeper(connections.identity,value=>setSession(value),()=>{reset();setError('Session renewal ended. Sign in again to return to this screen; unresolved command keys are retained.')})
  keeper.current=instance
  return()=>{instance.clear();if(keeper.current===instance)keeper.current=null}
 },[reset])
 useEffect(()=>{const handler=()=>{
  const before=parseRoute(knownHash.current),after=parseRoute(location.hash);knownHash.current=location.hash
  if(before.paymentId!==after.paymentId||before.review!==after.review){epoch.current++;active.current?.abort();setPayment(null);setEvidence(null);setPrivateEvidence(null);setReview(null);setAgent(null);setJourney(null);setUpdated('');setError('');setCommandError('');setBusy(false);setRefreshing(false);setRefreshResult('')}
  setHash(location.hash)
 };addEventListener('hashchange',handler);return()=>removeEventListener('hashchange',handler)},[])
 useEffect(()=>{window.scrollTo(0,0)},[hash])
 useEffect(()=>{
  if(callbackStarted.current)return;const query=location.search
  if(!new URLSearchParams(query).has('code')&&!new URLSearchParams(query).has('error')){
   if(sessionStorage.getItem('stableflow:fresh-login')){
    callbackStarted.current=true
    void resumeFreshLogin(connections.identity,sessionStorage,location.origin,location.pathname,query).then(url=>{if(url)location.assign(url);else setRedirecting(false)}).catch(failure=>{setRedirecting(false);setError(failure.message)})
   }
   return
  }
  callbackStarted.current=true;history.replaceState(null,'',location.pathname+location.hash)
  const generation=epoch.current
  finishLogin(connections.identity,query,sessionStorage,location.origin).then(value=>{if(generation===epoch.current){keeper.current!.start(value);location.hash=value.returnHash}}).catch(e=>{if(generation===epoch.current)setError(e.message)})
 },[])
 const load=useCallback(async()=>{
  // Polling must not cancel a slow read before its request timeout can complete.
  if(!session || active.current && !active.current.signal.aborted)return
  const controller=new AbortController();active.current=controller;const generation=epoch.current;setRefreshing(true)
  const get=async<T,>(path:string)=>request<T>(base,await keeper.current!.validToken(),path,{signal:controller.signal})
  try{
   const c=await get<Catalog>('/payments/workbench');let pe:PrivateEvidence|null=null;let p:Payment|null=null,e:Evidence|null=null,j:Journey|null=null,r:Recommendation|null=null,a:AgentRun|null=null
   if(route.paymentId){const path=paymentPath(route.paymentId);[p,e,j]=await Promise.all([get<Payment>(path),get<Evidence>(path+'/evidence'),get<Journey>(path+'/journey')]);const reviewId=route.review??e.recommendations[0]?.recommendation_id
    if(privatePayment(p))pe=await get<PrivateEvidence>(path+'/private-settlement');
    if(reviewId){r=await get<Recommendation>(path+'/recommendations/'+encodeURIComponent(String(reviewId)));const run=e.agentRuns.find(run=>run.recommendation_id===reviewId);if(run)a=await get<AgentRun>(path+'/recommendations/'+encodeURIComponent(String(reviewId))+'/agent-runs/'+encodeURIComponent(String(run.run_id)))}
   }
   if(controller.signal.aborted||generation!==epoch.current)return
   const completed=new Date().toISOString()
   setCatalog(c);setPayment(p);setEvidence(e);setPrivateEvidence(pe);setJourney(j);setReview(r);setAgent(a);setUpdated(completed);setError('');setRefreshResult('Evidence refreshed at '+time(completed)+'.')
  }catch(failure){
   if(controller.signal.aborted||generation!==epoch.current)return
   if(failure instanceof ApiError&&failure.status===401){reset();setError('Session is no longer authorized. Sign in again.');return}
   let message=failure instanceof Error?failure.message:'Unable to read application evidence'
   if(failure instanceof ApiError && [502,503,504].includes(failure.status))message=`Evidence refresh failed (HTTP ${failure.status}). The application API is unavailable. Retry after service connectivity is restored.`
   else if(failure instanceof TypeError)message='Unable to reach the application API. Check connectivity, then refresh evidence again.'
   else if(failure instanceof Error && failure.name==='TimeoutError')message='The evidence request timed out. Check service availability, then refresh evidence again.'
   setError(message);setRefreshResult('Refresh failed at '+time(new Date().toISOString())+'. The last successful refresh time is unchanged; previously loaded data may be out of date.')
  }finally{if(active.current===controller){active.current=null;if(generation===epoch.current)setRefreshing(false)}}
 },[session,base,route.paymentId,route.review,reset])
 useEffect(()=>{if(busy)return;const initial=setTimeout(()=>void load(),0);const timer=setInterval(()=>{if(!document.hidden)void load()},5000);return()=>{clearTimeout(initial);clearInterval(timer);active.current?.abort()}},[load,busy])
 async function command(path:string,body:unknown,name?:string){
  if(!session||!catalog||busy)throw new Error('An authenticated, ready session is required')
  const generation=epoch.current;setBusy(true);setError('');setCommandError('');setNotice('')
  try{const op=name?operation(sessionStorage,catalog.customerId,name,body):null
   const response=await request<Record<string,unknown>>(base,await keeper.current!.validToken(),path,{method:'POST',body,key:op?.key})
   if(op&&name)clearOperation(sessionStorage,catalog.customerId,name)
   if(generation===epoch.current){setNotice('Command acknowledged. Screens follow recorded server evidence.');if(path==='/payments'&&response.paymentId)go(`/payments/${encodeURIComponent(String(response.paymentId))}/routing`);else await load()}
   return response
  }catch(failure){if(generation===epoch.current){if(failure instanceof ApiError&&failure.status===401)reset();setCommandError((failure instanceof Error?failure.message:'Request failed')+'. If the outcome is uncertain, retry this exact command; its key is retained.')}throw failure
  }finally{if(generation===epoch.current)setBusy(false)}
 }
 const act=(path:string,body:unknown,name?:string)=>{void command(path,body,name).catch(()=>{})}
 const enabled=Boolean(session&&catalog&&!busy&&!error)
 function exportEvidence(){const blob=new Blob([JSON.stringify({exportedAt:new Date().toISOString(),scope:'Customer-authorized application evidence; independently verify public TESTNET receipts.',payment,evidence,review,agent,journey},null,2)],{type:'application/json'});const url=URL.createObjectURL(blob);const anchor=document.createElement('a');anchor.href=url;anchor.download=`${payment?.paymentId??'payment'}-evidence.json`;anchor.click();setTimeout(()=>URL.revokeObjectURL(url),1000)}
 return <div className="shell"><button className="skip-link" onClick={()=>document.getElementById("main")?.focus()}>Skip to payment content</button>
  <aside className="sidebar"><a className="brand" href="#/overview"><span className="brand-symbol">S</span><span>StableFlow<small>INTELLIGENT SETTLEMENT</small></span></a><p className="nav-label">PAYMENT WORKSPACE</p><nav aria-label="Main navigation"><a className={route.page==='/overview'?'selected':''} href="#/overview">Overview</a><a className={route.page==='/payments/new'?'selected':''} href="#/payments/new">New payment</a>{payment&&<><p className="nav-label">THIS PAYMENT</p>{tabs.map(([id,label])=><a key={id} className={route.tab===id?'selected':''} href={`#/payments/${encodeURIComponent(payment.paymentId)}/${id}${route.review?'?review='+encodeURIComponent(route.review):''}`}>{label}</a>)}</>}</nav><div className="sidebar-footer">USDC settlement | ETH for gas<br/>Fiat endpoints are simulated.<br/><span>Built for evidence, governed by policy.</span></div></aside>
  <div className="workspace"><header className="topbar"><span>Cross-border B2B payments</span><div><Badge>TEST ENVIRONMENT</Badge>{session&&<div className="signed-in-account"><button className="quiet" onClick={()=>{reset();clearPendingLogin(sessionStorage);setError('');try{const url=cognitoLogoutUrl(connections.identity,location.origin);if(url){location.assign(url);return}setNotice('Local session cleared. Your identity provider may retain its browser session.')}catch{setError('Local session cleared, but identity-provider sign-out could not be started. Check the configured identity endpoints.')}}}>Sign out / switch user</button><div className="signed-in-user" aria-label="Signed-in user">{catalog?<><span>Signed in as <strong><bdi>{catalog.actorUsername??catalog.actorId}</bdi></strong></span><small>{catalog.roles.map(role=>role.replaceAll('_',' ').toLowerCase()).join(' + ')}</small></>:<span>Verifying identity...</span>}</div></div>}</div></header>
  <main id="main" tabIndex={-1}><div className="intro"><div><p className="eyebrow">STABLECOIN PAYMENT PLATFORM</p><h1>{!session?'Connect to your payment workspace':route.page==='/payments/new'?'Create a payment intent':payment?tabs.find(([id])=>id===route.tab)?.[1]:'Payment overview'}</h1><p>USD funding to USDC treasury to blockchain settlement to optional fiat payout.</p></div>{payment&&<button className="secondary" onClick={exportEvidence}>Export evidence</button>}</div>
  <div className="truth-banner"><strong>Demonstration boundaries</strong><span>Funding, conversion, compliance, FX, Visa and bank credit are simulated. Blockchain is real only when recorded evidence says TESTNET / LIVE. Application ledger entries are separate accounting records.</span></div>
  {error&&<div role="alert" className="alert">{error}{session&&<button disabled={busy||refreshing} onClick={()=>void load()}>{refreshing?'Refreshing...':'Refresh evidence'}</button>}</div>}{commandError&&<div role="alert" className="alert">{commandError}</div>}{notice&&<p role="status" className="notice">{notice}</p>}
  {session&&<p role="status" aria-live="polite" className="muted">{refreshing?'Refreshing application evidence...':refreshResult}</p>}
  {!session?redirecting?<Card title="Opening secure sign-in"><p>Continuing to the identity provider after clearing its browser session.</p></Card>:<SignIn onSession={value=>{epoch.current++;keeper.current!.start(value);setError('')}} onError={setError}/>:<>
   <div className="session-strip"><span><strong>{catalog?.actorId??'Verifying identity...'}</strong> | {catalog?.roles.join(' + ')??'Server membership required'}</span><span>Customer {catalog?.customerId??'pending'} | Last successful refresh {time(updated)}</span><button className="quiet" disabled={busy||refreshing} onClick={()=>void load()}>{refreshing?'Refreshing...':'Refresh'}</button></div>
   {!catalog?<Card title="Connecting to the application"><p>The API must validate your token and server-managed membership. No demonstration data is substituted if it is unavailable.</p></Card>:route.page==='/payments/new'?<NewPayment key={catalog.customerId} catalog={catalog} enabled={enabled} onCreate={body=>command('/payments',body,'create')}/>:!route.paymentId?<Overview catalog={catalog}/>:payment&&evidence?<PaymentViews privateEvidence={privateEvidence} payment={payment} evidence={evidence} review={review} agent={agent} journey={journey} tab={route.tab} customerId={catalog.customerId} enabled={enabled} approver={catalog.roles.includes('PAYMENT_APPROVER')} act={act}/>:<Card title="Loading payment evidence"><p>Reading customer-authorized records. Commands remain unavailable until evidence loads.</p></Card>}
  </>}
  <footer>StableFlow | Evidence-led stablecoin settlement | No production money moves in this demonstration.</footer></main></div>
 </div>
}
function go(path:string){window.location.hash=path}
