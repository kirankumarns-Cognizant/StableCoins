/** Public connection settings, supplied by Vite from the shared configuration.
 * Identity endpoints are public configuration; tokens stay in memory.
 */
export interface PublicConnections {
  readonly apiBaseUrl: string
  readonly blockchain: {readonly chainId:number;readonly explorerBaseUrl:string}
  readonly identity: {
    readonly issuerUri: string
    readonly authorizationUri: string
    readonly tokenUri: string
    readonly clientId: string
    readonly redirectUri: string
    readonly scopes: string
  }
}
declare const __APP_CONNECTIONS__: PublicConnections
export const connections: PublicConnections = Object.freeze({
  ...__APP_CONNECTIONS__, blockchain: Object.freeze(__APP_CONNECTIONS__.blockchain), identity: Object.freeze(__APP_CONNECTIONS__.identity),
})
