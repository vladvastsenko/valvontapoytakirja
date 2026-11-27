"use strict";
const {createHash}=require("node:crypto");
function tokenKey(packageName,token) {
 return createHash("sha256").update(packageName).update("\0").update(token).digest("hex");
}
async function claimToken(db,packageName,token,uid,productId) {
 const ref=db.collection("proPurchaseOwners").doc(tokenKey(packageName,token));
 return db.runTransaction(async tx=>{
  const snapshot=await tx.get(ref);
  if(snapshot.exists) {
   const owner=snapshot.data();
   return owner.uid===uid && owner.productId===productId;
  }
  tx.create(ref,{uid,productId,packageName});
  return true;
 });
}
module.exports={tokenKey,claimToken};
