// Preserve the browser's zone and the offset at the displayed instant.
export const timeParts=(value:unknown)=>{
 if(value===null||value===undefined||value==='')return null
 const date=new Date(String(value))
 if(!Number.isFinite(date.getTime()))return null
 const zone=Intl.DateTimeFormat().resolvedOptions().timeZone
 const parts=new Intl.DateTimeFormat(undefined,{year:'numeric',month:'short',day:'numeric',hour:'numeric',minute:'2-digit',second:'2-digit',timeZone:zone,timeZoneName:'longOffset'}).formatToParts(date)
 return {iso:date.toISOString(),dateTime:parts.filter(part=>part.type!=='timeZoneName').map(part=>part.value).join('').trim(),timeZone:[parts.find(part=>part.type==='timeZoneName')?.value,'('+zone+')'].filter(Boolean).join(' ')}
}
export const time=(value:unknown)=>{
 const parts=timeParts(value)
 return parts?parts.dateTime+' '+parts.timeZone:value===null||value===undefined||value===''?'Pending':'Unavailable'
}
export const text=(value:unknown)=>value===null||value===undefined?'Pending':String(value)
