package cz.sucto.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cz.sucto.android.data.PartnerRecord

/** Seznam partnerů s hledáním a přidáním podle IČO (ARES). */
@Composable
fun PartnersScreen(state: PartnersState, model: PartnersViewModel, canCreate: Boolean, modifier: Modifier) {
    var aresDialog by rememberSaveable { mutableStateOf(false) }
    var ic by rememberSaveable { mutableStateOf("") }
    if (aresDialog) AlertDialog(onDismissRequest = { aresDialog = false },
        title = { Text("Přidat podle IČO") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Údaje o firmě se načtou z ARES.")
                OutlinedTextField(ic, { ic = it.filter(Char::isDigit).take(8) }, singleLine = true, label = { Text("IČO (8 číslic)") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number))
            }
        },
        confirmButton = { TextButton(onClick = { aresDialog = false; model.createFromAres(ic); ic = "" }, enabled = ic.length == 8) { Text("Přidat") } },
        dismissButton = { TextButton(onClick = { aresDialog = false; ic = "" }) { Text("Zrušit") } })
    state.message?.let { message ->
        AlertDialog(onDismissRequest = model::clearMessage, text = { Text(message) },
            confirmButton = { TextButton(onClick = model::clearMessage) { Text("OK") } })
    }
    LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(state.search, model::search, modifier = Modifier.weight(1f), singleLine = true,
                    label = { Text("Název, IČ, DIČ nebo adresa") })
                if (canCreate) {
                    if (state.creating) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    else FilledTonalButton(onClick = { aresDialog = true }) { Text("+ ARES") }
                }
            }
        }
        if (state.loading && state.items.isEmpty()) item { Loading("Načítám partnery…") }
        state.error?.let { error -> item { ErrorCard(error) { model.refresh() } } }
        if (!state.loading && state.error == null && state.items.isEmpty()) item {
            Text(if (state.search.isBlank()) "Zatím tu nejsou žádní partneři." else "Žádný partner neodpovídá hledání.")
        }
        items(state.items, key = { it.id }) { partner -> PartnerCard(partner) { model.open(partner) } }
    }
}

@Composable
private fun PartnerCard(partner: PartnerRecord, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(partner.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            partner.ic?.let { Text("IČ $it", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            partner.address?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

/** Detail partnera: kontakty, fakturace a smazání (server odmítne partnera, který má doklady). */
@Composable
fun PartnerDetailScreen(state: PartnersState, model: PartnersViewModel, canDelete: Boolean, modifier: Modifier) {
    val partner = state.detail ?: return
    var confirm by rememberSaveable { mutableStateOf(false) }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Smazat partnera?") },
        text = { Text("${partner.name} bude smazán. Partner s doklady se smazat nedá.") },
        confirmButton = { TextButton(onClick = { confirm = false; model.delete() }) { Text("Smazat", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Zrušit") } })
    state.message?.let { message ->
        AlertDialog(onDismissRequest = model::clearMessage, text = { Text(message) },
            confirmButton = { TextButton(onClick = model::clearMessage) { Text("OK") } })
    }
    LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text(partner.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(listOfNotNull("Odběratel".takeIf { partner.isCustomer }, "Dodavatel".takeIf { partner.isSupplier },
                "Plátce DPH".takeIf { partner.isTaxable }).joinToString(" · "), color = MaterialTheme.colorScheme.primary)
        }
        if (state.detailLoading) item { Loading("Načítám detail…") }
        state.detailError?.let { error -> item { ErrorCard(error) { model.open(partner) } } }
        item { PartnerSection("Identifikace", listOf("IČ" to partner.ic, "DIČ" to partner.dic, "Adresa" to partner.address)) }
        item { PartnerSection("Kontakt", listOf("E-mail" to partner.email, "Telefon" to partner.phone, "Web" to partner.web)) }
        item { PartnerSection("Fakturace", listOf("Měna" to partner.currency, "Splatnost" to partner.invoiceDueDays?.let { "$it dní" })) }
        if (canDelete) item {
            OutlinedButton(onClick = { confirm = true }, enabled = !state.deleting, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                Text(if (state.deleting) "Mažu…" else "Smazat partnera")
            }
        }
    }
}

@Composable
private fun PartnerSection(title: String, rows: List<Pair<String, String?>>) {
    val visible = rows.filter { !it.second.isNullOrBlank() }
    if (visible.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                visible.forEach { (label, value) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(value.orEmpty(), Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                    }
                }
            }
        }
    }
}
