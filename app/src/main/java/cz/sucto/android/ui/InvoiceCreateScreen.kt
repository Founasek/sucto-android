package cz.sucto.android.ui

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cz.sucto.android.data.*
import java.math.BigDecimal
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceCreateHost(factory: InvoiceCreateViewModel.Factory, owner: ViewModelStoreOwner, onClose: () -> Unit, onSaved: (Int) -> Unit) {

    val model: InvoiceCreateViewModel = viewModel(viewModelStoreOwner = owner, factory = factory)
    val state by model.state.collectAsStateWithLifecycle()
    var discard by remember { mutableStateOf(false) }
    var partners by remember { mutableStateOf(false) }
    LaunchedEffect(state.createdId) { state.createdId?.let(onSaved) }
    val close = { if (!state.submitting) { if (state.references == null) onClose() else discard = true } }
    BackHandler { close() }
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("Zahodit rozepsanou fakturu?") },
        text = { Text("Neuložené změny se ztratí.") }, confirmButton = { TextButton(onClick = onClose) { Text("Zahodit") } },
        dismissButton = { TextButton(onClick = { discard = false }) { Text("Pokračovat v úpravách") } })
    Scaffold(topBar = {
        TopAppBar(title = { Text(if (model.copyId != null) "Duplikovat fakturu" else if (model.direction == InvoiceDirection.Outgoing) "Nová vydaná faktura" else "Nová přijatá faktura") },
            navigationIcon = { IconButton(onClick = { close() }, enabled = !state.submitting) { Icon(Icons.Default.ArrowBack, contentDescription = "Zpět") } })
    }) { padding ->
        val refs = state.references
        val draft = state.draft
        LazyColumn(Modifier.fillMaxSize().padding(padding).imePadding(), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.loading) item { CircularProgressIndicator(); Text("Načítám údaje pro fakturu…") }
            state.error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
            state.copyWarning?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
            if (refs == null && !state.loading) item { Button(onClick = model::load) { Text("Zkusit znovu") } }
            if (refs != null && !state.loading) {
                item {
                    FormSection("Základní informace") {
                        DraftText("Číslo faktury", draft.number, enabled = !state.submitting && model.direction == InvoiceDirection.Incoming) { value -> model.edit { it.copy(number = value) } }
                        DraftText("Variabilní symbol", draft.variableSymbol, !state.submitting, numeric = true) { value -> model.edit { it.copy(variableSymbol = value) } }
                        OutlinedButton(onClick = { partners = true }, enabled = !state.submitting, modifier = Modifier.fillMaxWidth()) {
                            Text(state.selectedPartner?.name ?: if (model.direction == InvoiceDirection.Outgoing) "Vybrat odběratele" else "Vybrat dodavatele")
                        }
                    }
                }
                item {
                    FormSection("Časové údaje") {
                        DraftDate("Datum vystavení", draft.issue, !state.submitting) { value -> model.edit { it.copy(issue = value) } }
                        DraftDate("Datum splatnosti", draft.due, !state.submitting) { value -> model.edit { it.copy(due = value) } }
                        DraftDate("Datum UZP", draft.uzp, !state.submitting) { value -> model.edit { it.copy(uzp = value) } }
                    }
                }
                item {
                    FormSection("Platební a daňové údaje") {
                        ChoicePicker("Účet", draft.accountId, refs.accounts.map { Choice(it.id, it.name) }, !state.submitting) { value -> model.edit { it.copy(accountId = value) } }
                        ChoicePicker("Měna", draft.currencyId, refs.currencies, !state.submitting) { value -> model.edit { it.copy(currencyId = value) } }
                        ChoicePicker("Způsob platby", draft.paymentTypeId, refs.payments, !state.submitting, nullable = true) { value -> model.edit { it.copy(paymentTypeId = value) } }
                        if (model.company.isTaxable) ChoicePicker("DPH režim", draft.vatRegimeId, refs.regimes, !state.submitting) { value -> model.edit { it.copy(vatRegimeId = value) } }
                        DraftText("Číslo objednávky", draft.order, !state.submitting) { value -> model.edit { it.copy(order = value) } }
                    }
                }
                item { Text("Položky", style = MaterialTheme.typography.titleMedium) }
                items(draft.lines, key = { it.key }) { line ->
                    val amounts = line.amount(refs.vats)
                    FormSection("Položka ${draft.lines.indexOf(line) + 1}") {
                        DraftText("Název", line.name, !state.submitting) { model.updateLine(line.copy(name = it)) }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            DraftText("Množství", line.quantity, !state.submitting, true, Modifier.weight(1f)) { model.updateLine(line.copy(quantity = it)) }
                            DraftText("Jednotka", line.unit, !state.submitting, modifier = Modifier.weight(1f)) { model.updateLine(line.copy(unit = it)) }
                        }
                        DraftText("Jednotková cena", line.unitPrice, !state.submitting, true) { model.updateLine(line.copy(unitPrice = it)) }
                        ChoicePicker("Sazba DPH", line.vatId, refs.vats.map { Choice(it.id, "${it.rate.stripTrailingZeros().toPlainString()} %") }, !state.submitting) { id ->
                            if (id != null) model.updateLine(line.copy(vatId = id))
                        }
                        Text("Základ: ${money(amounts.base, "")}")
                        Text("DPH: ${money(amounts.tax, "")}")
                        Text("Celkem: ${money(amounts.total, "")}")
                        TextButton(onClick = { model.removeLine(line.key) }, enabled = !state.submitting) { Text("Odstranit položku", color = MaterialTheme.colorScheme.error) }
                    }
                }
                item { OutlinedButton(onClick = { model.addLine() }, enabled = !state.submitting, modifier = Modifier.fillMaxWidth()) { Text("Přidat položku") } }
                item {
                    FormSection("Texty faktury") {
                        DraftText("Úvodní text", draft.notice, !state.submitting) { value -> model.edit { it.copy(notice = value) } }
                        DraftText("Text v patičce", draft.footer, !state.submitting) { value -> model.edit { it.copy(footer = value) } }
                    }
                }
                item {
                    val amounts = draft.lines.map { it.amount(refs.vats) }
                    FormSection("Souhrn") {
                        val currency = refs.currencies.find { it.id == draft.currencyId }?.let { it.symbol ?: it.name }.orEmpty()
                        Text("Základ: ${money(amounts.fold(BigDecimal.ZERO) { total, line -> total + line.base }, currency)}")
                        Text("DPH: ${money(amounts.fold(BigDecimal.ZERO) { total, line -> total + line.tax }, currency)}")
                        Text("Celkem: ${money(amounts.fold(BigDecimal.ZERO) { total, line -> total + line.total }, currency)}", style = MaterialTheme.typography.titleLarge)
                    }
                }
                item {
                    Button(onClick = model::submit, enabled = !state.submitting && state.createdId == null,
                        modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(14.dp)) {
                        if (state.submitting) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        else Text("Vytvořit fakturu")
                    }
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
                }
            }
        }
    }
    if (partners) ModalBottomSheet(onDismissRequest = { partners = false }) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Vybrat partnera", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(state.partnerSearch, onValueChange = model::searchPartners, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                singleLine = true, label = { Text("Název, IČ nebo DIČ") })
            if (state.partnersLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.partnerError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                items(state.partners, key = { it.id }) { partner ->
                    TextButton(onClick = { model.selectPartner(partner); partners = false }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth()) { Text(partner.name); partner.ic?.let { Text("IČ $it") } }
                    }
                }
                if (!state.partnersLoading && state.partners.isEmpty()) item { Text("Žádní partneři neodpovídají hledání.") }
            }
        }
    }
}

@Composable
internal fun FormSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}
@Composable
internal fun DraftText(label: String, value: String, enabled: Boolean = true, numeric: Boolean = false,
    modifier: Modifier = Modifier, onValue: (String) -> Unit) {
    OutlinedTextField(value, onValueChange = onValue, modifier = modifier.fillMaxWidth(), label = { Text(label) }, enabled = enabled,
        singleLine = true, shape = RoundedCornerShape(14.dp), keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text))
}
@Composable
internal fun ChoicePicker(label: String, selected: Int?, choices: List<Choice>, enabled: Boolean = true, nullable: Boolean = false,
    onSelect: (Int?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Text("$label: ${choices.find { it.id == selected }?.name ?: "Vybrat"}")
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            if (nullable) DropdownMenuItem(text = { Text("Bez výběru") }, onClick = { expanded = false; onSelect(null) })
            choices.forEach { choice -> DropdownMenuItem(text = { Text(choice.name) }, onClick = { expanded = false; onSelect(choice.id) }) }
        }
    }
}
@Composable
internal fun DraftDate(label: String, value: LocalDate, enabled: Boolean = true, onDate: (LocalDate) -> Unit) {
    val context = LocalContext.current
    OutlinedButton(onClick = {
        DatePickerDialog(context, { _, year, month, day -> onDate(LocalDate.of(year, month + 1, day)) }, value.year, value.monthValue - 1, value.dayOfMonth).show()
    }, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("$label: ${dateLabel(value.toString())}") }
}