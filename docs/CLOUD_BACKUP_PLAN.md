# Tarkastus cloud backup — implementation plan

## Safety boundary
- This work is isolated on branch `cloud-backup-drive`, created from the tested `tarkastus` branch.
- Do not commit cloud work to `tarkastus` or merge it until the user tests and explicitly approves it.
- Keep the current stable APK/source behavior unchanged.

## Initial scope: one user, free Google Drive storage
- No team accounts, Firebase, paid backend, or web portal in this phase.
- Keep local-first operation: reports remain usable without internet.
- Provide a user-selected Google Drive destination through Android's Storage Access Framework where supported; do not embed service-account secrets or ask the user to share a Google password.
- Backup must include both report modules' source JSON, photos (including annotated versions), templates, customers/sites, and shared app settings/logo.
- Restore must be explicit, validate the archive before replacing local files, and create a safety copy of current local data before restore.
- Never silently delete cloud backups when a local report is deleted.
- PDF exports remain ordinary PDF files and should be included in the backup/archive workflow or separately uploaded to a user-visible Drive folder.

## Implementation sequence
1. Confirm a reliable Google Drive document-provider workflow on Android and persisted access behavior.
2. Add an isolated backup/restore service and archive format with versioning and integrity checks.
3. Add a small cloud-backup settings screen: choose Drive folder, backup now, last backup/status, restore.
4. Add optional automatic backup when online; keep manual backup available.
5. Build and test on device before considering any merge.

## Current app storage observations
- Unified launcher module: `:app`.
- Valvontapöytäkirja data: `context.filesDir/reports`, templates: `context.filesDir/templates`.
- Tarkastuspöytäkirja data: `context.filesDir/tarkastus_reports`, templates: `context.filesDir/tarkastus_templates`.
- Both modules also store settings, customers, sites and report photos in app-private storage.
- Existing visible archive: `Documents/TarkastusPro`; PDF exports are written under report-type subfolders.
- Do not treat the visible Documents archive as a complete backup: it does not contain all private photo/source data.

## Future compatibility
Keep the backup format versioned and separate from any future multi-user/team sync design. A later team implementation may add accounts, roles, common logo and shared PDF access without changing the local report format unnecessarily.
