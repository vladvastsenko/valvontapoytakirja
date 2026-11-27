const {test}=require("node:test");
const assert=require("node:assert/strict");
const {tokenKey,claimToken}=require("./ownership");
function database(){
 const records=new Map();
 return {records,collection:()=>({doc:key=>key}),runTransaction:async fn=>fn({
  get:async key=>({exists:records.has(key),data:()=>records.get(key)}),
  create:(key,value)=>{if(records.has(key))throw Error("duplicate");records.set(key,value);}
 })};
}
test("token digest is package scoped",()=>{
 assert.equal(tokenKey("a","token").length,64);
 assert.notEqual(tokenKey("a","token"),tokenKey("b","token"));
});
test("same owner can restore; different owner or product denied",async()=>{
 const db=database();
 assert.equal(await claimToken(db,"pkg","token","owner","pro"),true);
 assert.equal(await claimToken(db,"pkg","token","owner","pro"),true);
 assert.equal(await claimToken(db,"pkg","token","other","pro"),false);
 assert.equal(await claimToken(db,"pkg","token","owner","other"),false);
 assert.equal(db.records.size,1);
 assert.ok(!JSON.stringify([...db.records]).includes('"token"'));
});
