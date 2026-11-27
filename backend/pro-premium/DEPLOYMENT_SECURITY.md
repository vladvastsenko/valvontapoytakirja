# Pro-only Firestore deployment
Deploy these deny-all client rules exclusively to a dedicated Tarkastus Pro Firebase project, never Cloud or a shared database. Admin SDK access is governed by IAM. Verify project ID and existing rules before deployment; overlapping allow rules must not grant access to proPurchaseOwners. No client reads or writes are required.
 
A verified purchase is bound to the original Firebase UID. Linking email/password to the anonymous account preserves UID. After reinstall, sign in with that linked account before restoring Play purchases. An unlinked lost anonymous UID must not be automatically reassigned merely on possession of a purchase token. Until a separately authenticated recovery mechanism exists, handle such cases through verified support/refund. Do not promise automatic recovery before registration.
 
Run npm test for policy and ownership tests. Test deployed rules with the Firestore emulator for unauthenticated, anonymous, owner and unrelated authenticated clients; every direct read/write must fail. Verify Admin SDK transaction succeeds. No rules have been deployed by this commit.
