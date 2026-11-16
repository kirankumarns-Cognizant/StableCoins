import {refreshSession} from './auth.ts'
import type {Identity,Session} from './auth'

interface Runtime {
 now:()=>number
 schedule:(callback:()=>void,delay:number)=>ReturnType<typeof setTimeout>
 cancel:(timer:ReturnType<typeof setTimeout>)=>void
 renew:typeof refreshSession
}
const runtime:Runtime={now:()=>Date.now(),schedule:(callback,delay)=>setTimeout(callback,delay),cancel:timer=>clearTimeout(timer),renew:refreshSession}

/** Tokens remain in memory. A generation prevents late refresh from undoing signout. */
export class SessionKeeper {
 private current:Session|null=null
 private timer:ReturnType<typeof setTimeout>|undefined
 private pending:Promise<string>|null=null
 private controller:AbortController|null=null
 private generation=0
 private identity:Identity
 private changed:(session:Session)=>void
 private expired:()=>void
 private clock:Runtime
 constructor(identity:Identity,changed:(session:Session)=>void,expired:()=>void,clock:Runtime=runtime){this.identity=identity;this.changed=changed;this.expired=expired;this.clock=clock}
 start(session:Session){this.clear();this.current=session;this.changed(session);this.plan()}
 clear(){this.generation++;if(this.timer!==undefined)this.clock.cancel(this.timer);this.timer=undefined;this.controller?.abort();this.controller=null;this.pending=null;this.current=null}
 private renewable(){return this.identity.autoRefreshEnabled!==false&&Boolean(this.current?.refreshToken)}
 private plan(retry=false){
  if(this.timer!==undefined)this.clock.cancel(this.timer)
  if(!this.current)return
  const remaining=this.current.expiresAt-this.clock.now()
  const lead=this.renewable()?Math.min(60000,Math.max(1000,remaining/10)):0
  const delay=retry?Math.min(10000,remaining):remaining-lead
  this.timer=this.clock.schedule(()=>{this.timer=undefined;void this.renew().catch(()=>{})},Math.max(0,delay))
 }
 private end(){this.clear();this.expired()}
 async validToken():Promise<string>{
  if(!this.current)throw new Error('Sign in to continue')
  if(this.pending)return this.pending
  if(this.current.expiresAt-this.clock.now()<=5000)return this.renew()
  return this.current.token
 }
 private renew():Promise<string>{
  if(this.pending)return this.pending
  if(!this.current)return Promise.reject(new Error('Sign in to continue'))
  if(!this.renewable()){
   if(this.current.expiresAt<=this.clock.now()){this.end();return Promise.reject(new Error('Session expired'))}
   this.plan();return Promise.resolve(this.current.token)
  }
  const generation=this.generation,session=this.current,controller=new AbortController();this.controller=controller
  this.pending=(async()=>{
   try{
    const renewed=await this.clock.renew(this.identity,session,controller.signal)
    if(generation!==this.generation)throw new Error('Session changed')
    this.current=renewed;this.changed(renewed);this.plan();return renewed.token
   }catch(error){
    if(generation===this.generation){
     if((error instanceof Error&&error.message==='SESSION_RENEWAL_REJECTED')||this.current!.expiresAt<=this.clock.now()){this.end()}
     else{this.plan(true);return this.current!.token}
    }
    throw new Error('Sign in again to continue; the current payment path and command keys are retained')
   }finally{if(generation===this.generation){this.pending=null;this.controller=null}}
  })()
  return this.pending
 }
}
