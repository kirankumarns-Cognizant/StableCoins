import {test} from 'node:test'
import assert from 'node:assert/strict'
import {time} from '../src/lib/format.ts'
test('dates display the local zone and offset without changing the stored instant',()=>{
 const previous=process.env.TZ
 try{
  for(const [zone,offset] of [['Asia/Kolkata','GMT+05:30'],['UTC','GMT'],['America/New_York','GMT-05:00']]){
   process.env.TZ=zone
   const rendered=time('2026-11-21T16:20:51Z')
   assert.ok(rendered.includes(offset),rendered)
   assert.ok(rendered.includes('('+Intl.DateTimeFormat().resolvedOptions().timeZone+')'),rendered)
   assert.ok(rendered.includes('2026'),rendered)
  }
  process.env.TZ='America/New_York'
  assert.ok(time('2026-07-21T16:20:51Z').includes('GMT-04:00'))
 }finally{if(previous===undefined)delete process.env.TZ;else process.env.TZ=previous}
})
test('missing and malformed dates are not represented as valid deadlines',()=>{
 for(const value of [null,undefined,''])assert.equal(time(value),'Pending')
 assert.equal(time('not-a-date'),'Unavailable')
})
