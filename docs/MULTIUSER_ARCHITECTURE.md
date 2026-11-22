# Tarkastus Cloud — company accounts and collaboration (design baseline)

Status: design, not deployed. Branch: auth-registration. Existing stable branches and existing local/Drive data must not be modified.

## Decisions
- One Firebase Auth user (uid) belongs to at most one company.
- A company has one administrator and at most five total active members, including the administrator.
- No personal exemptions, paywall, or reduced report functionality.
- Both report types remain available to every member.
- Company logo, customers, sites and published PDFs are shared. Inspector profile and signature remain per-user.
- Subscription state is company-wide, future use only. AdMob and billing are deferred.
- Existing device backup and SAF-based Google Drive sync are **not** company authorization mechanisms.

## Firestore proposed data
- `companies/{companyId}`: name, ownerUid, createdAt, memberLimit=5, subscriptionStatus='none'.
- `memberships/{uid}`: companyId, role ('admin'|'member'), status ('active'), joinedAt. Document id = Firebase Auth uid; prevents a user having two memberships.
- `companies/{companyId}/members/{uid}`: role, displayName, email, joinedAt.
- `companies/{companyId}/invites/{inviteId}`: normalizedEmail, status, createdBy, createdAt, expiresAt; invite tokens must be stored as hashes if used.
- `companies/{companyId}/customers/{id}`, `sites/{id}`, `reports/{id}`: future shared records with authorUid, createdAt, updatedAt and companyId.
- `companies/{companyId}/pdfs/{id}`: metadata only; binary PDFs need Firebase Storage or a company-controlled storage provider.

## Critical authorization
- Firebase Authentication alone does not protect Firestore or Google Drive content.
- Never trust role, companyId, member count, or subscription status supplied by an Android client.
- Company creation, invitations, joining, leaving and admin transfer must be atomic, server-validated operations (e.g. Cloud Functions + Firestore transactions).
- Enforce the five-person cap in the join transaction; never only in the UI.
- Firestore Security Rules: members can read only their own company; member writes only to permitted shared records; admin-only changes to company identity, invites and membership. Deny all unspecified paths.
- Firebase Storage Rules (if adopted): authorize read/write against the authenticated user's membership and companyId; separate PDF write from read permissions.
- Invite acceptance must verify the signed-in user's verified email matches the invitation, with expiry and one-time use.
- Disable cross-company data access even when a user guesses document IDs. Do not store unprotected sensitive records in public URLs.
- Do not synchronize private per-user settings/signatures into company-wide folders.

## Migration strategy
1. Keep existing local report repository, exports and Drive backups untouched.
2. Add Firestore and backend rules/functions with tests before exposing a company UI.
3. Add onboarding: create company / accept invitation / pending invitation.
4. Add company management UI: members, invitations, company logo.
5. Implement explicit shared customer/site sync with conflict and ownership policies.
6. Publish PDF copies to company storage without deleting local copies; enable all members to view PDFs.
7. Verify two devices, two different accounts, five-person limit, denied sixth member, invalid invites, logout/account switching, offline behavior and data retention.

## Documentation for release
- End-user instructions (both report types, photo/PDF workflow, backup, language selection).
- Administrator instructions (company creation, invitation, permissions, shared branding).
- Privacy policy (Firebase, storage, advertising, retention, deletion, support contact).
- Terms of service (roles, acceptable use, responsibility for report content, availability, termination).
- Later: AdMob and Google Play Billing terms; company-wide ad-free subscription with no feature restrictions.

## Open implementation issue
Firebase project must have Firestore enabled and appropriate security rules/functions deployed. GitHub source commits alone cannot deploy the Firebase backend or prove production security.
