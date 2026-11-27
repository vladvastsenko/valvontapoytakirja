const {test}=require("node:test");
const assert=require("node:assert/strict");
const {accountId,evaluate}=require("./policy");
const now=1700000000000;
const fixture=(state="ACTIVE",expiry=now+900000)=>({
 externalAccountIdentifiers:{obfuscatedExternalAccountId:accountId("owner")},
 lineItems:[{productId:"pro",expiryTime:new Date(expiry).toISOString()}],
 subscriptionState:"SUBSCRIPTION_STATE_"+state,
 acknowledgementState:"ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED"
});
const check=d=>evaluate(d,"owner","pro",now);
test("active lease is bounded",()=>assert.equal(check(fixture()).expiresAtMillis,now+300000));
test("wrong account denied",()=>assert.ok(evaluate(fixture(),"other","pro",now).error));
test("wrong product denied",()=>assert.ok(evaluate(fixture(),"owner","other",now).error));
test("missing binding denied",()=>assert.ok(check({...fixture(),externalAccountIdentifiers:{}}).error));
for(const [input,expected] of [["ACTIVE","ACTIVE"],["IN_GRACE_PERIOD","ACTIVE"],["CANCELED","ACTIVE"],["PENDING","PENDING"],["ON_HOLD","SUSPENDED"],["PAUSED","SUSPENDED"],["EXPIRED","EXPIRED"],["PENDING_PURCHASE_CANCELED","EXPIRED"],["OTHER","UNKNOWN"]]){
 test(input,()=>{const r=check(fixture(input));assert.equal(r.state,expected);if(expected!=="ACTIVE")assert.equal(r.expiresAtMillis,0);});
}
test("expiry boundary",()=>assert.equal(check(fixture("ACTIVE",now)).state,"EXPIRED"));
test("short lease respects expiry",()=>assert.equal(check(fixture("ACTIVE",now+1)).expiresAtMillis,now+1));
test("pending acknowledgement",()=>assert.equal(check({...fixture(),acknowledgementState:"ACKNOWLEDGEMENT_STATE_PENDING"}).acknowledge,true));
test("unknown acknowledgement fails closed",()=>assert.equal(check({...fixture(),acknowledgementState:undefined}).state,"UNKNOWN"));
