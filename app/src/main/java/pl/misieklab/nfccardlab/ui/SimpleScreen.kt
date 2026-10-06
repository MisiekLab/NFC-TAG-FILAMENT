package pl.misieklab.nfccardlab.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import pl.misieklab.nfccardlab.*
import pl.misieklab.nfccardlab.nfc.*

internal enum class SimpleTab(val label: String, val symbol: String) {
    TAG("Tag", "◎"), WRITE("Zapis", "✎"), PREPARE("Przygotuj", "✓")
}
private enum class SimpleAction { WRITE, FORMAT, ERASE, CONVERT }
private data class SimpleConfirmation(val action: SimpleAction, val uid: String, val generation: Long,
    val kind: RecordKind = RecordKind.URI, val value: String = "", val appLabel: String = "")

@Composable
internal fun SimpleScreen(s: LabState, model: LabViewModel, tab: SimpleTab, navigate: (SimpleTab) -> Unit) {
    val context = LocalContext.current
    var kindName by rememberSaveable { mutableStateOf(RecordKind.URI.name) }
    val kind = RecordKind.valueOf(kindName)
    var link by rememberSaveable { mutableStateOf("") }
    var text by rememberSaveable { mutableStateOf("") }
    var appPackage by rememberSaveable { mutableStateOf("") }
    var appLabel by rememberSaveable { mutableStateOf("") }
    var showAppPicker by remember { mutableStateOf(false) }
    var confirmation by remember { mutableStateOf<SimpleConfirmation?>(null) }
    val info = s.info
    val ready = s.sessionReady && !s.busy
    val hasNdef = SimpleWorkflow.hasNdef(info)
    fun ask(action: SimpleAction) {
        val generation = model.generation() ?: return
        val uid = info?.uid ?: return
        confirmation = SimpleConfirmation(action, uid, generation, kind,
            when (kind) { RecordKind.URI -> link.trim(); RecordKind.APPLICATION -> appPackage; else -> text }, appLabel)
    }
    SimpleCard(if (info == null) "1. Przyłóż tag" else "Twój tag") {
        if (info == null) {
            Text("Włącz NFC i przyłóż tag do tylnej części telefonu. Telefon rozpozna go automatycznie.")
        } else {
            Text(info.classification)
            Text("ID: ${info.uid}", fontFamily = FontFamily.Monospace)
            Text(if (!s.sessionReady) "Przyłóż ten tag ponownie, aby wykonać operację."
                else if (hasNdef && s.ndef?.writable == true) "Gotowy do zapisu tekstu, linku lub aplikacji."
                else if (hasNdef && s.ndef?.writable == false) "Tag tylko do odczytu — nie można go zapisać."
                else if (hasNdef) "Odczytaj tag ponownie, aby sprawdzić możliwość zapisu."
                else "Ten tag wymaga przygotowania, zanim zapiszesz tekst, link lub aplikację.")
        }
    }
    when (tab) {
        SimpleTab.TAG -> {
            if (info != null) {
                SimpleCard("Zawartość") {
                    if (s.ndef?.records?.isNotEmpty() == true) s.ndef.records.forEach {
                        Text(it.replaceFirst("URI: ", "Link: ").replaceFirst("TEXT: ", "Tekst: ").replaceFirst("APPLICATION: ", "Aplikacja: "))
                    } else Text(if (hasNdef) "Brak odczytanej treści." else "Dane techniczne są dostępne w trybie zaawansowanym.")
                    if (hasNdef) OutlinedButton(onClick = model::readNdef, enabled = ready, modifier = Modifier.fillMaxWidth()) { Text("Odczytaj ponownie") }
                }
                Button(onClick = { navigate(if (hasNdef) SimpleTab.WRITE else SimpleTab.PREPARE) }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (hasNdef) "Zapisz tekst, link lub aplikację" else "Przygotuj tag")
                }
            }
            SimpleCard("Jak używać?") {
                Text("1. Przyłóż tag i poczekaj na rozpoznanie.\n2. Wybierz Zapis i wpisz tekst, link lub aplikację.\n3. Potwierdź zapis i trzymaj tag do komunikatu „Zapisano i sprawdzono”.")
                Text("Dla taga z filamentu Bambu najpierw przejdź do Przygotuj. Zmiana jego danych oznacza utratę pierwotnej funkcji oznaczenia filamentu.")
            }
        }
        SimpleTab.WRITE -> {
            if (!hasNdef) {
                SimpleCard("Najpierw przygotuj tag") {
                    Text("Aby telefon odczytywał link, tekst lub aplikację, tag musi mieć format NDEF.")
                    Button(onClick = { navigate(SimpleTab.PREPARE) }, enabled = info != null, modifier = Modifier.fillMaxWidth()) { Text("Przejdź do przygotowania") }
                }
            }
            SimpleCard("Co zapisać?") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterChip(selected = kind == RecordKind.URI, onClick = { kindName = RecordKind.URI.name }, label = { Text("Link") })
                    FilterChip(selected = kind == RecordKind.TEXT, onClick = { kindName = RecordKind.TEXT.name }, label = { Text("Tekst") })
                    FilterChip(selected = kind == RecordKind.APPLICATION, onClick = { kindName = RecordKind.APPLICATION.name }, label = { Text("Aplikacja") })
                }
                if (kind == RecordKind.URI) OutlinedTextField(link, { link = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Adres strony") }, placeholder = { Text("https://bitback.pl/") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                else if (kind == RecordKind.APPLICATION) {
                    OutlinedButton(onClick = { showAppPicker = true }, modifier = Modifier.fillMaxWidth(), enabled = !s.busy) {
                        Text(if (appPackage.isBlank()) "Dodaj aplikację" else "Zmień aplikację")
                    }
                    Text("Wybierz z listy aplikacji zainstalowanych na tym telefonie.")
                    if (appPackage.isNotBlank()) {
                        Text(appLabel.ifBlank { appPackage }, style = MaterialTheme.typography.titleMedium)
                        Text(appPackage, style = MaterialTheme.typography.bodySmall)
                    }
                }
                else OutlinedTextField(text, { text = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Twój tekst") }, minLines = 3)
                val value = when (kind) { RecordKind.URI -> link.trim(); RecordKind.APPLICATION -> appPackage; else -> text }
                val validation = runCatching {
                    require(value.isNotBlank()) { if (kind == RecordKind.APPLICATION) "Wybierz aplikację z telefonu." else "Wpisz treść, którą chcesz zapisać." }
                    if (kind == RecordKind.URI) require(value.startsWith("https://", true) || value.startsWith("http://", true)) { "Wpisz pełny adres zaczynający się od https:// lub http://." }
                    val count = NdefBuilder.build(kind, value, language = "pl").toByteArray().size
                    val capacity = s.ndef?.capacity ?: error("Przyłóż tag ponownie, aby sprawdzić pojemność.")
                    require(s.ndef.writable == true) { "Tag nie pozwala na zapis." }
                    require(count <= capacity) { "Treść jest za długa. Skróć ją." }
                    "$count / $capacity bajtów — treść mieści się na tagu."
                }
                Text(validation.getOrElse { it.message ?: "Sprawdź wpisaną treść." })
                Button(onClick = { ask(SimpleAction.WRITE) }, enabled = hasNdef && ready && validation.isSuccess, modifier = Modifier.fillMaxWidth()) { Text("Zapisz i sprawdź") }
                Text("Zapis zastąpi obecną wiadomość na tagu.")
                if (kind == RecordKind.APPLICATION) Text("Po zapisie wyjdź z NFC Card Lab i ponownie przyłóż tag do odblokowanego telefonu z włączonym NFC. Android użyje nazwy wybranej aplikacji do jej otwarcia. Nie zapisujemy samej aplikacji na tagu. Obsługa może zależeć od telefonu; na innym telefonie bez tej aplikacji może otworzyć się Google Play.")
                if (kind == RecordKind.URI) Text("Po zapisie wyjdź z aplikacji, odsuń tag i przyłóż go do odblokowanego telefonu. Android obsłuży link; sposób otwarcia zależy od ustawień telefonu i przeglądarki.")
            }
        }
        SimpleTab.PREPARE -> {
            when {
                info == null -> SimpleCard("Czekam na tag") { Text("Najpierw przyłóż tag do telefonu.") }
                hasNdef -> SimpleCard("Gotowy!") {
                    Text("Tag ma już format NDEF. Nie musisz go czyścić ani konwertować. Nowy zapis zastąpi obecną wiadomość.")
                    Button(onClick = { navigate(SimpleTab.WRITE) }, modifier = Modifier.fillMaxWidth()) { Text("Przejdź do zapisu") }
                }
                s.pendingFormatUid == info.uid -> SimpleCard("Sprawdź przygotowanie") {
                    Text("Odsuń tag i przyłóż go ponownie. Gotowość potwierdzimy dopiero po wykryciu NDEF.")
                }
                SimpleWorkflow.canPrepareBambu(info) -> SimpleCard("Tag z filamentu Bambu") {
                    Text("Ten typ pamięci może pasować do taga Bambu. Sam typ taga nie potwierdza jego pochodzenia. Użyj tej opcji wyłącznie dla własnego taga z filamentu Bambu.")
                    Text("1. Rozpoznanie — gotowe\n2. Usunięcie starych danych — ${if (s.erasedUid == info.uid) "sprawdzone" else "do wykonania"}\n3. Przygotowanie do linków — do wykonania")
                    Text("Przed zmianami aplikacja zapisze lokalną kopię odczytu. Czyszczenie zachowuje identyfikator i klucze; konwersja zmienia ustawienia dostępu niezbędne do NDEF. Pierwotne dane filamentu zostaną usunięte.")
                    if (!SimpleWorkflow.canConvert(info, s.erasedUid)) Button(onClick = { ask(SimpleAction.ERASE) }, enabled = ready, modifier = Modifier.fillMaxWidth()) { Text("Usuń stare dane…") }
                    else Button(onClick = { ask(SimpleAction.CONVERT) }, enabled = ready, modifier = Modifier.fillMaxWidth()) { Text("Przygotuj do linków…") }
                    Text("Po przygotowaniu trzeba odsunąć i ponownie przyłożyć tag. Każdy etap zostanie sprawdzony.")
                }
                "android.nfc.tech.NdefFormatable" in info.technologies -> SimpleCard("Przygotuj pusty tag") {
                    Text("Telefon zgłasza możliwość formatowania NDEF. Nie gwarantuje to powodzenia. Formatowanie może zastąpić stare dane.")
                    Button(onClick = { ask(SimpleAction.FORMAT) }, enabled = ready, modifier = Modifier.fillMaxWidth()) { Text("Formatuj jako NDEF…") }
                }
                else -> SimpleCard("Nieobsługiwany format") {
                    Text("Ten tag nie udostępnia obsługiwanej metody przygotowania przez Android NFC. Informacje techniczne są w trybie zaawansowanym.")
                }
            }
            // Standard Android formatting remains available even for a non-Bambu Classic tag.
            if (info != null && !hasNdef && SimpleWorkflow.canPrepareBambu(info) && "android.nfc.tech.NdefFormatable" in info.technologies && s.pendingFormatUid != info.uid) {
                SimpleCard("Inny, pusty tag") {
                    Text("Jeśli to nie jest tag Bambu, możesz spróbować standardowego formatowania Androida. Nie omija ono kluczy ani zabezpieczeń.")
                    OutlinedButton(onClick = { ask(SimpleAction.FORMAT) }, enabled = ready, modifier = Modifier.fillMaxWidth()) { Text("Formatuj pusty tag…") }
                }
            }
        }
    }
    if (showAppPicker) AppPicker(onDismiss = { showAppPicker = false }, onSelect = {
        appPackage = it.packageName
        appLabel = it.label
        showAppPicker = false
    })
    confirmation?.let { pending ->
        var agreed by remember(pending) { mutableStateOf(false) }
        var trailerConsent by remember(pending) { mutableStateOf(false) }
        val memoryChange = pending.action == SimpleAction.ERASE || pending.action == SimpleAction.CONVERT
        val current = ready && model.generation() == pending.generation && info?.uid == pending.uid
        AlertDialog(onDismissRequest = { confirmation = null },
            title = { Text(when (pending.action) {
                SimpleAction.WRITE -> "Zapisać nową treść?"
                SimpleAction.FORMAT -> "Przygotować pusty tag?"
                SimpleAction.ERASE -> "Usunąć dane filamentu?"
                SimpleAction.CONVERT -> "Przygotować do linków?"
            }) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Tag: ${pending.uid}", fontFamily = FontFamily.Monospace)
                    if (pending.action == SimpleAction.WRITE) {
                        Text(if (pending.kind == RecordKind.APPLICATION) "Aplikacja: ${pending.appLabel}\n${pending.value}" else "Nowa treść:\n${pending.value}")
                        Text("Zastąpi obecną wiadomość. Zapis będzie sprawdzony ponownym odczytem.")
                    } else {
                        Text(if (memoryChange) "Używaj wyłącznie własnego taga z filamentu Bambu. Pierwotna funkcja oznaczenia filamentu zostanie utracona. Nie odsuwaj taga podczas operacji."
                            else "Używaj wyłącznie własnego pustego taga lub takiego, którego stare dane możesz utracić.")
                        Row {
                            Checkbox(agreed, { agreed = it })
                            Text(if (memoryChange) "To mój tag Bambu. Zgadzam się na zmianę jego danych." else "To mój tag. Zgadzam się na formatowanie.", modifier = Modifier.weight(1f))
                        }
                        if (memoryChange) {
                            Text("Ten etap wymaga kontrolowanej zmiany bloków ustawień. Nieprawidłowy zapis może utrudnić dalszy dostęp. Blok z identyfikatorem pozostaje zablokowany.")
                            Row {
                                Checkbox(trailerConsent, { trailerConsent = it })
                                Text("Zezwalam również na wymaganą zmianę ustawień sektorów tego taga.", modifier = Modifier.weight(1f))
                            }
                        }
                    }
                    if (!current) Text("Kontakt z tagiem zmienił się. Anuluj i przyłóż tag ponownie.")
                }
            },
            dismissButton = { TextButton(onClick = { confirmation = null }) { Text("Anuluj") } },
            confirmButton = { TextButton(enabled = current && (pending.action == SimpleAction.WRITE || (agreed && (!memoryChange || trailerConsent))), onClick = {
                confirmation = null
                when (pending.action) {
                    SimpleAction.WRITE -> model.writeNdef(pending.kind, pending.value, "", "pl", pending.generation)
                    SimpleAction.FORMAT -> model.formatNdef(pending.generation)
                    SimpleAction.ERASE -> model.eraseBambuData(context, pending.uid, pending.generation, guidedConsent = true)
                    SimpleAction.CONVERT -> model.convertBambuNdef(context, pending.uid, pending.generation, guidedConsent = true)
                }
            }) { Text(if (pending.action == SimpleAction.WRITE) "Zapisz" else "Potwierdzam") } })
    }
}

@Composable
private fun SimpleCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}
