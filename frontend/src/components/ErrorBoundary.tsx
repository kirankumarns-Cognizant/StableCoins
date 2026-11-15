import {Component} from 'react'
import type {ReactNode} from 'react'
/** Prevent malformed or incompatible evidence from producing a blank payment screen. */
export class ErrorBoundary extends Component<{children:ReactNode},{failed:boolean}>{
 state={failed:false}
 static getDerivedStateFromError(){return {failed:true}}
 render(){return this.state.failed?<main><h1>Application evidence could not be displayed</h1><p>Check the public connection configuration and API contract, then reload. Payment commands are never retried automatically.</p><button onClick={()=>location.reload()}>Reload application</button></main>:this.props.children}
}
