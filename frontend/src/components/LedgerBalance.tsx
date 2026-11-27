import {balance} from '../lib/safety'
export function LedgerBalance({entries}:{entries:{asset:string;debit:string;credit:string}[]}){
 let totals:Record<string,string>
 try{totals=balance(entries)}catch{return <p className="alert">Exact ledger arithmetic could not be verified. Review the source journal.</p>}
 return <p className="balance">Per-asset debit - credit: {Object.entries(totals).map(([asset,value])=>`${asset} ${value}`).join(' | ')} | {Object.values(totals).every(v=>/^0(\.0+)?$/.test(v))?'BALANCED':'IMBALANCE | REVIEW REQUIRED'}</p>
}
