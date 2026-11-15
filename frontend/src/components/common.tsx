import type {ReactNode} from 'react'
import type {Section} from '../lib/model'
import {text} from '../lib/format'
export function Badge({children}:{children:ReactNode}){return <span className="badge">{children}</span>}
export function Card({title,children}:{title:string;children:ReactNode}){return <section className="card"><h2>{title}</h2>{children}</section>}
export function Details({title,value}:{title:string;value:unknown}){return <details><summary>{title}</summary><pre>{JSON.stringify(value,null,2)}</pre></details>}
export function Fields({values}:{values:Record<string,unknown>}){return <dl>{Object.entries(values).map(([key,value])=><div key={key}><dt>{key}</dt><dd>{text(value)}</dd></div>)}</dl>}
export function EvidenceSection<T>({section,children}:{section?:Section<T>;children:(data:T)=>ReactNode}){if(!section||section.status!=='AVAILABLE'||section.data==null)return <p className="notice">{section?.status??'PENDING'} ? {section?.reason??'Evidence has not been recorded yet.'}</p>;return <>{children(section.data)}</>}
