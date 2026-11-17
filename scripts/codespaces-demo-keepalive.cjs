#!/usr/bin/env node
// Explicit foreground demo helper. Does not start services or probe application health.
const intervalMs=28*60*1000;
function startKeepAlive({environment=process.env,write=console.log,now=()=>new Date().toISOString(),schedule=setInterval,cancel=clearInterval}={}) {
  if(environment.CODESPACES!=='true' || !/^[a-z0-9-]+$/i.test(environment.CODESPACE_NAME || '')) throw Error('Run this helper inside a GitHub Codespaces terminal.');
  write('Demo keep-alive started: terminal output every 28 minutes. Keep this terminal connected; Ctrl+C stops it.');
  write('Running Codespaces consumes compute usage. This does not extend quote/token expiry or override Codespaces runtime limits.');
  let stopped=false;
  const heartbeat=()=>{if(!stopped)write(`[${now()}] DEMO KEEP-ALIVE | terminal activity only; application health is not checked`);};
  heartbeat();
  const timer=schedule(heartbeat,intervalMs);
  return ()=>{if(stopped)return;stopped=true;cancel(timer);write('Demo keep-alive stopped.');};
}
function main(args=process.argv.slice(2)) {
  if(args.length===1 && args[0]==='--help'){console.log('Usage: node scripts/codespaces-demo-keepalive.cjs\nRun in a connected Codespaces terminal during the demo. Prints activity every 28 minutes; Ctrl+C stops it.');return;}
  if(args.length)throw Error('Usage: node scripts/codespaces-demo-keepalive.cjs [--help]');
  const stop=startKeepAlive();
  process.once('SIGINT',stop);process.once('SIGTERM',stop);
}
module.exports={startKeepAlive,main};
if(require.main===module){try{main();}catch(error){console.error(error.message);process.exitCode=2;}}
