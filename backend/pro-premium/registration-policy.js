function registrationAllowed(uid,record){
 return typeof uid==="string" && uid.length>0 && record?.uid===uid && record.paid===true;
}
module.exports={registrationAllowed};
