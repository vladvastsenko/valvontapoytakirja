const {onCall,HttpsError}=require("firebase-functions/v2/https");
const {defineString}=require("firebase-functions/params");
const {initializeApp}=require("firebase-admin/app");
const {google}=require("googleapis");
const {getFirestore}=require("firebase-admin/firestore");
const {claimToken}=require("./ownership");
const {getAuth}=require("firebase-admin/auth");
const {createHash,randomBytes}=require("node:crypto");
const {evaluate}=require("./policy");
initializeApp();
const product=defineString("PRO_SUBSCRIPTION_PRODUCT");
exports.verifyTarkastusProSubscription=onCall({region:"europe-west1"},async request=>{
 if(!request.auth) throw new HttpsError("unauthenticated","Sign in required");
 const uid=request.auth.uid, token=request.data?.token;
 if(typeof token!=="string"||token.length<10||token.length>4096) throw new HttpsError("invalid-argument","Invalid token");
 const auth=new google.auth.GoogleAuth({scopes:["https://www.googleapis.com/auth/androidpublisher"]});
 const api=google.androidpublisher({version:"v3",auth});
 const packageName="fi.vladi.tarkastuspro", productId=product.value();
 try {
  const {data}=await api.purchases.subscriptionsv2.get({packageName,token});
  const result=evaluate(data,uid,productId,Date.now());
  if(result.error) throw new HttpsError("permission-denied",result.error);
  if(result.state === "ACTIVE") {
   const db=getFirestore();
   const accepted=await claimToken(db,packageName,token,uid,productId);
   if(!accepted) throw new HttpsError("permission-denied","Purchase already bound");
  }
  if(result.acknowledge) await api.purchases.subscriptions.acknowledge({packageName,subscriptionId:productId,token,requestBody:{}});
  return {uid:result.uid,state:result.state,expiresAtMillis:result.expiresAtMillis};
 } catch(error) {
  if(error instanceof HttpsError) throw error;
  throw new HttpsError("unavailable","Subscription verification unavailable");
 }
});

exports.getTarkastusProCheckoutConfig=onCall({region:"europe-west1"},async request=>{
 if(!request.auth) throw new HttpsError("unauthenticated","Authentication required");
 const productId=product.value();
 if(!productId) throw new HttpsError("failed-precondition","Checkout not configured");
 return {productId};
});

exports.prepareTarkastusProRecovery=onCall({region:"europe-west1"},async request=>{
 if(!request.auth || request.auth.token.firebase?.sign_in_provider!=="anonymous") throw new HttpsError("permission-denied","Anonymous checkout required");
 const uid=request.auth.uid;
 const secret=randomBytes(32).toString("base64url");
 const hash=createHash("sha256").update(secret).digest("hex");
 const ref=getFirestore().collection("proRecovery").doc(hash);
 await ref.create({uid,createdAt:Date.now(),used:false});
 return {secret};
});
exports.recoverTarkastusProAccount=onCall({region:"europe-west1"},async request=>{
 if(!request.auth || request.auth.token.firebase?.sign_in_provider!=="anonymous") throw new HttpsError("permission-denied","Anonymous session required");
 const secret=request.data?.secret, token=request.data?.token;
 if(typeof secret!=="string"||!/^[A-Za-z0-9_-]{43}$/.test(secret)||typeof token!=="string"||token.length<10||token.length>4096) throw new HttpsError("invalid-argument","Invalid recovery credentials");
 const db=getFirestore(), ref=db.collection("proRecovery").doc(createHash("sha256").update(secret).digest("hex"));
 const snap=await ref.get(), record=snap.data();
 if(!record || record.used) throw new HttpsError("permission-denied","Recovery unavailable");
 const user=await getAuth().getUser(record.uid);
 if(user.disabled || user.providerData.length!==0 || user.email || user.phoneNumber) throw new HttpsError("permission-denied","Use existing account sign-in");
 const auth=new google.auth.GoogleAuth({scopes:["https://www.googleapis.com/auth/androidpublisher"]});
 const api=google.androidpublisher({version:"v3",auth});
 const {data}=await api.purchases.subscriptionsv2.get({packageName:"fi.vladi.tarkastuspro",token});
 const result=evaluate(data,record.uid,product.value(),Date.now());
 if(result.error || result.state!=="ACTIVE") throw new HttpsError("permission-denied","Recovery unavailable");
 if(!await claimToken(db,"fi.vladi.tarkastuspro",token,record.uid,product.value())) throw new HttpsError("permission-denied","Recovery unavailable");
 await db.runTransaction(async tx=>{
  const current=(await tx.get(ref)).data();
  if(!current || current.used || current.uid!==record.uid) throw new HttpsError("permission-denied","Recovery unavailable");
  tx.update(ref,{used:true});
 });
 return {customToken:await getAuth().createCustomToken(record.uid)};
});
