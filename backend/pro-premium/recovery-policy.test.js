const {test}=require("node:test");
const assert=require("node:assert/strict");
const {nextAttempt}=require("./recovery-policy");
test("five requests per window",()=>{
 let state;
 for(let i=0;i<5;i++) state=nextAttempt(state,1000+i);
 assert.equal(state.count,5);
 assert.equal(nextAttempt(state,2000),null);
 assert.deepEqual(nextAttempt(state,601000),{start:601000,count:1});
});
