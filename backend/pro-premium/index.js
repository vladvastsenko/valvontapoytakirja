const {onCall,HttpsError}=require("firebase-functions/v2/https");
const {defineString}=require("firebase-functions/params");
const {initializeApp}=require("firebase-admin/app");
const {google}=require("googleapis");
const {getFirestore}=require("firebase-admin/firestore");
const {claimToken}=require("./ownership");
const {getAuth}=require("firebase-admin/auth");
const {createHash,randomBytes}=require("node:crypto");
const {nextAttempt}=require("./recovery-policy");
async function throttle(uid,operation){
 const db=getFirestore(),ref=db.collection("proRecoveryLimits").doc(createHash("sha256").update(operation+":"+uid).digest("hex"));
 await db.runTransaction(async tx=>{
  const previous=(await tx.get(ref)).data();
  const next=nextAttempt(previous,Date.now(),operation==="verify"?30:5);
  if(!next) throw new HttpsError("resource-exhausted","Try again later");
  tx.set(ref,next);
 });
}
const {evaluate}=require("./policy");
const {registrationAllowed}=require("./registration-policy");
initializeApp();
const product=defineString("PRO_SUBSCRIPTION_PRODUCT");
exports.verifyTarkastusProSubscription=onCall({region:"europe-west1"},async request=>{
 if(!request.auth) throw new HttpsError("unauthenticated","Sign in required");
 const uid=request.auth.uid, token=request.data?.token;
 if(typeof token!=="string"||token.length<10||token.length>4096) throw new HttpsError("invalid-argument","Invalid token");
 await throttle(uid,"verify");
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
  if(result.state === "ACTIVE") {
   const ref=getFirestore().collection("proRegistration").doc(uid);
   await ref.set({uid,paid:true},{merge:true});
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
 if(!request.auth || !["anonymous","custom"].includes(request.auth.token.firebase?.sign_in_provider)) throw new HttpsError("permission-denied","Anonymous checkout required");
 const uid=request.auth.uid;
 const user=await getAuth().getUser(uid);
 if(user.disabled || user.providerData.length || user.email || user.phoneNumber) throw new HttpsError("permission-denied","Existing account");
 await throttle(uid,"prepare");
 const secret=randomBytes(32).toString("base64url");
 const hash=createHash("sha256").update(secret).digest("hex");
 const ref=getFirestore().collection("proRecovery").doc(hash);
 await ref.create({uid,createdAt:Date.now(),used:false});
 return {secret};
});
exports.recoverTarkastusProAccount=onCall({region:"europe-west1"},async request=>{
 if(!request.auth || !["anonymous","custom"].includes(request.auth.token.firebase?.sign_in_provider)) throw new HttpsError("permission-denied","Anonymous session required");
 const requester=await getAuth().getUser(request.auth.uid);
 if(requester.disabled || requester.providerData.length || requester.email || requester.phoneNumber) throw new HttpsError("permission-denied","Existing account");
 await throttle(request.auth.uid,"recover");
 const secret=request.data?.secret, token=request.data?.token;
 if(typeof secret!=="string"||!/^[A-Za-z0-9_-]{43}$/.test(secret)||typeof token!=="string"||token.length<10||token.length>4096) throw new HttpsError("invalid-argument","Invalid recovery credentials");
 const db=getFirestore(), ref=db.collection("proRecovery").doc(createHash("sha256").update(secret).digest("hex"));
 await throttle(createHash("sha256").update(secret).digest("hex"),"recover-code");
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
  const now=Date.now();
  if(typeof current.lastIssuedAt==="number" && now-current.lastIssuedAt<60000) throw new HttpsError("resource-exhausted","Try again later");
  tx.update(ref,{lastIssuedAt:now});
 });
 return {customToken:await getAuth().createCustomToken(record.uid)};
});

exports.finishTarkastusProRecovery=onCall({region:"europe-west1"},async request=>{
 if(!request.auth) throw new HttpsError("unauthenticated","Sign in required");
 await throttle(request.auth.uid,"finish");
 const secret=request.data?.secret;
 if(typeof secret!=="string"||!/^[A-Za-z0-9_-]{43}$/.test(secret)) throw new HttpsError("invalid-argument","Invalid recovery credentials");
 const user=await getAuth().getUser(request.auth.uid);
 if(!user.providerData.some(p=>p.providerId==="password"||p.providerId==="google.com")) throw new HttpsError("failed-precondition","Complete registration first");
 const db=getFirestore(),ref=db.collection("proRecovery").doc(createHash("sha256").update(secret).digest("hex"));
 await db.runTransaction(async tx=>{
  const record=(await tx.get(ref)).data();
  if(!record||record.uid!==request.auth.uid) throw new HttpsError("permission-denied","Recovery unavailable");
  tx.update(ref,{used:true});
 });
 return {ok:true};
});

// Requires Firebase Authentication with Identity Platform. Anonymous checkout remains free.
const {beforeUserCreated,beforeUserSignedIn}=require("firebase-functions/v2/identity");
exports.blockUnpaidProRegistration=beforeUserCreated({region:"europe-west1"},event=>{
 const user=event.data;
 if(!user || user.email || user.phoneNumber || (user.providerData||[]).length) {
  throw new HttpsError("permission-denied","Complete verified checkout before linking an account");
 }
});

 // Existing Google accounts only. Never create or link an identity in this endpoint.
 exports.signInExistingTarkastusProGoogle=onCall({region:"europe-west1"},async request=>{
  const token=request.data?.idToken;
  if(typeof token!=="string"||token.length<100||token.length>8192) throw new HttpsError("invalid-argument","Invalid credential");
  const audience=defineGoogleAudience.value();
  if(!audience) throw new HttpsError("failed-precondition","Google sign-in not configured");
  try {
   const ticket=await new google.auth.OAuth2().verifyIdToken({idToken:token,audience});
   const payload=ticket.getPayload();
   if(!payload?.sub || payload.email_verified!==true) throw new Error("Invalid identity");
   const user=await getAuth().getUserByProviderUid("google.com",payload.sub);
   if(user.disabled || !registrationAllowed(user.uid,(await getFirestore().collection("proRegistration").doc(user.uid).get()).data())) throw new Error("Unapproved account");
   await throttle(user.uid,"google-login");
   return {customToken:await getAuth().createCustomToken(user.uid)};
  } catch(error) {
   throw new HttpsError("permission-denied","Existing Google account required");
  }
 });
 const defineGoogleAudience=defineString("PRO_GOOGLE_WEB_CLIENT_ID");

exports.blockUnpaidProSignIn=beforeUserSignedIn({region:"europe-west1"},async event=>{
 const user=event.data;
 if(!user?.uid) throw new HttpsError("permission-denied","Invalid account");
 const record=(await getFirestore().collection("proRegistration").doc(user.uid).get()).data();
 if(!registrationAllowed(user.uid,record)) throw new HttpsError("permission-denied","Verified purchase required");
});
