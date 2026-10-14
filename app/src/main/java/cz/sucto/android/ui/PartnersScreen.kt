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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
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
    LazyColumn(modifier, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                SearchField(state.search, model::search, "Název, IČ, DIČ nebo adresa", Modifier.weight(1f))
                if (canCreate) {
                    if (state.creating) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    else FilledTonalButton(onClick = { aresDialog = true }) { Text("+ ARES") }
                }
            }
        }
        if (state.loading && state.items.isEmpty()) item { Loading("Načítám partnery…") }
        state.error?.let { error -> item { ErrorCard(error) { model.refresh() } } }
        if (!state.loading && state.error == null && state.items.isEmpty()) item {
            EmptyState(Icons.Default.Person, if (state.search.isBlank()) "Zatím tu nejsou žádní partneři." else "Žádný partner neodpovídá hledání.")
        }
        items(state.items, key = { it.id }) { partner -> PartnerCard(partner) { model.open(partner) } }
    }
}

@Composable
private fun PartnerCard(partner: PartnerRecord, onClick: () -> Unit) {
    SuctoCard(onClick = onClick) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IconBadge(if (partner.isCompany) Icons.Default.Home else Icons.Default.Person)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(partner.name, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                partner.ic?.let { Text("IČ $it", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium) }
                partner.address?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2) }
            }
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
    LazyColumn(modifier, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            HeroCard {
                Text(partner.name, color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.titleLarge)
                Text(listOfNotNull("Odběratel".takeIf { partner.isCustomer }, "Dodavatel".takeIf { partner.isSupplier },
                    "Plátce DPH".takeIf { partner.isTaxable }).joinToString(" · "), color = androidx.compose.ui.graphics.Color.White.copy(alpha = .8f))
            }
        }
        if (state.detailLoading) item { Loading("Načítám detail…") }
        state.detailError?.let { error -> item { ErrorCard(error) { model.open(partner) } } }
        item { PartnerSection("Identifikace", listOf("IČ" to partner.ic, "DIČ" to partner.dic, "Adresa" to partner.address)) }
        item { PartnerSection("Kontakt", listOf("E-mail" to partner.email, "Telefon" to partner.phone, "Web" to partner.web)) }
        item { PartnerSection("Fakturace", listOf("Měna" to partner.currency, "Splatnost" to partner.invoiceDueDays?.let { "$it dní" })) }
        if (canDelete) item {
            SoftButton(if (state.deleting) "Mažu…" else "Smazat partnera", { confirm = true }, enabled = !state.deleting, destructive = true)
        }
    }
}

@Composable
private fun PartnerSection(title: String, rows: List<Pair<String, String?>>) {
    val visible = rows.filter { !it.second.isNullOrBlank() }
    if (visible.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(title)
        SuctoCard { visible.forEach { (label, value) -> InfoRow(label, value.orEmpty()) } }
    }
}
