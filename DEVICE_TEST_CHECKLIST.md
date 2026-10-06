# Samsung S25 / own tag acceptance checks

Status updated 2026-10-05: mixed DEVICE_TESTED / REQUIRES_DEVICE_TEST.

Already recorded: installation and updates on Samsung S25 SM-S931B; Reader Mode and recognition of the two owned tags; controlled erase and conversion of UID 42 27 04 26; NDEF URI write/readback of https://bitback.pl/ on an earlier APK. On the latest APK, Add application local list, search and selection were verified on-device. These results do not prove every check below.

Not yet recorded: APPLICATION/AAR write and launch after re-tap; actual browser launch; generic SAF export picker, generic data-block write UI and standard NdefFormatable success on a compatible blank tag. The numbered checks below are an acceptance plan, not a claim that they all passed.

1. Install the debug APK, enable NFC, unlock S25 and open NFC Card Lab.
2. With your reference tag, confirm: MIFARE Classic compatible; UID 12 66 FA 25; 4 B; ISO/IEC 14443-A; ATQA 04 00; SAK 08; technologies MifareClassic / NfcA / NdefFormatable. Record dynamic size/count/type values; do not assume silicon identity.
3. Enter a possessed six-byte Key A or Key B. Authenticate a selected sector. Also confirm a deliberately wrong owned-tag key reports AUTH FAILED without a crash.
4. READ ALL ACCESSIBLE; review accessible block bytes and failed sectors. Export TXT and JSON before changing contents. Confirm no key editor value or trailer key slots appear in either export.
5. Confirm block 0 and every sector trailer are blocked by default. Confirm block 0 remains blocked after enabling developer settings. Do not write a trailer merely to test the button.
6. Choose a data block you know is expendable and not used by your tag's application or NDEF structures. Read it, record original bytes, enter 16 test bytes, review Current/New and confirm WRITE. Expect WRITE VERIFIED only after a full 16-byte comparison.
7. Remove the tag during read, authentication and during a disposable-tag write. Expect a TAG LOST or I/O message without a crash. Re-read contents before any retry; writes can partially complete.
8. Switch to a different tag while a confirmation is pending; verify the stale confirmation cannot write. Background the app and resume; verify key input / developer permissions are cleared and a re-tap is required.
9. On a compatible expendable NDEF tag write Text and URI, then re-tap and inspect the actual records. Repeat with MIME / External Type if needed. Try a read-only tag and an oversized message; writes must be blocked.
10. Format only a compatible expendable blank NdefFormatable tag. Confirm the warning; keep it present until the command finishes; remove/re-tap the same tag. Expect Ndef to appear before FORMAT VERIFIED is reported. Formatting is destructive to existing memory structures.
11. Confirm APDU displays IsoDep diagnostics only and reports unavailable tech accurately.
12. Assess HEX readability, navigation, keyboard, document picker and one-handed operation on the actual S25 display.

A sector trailer operation is a separate advanced acceptance test on an expendable tag with known access conditions and recovery knowledge. It is not required to validate safe data-block writes.


## Prosty widok i Dodaj aplikację

13. Open Zapis → Aplikacja → Dodaj aplikację. Confirm this device's enabled launcher apps appear, search by display name/package, and choose the app you actually want to launch. Repeat on another phone with its own different app list; no predefined inventory is required.
14. Hold an owned writable NDEF tag. Confirm the selected app label/package and target UID before overwriting its message. Verify capacity validation includes the complete AAR message. Keep the tag present until Zapisano i sprawdzono.
15. Re-tap in NFC Card Lab and verify Aplikacja: selected.package, then exit to the home screen, remove/re-present to the unlocked NFC-enabled phone. Record whether the selected app opens, including any system prompt. This is REQUIRES_DEVICE_TEST.
16. Only when useful, test the same tag on an owned Android phone without that app. Record actual Google Play/fallback behavior; do not infer it from compilation. One tag references one package.
17. Test simple Przygotuj gating on an expendable owned tag: already NDEF goes to Zapis; matching verified erase UID is required for guided Bambu conversion; changing UID invalidates confirmation. Ownership/discard-data and sector-setting consent are separate. Never write block 0 for a UI test.

Latest build evidence: 91 tests per debug/release variant, no failures/errors/skips; lint has no errors. Unit results do not establish physical launch behavior.
