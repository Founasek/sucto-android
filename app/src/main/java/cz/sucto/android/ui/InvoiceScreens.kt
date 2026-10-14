package cz.sucto.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cz.sucto.android.data.*
import cz.sucto.android.ui.theme.*

/** Seznam faktur: přepínač Vydané/Přijaté, hledání, rychlé filtry, karty faktur a automatické dočítání dalších stránek. */
@Composable
internal fun InvoiceListScreen(state: InvoiceListState, direction: InvoiceDirection, onDirection: (InvoiceDirection) -> Unit,
    onSearch: (String) -> Unit, onFilter: (InvoiceFilter) -> Unit, onNext: () -> Unit, onRetry: () -> Unit,
    onOpen: (Invoice) -> Unit, onAdvanced: (InvoiceAdvancedFilter) -> Unit, modifier: Modifier) {
    var showFilters by remember { mutableStateOf(false) }
    if (showFilters) InvoiceFilterSheet(state.query.advanced, onDismiss = { showFilters = false }, onApply = onAdvanced)
    LazyColumn(modifier, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            SegmentedTabs(InvoiceDirection.entries.map { it.title }, direction.ordinal, { onDirection(InvoiceDirection.entries[it]) })
        }
        item { SearchField(state.query.search, onSearch, "Číslo faktury nebo partner") }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(InvoiceFilter.entries.toList()) { filter -> FilterPill(filter.title, state.query.filter == filter, { onFilter(filter) }) }
                item {
                    val count = state.query.advanced.activeCount
                    FilterPill(if (count == 0) "Další filtry" else "Další filtry ($count)", count > 0, { showFilters = true })
                }
            }
        }
        items(state.items, key = { it.id }) { invoice -> InvoiceCard(invoice, onClick = { onOpen(invoice) }) }
        state.error?.let { error -> item { ErrorCard(error, onRetry) } }
        if (state.loading) item { Loading("Načítám faktury…") }
        if (!state.loading && state.error == null && state.items.isEmpty()) item {
            EmptyState(Icons.Default.List, "Žádné faktury neodpovídají výběru.")
        }
        // Další stránka se načte, jakmile se k tomuto prvku doroluje.
        if (!state.loading && state.hasMore && state.error == null && state.items.isNotEmpty()) item(key = "more-${state.nextPage}") {
            LaunchedEffect(state.nextPage) { onNext() }
            Spacer(Modifier.height(1.dp))
        }
    }
}

@Composable
private fun InvoiceCard(invoice: Invoice, onClick: () -> Unit) {
    SuctoCard(onClick = onClick) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(invoice.number, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(8.dp))
            StatusBadge(invoice)
        }
        Text(invoice.partner ?: "—", style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(money(invoice.total, invoice.currency), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge.merge(MoneyStyle), maxLines = 1)
            invoice.due?.let {
                val color = if (invoice.isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.DateRange, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
                    Text(dateLabel(it), color = color, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/** Detail faktury: hlavní karta se stavem a částkou, akce, údaje, položky a texty. */
@Composable
internal fun InvoiceDetail(invoice: Invoice, loading: Boolean, error: String?, onRetry: () -> Unit,
    onDuplicate: () -> Unit, canDuplicate: Boolean, recipient: String?, actions: InvoiceActions?, modifier: Modifier) {
    var confirmPay by remember(invoice.id) { mutableStateOf(false) }
    var emailDialog by remember(invoice.id) { mutableStateOf(false) }
    var reminderDialog by remember(invoice.id) { mutableStateOf(false) }
    var showQr by remember(invoice.id) { mutableStateOf(false) }
    val context = LocalContext.current
    if (confirmPay && actions != null) AlertDialog(onDismissRequest = { confirmPay = false }, title = { Text("Uhradit fakturu?") },
        text = { Text("Faktura ${invoice.number} se označí jako zaplacená a vytvoří se pokladní doklad. Akci nelze vrátit zpět.") },
        confirmButton = { TextButton(onClick = { confirmPay = false; actions.onPay() }) { Text("Uhradit") } },
        dismissButton = { TextButton(onClick = { confirmPay = false }) { Text("Zrušit") } })
    if (emailDialog && actions != null) SendEmailDialog("Odeslat fakturu e-mailem", invoice.partnerEmail.orEmpty(), "", "Zpráva (nepovinná)", null,
        actions.onEmail, onDismiss = { emailDialog = false })
    if (reminderDialog && actions != null) SendEmailDialog("Odeslat upomínku", invoice.partnerEmail.orEmpty(),
        Reminders.render(AppSettings.reminderTemplate(context), invoice, actions.companyName), "Text upomínky",
        "Zpráva se odešle zákazníkovi spolu s fakturou. Výchozí text změníte v Nastavení." +
            (actions.lastReminder?.let { " Poslední upomínka byla odeslána $it." } ?: ""),
        { email, comment, done -> actions.onEmail(email, comment) { failure -> if (failure == null) actions.onReminderSent(); done(failure) } },
        onDismiss = { reminderDialog = false })
    if (showQr) InvoiceQrSheet(invoice, recipient, onDismiss = { showQr = false })

    LazyColumn(modifier, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            HeroCard(colors = if (invoice.isOverdue || invoice.statusId == 10) NegativeGradient else BrandGradient) {
                Text(invoice.partner ?: "—", color = Color.White, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(money(invoice.total, invoice.currency), color = Color.White, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(if (invoice.isOverdue) "Po splatnosti · ${invoice.status}" else invoice.status, color = Color.White.copy(alpha = .8f),
                    style = MaterialTheme.typography.labelLarge, modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = .16f))
                        .padding(horizontal = 12.dp, vertical = 5.dp))
            }
        }
        if (loading) item { Loading("Načítám detail…") }
        error?.let { item { ErrorCard(it, onRetry) } }
        if (!loading && error == null) item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (actions?.canPay == true) GradientButton(if (actions.busy) "Zpracovávám…" else "Uhradit fakturu", { confirmPay = true }, enabled = !actions.busy)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (PaymentQr.payload(invoice, recipient) != null) SoftButton("QR platba", { showQr = true }, Modifier.weight(1f))
                    if (canDuplicate) SoftButton("Duplikovat", onDuplicate, Modifier.weight(1f))
                }
                if (actions != null) SoftButton("Odeslat e-mailem", { emailDialog = true })
                if (actions?.remindersEnabled == true && invoice.isOverdue) SoftButton("Odeslat upomínku", { reminderDialog = true })
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Údaje faktury")
                SuctoCard {
                    InfoRow("Vystaveno", dateLabel(invoice.issue))
                    InfoRow("Splatnost", dateLabel(invoice.due))
                    InfoRow("Variabilní symbol", invoice.variableSymbol ?: "—")
                    invoice.order?.let { InfoRow("Číslo objednávky", it) }
                    (invoice.iban ?: invoice.bankIban)?.let { InfoRow("IBAN", it) }
                    invoice.bankNumber?.let { InfoRow("Bankovní účet", it) }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
                    InfoRow("Základ", money(invoice.base, invoice.currency))
                    InfoRow("Celkem", money(invoice.total, invoice.currency))
                }
            }
        }
        if (invoice.items.isNotEmpty()) item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Položky")
                SuctoCard {
                    invoice.items.forEachIndexed { index, line ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
                        InvoiceLineRow(line, invoice.currency)
                    }
                }
            }
        }
        invoice.note?.let { note -> item { TextBlock("Poznámka", note) } }
        invoice.footer?.let { footer -> item { TextBlock("Patička", footer) } }
    }
}

@Composable
private fun InvoiceLineRow(line: InvoiceLine, currency: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(line.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(money(line.total, currency), style = MaterialTheme.typography.bodyLarge.merge(MoneyStyle))
        }
        val detail = listOfNotNull(listOfNotNull(line.quantity, line.unit).joinToString(" ").ifBlank { null }, line.vat?.let { "DPH $it %" }).joinToString(" · ")
        if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun TextBlock(title: String, text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(title)
        SuctoCard { Text(text, style = MaterialTheme.typography.bodyMedium) }
    }
}
