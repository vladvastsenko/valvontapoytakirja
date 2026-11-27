const {onCall,HttpsError}=require("firebase-functions/v2/https");
const {defineString}=require("firebase-functions/params");
const {initializeApp}=require("firebase-admin/app");
const {google}=require("googleapis");
const {getFirestore}=require("firebase-admin/firestore");
const {claimToken}=require("./ownership");
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
