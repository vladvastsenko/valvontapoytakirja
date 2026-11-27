const test=require("node:test");
const assert=require("node:assert/strict");
const {registrationAllowed}=require("./registration-policy");
test("unpaid and missing records denied",()=>{for(const r of [undefined,null,{}, {uid:"a",paid:false},{uid:"a",paid:"true"}]) assert.equal(registrationAllowed("a",r),false);});
test("only verified purchaser UID accepted",()=>{assert.equal(registrationAllowed("a",{uid:"a",paid:true}),true);assert.equal(registrationAllowed("b",{uid:"a",paid:true}),false);});
