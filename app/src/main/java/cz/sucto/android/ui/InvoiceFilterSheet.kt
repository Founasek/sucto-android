package cz.sucto.android.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cz.sucto.android.data.*
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceFilterSheet(initial: InvoiceAdvancedFilter, onDismiss: () -> Unit, onApply: (InvoiceAdvancedFilter) -> Unit) {
    var filter by remember(initial) { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(Modifier.fillMaxWidth().imePadding(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("Filtry", style = MaterialTheme.typography.titleLarge) }
            item { ChoicePicker("Stav", filter.status, InvoiceStatuses.mapIndexed { index, name -> Choice(index + 1, name) }, nullable = true) { filter = filter.copy(status = it) } }
            item { ChoicePicker("Typ dokladu", filter.kind, listOf(Choice(1, "Faktura"), Choice(2, "Výzva k platbě"), Choice(3, "Opravný doklad")), nullable = true) { filter = filter.copy(kind = it) } }
            item { FormSection("Datum vystavení") {
                FilterDate("Od", filter.issueFrom) { filter = filter.copy(issueFrom = it) }
                FilterDate("Do", filter.issueTo) { filter = filter.copy(issueTo = it) }
            } }
            item { FormSection("Datum splatnosti") {
                FilterDate("Od", filter.dueFrom) { filter = filter.copy(dueFrom = it) }
                FilterDate("Do", filter.dueTo) { filter = filter.copy(dueTo = it) }
            } }
            item { FormSection("Datum UZP") {
                FilterDate("Od", filter.uzpFrom) { filter = filter.copy(uzpFrom = it) }
                FilterDate("Do", filter.uzpTo) { filter = filter.copy(uzpTo = it) }
            } }
            item { FormSection("Částka s DPH") {
                DraftText("Minimální částka", filter.minPrice, numeric = true) { filter = filter.copy(minPrice = it) }
                DraftText("Maximální částka", filter.maxPrice, numeric = true) { filter = filter.copy(maxPrice = it) }
            } }
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            item {
                Button(onClick = { error = filter.validation(); if (error == null) { onApply(filter); onDismiss() } }, modifier = Modifier.fillMaxWidth()) { Text("Použít filtry") }
                TextButton(onClick = { filter = InvoiceAdvancedFilter(); error = null }, modifier = Modifier.fillMaxWidth()) { Text("Vymazat podrobné filtry") }
            }
        }
    }
}
@Composable
private fun FilterDate(label: String, value: LocalDate?, onSelect: (LocalDate?) -> Unit) {
    val context = LocalContext.current
    Row {
        OutlinedButton(onClick = {
            val date = value ?: LocalDate.now()
            DatePickerDialog(context, { _, year, month, day -> onSelect(LocalDate.of(year, month + 1, day)) }, date.year, date.monthValue - 1, date.dayOfMonth).show()
        }, modifier = Modifier.weight(1f)) { Text("$label: ${if (value == null) "Bez omezení" else dateLabel(value.toString())}") }
        if (value != null) TextButton(onClick = { onSelect(null) }) { Text("Zrušit") }
    }
}