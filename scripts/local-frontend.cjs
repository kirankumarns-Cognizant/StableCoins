// Development-only Vite lifecycle. Never installs dependencies or opens a browser.
const {spawn}=require('node:child_process');
const {openSync,closeSync}=require('node:fs');
const {join}=require('node:path');
const marker='stableflow-vite-development';

function frontendEnvironment(parent,paymentUrl) {
  const target=new URL(paymentUrl);
  if(target.protocol!=='http:' || !['localhost','127.0.0.1','[::1]'].includes(target.hostname) || target.username || target.password || target.pathname!=='/' || target.search || target.hash) throw Error('Frontend proxy requires an HTTP loopback payment origin');
  const environment={...parent,PAYMENT_SERVICE_URL:target.origin};
  const name=parent.CODESPACE_NAME,domain=parent.GITHUB_CODESPACES_PORT_FORWARDING_DOMAIN;
  const hosts=(parent.FRONTEND_DEV_ALLOWED_HOSTS || '').split(',').map(host=>host.trim()).filter(Boolean);
  if(typeof name==='string' && /^[a-z0-9-]+$/i.test(name) && typeof domain==='string' && /^[a-z0-9]+(?:[.-][a-z0-9]+)*$/i.test(domain)) hosts.push(`${name}-5173.${domain}`);
  environment.FRONTEND_DEV_ALLOWED_HOSTS=[...new Set(hosts)].join(',');
  return environment;
}

async function frontendState(fetcher=fetch,origin='http://127.0.0.1:5173') {
  let response;
  try {response=await fetcher(origin+'/__stablecoins_dev_health',{signal:AbortSignal.timeout(1500),redirect:'error',cache:'no-store'});}
  catch {return 'UNREACHABLE';}
  try {
    if(response.status!==200 || (await response.json()).application!==marker) return 'OCCUPIED';
    const page=await fetcher(origin+'/',{signal:AbortSignal.timeout(1500),redirect:'error',cache:'no-store'});
    if(page.status!==200 || !(await page.text()).includes('StableFlow | Stablecoin Payment Workspace')) return 'NOT_READY';
    const api=await fetcher(origin+'/api/v1/payments/workbench',{signal:AbortSignal.timeout(1500),redirect:'error',cache:'no-store'});
    await api.body?.cancel();
    return api.status===401?'READY':'NOT_READY';
  } catch {return 'NOT_READY';}
}

async function startFrontend({root,logPath,paymentUrl,timeoutMs=30000,environment=process.env},dependencies={}) {
  if(!Number.isSafeInteger(timeoutMs) || timeoutMs<1000 || timeoutMs>120000) throw Error('Invalid local frontend startup timeout');
  const env=frontendEnvironment(environment,paymentUrl);
  const state=dependencies.state || frontendState;
  const initial=await state();
  if(initial==='READY') return {name:'frontend-start',status:'PASSED',reused:true};
  if(initial==='OCCUPIED' || initial==='NOT_READY') throw Error('Port 5173 is already serving an unrecognized or unready frontend. Stop its existing dev terminal with Ctrl+C, then retry start');
  const launch=dependencies.launch || (()=>{
    const log=openSync(logPath,'a');
    try {
      return spawn(process.execPath,[join(root,'frontend/node_modules/vite/bin/vite.js'),'--host','0.0.0.0','--port','5173','--strictPort'],{
        cwd:join(root,'frontend'),env,detached:true,windowsHide:true,stdio:['ignore',log,log],
      });
    } finally {closeSync(log);}
  });
  const child=launch(env);
  let exited=false;
  child.once('error',()=>{exited=true;});
  child.once('exit',()=>{exited=true;});
  child.unref();
  const pause=dependencies.pause || (ms=>new Promise(resolve=>setTimeout(resolve,ms)));
  const deadline=Date.now()+timeoutMs;
  try {
    while(Date.now()<deadline && !exited) {
      if(await state()==='READY' && !exited) return {name:'frontend-start',status:'PASSED',reused:false,pid:child.pid,log:logPath};
      await pause(500);
    }
    throw Error('Frontend startup failed or timed out; inspect its local frontend.log');
  } catch(error) {
    // Only terminate the child created by this invocation, never another port owner.
    if(!exited) child.kill();
    throw error;
  }
}
module.exports={frontendEnvironment,frontendState,startFrontend};
