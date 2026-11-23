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
- Inspector dropdown in both Valvontapöytäkirja and Tarkastuspöytäkirja editors, visible only when two or more saved profiles exist; one profile is auto-selected on new reports.
- Inspector signature strokes can be drawn in profile settings; legacy inspector settings can be manually copied into a new profile.
- Temporary sync files are excluded from Drive upload.

## Still needed before claiming multiuser completion
- Verify inspectors added on one device appear on another after sync and screen reopening.
- Signature strokes are stored in portable JSON; imported signature image paths are still device-local and need a portable asset strategy.
- Verify Google Drive's Android document picker can open a folder shared from a different Google account; implement a supported alternative if not.
- Test bidirectional sync for profile additions/updates/deletions and avoid overwriting colleagues' data. Existing sync still mirrors the whole app-private files directory, including app_settings.json; this must be reviewed before enabling real group collaboration.
- Test simultaneous edits, data privacy, offline use, logout/account switching and recovery from backups.
- Translate all newly added UI labels into all nine supported languages.
- Run a compilation check and device test before shipping.

## Later documentation
- User manual, Google Drive folder-sharing guide, privacy policy, terms of service.

## User manual requirements (agreed)
- One user may save multiple inspector profiles for different qualifications, certifications, licenses, or inspection purposes, and select the appropriate profile for each report.
- Explain that empty fields should not be printed in exported PDFs; verify this in both report generators before documenting as guaranteed behavior.
- Explain how to add custom fields with user-defined labels and use them for different inspection scenarios; verify which report types support this before documenting scope.
- Profile selection appears only if there are at least two saved inspectors; with one, it is automatic.

## Shared Drive sync safety (2026-11-23)
- Shared data now uses `Tarkastus_Sync/shared_v2` and a new `baseline_shared_v2`; only `customers/*.json`, `customer_sites/*.json`, `reports/*.json`, `tarkastus_reports/*.json`, and their report photo assets are eligible. Exported PDFs retain their separate sync mechanism.
- Personal `app_settings.json`, `settings/`, `inspectors/`, templates, authentication/preferences, and other app-private files are **not** uploaded to the shared folder.
- Personal inspector profiles are **local to the device** at this stage; syncing them privately between a user's own devices still requires a separate authenticated personal backup/restore design. Do not promise personal multi-device sync yet.
- Shared deletions are disabled in the file sync. A missing file on either side is restored from the existing copy. A future explicit, permissioned delete/tombstone design is needed.
- Old `Tarkastus_Sync/data` may contain previously synchronized private information. Sync now refuses to continue if it is nonempty. The folder owner must inspect/back up and remove this legacy folder manually; do not automatically delete it.
- Simultaneous changes to the same file are preserved as conflicts where possible, but there is no server-side atomic compare-and-swap for SAF/DocumentFile. Concurrent multi-device editing still requires real-device tests.
- No APK build or Android/Drive end-to-end tests were run for this change.
