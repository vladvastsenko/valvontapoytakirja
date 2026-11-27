const test=require("node:test");
const assert=require("node:assert/strict");
const {registrationAllowed}=require("./registration-policy");
test("unpaid and missing records denied",()=>{for(const r of [undefined,null,{}, {uid:"a",paid:false},{uid:"a",paid:"true"}]) assert.equal(registrationAllowed("a",r),false);});
test("only verified purchaser UID accepted",()=>{assert.equal(registrationAllowed("a",{uid:"a",paid:true}),true);assert.equal(registrationAllowed("b",{uid:"a",paid:true}),false);});

const {legacyPurchaseAllowed}=require("./registration-policy");
test("legacy migration requires server-owned Pro purchase for same UID and product",()=>{
 const record={uid:"a",packageName:"fi.vladi.tarkastuspro",productId:"monthly"};
 assert.equal(legacyPurchaseAllowed("a",record,"monthly"),true);
 assert.equal(legacyPurchaseAllowed("b",record,"monthly"),false);
 assert.equal(legacyPurchaseAllowed("a",record,"other"),false);
 assert.equal(legacyPurchaseAllowed("a",{...record,packageName:"fi.vladi.tarkastuscloud"},"monthly"),false);
 assert.equal(legacyPurchaseAllowed("a",null,"monthly"),false);
});
