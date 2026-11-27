import type {Payment,Recommendation} from './model'
export function privatePayment(payment:Payment,review?:Recommendation|null):boolean {
 return payment.intent?.settlement?.context.token.chain.classification==='PRIVATE_DEMO'||Boolean(review?.document.settlementQuote)
}
export function privateAcceptance(review:Recommendation|null){
 const q=review?.document.settlementQuote?.review
 return review&&q?{recommendationId:review.recommendationId,expectedVersion:review.version,quoteId:q.quoteId,quoteVersion:q.quoteVersion}:null
}
