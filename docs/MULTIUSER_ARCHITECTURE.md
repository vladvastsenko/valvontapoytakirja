# Tarkastus Cloud — Google Drive collaboration plan

Status: implementation in progress on `auth-registration`. No APK build or device test yet.

## Confirmed product decisions
- Firebase Authentication registration stays.
- No Firestore, company management, internal admin roles, or company membership system.
- Google Drive folder owner grants access to colleagues in Google Drive.
- Shared customer/site records, inspector profiles and reports are intended to sync through the existing Google Drive workflow.
- All report functions remain free. AdMob is deferred; optional ad removal is personal, with pricing decided after real ad revenue is measured.
- Do not modify stable branches.

## Implemented in source
- Serializable InspectorProfile with id, name, certificate number, phone, email and signature fields.
- Inspector profile repository with individual JSON files under `filesDir/inspectors`; files are archived to `Asetukset/Tarkastajat`.
- Inspector profile management in Valvontapöytäkirja settings.
- Inspector dropdown in Valvontapöytäkirja report editor; selected inspector details are copied to report.

## Still needed before claiming multiuser completion
- Add inspector selection to Tarkastuspöytäkirja using the same shared profile records.
- Complete signature image/strokes handling and ensure per-inspector images are portable across devices.
- Verify Google Drive's Android document picker can open a folder shared from a different Google account; implement a supported alternative if not.
- Test bidirectional sync for profile additions/updates/deletions and avoid overwriting colleagues' data.
- Test simultaneous edits, data privacy, offline use, logout/account switching and recovery from backups.
- Translate all newly added UI labels into all nine supported languages.
- Run a compilation check and device test before shipping.

## Later documentation
- User manual, Google Drive folder-sharing guide, privacy policy, terms of service.
