export class ApiError extends Error {status:number;code:string;constructor(status:number,code:string){super(code);this.status=status;this.code=code}}
export function apiRoot(base:string,origin:string):string {
 const url=new URL(base,origin)
 if(url.username||url.password||url.search||url.hash||(url.origin!==origin&&url.protocol!=='https:'))throw new Error('API configuration requires same-origin or HTTPS')
 return url.href.replace(/\/$/,'')
}
export async function request<T>(base:string,token:string,path:string,options:{method?:string;body?:unknown;key?:string;signal?:AbortSignal}={}):Promise<T>{
 if(!token||/[\r\n]/.test(token))throw new ApiError(401,'ACCESS_TOKEN_REQUIRED')
 if(!path.startsWith('/')||path.startsWith('//')||path.includes('..'))throw new Error('Invalid API path')
 const timeout=AbortSignal.timeout(20000);const signal=options.signal?AbortSignal.any([timeout,options.signal]):timeout
 const response=await fetch(base+path,{method:options.method??'GET',signal,redirect:'error',credentials:'omit',cache:'no-store',headers:{Authorization:`Bearer ${token}`,Accept:'application/json',...(options.body!==undefined?{'Content-Type':'application/json'}:{}),...(options.key?{'Idempotency-Key':options.key}:{})},...(options.body!==undefined?{body:JSON.stringify(options.body)}:{})})
 const text=await response.text();let body:unknown
 try{body=text?JSON.parse(text):null}catch{throw new ApiError(response.status,'INVALID_API_RESPONSE')}
 if(!response.ok){const error=body as {code?:string;errorCode?:string};throw new ApiError(response.status,error?.code??error?.errorCode??`HTTP_${response.status}`)}
 return body as T
}
