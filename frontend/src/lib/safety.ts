// Display arithmetic only. Posting and eligibility stay authoritative in Java.
export function units(value: string, scale: number): bigint {
  if (!/^\d+(\.\d+)?$/.test(value)) throw new Error('Invalid decimal evidence')
  const [whole, fraction = ''] = value.split('.')
  if (fraction.length > scale) throw new Error('Evidence precision exceeds asset scale')
  return BigInt(whole) * 10n ** BigInt(scale) + BigInt(fraction.padEnd(scale, '0') || '0')
}
export function decimal(value: bigint, scale: number): string {
  const sign = value < 0 ? '-' : ''; const absolute = value < 0 ? -value : value
  const digits = absolute.toString().padStart(scale + 1, '0')
  return sign + (scale ? `${digits.slice(0, -scale)}.${digits.slice(-scale)}` : digits)
}
export function balance(entries: {asset:string;debit:string;credit:string}[]): Record<string,string> {
  if(entries.length===0)throw new Error('No ledger entries recorded')
  const scales:Record<string,number> = {USD:2, INR:2, USDC:6, ETH:18, DEMOUSD:6, BESU_GAS:18}
  const totals:Record<string,bigint> = {}
  for (const e of entries) {if (!(e.asset in scales)) throw new Error('Unsupported asset');totals[e.asset]=(totals[e.asset]??0n)+units(e.debit,scales[e.asset])-units(e.credit,scales[e.asset])}
  return Object.fromEntries(Object.entries(totals).map(([asset,total])=>[asset,decimal(total,scales[asset])]))
}
export function explorer(chainId:number, hash:string, configured:{chainId:number;explorerBaseUrl:string}):string|undefined {
 if(!configured||![84532,11155111].includes(chainId)||chainId!==configured.chainId||!/^0x[0-9a-fA-F]{64}$/.test(hash))return
 try{const base=new URL(configured.explorerBaseUrl);if(base.protocol!=='https:'||base.username||base.password||base.search||base.hash)return
  if(!base.pathname.endsWith('/'))base.pathname+='/'
  return new URL('tx/'+hash,base).href
 }catch{return}
}
export interface Pending {key:string;body:string;createdAt:string}
export function operation(storage:Storage, customer:string, name:string, body:unknown):Pending {
  const slot=`stableflow:${customer}:${name}`; const serialized=JSON.stringify(body??null);const previous=storage.getItem(slot)
  if(previous){const parsed=JSON.parse(previous) as Pending;if(parsed.body!==serialized)throw new Error('An unresolved request exists. Restore and retry its original instruction first.');return parsed}
  const next={key:crypto.randomUUID(),body:serialized,createdAt:new Date().toISOString()};storage.setItem(slot,JSON.stringify(next));return next
}
export function pending(storage:Storage,customer:string,name:string):Pending|null {const value=storage.getItem(`stableflow:${customer}:${name}`);return value?JSON.parse(value) as Pending:null}
export function clearOperation(storage:Storage,customer:string,name:string){storage.removeItem(`stableflow:${customer}:${name}`)}
export function paymentPath(id:string){return `/payments/${encodeURIComponent(id)}`}
export function parseRoute(hash:string){const path=hash.replace(/^#/, '')||'/overview';const [pathname,query='']=path.split('?');const match=/^\/payments\/([^/]+)\/(routing|treasury|approvals|journey|blockchain|ledger|payout)$/.exec(pathname);return {page:pathname, paymentId:match?decodeURIComponent(match[1]):null,tab:match?.[2]??'routing',review:new URLSearchParams(query).get('review')}}
