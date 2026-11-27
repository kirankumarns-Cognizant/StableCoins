import {test,after} from 'node:test'
import assert from 'node:assert/strict'
import {privatePayment,privateAcceptance} from '../src/lib/settlement.ts'
import {balance,explorer} from '../src/lib/safety.ts'
import {createServer} from 'vite'
import react from '@vitejs/plugin-react'
import React from 'react'
import {renderToStaticMarkup} from 'react-dom/server'
const context={token:{chain:{networkId:'besu-local',chainId:20261125,classification:'PRIVATE_DEMO',genesisHash:'0x'+'a'.repeat(64)},contract:'0x'+'1'.repeat(40),symbol:'DEMOUSD',decimals:6,codeHash:'0x'+'b'.repeat(64)},nativeAsset:{symbol:'BESU_GAS',decimals:18},treasury:'0x'+'2'.repeat(40),feeModel:'EXECUTION_ONLY',policyVersion:'besu-demo-v1'}
const instruction={context,recipient:'0x'+'3'.repeat(40),sourceUsd:'1',tokenAmount:'1',outcome:'TOKEN_WALLET',deliverBy:'2026-11-28T00:00:00Z'}
const quote={review:{recommendationId:'review',preparationId:'prep',quoteId:'quote',quoteVersion:1,providerId:'BESU-SIMULATED',instruction,feeUsd:'0.10',nativeFeeReserve:'0.000000000000001',expiresAt:'2026-11-28T00:00:00Z'},availability:{tokenAvailable:'100',nativeAvailable:'1',preparation:{observation:{}}},terms:{}}
const review={recommendationId:'review',version:2,state:'APPROVED',approvalCount:2,requiredApprovals:2,document:{settlementQuote:quote,quote:null,assessment:null,evidence:null}}
const payment={paymentId:'PAY-X',correlationId:'CORR-X',amount:'1',sourceCurrency:'USD',status:'COMPLIANCE_APPROVED',intent:{settlement:{context},instruction:{requiredOutcome:'TOKEN_WALLET'}}}
const evidence={journals:{status:'AVAILABLE',data:[]},reconciliation:{status:'PENDING'},recommendations:[],decisions:[]}
const privateEvidence={instruction,preparations:[],claims:[],submission:[],receipt:[],closure:[],failure:[],authorization:[],compensation:[],recovery:{eligible:false}}
test('private routing does not reinterpret legacy USDC or use a public quote shape',()=>{
 assert.equal(privatePayment(payment),true);assert.equal(privatePayment({intent:{instruction:{requiredOutcome:'USDC_WALLET'}}}),false)
 assert.equal(privatePayment({},review),true);assert.deepEqual(privateAcceptance(review),{recommendationId:'review',expectedVersion:2,quoteId:'quote',quoteVersion:1});assert.equal(privateAcceptance(null),null)
})
test('private token and gas arithmetic remains separate and exact to one wei',()=>{
 assert.deepEqual(balance([{asset:'DEMOUSD',debit:'1',credit:'1'},{asset:'BESU_GAS',debit:'0.000000000000000002',credit:'0.000000000000000001'}]),{DEMOUSD:'0.000000',BESU_GAS:'0.000000000000000001'})
 assert.equal(explorer(20261125,'0x'+'a'.repeat(64),{chainId:20261125,explorerBaseUrl:'https://sepolia.basescan.org'}),undefined)
})
const server=await createServer({configFile:false,root:process.cwd(),plugins:[react()],server:{middlewareMode:true,watch:null},appType:'custom'})
after(()=>server.close())
const {PrivatePaymentViews}=await server.ssrLoadModule('/src/components/PrivatePaymentViews.tsx')
const render=(tab,extra={})=>renderToStaticMarkup(React.createElement(PrivatePaymentViews,{payment,evidence,privateEvidence,review,journey:{steps:[]},tab,enabled:true,approver:true,act:()=>{},...extra}))
test('all private screens render with a private-only quote and no public explorer links',()=>{
 for(const tab of ['routing','treasury','approvals','journey','blockchain','ledger','payout']){
  const html=render(tab);assert(html.includes('PRIVATE_DEMO'));assert(html.includes('DEMOUSD'));assert(!html.includes('basescan.org'));assert(!html.includes('Execute approved USDC'))
 }
})
test('missing private evidence disables the workflow rather than substituting public data',()=>{
 const html=render('journey',{privateEvidence:null});assert(html.includes('unavailable'));assert(!html.includes('<button'))
})
test('submitted claim prevents a second execution even with approved review',()=>{
 const html=render('journey',{privateEvidence:{...privateEvidence,claims:[{execution_id:'saved'}]}})
 assert.match(html,/<button disabled="">Execute approved private payment/)
})
test('reverted recovery explains control accounting and stays disabled without current authorization evidence',()=>{
 const html=render('ledger');assert(html.includes('does not refund USD'));assert(html.includes('BESU_GAS'));assert.match(html,/<button disabled="">Authorize control release/)
})
