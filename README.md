# NFC Card Lab

## GitHub i pobranie aplikacji

Repozytorium: https://github.com/iikakaczmarekmichal-sys/NFC-Card-Lab — PUBLIC. Kod, dokumentacja i APK są dostępne dla wszystkich.

Aktualne APK i dokument Word są dostępne w [wydaniu v1.0.0](https://github.com/iikakaczmarekmichal-sys/NFC-Card-Lab/releases/tag/v1.0.0).

- [Pobierz APK](https://github.com/iikakaczmarekmichal-sys/NFC-Card-Lab/releases/download/v1.0.0/app-debug.apk)
- [Pobierz dokumentację Word](https://github.com/iikakaczmarekmichal-sys/NFC-Card-Lab/releases/download/v1.0.0/NFC_Card_Lab_Dokumentacja_projektu_2026-10-05.docx)

 Pobieranie projektu i plików wydania nie wymaga zaproszenia ani konta GitHub. APK jest wersją debug, nie wydaniem Google Play. Projekt zawiera źródła, testy, Gradle Wrapper i dokumentację; kopie odczytów własnych tagów, katalogi build/cache, ustawienia SDK i klucze podpisu nie są publikowane.

Dokument Word z 5 października opisuje stan przed utworzeniem repozytorium. Publikację rozpoczęto 6 października 2026; aktualne informacje o GitHub znajdują się w tym README oraz VALIDATION.md.


## Aktualna dokumentacja projektu

`docs/NFC_Card_Lab_Dokumentacja_projektu_2026-10-05.docx` — wersja 1.1, 10 stron, sprawdzony układ każdej strony. Zawiera obsługę Tag/Zapis/Przygotuj, opcję Dodaj aplikację z lokalnej listy każdego telefonu, schemat rozpoznania/czyszczenia/konwersji, ochronę struktury, budowanie i instalację, wyniki 91 testów na wariant oraz rozróżnienie prób urządzenia i funkcji wymagających dalszej walidacji.

Źródło dokumentu: `docs/generate_documentation.py`. Szczegółowe dowody i ograniczenia: `VALIDATION.md`; plan pozostałych prób: `DEVICE_TEST_CHECKLIST.md`. Wcześniejszy DOCX i źródła dokumentacji zachowano w `backups/before-docs-update-20261005/`. Aktualny APK jest wersją z Dodaj aplikację, zainstalowaną na S25; lista, wyszukiwanie i wybór zostały sprawdzone. Dokumentowanie nie zmieniło aplikacji ani pamięci tagów.


## Dodaj aplikację — 2026-10-05

W **Zapis → Aplikacja → Dodaj aplikację** użytkownik wybiera z listy aplikacji zainstalowanych na swoim telefonie. Lista jest odczytywana lokalnie przy otwarciu okna, z wyszukiwaniem nazwy lub pakietu. Nie jest to ustalona z góry lista naszych aplikacji. Każdy użytkownik może wybrać inną aplikację i zapisać ją na swoim tagu. Listę można otworzyć jeszcze przed przyłożeniem taga; zapis pozostaje zablokowany bez aktywnego, zapisywalnego taga NDEF i prawidłowej pojemności.

Na tag zapisywany jest jeden standardowy Android Application Record (`NdefRecord.createApplicationRecord`) z nazwą pakietu, nie plik APK. Zapis zastępuje poprzednią wiadomość NDEF, wymaga potwierdzenia dla bieżącej sesji i jest sprawdzany ponownym odczytem całej wiadomości. W odczycie rekord pojawia się jako **Aplikacja: nazwa.pakietu**. Tryb zaawansowany też obsługuje APPLICATION z ręcznym wpisaniem pakietu.

Po zapisaniu wyjdź z NFC Card Lab, odsuń tag i przyłóż go do odblokowanego telefonu z włączonym NFC. Uruchomieniem aplikacji zajmuje się Android. Jeżeli wybranej aplikacji nie ma na drugim telefonie, system może skierować do Google Play. Jeden tag wskazuje jeden zapisany pakiet; nie jest to mechanizm przypisywania różnych aplikacji do tego samego taga zależnie od telefonu. Wybór aplikacji na każdym telefonie dotyczy zapisu własnego taga.

Lista obejmuje aktywne aplikacje z kategorią LAUNCHER w bieżącym profilu. Aplikacje ukryte, zawieszone, bez aktywności uruchamiającej lub z innego profilu mogą nie być widoczne. Dodano wyłącznie deklarację widoczności MAIN/LAUNCHER w `<queries>`, bez QUERY_ALL_PACKAGES, Internetu i nowych uprawnień. Ochrona Block 0 i kontrola trailerów nie zmieniają się.

Dokumentacja Android: https://developer.android.com/develop/connectivity/nfc/nfc oraz https://developer.android.com/training/package-visibility/declaring.


## Prosty interfejs — 2026-10-05

Aplikacja uruchamia się domyślnie w polskim trybie prostym. Ma trzy zakładki:

- **Tag** — przyłóż tag, zobacz jego typ, identyfikator oraz odczytany tekst lub link.
- **Zapis** — wybierz Link lub Tekst, wpisz treść i naciśnij **Zapisz i sprawdź**. Potwierdź zastąpienie obecnej wiadomości i trzymaj tag do zakończenia weryfikacji.
- **Przygotuj** — tag już mający NDEF przechodzi bezpośrednio do zapisu. Dla własnego taga z filamentu Bambu: rozpoznanie, osobno potwierdzone czyszczenie, osobno potwierdzona konwersja, ponowne przyłożenie i wykrycie NDEF. Inny pusty tag NdefFormatable może korzystać ze standardowego formatowania Androida.

Ustawienia → **Tryb zaawansowany** przywracają oryginalne SCAN, DETAILS, MEMORY, WRITE, NDEF, APDU i LOG, edytor HEX, ręczne klucze oraz eksport. Wybór interfejsu jest zapamiętywany; klucze nie są zapisywane. Tryb zaawansowany nie daje sam w sobie zgody na zmianę trailerów.

Przygotowanie Bambu wymaga potwierdzenia własności, utraty danych filamentu oraz osobnej zgody na ustawienia sektorów. Typ Classic 1K nie dowodzi pochodzenia Bambu; istniejący preflight sprawdza dostęp przed zmianami. Blok 0 pozostaje chroniony. Konwersja w trybie prostym wymaga sprawdzonego czyszczenia tego samego UID i ponownie sprawdza zawartość pamięci. Kopie odczytu z ukrytymi polami kluczy pozostają w prywatnym katalogu aplikacji; nie są pełną kopią kluczy ani gwarancją odtworzenia pierwotnej funkcji.

Po zapisie linku wyjdź z NFC Card Lab, odsuń tag i ponownie przyłóż go do odblokowanego telefonu z włączonym NFC. Obsługa linku zależy od Androida i domyślnej przeglądarki.

Aktualny prosty interfejs: COMPILE_TESTED, UNIT_TESTED. Po tym etapie zainstalowano aktualizację i sprawdzono interfejs na S25. Próby fizycznego czyszczenia i URI dotyczą wcześniejszego APK; zapis AAR i otwarcie aplikacji po przyłożeniu nie są jeszcze potwierdzone. Dokument Word w wersji 1.1 opisuje aktualny prosty interfejs, Dodaj aplikację oraz zachowany tryb zaawansowany.


Native Kotlin / Jetpack Compose / Material 3 NFC diagnostics for Samsung Galaxy S25, Android 16.
Package: `pl.misieklab.nfccardlab`. minSdk 29; compileSdk / targetSdk 36.
API 36 was the highest installed non-preview Android SDK platform at the initial audit (2026-10-05).

## Open and build

Open this folder in Android Studio. Use Android Studio's bundled JDK (17 or newer) and install Android SDK Platform 36 / Build Tools 36.0.0.
Android Studio generates `local.properties` for the selected SDK. This machine-specific file is excluded from source snapshots and Git.

Windows PowerShell:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat test
.\gradlew.bat assembleDebug
.\gradlew.bat lintDebug
```

The installable debug APK is `app/build/outputs/apk/debug/app-debug.apk`.
Linux / macOS: `chmod +x gradlew`, then `./gradlew test assembleDebug`.
Dependencies and Gradle are downloaded during build; the Android application itself has no Internet permission and needs no server.

## Use

Enable NFC in Android Settings, unlock the phone, open the app, and keep your tag against its NFC antenna.
SCAN and DETAILS show values reported by Android; they do not infer a silicon model or authenticity.
UID DEC is unsigned big endian. ATQA retains Android API byte order.
Reader Mode polls NFC-A / B / F / V without skipping Android's NDEF detection.

MEMORY displays sector layouts returned by `sectorToBlock()` and `getBlockCountInSector()`, including large sectors on compatible 4K tags.
Choose KEY A or KEY B and enter your own six-byte key. Both compact and spaced HEX are accepted.
TRY DEFAULT KEY tries only `MifareClassic.KEY_DEFAULT` once. It also puts that public default in the in-memory editor for later reads.
READ ALL ACCESSIBLE uses only the selected key; failed authentication advances to the next sector.
Read denials remain visible and do not invent block data. Tag loss preserves completed dump rows where available.

In WRITE choose the sector and absolute block index, enter exactly 16 bytes, and select WRITE or WRITE + VERIFY.
Both buttons read current contents before displaying confirmation, re-authenticate and re-read before writing, then read back and compare all 16 data bytes.
A changed tag session or changed block contents cancels the write. There is no automatic retry of writes.
Choose a data block you have reserved for experiments: writing arbitrary data blocks can replace NDEF or application contents.

Raw manufacturer block 0 is always protected, including in developer mode.
Raw sector trailers are protected by default. Settings requires Developer mode plus Allow sector trailer writes.
Trailer writes also require valid complementary access bits and a second dialog with `WRITE TRAILER`.
Valid encoding does not guarantee reversible access permissions: deliberately chosen access conditions can lock a sector.
Trailer verification re-authenticates with the new selected key and compares access bytes / GPB only.
It explicitly does not claim full key-byte readback. Key A is not displayed as an observed key; both trailer key slots are hidden in UI dumps and exports.
No UID modification, magic commands, authentication bypass, cloning, dictionaries or key attacks are implemented.

NDEF supports UTF-8 Text (language code), absolute URI, UTF-8 MIME payloads and UTF-8 External Type payloads.
Writing replaces the complete existing NDEF message after checking writability and capacity; a fresh NFC read verifies the message bytes.
NdefFormatable formatting creates a minimal empty Text record, closes the connection and asks for a remove/re-tap.
Formatting uses Android's standard formatter, which can change memory allocation and trailer/access structures on MIFARE Classic.
The raw block-write protection is not a promise that standard NDEF formatting preserves existing tag structures.
Format only an owned tag whose existing contents you can discard. After re-tap the app checks the same UID and presence of Ndef technology.
A UID match is a diagnostic identifier, not proof of identity or authenticity.

APDU is an IsoDep information screen. It sends no arbitrary APDUs.
LOG retains up to 400 events in app memory. It excludes keys and NDEF payloads.
JSON/TXT export uses Android's document picker; there is no storage permission. Authentication keys are not passed to the exporter; trailer key slots are redacted.

## Architecture and privacy

`nfc/`: independent tag detector, session arbiter, Classic transactions, NDEF builder/manager, hex utilities, result mapping and exporter.
`LabViewModel`: StateFlow, operation gate and UI state.
`ui/LabApp.kt`: Compose screens, editor and confirmations.
`MainActivity`: lifecycle Reader Mode registration only.

All NFC I/O runs on Dispatchers.IO, guarded by a shared Mutex.
Every opened technology is closed in finally, including connect failure, tag loss and cancellation.
Leaving the foreground cancels operations, invalidates pending writes, closes active I/O, clears the key input and resets developer permissions.
Keys are never persisted. Temporary parsed key arrays are zeroed after use.
Kotlin/JVM immutable strings cannot offer guaranteed memory zeroization; use app-memory handling only.
FLAG_SECURE blocks screenshots/recents previews. Backup and device-transfer rules exclude app data.
No analytics, crash-reporting SDK, network service, or Internet permission is included.

## Validation boundaries

See `VALIDATION.md` and `DEVICE_TEST_CHECKLIST.md`.
Unit/Robolectric tests exercise parsers, policies, transaction ordering, verification failures, session handling, cleanup, NDEF builders, export redaction and Activity lifecycle.
The reference UID/ATQA/SAK is a unit-test fixture only. Production values are read dynamically.
Build and unit-test success do not establish Samsung NFC hardware compatibility or physical tag write success.

## Controlled erasure of an owned Bambu filament tag

MEMORY exposes ERASE BAMBU DATA only with Developer mode and Allow sector trailer writes enabled.
The extra confirmation requires ERASE followed by the displayed UID without spaces.
This opt-in operation uses the published Bambu Research Group / queengooborg deterministic A+B derivation:
https://github.com/queengooborg/Bambu-Lab-RFID-Tag-Guide/blob/main/deriveKeys.py
It is limited to Classic 1K / 4-byte UID tags; no guessing of keys is performed.
It backs up every block, redacts keys, temporarily changes only data-block access conditions where necessary, zeroes 47 data blocks, restores original controls and verifies manufacturer bytes, controls and key authentication.
Keep the tag in place and the app foreground until completion. Read the ERASE REPORT before retrying an interrupted operation.
Backups and a phase journal are app-private under files/erase-backups; erasure never removes the backup.
Credentials and developer permissions are cleared after the operation.
This removes filament application data and does not convert the tag to NDEF. A blank NDEF tag remains the straightforward option for a web link.
See VALIDATION.md for the actual tested tag and known limits.
## Authorized Classic-to-NDEF conversion

NDEF exposes CONVERT CLEARED TAG TO NDEF only with Developer mode and Allow sector trailer writes enabled, plus a typed UID confirmation. It supports cleared own Classic 1K tags with the published Bambu keys, verified across all 16 sectors before writing. Nonzero data is rejected.
The converter follows NXP AN10787 (MAD1, CRC and public MAD Key A) and AN1305 (NFC sectors, public NFC Key A, access bits, GPB and NDEF TLV):
https://www.nxp.com/docs/en/application-note/AN10787.pdf
https://www.nxp.com/docs/en/application-note/AN1305.pdf
It preserves Block 0 and original Key B. Key A and access permissions deliberately change. The NFC mapping is published last, every block is verified, and rollback is attempted using unchanged Key B. Backup and phase journal are saved under files/conversion-backups with key slots redacted. Tag loss can prevent rollback; an incomplete conversion must not be retried blindly.
After conversion, remove and re-present the tag. Android Ndef technology detection and Ndef message read must succeed before ordinary Text/URI writes are offered.
On 2026-10-05 the Samsung S25 physically converted own UID 42 27 04 26, detected Ndef with 716-byte writable capacity, and wrote URI https://bitback.pl/ via Ndef.writeNdefMessage with a successful byte-for-byte read-back comparison. This result applies to this device/tag, not to every MIFARE tag or phone. NdefFormatable.format remains unverified for the Bambu tags.
