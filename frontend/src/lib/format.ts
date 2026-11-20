// Show the browser's zone and UTC offset, including historical daylight-saving rules.
export const time=(value:unknown)=>{
 if(value===null||value===undefined||value==='')return 'Pending'
 const date=new Date(String(value))
 if(!Number.isFinite(date.getTime()))return 'Unavailable'
 const zone=Intl.DateTimeFormat().resolvedOptions().timeZone
 return new Intl.DateTimeFormat(undefined,{year:'numeric',month:'short',day:'numeric',hour:'numeric',minute:'2-digit',second:'2-digit',timeZone:zone,timeZoneName:'longOffset'}).format(date)+' ('+zone+')'
}
export const text=(value:unknown)=>value===null||value===undefined?'Pending':String(value)
