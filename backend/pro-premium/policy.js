"use strict";
const crypto = require("node:crypto");
const accountId = uid => crypto.createHash("sha256").update(uid).digest("hex");
function evaluate(data, uid, product, now) {
  if (!uid || !product || data?.externalAccountIdentifiers?.obfuscatedExternalAccountId !== accountId(uid))
    return {error:"Account mismatch"};
  const items = (data.lineItems || []).filter(x => x.productId === product);
  if (!items.length) return {error:"Product mismatch"};
  const expiry = Math.max(...items.map(x => Date.parse(x.expiryTime) || 0));
  const states = {
    SUBSCRIPTION_STATE_ACTIVE:"ACTIVE", SUBSCRIPTION_STATE_IN_GRACE_PERIOD:"ACTIVE",
    SUBSCRIPTION_STATE_CANCELED:"ACTIVE", SUBSCRIPTION_STATE_PENDING:"PENDING",
    SUBSCRIPTION_STATE_ON_HOLD:"SUSPENDED", SUBSCRIPTION_STATE_PAUSED:"SUSPENDED",
    SUBSCRIPTION_STATE_EXPIRED:"EXPIRED", SUBSCRIPTION_STATE_PENDING_PURCHASE_CANCELED:"EXPIRED"
  };
  let state = states[data.subscriptionState] || "UNKNOWN";
  if (state === "ACTIVE" && expiry <= now) state = "EXPIRED";
  const ack = data.acknowledgementState;
  if (state === "ACTIVE" && !["ACKNOWLEDGEMENT_STATE_PENDING","ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED"].includes(ack)) state = "UNKNOWN";
  return {uid,state,expiresAtMillis: state === "ACTIVE" ? Math.min(expiry,now+300000) : 0,
    acknowledge:state === "ACTIVE" && ack === "ACKNOWLEDGEMENT_STATE_PENDING"};
}
module.exports = {accountId,evaluate};
