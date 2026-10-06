# Validation — 2026-10-05

```text
BUILD_STATUS=BUILD SUCCESSFUL / COMPILE_TESTED
TEST_STATUS=PASS / UNIT_TESTED / 61 debug + 61 release executions
APK_PATH=D:\Codex\NFC-Card-Lab\app\build\outputs\apk\debug\app-debug.apk
APK_SHA256=65E6142373F0858D7EB27ACE12B85BD29DB0FF487821262D4A8F191A5A88A7D0
PACKAGE_NAME=pl.misieklab.nfccardlab
MIN_SDK=29
COMPILE_SDK=36
TARGET_SDK=36
DEVICE_TESTED=PARTIAL — Samsung SM-S931B / Android 16; controlled erase UID 42 27 04 26 verified

NFC_READER_MODE=DEVICE_TESTED — own tags UID 12 66 FA 25 and 42 27 04 26
NDEF_READ=IMPLEMENTED / COMPILE_TESTED / REQUIRES_DEVICE_TEST
NDEF_WRITE=IMPLEMENTED / BUILDERS UNIT_TESTED / REQUIRES_DEVICE_TEST
NDEF_FORMAT=IMPLEMENTED / COMPILE_TESTED / REQUIRES_DEVICE_TEST
MIFARE_CLASSIC_READ=UNIT_TESTED / DEVICE_TESTED — 64 blocks on UID 42 27 04 26
MIFARE_CLASSIC_AUTH=UNIT_TESTED / DEVICE_TESTED — A+B all 16 sectors on UID 42 27 04 26
MIFARE_CLASSIC_WRITE=UNIT_TESTED / DEVICE_TESTED — controlled data erase on UID 42 27 04 26 only
WRITE_VERIFY=UNIT_TESTED / DEVICE_TESTED — 47/47 zero data blocks; protected bytes preserved
PROTECTED_BLOCKS=BLOCK 0 ALWAYS; TRAILERS DEFAULT / UNIT_TESTED
MEMORY_DUMP=IMPLEMENTED / COMPILE_TESTED / REQUIRES_DEVICE_TEST
EXPORT_JSON=IMPLEMENTED / UNIT_TESTED / DOCUMENT PICKER REQUIRES_DEVICE_TEST
EXPORT_TXT=IMPLEMENTED / UNIT_TESTED / DOCUMENT PICKER REQUIRES_DEVICE_TEST
```

## Executed checks

- `gradlew.bat test`: BUILD SUCCESSFUL, zero failures. Final combined check `test assembleDebug lintDebug` also succeeded.
- `gradlew.bat assembleDebug`: BUILD SUCCESSFUL; final APK rebuilt after the final manifest change.
- `gradlew.bat lintDebug`: BUILD SUCCESSFUL, zero errors, eight warnings. Reports recommend newer pinned dependencies, optional NFC hardware, KTX URI syntax and older-Android backup configuration. NFC is intentionally required. Manifest explicitly sets allowBackup=false, fullBackupContent=false and dataExtractionRules exclusions.
- Test cases: 17 HEX/UID/policy; 11 Classic transaction; 11 NDEF/export; 8 session/detector; Activity smoke at SDK 29 and SDK 36 = 2 executions. This is 49 per build variant, not 98 distinct test cases.
- Reference-tag fixture: Android/Robolectric metadata exposes UID 12 66 FA 25 / 4 B / ATQA 04 00 / SAK 08 / MifareClassic + NfcA + NdefFormatable. It verifies dynamic detector output and memory mapping, not a physical read.
- Transport fakes verify failed auth, stale current data, expired sessions, invalid lengths and protected blocks never reach write; a successful data write must read/authenticate/write/read in sequence.
- Session tests verify shared I/O serialization and close on successful completion, connection failure, tag loss and cancellation.
- NDEF tests inspect actual Android NdefRecord encodings for Text, URI, MIME and External Type and test writability/capacity rejection.
- Export tests inspect actual JSON and TXT with trailer keys redacted.
- APK signature v2 verified by apksigner. ZIP alignment validated by zipalign at 16 KiB. ELF PT_LOAD alignments of packaged graphics native libraries inspected: 16 KiB across packaged ABIs.
- Merged APK permission inspection: NFC plus AndroidX-generated app-signature receiver permission; no Internet/storage permission.
- Source review and pattern scan found no credentials or private-key payloads. Authentication key inputs are never logged or passed to exporters. The reference UID occurs only in test fixtures/documentation.
- No pre-existing NFC Card Lab was found in D:\Codex, the workspace manifest, or the searched user Documents/project paths. Existing repositories and other applications were preserved. The new folder contains all task source changes.

## KNOWN_LIMITATIONS

- Initial build-only scope was REQUIRES_DEVICE_TEST. Subsequent Samsung S25 physical read/authentication, controlled erase, custom NDEF conversion and URI-write evidence is recorded below. Standard NdefFormatable.format, document-picker export and general hardware compatibility remain unverified.
- Android/Robolectric Activity startup at API 36 is UNIT_TESTED, not DEVICE_TESTED and not a visual screen review.
- MIFARE Classic availability and permitted operations depend on the tag, NFC chipset/driver, supplied key and access bits; compilation does not prove S25 write support.
- APDU tab is informational; arbitrary APDUs are not implemented.
- Key A is not reliably readable; key slots are masked. Developer trailer writes verify the selected new authentication key and access/GPB bytes, not both full key byte arrays. A valid access-bit encoding can still lock access.
- NDEF formatting is potentially destructive and uses the stock Android formatter. It can rewrite existing allocation/access structures on compatible Classic tags; raw block protection does not apply to internal standard formatting.
- A physical write interrupted by tag loss may leave partial content. There is no automatic write retry.
- Raw data writes can invalidate an existing NDEF/application payload if a used block is selected; block 0/trailer protection is not proof that arbitrary data is expendable.
- Dump authentication uses one manually supplied selected key per pass. Key dictionaries, attacks, per-sector stored keys and persistent key storage are not implemented.
- MIME and External Type editing accepts UTF-8 content, not arbitrary binary payload files.
- Key text is app-memory-only. Immutable JVM strings cannot be guaranteed zeroed immediately, although parsed temporary key arrays are cleared.
- No previous UI reference image/source was available; the requested AMOLED/navy/cyan technical style was implemented from the supplied description.
- This is a debug-signed APK, not a store release signing setup.
- New project publication requires the user's one-time choice of private repository name and scope; no root repository or unauthorized remote was created.

## Controlled erase — live evidence, 2026-10-05

User explicitly authorized temporary sector-trailer access changes, erasure, preservation of UID/keys, and restoration of original access conditions.
The published deterministic Bambu KDF supports A and B. No brute force, key dictionaries, UID changes or non-public NFC commands were used.
A new gated operation in MEMORY requires Developer mode, Allow sector trailer writes and a typed UID confirmation.
All 16 sectors must pass A+B authentication and complete read/permission preflight before any write. A fsynced app-private JSON backup redacts all trailer key slots.
Temporary controls only alter the data-block access conditions, preserving the trailer's own permissions, GPB and key values. Original controls are restored in finally after each sector.
All 64 blocks were read again after erasure. Block 0 matched the original; all 16 trailer control/GPB fields matched; both original keys authenticated in every sector; all 47 data blocks were zero.
An interruption may leave partial data erasure or temporary access conditions. The app must not claim completion on failure; restoration is attempted even on cancellation and failures are journaled. Keep the tag present throughout.
This operation is limited to 4-byte UID, Classic 1K own Bambu tags, and does not format the tag as NDEF.

Device result:
ERASE VERIFIED — 47 DATA BLOCKS ZERO; KEYS/UID PRESERVED; ACCESS RESTORED
UID: 42 27 04 26
Backup: backups/tag-42270426-erase-20261005/erase-42270426-1791216524485-before.json
After: backups/tag-42270426-erase-20261005/erase-42270426-1791216524485-after.json
Journal: backups/tag-42270426-erase-20261005/erase-42270426-1791216524485-state.txt
All three were copied with binary-safe ADB exec-out and their SHA-256 hashes matched the phone.
Independent JSON comparison verified 47 zero data blocks, unchanged manufacturer block and 16 unchanged trailer controls; 12 key bytes per trailer were redacted.

Tests: test and assembleDebug BUILD SUCCESSFUL; lintDebug BUILD SUCCESSFUL. Twelve additional tests cover HKDF RFC 5869, layout/access encoding, all 47 blocks, full preflight before writes, backup failure, failed auth, immutable permissions, failed data write/readback, cancellation restoration and required authorizations.

NDEF format attempts on UID 12 66 FA 25 failed (I/O / lost or stale session). No successful NDEF format or URI write is claimed.
At the controlled-erase stage the link had not yet been written. The later conversion and successful URI write are recorded below.
## Classic NDEF conversion and URI write — live evidence, 2026-10-05

COMPILE_TESTED: gradlew.bat test assembleDebug lintDebug => BUILD SUCCESSFUL.
UNIT_TESTED: 75 tests per debug/release variant, 0 failures/errors. Fourteen new tests cover the NXP published MAD CRC example, NFC AID ordering/CRC, valid recoverable controls, full conversion, B/manufacturer preservation, publish-last ordering, complete preflight, backup failure, nonzero data rejection, malformed controls, data/MAD failures with rollback, cancellation rollback, lost-tag failure, authorization and TLV bounds.
DEVICE_TESTED: Samsung SM-S931B, Android 16; device serial omitted from shared documentation.
Installed APK SHA256: FC1DDE1DB3DFF1B26A26F5C60B31B56A740B9232373A4997AEEBB87D92BB4AE5.
UID: 42 27 04 26, Classic compatible 1K, 16 sectors, 64 blocks.

User explicitly authorized conversion of the cleared own Bambu tag to NDEF, including sector Key A and access-control changes. No block-0 write, UID change, magic command, brute force or attack was used.
Every original A/B authenticated before conversion. All 64 blocks were backed up before writing, 47 data blocks confirmed cleared. NFC data sectors were configured before publishing MAD. Final 64-block read verified manufacturer bytes, controls, MAD and empty Text TLV; public A and original B authenticated in all sectors.
Sector 0 controls/GPB: 78 77 88 C1; sectors 1–15: 7F 07 88 40. Key A is deliberately changed to standard public MAD/NFC keys; original Key B is preserved and authentication-verified.
Result: CONVERSION RAW VERIFIED.
After actual re-presentation Android detected Ndef, writable YES, capacity 716 B, and a valid empty Text record.
URI https://bitback.pl/ encoded into a 16-byte NDEF message and was written through Ndef.writeNdefMessage; Ndef.ndefMessage read back identical bytes.
Result: NDEF WRITE VERIFIED; observed record URI: https://bitback.pl/.

Evidence folder: backups/tag-42270426-ndef-20261005/
convert-42270426-1791218008542-before.json — SHA256 b777a14bef7c856a0140bb5b20f471aa5ac6cfde8430092e1a6541f93d92b0eb
convert-42270426-1791218008542-after.json — SHA256 7762f43d50e8c126bb0fe9e97baa7f0344929ba7fbf58be3281ccb546c91c818
convert-42270426-1791218008542-state.txt — SHA256 8a571ae5ab2441d3a41d49b2d033edf29aec8aa2d6a6dec180df5a4d4911529a
All three copied with binary-safe ADB exec-out and hashes matched the phone. Independent JSON check confirms identical manufacturer bytes, expected 16 control/GPB fields, authentication verification and redaction of all 12 key bytes per trailer.
ndef-uri-device-evidence.json records the actual UI outcome after the normal Android NDEF URI write/readback. The raw conversion after.json is the empty-Text state before the URI write, not a final URI memory dump.

KNOWN_LIMITATIONS: standard NdefFormatable.format did not succeed on the earlier Bambu tag; no generic format success claimed. This custom converter is for cleared Classic 1K tags with authorized known derived keys only. Physical tag loss can leave partial conversion and defeat rollback; recovery is not automatic. Device test covers this S25 and UID only. A blank NTAG remains an alternative when Classic is unsupported on another phone. APDU remains diagnostic. No actual browser launch test performed.


## Prosty interfejs — 2026-10-05 (kolejny etap)

- `gradlew.bat test assembleDebug lintDebug`: BUILD SUCCESSFUL.
- Debug: 83 tests, 0 failures, 0 errors, 0 skipped.
- Release: 83 tests, 0 failures, 0 errors, 0 skipped.
- Lint: 0 errors; existing dependency/version, UseKtx and NFC-required feature warnings remain. No dependency upgrade in this UI task.
- COMPILE_TESTED=YES; UNIT_TESTED=YES; NEW_UI_DEVICE_TESTED=NO; INSTALL_STATUS=PHONE_OFFLINE.
- ADB `devices -l` and `mdns services`: no connected or advertised phone during this stage.
- Added eight workflow tests: already-NDEF tags cannot enter Bambu preparation, conversion needs matching verified erase UID, unsupported layout/UID rejected, failure does not display success, raw conversion requires re-tap, tag-lost instructions, verified write permits removal, old success cannot permit removal during a busy operation.
- Existing NFC IO, raw-write policy, UID protection, conversion/erase preflight, readback and rollback tests still pass.
- New simple flow requests ownership/discard-data confirmation and separate trailer consent before Bambu erase or conversion. Guided consent is scoped to that operation; advanced write privileges remain disabled unless explicitly enabled separately.
- Previous sources and APK preserved under `backups/before-simple-ui-20261005/`.
- New interface is not yet visually or NFC-tested on Samsung. Historical tag 42 27 04 26 / NDEF URI device results above apply to the previous build; no tag memory changed in this stage.
- Package remains `pl.misieklab.nfccardlab`, minSdk 29, targetSdk/compileSdk 36, no Internet permission.
- APK SHA-256: `9C9EFB71C5E54A9EB4D939C3265DB3EDD9471044D849C904CA4AE581683F1F74`
- APK bytes: 25465693


## Dodaj aplikację — 2026-10-05 (kolejny etap)

- `gradlew.bat test assembleDebug lintDebug`: BUILD SUCCESSFUL.
- Debug: 91 tests, 0 failures, 0 errors, 0 skipped. Release: 91 tests, 0 failures, 0 errors, 0 skipped.
- Added 4 AAR tests on API 29 and 36 (8 executions per variant): exact external type and package payload, full byte round-trip/description, invalid package rejection, full-message capacity including read-only rejection.
- Lint: 0 errors. Existing version/dependency, UseKtx and NFC-required-feature warnings remain.
- COMPILE_TESTED=YES; UNIT_TESTED=YES; APP_PICKER_DEVICE_TESTED=YES.
- Updated existing package on Samsung SM-S931B using `adb install -r --user 0`: Success; Activity launch: ok.
- On-device UI verified: third Application option, Add application button, actual locally installed launcher-app list, search filtering, selecting a result and returning its label and package to the editor. This did not write a tag. Temporary selection of NFC Card Lab used for UI verification is cleared by restarting the app before handoff.
- AAR_TAG_WRITE_DEVICE_TESTED=NO; AAR_RESCAN_LAUNCH_DEVICE_TESTED=NO; both require a physical tag and a user-selected app. Existing NDEF write/readback implementation is reused; earlier URI tag results are not evidence of application launch.
- App inventory is not exported or logged. Picker queries current-profile enabled launcher apps off the main thread and avoids QUERY_ALL_PACKAGES. Package is recorded only as NDEF content after write confirmation.
- NFC connection serialization, tag-session confirmation, capacity/writable checks and protected-block policy remain in force.
- Prior sources and APK preserved under `backups/before-app-record-20261005/`.
- Package: pl.misieklab.nfccardlab; minSdk 29; target/compileSdk 36.
- APK SHA-256: `34B1805003DE9B0FEA805A38C1C3DA051C37C3D1FD9D2B126F0FDC27D97CAF34`
- APK bytes: 25513993


## Aktualizacja dokumentacji — 2026-10-05

- Documentation version 1.1: 10 pages; includes current simple UI, local app selection on each phone, AAR semantics, erase/convert workflow, safety, installation and staged evidence.
- Every rendered page 1–10 was opened and visually reviewed: no clipping, overlaps, missing glyphs or broken tables.
- Bundled python-docx authoring; existing OpenOffice/UNO conversion adapter followed by packaged render_docx rasterization. QA images and PDF are internal under backups/documentation-qa-update-20261005/.
- Current saved test report XML checked: 91 tests each for debug/release; no failures/errors/skips. No new Gradle run required for this documentation-only task.
- Prior DOCX/builder and supporting documentation preserved under backups/before-docs-update-20261005/.
- README and acceptance checklist updated; historical offline/install stage retained as history and later successful phone update explained.
- Application Kotlin/manifest/APK and physical tag memory unchanged by this documentation task. No new phone installation, tag write or application-launch proof claimed.
- DOCX SHA-256: `2AB0C777C8043C58CF6EEEE7608BA69C67DED237AE6D939677AE5F683EFE65D7`


## Prywatne repozytorium GitHub — 2026-10-06

Repository created and visibility verified PRIVATE: https://github.com/iikakaczmarekmichal-sys/NFC-Card-Lab. Initial source publication scope: 44 reviewed files including Gradle Wrapper, Kotlin code, tests, documentation and source manifest. Ignored backups, local.properties, caches, build directories and signing credentials are excluded. Phone serial removed from shared validation text. APK and DOCX are intended as release assets; completion and remote commit are verified separately after upload. Historical pre-Git status in documentation dated 2026-10-05 remains a record of that earlier stage. No new physical NFC test or new build is claimed for publication.


### Zakończenie pierwszej publikacji

Initial commit 0000bb270fa5150c1afe07604a3458701291e717 published to origin/main; local and remote SHA matched. GitHub repository visibility rechecked PRIVATE; default branch main. Repository registered in the existing central projects.json, whose Finish returned SYNCED at cb203950ff08d04db9dccc31c4e8442f72a9689c.

Release v1.0.0 published with app-debug.apk (25,513,993 bytes) and documentation 1.1 (51,698 bytes). GitHub asset digests equal local SHA-256: APK 34b1805003de9b0fea805a38c1c3da051c37c3d1fd9d2b126f0fdc27d97caf34; DOCX 2ab0c777c8043c58cf6eeee7608ba69c67ded237ae6d939677ae5f683efe65d7. Release points to the initial source commit. This later documentation commit changes only README, validation text and source-manifest hashes.

During bootstrap git diff --check detected only blank EOF lines in LabViewModel.kt, LabApp.kt and generate_documentation.py. They were removed; no executable behavior changed and APK remains the previously tested build. No Gradle or device checks repeated solely for GitHub publication. Previous test/device evidence remains scoped as documented above.


## Publiczny dostęp — 2026-10-06

At the owner's explicit request, repository visibility was changed from PRIVATE to PUBLIC and confirmed with GitHub metadata. Earlier private-publication entries are historical. Source, documentation and v1.0.0 APK assets are now available to everyone. Local ignored tag backups, SDK settings and signing credentials remain excluded. No application behavior or physical NFC test changed.

The existing automatic Codex synchronization mechanism only accepts PRIVATE repositories. Its safeguards were not modified; the catalog records this public exception and automatic-private bootstrap is disabled for this project. This task's narrowly scoped README/validation/source-manifest publication follows the owner's explicit public-sharing authorization using normal Git checks, without rewriting history or bypassing Git hooks.
