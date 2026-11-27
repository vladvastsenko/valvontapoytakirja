function registrationAllowed(uid,record){
 return typeof uid==="string" && uid.length>0 && record?.uid===uid && record.paid===true;
}
function legacyPurchaseAllowed(uid,record,productId){
 return typeof uid==="string" && uid.length>0 && typeof productId==="string" && productId.length>0 &&
 record?.uid===uid && record.packageName==="fi.vladi.tarkastuspro" && record.productId===productId;
}
module.exports={registrationAllowed,legacyPurchaseAllowed};
