// Opt-in, bounded CLI diagnostics. Never print container environment or unfiltered logs.
const {openSync,readSync,closeSync,fstatSync,realpathSync,writeFileSync}=require('node:fs');
const {join,relative,isAbsolute,resolve}=require('node:path');
const services=new Set(['postgres','localstack','payment-service','compliance-quote-service','blockchain-service','settlement-ledger-service','reconciliation-service','visa-simulator']);

function redact(text,environment=process.env) {
  let safe=String(text).replace(/\x1b\][^\x07]*(?:\x07|\x1b\\)/g,'').replace(/\x1b\[[0-?]*[ -/]*[@-~]/g,'').replace(/[\x00-\x08\x0b-\x1f\x7f]/g,'');
  for(const [name,value] of Object.entries(environment))if(/password|secret|token|api.?key|private.?key|access.?key|authorization|cookie|credential/i.test(name) && typeof value==='string' && value.length>=4)safe=safe.split(value).join('[REDACTED]');
  return safe
    .replace(/-----BEGIN [^-]*PRIVATE KEY-----[\s\S]*?(?:-----END [^-]*PRIVATE KEY-----|$)/g,'[REDACTED PRIVATE KEY]')
    .replace(/\b(?:Bearer|Basic)\s+[^\s,"']+/gi,'[REDACTED AUTHORIZATION]')
    .replace(/(["']?[\w.-]*(?:password|secret|token|api[-_]?key|private[-_]?key|access[-_]?key|authorization|cookie|credential)[\w.-]*["']?\s*[:=]\s*)(?:"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|[^,\s;]+)/gi,'$1[REDACTED]')
    .replace(/(?:https?|postgres(?:ql)?):\/\/[^\s"'<>]+/gi,value=>{try{const url=new URL(value);return url.protocol+'//'+url.host+(url.pathname!=='/' || url.search?'[REDACTED_PATH]':'/');}catch{return '[REDACTED_URL]';}})
    .replace(/\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b/gi,'[REDACTED_EMAIL]')
    .replace(/\beyJ[\w-]+\.[\w-]+\.[\w-]+\b/g,'[REDACTED_JWT]')
    .replace(/\b(?:sk-(?:proj-)?[\w-]{12,}|(?:0x)?[a-f0-9]{64}|[A-Za-z0-9+/_=-]{48,})\b/gi,'[REDACTED_VALUE]');
}
function excerpt(text,environment) {
  const lines=redact(text,environment).split(/\r?\n/),selected=new Set();
  for(let i=0;i<lines.length;i++)if(/\b(?:error|failed|failure|unhealthy|denied|timeout|refused|expected|actual|but was|caused by|not found|unable|cannot|could not)\b|\b\w*(?:Exception|Error)\b|not ok|[✖×]/i.test(lines[i])) {
    selected.add(i);if(i+1<lines.length)selected.add(i+1);
  }
  return [...selected].slice(-20).map(i=>'  '+lines[i].slice(0,500));
}
function readTail(file,boundary,since=0) {
  let fd;
  try {
    const actual=realpathSync(file),base=realpathSync(boundary),part=relative(base,actual);
    if(part==='..' || part.startsWith('..\\') || part.startsWith('../') || isAbsolute(part))return null;
    fd=openSync(actual,'r');const stat=fstatSync(fd);
    if(!stat.isFile() || stat.mtimeMs<since)return null;
    const size=Math.min(stat.size,65536),buffer=Buffer.alloc(size);
    readSync(fd,buffer,0,size,stat.size-size);
    return buffer.toString('utf8');
  } catch {return null;} finally {if(fd!==undefined)closeSync(fd);}
}
async function collectServiceDiagnostics(report,directory,execute) {
  if(report.action!=='start' || report.status!=='FAILED')return null;
  const names=[...new Set([...(report.containers || []),...(report.components || [])].filter(item=>item.status==='FAILED' && services.has(item.name)).map(item=>item.name))];
  if(!names.length)return null;
  try {
    const result=await execute('docker',['compose','--profile','services','logs','--no-color','--tail','80',...names],{timeoutMs:15000});
    if(result.code!==0)return {status:'UNAVAILABLE',reason:'FAILED_SERVICE_LOGS_UNAVAILABLE'};
    const log=join(directory,'failed-services.log');
    writeFileSync(log,redact(String(result.output || '').slice(-65536)),{mode:0o600});
    return {status:'COLLECTED',log};
  } catch {return {status:'UNAVAILABLE',reason:'FAILED_SERVICE_LOGS_UNAVAILABLE'};}
}
function failureDetails(report,{root,directory,environment=process.env}) {
  if(!['start','verify'].includes(report.action) || report.status!=='FAILED')return [];
  const sources=(report.checks || []).filter(check=>check.status==='FAILED' && check.log).map(check=>({name:check.name,file:check.log,boundary:directory}));
  // Fresh failed test reports usually contain the assertions omitted from Maven's final summary.
  for(const suite of report.java?.suites || [])if((suite.failures || suite.errors) && (services.has(suite.module) || ['platform-flow','platform-security'].includes(suite.module)) && /^[\w$]+(?:\.[\w$]+)+$/.test(suite.name)) {
    for(const folder of ['surefire-reports','failsafe-reports']) {
      const boundary=join(root,'services',suite.module,'target',folder);
      sources.push({name:suite.name,file:join(boundary,suite.name+'.txt'),boundary,since:Date.parse(report.startedAt) || 0});
    }
  }
  if(report.diagnostics?.log)sources.push({name:'failed services',file:report.diagnostics.log,boundary:directory});
  const messages=['CLI failure details (bounded, redacted excerpts):'];let shown=0;
  for(const source of sources) {
    if(shown===6){messages.push('Additional details omitted; inspect the diagnostic files locally.');break;}
    const text=readTail(resolve(source.file),source.boundary,source.since);
    if(text===null)continue;
    const lines=excerpt(text,environment);
    if(lines.length){messages.push('Failure details: '+source.name,...lines);shown++;}
  }
  if(!shown)messages.push('No matching error excerpt available. Use the failure summary and diagnostic log paths.');
  if(report.diagnostics?.status==='UNAVAILABLE')messages.push('Failed service logs unavailable; the original failure result is retained.');
  return messages;
}
module.exports={redact,excerpt,readTail,collectServiceDiagnostics,failureDetails};
