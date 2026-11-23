// Identify only Node entry points belonging to this checkout; never kill by port/name alone.
const fs=require('node:fs');
const {spawnSync}=require('node:child_process');
const {join,resolve,basename,isAbsolute}=require('node:path');
function linuxProcesses(proc='/proc') {
 const rows=[];
 for(const entry of fs.readdirSync(proc)) {
  if(!/^\d+$/.test(entry))continue;
  try {
   const base=join(proc,entry),exe=fs.readlinkSync(join(base,'exe'));
   if(!/^node(?:js)?$/.test(basename(exe)))continue;
   const stat=fs.readFileSync(join(base,'stat'),'utf8');
   const fields=stat.slice(stat.lastIndexOf(')')+2).split(' ');
   if(fields[0]==='Z')continue;
   rows.push({pid:Number(entry),exe,args:fs.readFileSync(join(base,'cmdline'),'utf8').split('\0').filter(Boolean),cwd:fs.readlinkSync(join(base,'cwd')),started:fields[19]});
  } catch { /* Exited or not inspectable; port verification still fails closed. */ }
 }
 return rows;
}
function windowsArguments(command) {
 return [...String(command || '').matchAll(/"([^"]*)"|([^\s"]+)/g)].map(m=>m[1]??m[2]);
}
function nodeProcesses(platform=process.platform) {
 if(platform==='linux')return linuxProcesses();
 if(platform!=='win32')throw Error('Automatic frontend stop supports Linux/Codespaces and Windows');
 const script='Get-CimInstance Win32_Process -Filter "Name = \'node.exe\'" | Select-Object ProcessId,ExecutablePath,CommandLine,CreationDate | ConvertTo-Json -Compress';
 const result=spawnSync('powershell.exe',['-NoProfile','-NonInteractive','-Command',script],{encoding:'utf8',windowsHide:true,timeout:10000});
 if(result.status!==0)throw Error('Frontend process inspection unavailable');
 let json;try{json=JSON.parse(result.stdout.trim() || '[]');}catch{throw Error('Frontend process inspection returned invalid data');}
 const rows=Array.isArray(json)?json:[json];
 return rows.filter(Boolean).map(r=>({pid:r.ProcessId,exe:r.ExecutablePath,args:windowsArguments(r.CommandLine),started:JSON.stringify(r.CreationDate)}));
}
function ownsFrontend(row,root) {
 if(!Number.isInteger(row.pid)||row.pid<=1||row.pid===process.pid||!row.started||!row.exe||!/^node(?:js)?(?:\.exe)?$/i.test(basename(row.exe)))return false;
 const entry=row.args?.[1];if(!entry||(!row.cwd&&!isAbsolute(entry)))return false;
 try {
  const expected=fs.realpathSync(join(root,'frontend/node_modules/vite/bin/vite.js'));
  const actual=fs.realpathSync(resolve(row.cwd || root,entry));
  return actual===expected && (!row.cwd || fs.realpathSync(row.cwd)===fs.realpathSync(join(root,'frontend')));
 } catch {return false;}
}
module.exports={linuxProcesses,windowsArguments,nodeProcesses,ownsFrontend};
