# Tarkastus Pro — Google Drive collaboration plan

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
- Shared data now uses `TarkastusPro_Building_Sync/shared_v2` and a new `baseline_shared_v2`; only `customers/*.json`, `customer_sites/*.json`, `reports/*.json`, `tarkastus_reports/*.json`, and their report photo assets are eligible. Exported PDFs retain their separate sync mechanism.
- Personal `app_settings.json`, `settings/`, `inspectors/`, templates, authentication/preferences, and other app-private files are **not** uploaded to the shared folder.
- Personal inspector profiles are **local to the device** at this stage; syncing them privately between a user's own devices still requires a separate authenticated personal backup/restore design. Do not promise personal multi-device sync yet.
- Shared deletions are disabled in the file sync. A missing file on either side is restored from the existing copy. A future explicit, permissioned delete/tombstone design is needed.
- Old `TarkastusPro_Building_Sync/data` may contain previously synchronized private information. Sync now refuses to continue if it is nonempty. The folder owner must inspect/back up and remove this legacy folder manually; do not automatically delete it.
- Simultaneous changes to the same file are preserved as conflicts where possible, but there is no server-side atomic compare-and-swap for SAF/DocumentFile. Concurrent multi-device editing still requires real-device tests.
- No APK build or Android/Drive end-to-end tests were run for this change.

## Additional PDF safety changes
- Exported PDF synchronization no longer deletes the other copy when a file disappears on one device or Drive.
- Migration of legacy `TarkastusPro_Building_Sync/PDF` copies files but does not delete the original folder.
- **Privacy boundary:** Shared report JSON and PDFs intentionally include the inspector identity and signature captured in that report. Private profile/settings files are excluded, but reports are accessible to everyone with Drive-folder access. Limit folder sharing accordingly.
- Pending: SAF provider tests, two-device concurrent write tests, crash recovery, explicit deletion UX and review of filename collisions.

## Interrupted local download recovery
- Downloads are staged as `.sync_tmp`; before replacement the existing file is renamed to `.sync_backup`.
- Failed replacements attempt rollback; the next sync restores a stranded `.sync_backup` if the target is missing.
- Neither temporary nor backup files are uploaded.
- This is **not** a cross-device transaction: SAF Drive writes still need verification and concurrency testing. The restore behavior has not yet been device-tested.

## Concurrent shared-file writes (in progress)
- Before each action, the Drive file is re-read and compared with its hash from the initial scan. A changed remote file causes a conflict instead of an automatic overwrite.
- Shared-data uploads stage bytes in a temporary Drive document, verify SHA-256, and preserve the prior remote bytes as a backup before replacement. Temporary/backup files are excluded from synchronization scans.
- **Important limitation:** DocumentFile/SAF offers no atomic compare-and-swap, locking, or transactional rename. Two devices can still race between the final hash check and replacement. This is risk reduction, **not** a correctness guarantee. For robust concurrent editing, use a server-mediated versioned object store or immutable per-edit versions.
- Backup files created by replacements require retention and recovery tooling; they must not be silently treated as ordinary shared documents.
- No device build or multi-device test was run.

## Immutable report revisions (2026-11-23)
- Before writing a report JSON into `TarkastusPro_Building_Sync/shared_v2/reports` or `tarkastus_reports`, the app saves its SHA-256-addressed version in `TarkastusPro_Building_Sync/shared_v2/Versions/<report-kind>/<report-id>_<sha256>.json`.
- If replacing a remote report, its previous bytes are also saved in Versions before deletion. Each version is checked against its SHA-256 digest.
- Versions is excluded from the normal shared-data file scanner and is not auto-deleted. This preserves history independently of the mutable current report.
- **Limitations:** no revision browsing/restoration UI yet; concurrent createFile races and SAF non-atomic writes can still cause conflicts or provider-specific duplicate filenames. JSON revisions do not by themselves preserve all referenced photos; PDF sync is a separate dataset. Real-device verification is required.

## Optional biometric app lock (2026-11-23)
- AndroidX BiometricPrompt with BIOMETRIC_STRONG; device-only preference scoped to Firebase UID.
- After first authenticated Firebase session, offer opt-in once; users can enable/disable via Settings → Biometrinen lukitus.
- On subsequent cold starts with a persisted Firebase session and lock enabled, the app content remains hidden until successful biometric confirmation. Failed/cancelled prompts allow retry; sign-out is available.
- No biometric templates or fingerprint images are stored by the app. This is an app-unlock gate, **not** a Firebase login credential or cryptographic binding of Firebase refresh tokens.
- No fallback to PIN is currently enabled. If biometrics become unavailable, the user can sign out; recovery/reset behavior and account switching need device tests.
- **Security limitations:** current implementation does not re-lock on background/resume, and other Android activities launched outside the main gate need separate protection review. Biometric UI text is currently Finnish and needs translation across supported UI languages.
- No APK build, emulator run or device test was performed. Test on HONOR Magic V3: first opt-in, dismiss, failed attempt, restart, sign-out, settings switch, changed fingerprints, and account switching.

## Guest mode (2026-11-23)
- Login screen offers `Jatka ilman rekisteröitymistä`; guest choice is persisted locally.
- Guests can use the existing local report editors and local PDF export. The cloud sync/backup screen requires a Firebase user and the management screen hides cloud/biometric controls in guest mode.
- Management offers `Kirjaudu sisään / Rekisteröidy` to return to authentication without deleting local report files.
- Signing in does not automatically upload local reports; explicit cloud synchronization remains required. Before first shared-folder sync, users must review which reports will be shared.
- Guest-mode strings are currently Finnish and need translations. Account transitions and any secondary activities should be tested on device.
- No APK build or device test performed.
