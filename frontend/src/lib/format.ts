export const time=(value:unknown)=>value?new Date(String(value)).toLocaleString(): 'Pending'
export const text=(value:unknown)=>value===null||value===undefined?'Pending':String(value)
