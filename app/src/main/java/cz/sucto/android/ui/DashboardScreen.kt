package cz.sucto.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.sucto.android.data.*
import kotlinx.coroutines.launch
import cz.sucto.android.ui.theme.*
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.max

enum class DashboardTab(val title: String) { Overview("Přehled"), Invoices("Faktury"), Cash("Pokladna"), Company("Firma") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(company: Company, companies: List<Company>, model: DashboardViewModel, cashModel: CashViewModel, companyModel: CompanyViewModel, partnersModel: PartnersViewModel,
    onSwitchCompany: (Company) -> Unit, onCompanies: () -> Unit, onLogout: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val cash by cashModel.state.collectAsStateWithLifecycle()
    val profile by companyModel.state.collectAsStateWithLifecycle()
    val partners by partnersModel.state.collectAsStateWithLifecycle()
    var switcher by remember { mutableStateOf(false) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var exporting by remember { mutableStateOf(false) }
    // Přepínač upomínek se načte znovu po návratu z Nastavení.
    val remindersEnabled = remember(settingsOpen) { AppSettings.remindersEnabled(context) }
    var partnersOpen by rememberSaveable { mutableStateOf(false) }
    val canCreatePartner = true // oprávnění Partner se při neznámém stavu nechává na serveru, jako v iOS
    var tab by rememberSaveable { mutableStateOf(DashboardTab.Overview) }
    var direction by rememberSaveable { mutableStateOf(InvoiceDirection.Outgoing) }
    var menu by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); model.clearMessage() } }
    DisposableEffect(model, cashModel, companyModel, partnersModel) {
        model.start(); cashModel.start(); companyModel.start()
        onDispose { model.stop(); cashModel.stop(); companyModel.stop(); partnersModel.stop() }
    }
    if (switcher) CompanySwitcherSheet(companies, company, onSelect = { switcher = false; onSwitchCompany(it) }, onDismiss = { switcher = false })
    if (state.createDirection != null) {
        InvoiceCreateHost(model.createFactory(company), model.createOwner, onClose = model::closeCreate, onSaved = { id ->
            model.invoiceCreated(id); tab = DashboardTab.Invoices; direction = state.createDirection ?: direction
        })
        return
    }
    BackHandler { if (settingsOpen) settingsOpen = false else if (state.detail != null) model.closeInvoice() else if (cash.detail != null) cashModel.close() else if (tab == DashboardTab.Company && partners.detail != null) partnersModel.closeDetail() else if (tab == DashboardTab.Company && partnersOpen) partnersOpen = false else if (profile.selectedAccount != null) companyModel.openAccount(null) else onCompanies() }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(title = { Text((if (settingsOpen) "Nastavení" else null) ?: state.detail?.number ?: cash.detail?.number ?: (if (tab == DashboardTab.Company) partners.detail?.name.takeIf { partnersOpen } ?: (if (partnersOpen) "Partneři" else profile.selectedAccount?.name) else null) ?: company.name, maxLines = 2) },
                navigationIcon = {
                    val accountOpen = tab == DashboardTab.Company && (profile.selectedAccount != null || partnersOpen)
                    if (settingsOpen || state.detail != null || cash.detail != null || accountOpen) IconButton(onClick = {
                        if (settingsOpen) settingsOpen = false else if (state.detail != null) model.closeInvoice() else if (cash.detail != null) cashModel.close()
                        else if (partners.detail != null) partnersModel.closeDetail() else if (partnersOpen) partnersOpen = false else companyModel.openAccount(null)
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Zpět na seznam")
                    }
                }, actions = {
                    if (state.detail == null && cash.detail == null) IconButton(onClick = {
                        when (tab) { DashboardTab.Overview -> model.refreshOverview(); DashboardTab.Invoices -> model.refresh(direction); DashboardTab.Cash -> cashModel.refresh(); DashboardTab.Company -> if (partnersOpen) partnersModel.refresh() else companyModel.refresh() }
                    }) { Icon(Icons.Default.Refresh, contentDescription = "Obnovit") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Menu") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            if (tab == DashboardTab.Invoices && state.detail == null && cash.detail == null) DropdownMenuItem(
                                text = { Text(if (exporting) "Exportuji…" else "Exportovat seznam do CSV") }, enabled = !exporting, onClick = {
                                    menu = false; exporting = true
                                    scope.launch {
                                        try { shareInvoicesCsv(context, model.fetchAllMatching(direction), direction == InvoiceDirection.Outgoing)
                                        } catch (error: kotlinx.coroutines.CancellationException) { throw error
                                        } catch (error: Exception) { model.showMessage((error as? ApiException)?.message ?: "Export se nezdařil.")
                                        } finally { exporting = false }
                                    }
                                })
                            DropdownMenuItem(text = { Text("Nastavení") }, onClick = { menu = false; settingsOpen = true })
                            DropdownMenuItem(text = { Text("Odhlásit se") }, onClick = { menu = false; onLogout() })
                        }
                    }
                })
        },
        floatingActionButton = {
            if (tab == DashboardTab.Invoices && state.detail == null && cash.detail == null &&
                (if (direction == InvoiceDirection.Outgoing) state.canCreateOutgoing else state.canCreateIncoming)) {
                FloatingActionButton(onClick = { model.openCreate(direction) }) { Icon(Icons.Default.Add, contentDescription = "Nová faktura") }
            }
        },
        bottomBar = {
            if (state.detail == null && cash.detail == null) NavigationBar {
                DashboardTab.entries.forEach { item ->
                    // Opakované klepnutí na už otevřenou záložku Firma otevře přepínač firem.
                    NavigationBarItem(selected = tab == item, onClick = { if (item == DashboardTab.Company && tab == item) switcher = true else tab = item },
                        icon = { Icon(when (item) { DashboardTab.Overview -> Icons.Default.Home; DashboardTab.Company -> Icons.Default.AccountBox
                            else -> Icons.Default.List }, contentDescription = null) },
                        label = { Text(item.title) })
                }
            }
        },
    ) { padding ->
        val modifier = Modifier.fillMaxSize().padding(padding)
        val detail = state.detail
        if (settingsOpen) SettingsScreen(onLogout, modifier)
        else if (detail != null) InvoiceDetail(detail, state.detailLoading, state.detailError,
            onRetry = { model.openInvoice(state.detailDirection, detail) },
            onDuplicate = { model.openCreate(state.detailDirection, detail.id) },
            canDuplicate = if (state.detailDirection == InvoiceDirection.Outgoing) state.canCreateOutgoing else state.canCreateIncoming,
            recipient = if (state.detailDirection == InvoiceDirection.Outgoing) company.name else detail.partner,
            actions = if (state.detailDirection == InvoiceDirection.Outgoing) InvoiceActions(
                canPay = detail.statusId !in listOf(1, 8, 10, 11), busy = state.actionBusy,
                remindersEnabled = remindersEnabled, companyName = company.name,
                lastReminder = AppSettings.lastReminder(context, detail.id)?.format(java.time.format.DateTimeFormatter.ofPattern("d. M. yyyy")),
                onPay = { model.payInvoice(detail) }, onEmail = { email, comment, done -> model.sendEmail(detail, email, comment, done) },
                onReminderSent = { AppSettings.recordReminder(context, detail.id) }) else null,
            modifier = modifier)
        else if (tab == DashboardTab.Company && partnersOpen) {
            if (partners.detail != null) PartnerDetailScreen(partners, partnersModel, canCreatePartner, modifier)
            else PartnersScreen(partners, partnersModel, canCreatePartner, modifier)
        }
        else if (tab == DashboardTab.Company) CompanyScreen(company, profile, companyModel::refresh, companyModel::openAccount, { switcher = true },
            { partnersOpen = true; partnersModel.start() }, modifier)
        else if (cash.detail != null || tab == DashboardTab.Cash) CashScreen(cash, cashModel, modifier)
        else if (tab == DashboardTab.Overview) OverviewScreen(state, model::selectYear, model::refreshOverview, modifier)
        else InvoiceListScreen(if (direction == InvoiceDirection.Outgoing) state.outgoing else state.incoming, direction,
            onDirection = { direction = it }, onSearch = { model.search(direction, it) }, onFilter = { model.filter(direction, it) },
            onNext = { model.nextPage(direction) }, onRetry = { if ((if (direction == InvoiceDirection.Outgoing) state.outgoing else state.incoming).items.isEmpty()) model.refresh(direction) else model.nextPage(direction) },
            onOpen = { model.openInvoice(direction, it) }, onAdvanced = { model.advancedFilter(direction, it) }, modifier = modifier)
    }
}

@Composable
private fun OverviewScreen(state: DashboardState, onYear: (Int) -> Unit, onRetry: () -> Unit, modifier: Modifier) {
    var yearMenu by remember { mutableStateOf(false) }
    LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Přehled hospodaření", style = MaterialTheme.typography.titleMedium)
                Box {
                    OutlinedButton(onClick = { yearMenu = true }) { Text(state.year.toString()) }
                    DropdownMenu(expanded = yearMenu, onDismissRequest = { yearMenu = false }) {
                        val current = LocalDate.now().year
                        (current downTo current - 5).forEach { year ->
                            DropdownMenuItem(text = { Text(year.toString()) }, onClick = { yearMenu = false; onYear(year) })
                        }
                    }
                }
            }
        }
        if (state.overviewLoading) item { Loading("Načítám přehled…") }
        state.overviewError?.let { error -> item { ErrorCard(error, onRetry) } }
        val overview = state.overview
        if (overview != null && overview.sections.isEmpty() && !state.overviewLoading) item {
            Text(if (overview.fromInvoices) "Za rok ${state.year} nejsou vystavené ani přijaté faktury."
                else "Za rok ${state.year} nejsou v účetním deníku žádné údaje.")
        }
        overview?.sections?.forEach { section ->
            item(key = "result-${section.currency}") {
                Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Ink)) {
                    Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Ink, InkSoft))).padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Hospodářský výsledek · ${state.year}", color = Color.White.copy(alpha = .7f), style = MaterialTheme.typography.labelLarge)
                        Text(money(section.result, section.currency), color = Color.White, fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.headlineMedium)
                        if (overview.fromInvoices) Text("Přehled z faktur · částky bez DPH", color = Color.White.copy(alpha = .7f),
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item(key = "totals-${section.currency}") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FigureCard(if (overview.fromInvoices) "Vydané" else "Výnosy", section.revenue, section.currency, Brand, Modifier.weight(1f))
                    FigureCard(if (overview.fromInvoices) "Přijaté" else "Náklady", section.cost, section.currency, Color(0xFFF59E0B), Modifier.weight(1f))
                }
            }
            item(key = "chart-${section.currency}") { MonthlyChart(section, overview.fromInvoices) }
        }
    }
}
@Composable
private fun FigureCard(title: String, amount: BigDecimal, currency: String, color: Color, modifier: Modifier) {
    Card(modifier, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = color, style = MaterialTheme.typography.labelLarge)
            Text(money(amount, currency), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}
@Composable
private fun MonthlyChart(section: OverviewSection, fromInvoices: Boolean) {
    var selected by rememberSaveable(section.currency) { mutableStateOf(LocalDate.now().monthValue) }
    val revenueLabel = if (fromInvoices) "Vydané" else "Výnosy"
    val costLabel = if (fromInvoices) "Přijaté" else "Náklady"
    val figure = section.months.first { it.month == selected }
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Po měsících · ${section.currency}", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(revenueLabel, color = Brand, style = MaterialTheme.typography.labelMedium)
                Text(costLabel, color = Color(0xFFF59E0B), style = MaterialTheme.typography.labelMedium)
            }
            val axisColor = MaterialTheme.colorScheme.outlineVariant
            Canvas(Modifier.fillMaxWidth().height(160.dp).semantics {
                contentDescription = "Měsíční graf. Hodnoty zvoleného měsíce jsou uvedené pod grafem."
            }) {
                val values = section.months.flatMap { listOf(it.revenue.toFloat(), it.cost.toFloat()) }
                val maximum = max(1f, values.maxOrNull() ?: 1f)
                val minimum = kotlin.math.min(0f, values.minOrNull() ?: 0f)
                val span = maximum - minimum
                val baseline = size.height * maximum / span
                drawLine(axisColor, Offset(0f, baseline), Offset(size.width, baseline), 1.dp.toPx())
                val slot = size.width / 12f
                val width = slot * .28f
                section.months.forEachIndexed { index, month ->
                    listOf(month.revenue.toFloat() to Brand, month.cost.toFloat() to Color(0xFFF59E0B)).forEachIndexed { side, (amount, color) ->
                        val y = baseline - amount / span * size.height
                        drawRect(color, Offset(slot * index + slot * .12f + side * width * 1.25f, kotlin.math.min(y, baseline)),
                            Size(width, abs(baseline - y)))
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                (1..12).forEach { Text(it.toString(), style = MaterialTheme.typography.labelSmall) }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..12).forEach { month -> FilterChip(selected == month, onClick = { selected = month }, label = { Text("$month") }) }
            }
            Text("Měsíc $selected", style = MaterialTheme.typography.labelLarge)
            Text("$revenueLabel: ${money(figure.revenue, section.currency)}")
            Text("$costLabel: ${money(figure.cost, section.currency)}")
            Text("Výsledek: ${money(figure.revenue - figure.cost, section.currency)}")
        }
    }
}

@Composable
private fun InvoiceListScreen(state: InvoiceListState, direction: InvoiceDirection, onDirection: (InvoiceDirection) -> Unit,
    onSearch: (String) -> Unit, onFilter: (InvoiceFilter) -> Unit, onNext: () -> Unit, onRetry: () -> Unit,
    onOpen: (Invoice) -> Unit, onAdvanced: (InvoiceAdvancedFilter) -> Unit, modifier: Modifier) {
    var showFilters by remember { mutableStateOf(false) }
    if (showFilters) InvoiceFilterSheet(state.query.advanced, onDismiss = { showFilters = false }, onApply = onAdvanced)
    LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            TabRow(selectedTabIndex = direction.ordinal) {
                InvoiceDirection.entries.forEach { side ->
                    Tab(selected = direction == side, onClick = { onDirection(side) }, text = { Text(side.title) })
                }
            }
        }
        item {
            OutlinedTextField(state.query.search, onValueChange = onSearch, singleLine = true,
                modifier = Modifier.fillMaxWidth(), label = { Text("Hledat číslo faktury nebo partnera") }, shape = RoundedCornerShape(14.dp))
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InvoiceFilter.entries.forEach { filter ->
                    FilterChip(state.query.filter == filter, onClick = { onFilter(filter) }, label = { Text(filter.title) })
                }
            }
        }
        item {
            OutlinedButton(onClick = { showFilters = true }, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.query.advanced.activeCount == 0) "Podrobné filtry" else "Podrobné filtry (${state.query.advanced.activeCount})")
            }
        }
        items(state.items, key = { it.id }) { invoice -> InvoiceCard(invoice, onClick = { onOpen(invoice) }) }
        state.error?.let { error -> item { ErrorCard(error, onRetry) } }
        if (state.loading) item { Loading("Načítám faktury…") }
        if (!state.loading && state.error == null && state.items.isEmpty()) item { Text("Žádné faktury neodpovídají výběru.") }
        if (!state.loading && state.hasMore && state.error == null) item {
            OutlinedButton(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text("Načíst další") }
        }
    }
}
@Composable
private fun InvoiceCard(invoice: Invoice, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(invoice.number, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                InvoiceBadge(invoice)
            }
            Text(invoice.partner ?: "—", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(money(invoice.total, invoice.currency), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold)
                invoice.due?.let { Text(dateLabel(it), color = if (invoice.isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}
@Composable
private fun InvoiceBadge(invoice: Invoice) {
    val color = when {
        invoice.isOverdue -> MaterialTheme.colorScheme.error
        invoice.statusId in listOf(8, 11) -> Brand
        invoice.statusId == 10 -> MaterialTheme.colorScheme.error
        invoice.statusId in listOf(2, 3) -> Color(0xFF3B82F6)
        invoice.statusId in listOf(5, 6, 7) -> Color(0xFFF59E0B)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(color = color.copy(alpha = .13f), shape = RoundedCornerShape(8.dp)) {
        Text(if (invoice.isOverdue) "Po splatnosti" else invoice.status, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            color = color, style = MaterialTheme.typography.labelSmall)
    }
}
@Composable
private fun InvoiceDetail(invoice: Invoice, loading: Boolean, error: String?, onRetry: () -> Unit,
    onDuplicate: () -> Unit, canDuplicate: Boolean, recipient: String?, actions: InvoiceActions?, modifier: Modifier) {
    var confirmPay by remember(invoice.id) { mutableStateOf(false) }
    var emailDialog by remember(invoice.id) { mutableStateOf(false) }
    var reminderDialog by remember(invoice.id) { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    if (confirmPay && actions != null) AlertDialog(onDismissRequest = { confirmPay = false }, title = { Text("Uhradit fakturu?") },
        text = { Text("Faktura ${invoice.number} se označí jako zaplacená a vytvoří se pokladní doklad. Akci nelze vrátit zpět.") },
        confirmButton = { TextButton(onClick = { confirmPay = false; actions.onPay() }) { Text("Uhradit") } },
        dismissButton = { TextButton(onClick = { confirmPay = false }) { Text("Zrušit") } })
    if (emailDialog && actions != null) SendEmailDialog("Odeslat fakturu e-mailem", invoice.partnerEmail.orEmpty(), "", "Zpráva (nepovinná)", null,
        actions.onEmail, onDismiss = { emailDialog = false })
    if (reminderDialog && actions != null) SendEmailDialog("Odeslat upomínku", invoice.partnerEmail.orEmpty(),
        Reminders.render(AppSettings.reminderTemplate(context), invoice, actions.companyName), "Text upomínky",
        "Zpráva se odešle zákazníkovi spolu s fakturou. Výchozí text změníte v Nastavení." + (actions.lastReminder?.let { " Poslední upomínka byla odeslána $it." } ?: ""),
        { email, comment, done -> actions.onEmail(email, comment) { failure -> if (failure == null) actions.onReminderSent(); done(failure) } },
        onDismiss = { reminderDialog = false })
    var showQr by remember(invoice.id) { mutableStateOf(false) }
    if (showQr) InvoiceQrSheet(invoice, recipient, onDismiss = { showQr = false })
    LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Ink)) {
                Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(invoice.partner ?: "—", color = Color.White, style = MaterialTheme.typography.titleLarge)
                    Text(money(invoice.total, invoice.currency), color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(invoice.status, color = Color.White.copy(alpha = .7f))
                }
            }
        }
        if (!loading && error == null) item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (canDuplicate) OutlinedButton(onClick = onDuplicate, modifier = Modifier.weight(1f)) { Text("Duplikovat") }
                if (PaymentQr.payload(invoice, recipient) != null) OutlinedButton(onClick = { showQr = true }, modifier = Modifier.weight(1f)) { Text("QR platba") }
            }
        }
        if (!loading && error == null && actions != null) item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (actions.canPay) Button(onClick = { confirmPay = true }, enabled = !actions.busy, modifier = Modifier.fillMaxWidth()) {
                    Text(if (actions.busy) "Zpracovávám…" else "Uhradit fakturu")
                }
                OutlinedButton(onClick = { emailDialog = true }, modifier = Modifier.fillMaxWidth()) { Text("Odeslat e-mailem") }
                if (actions.remindersEnabled && invoice.isOverdue) OutlinedButton(onClick = { reminderDialog = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Odeslat upomínku")
                }
            }
        }
        if (loading) item { Loading("Načítám detail…") }
        error?.let { item { ErrorCard(it, onRetry) } }
        item {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Údaje faktury", style = MaterialTheme.typography.titleMedium)
                    DetailRow("Vystaveno", dateLabel(invoice.issue))
                    DetailRow("Splatnost", dateLabel(invoice.due))
                    DetailRow("Variabilní symbol", invoice.variableSymbol ?: "—")
                    invoice.order?.let { DetailRow("Číslo objednávky", it) }
                    (invoice.iban ?: invoice.bankIban)?.let { DetailRow("IBAN", it) }
                    invoice.bankNumber?.let { DetailRow("Bankovní účet", it) }
                    DetailRow("Základ", money(invoice.base, invoice.currency))
                    DetailRow("Celkem", money(invoice.total, invoice.currency))
                }
            }
        }
        items(invoice.items, key = { it.id }) { line ->
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(line.name, style = MaterialTheme.typography.titleMedium)
                    Text(listOfNotNull(line.quantity, line.unit).joinToString(" "))
                    line.vat?.let { Text("DPH $it %", style = MaterialTheme.typography.bodySmall) }
                    Text(money(line.total, invoice.currency), fontWeight = FontWeight.SemiBold)
                }
            }
        }
        invoice.note?.let { item { Text(it) } }
        invoice.footer?.let { item { Text(it) } }
    }
}
@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(1f))
    }
}
@Composable
internal fun Loading(label: String) {
    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        Text(label)
    }
}
@Composable
internal fun ErrorCard(error: String, onRetry: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(error, color = MaterialTheme.colorScheme.error)
        OutlinedButton(onClick = onRetry) { Text("Zkusit znovu") }
    }
}
@Composable
private fun CashScreen(state: CashState, model: CashViewModel, modifier: Modifier) {
    val detail = state.detail
    LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (detail == null) {
            item {
                TabRow(selectedTabIndex = state.direction.ordinal) {
                    CashDirection.entries.forEach { direction ->
                        Tab(selected = state.direction == direction, onClick = { model.select(direction) }, text = { Text(direction.title) })
                    }
                }
            }
            item {
                OutlinedTextField(state.search, onValueChange = model::search, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp), label = { Text("Hledat číslo dokladu nebo partnera") })
            }
            items(state.items, key = { it.id }) { voucher ->
                Card(onClick = { model.open(voucher) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(voucher.number, style = MaterialTheme.typography.labelLarge)
                        Text(voucher.recipient ?: voucher.description ?: "—", style = MaterialTheme.typography.titleMedium)
                        Text(money(voucher.total, "Kč"), fontWeight = FontWeight.SemiBold)
                        voucher.date?.let { Text(dateLabel(it), style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
            if (state.loading) item { Loading("Načítám doklady…") }
            state.error?.let { error -> item { ErrorCard(error) { if (state.items.isEmpty()) model.refresh() else model.nextPage() } } }
            if (!state.loading && state.error == null && state.items.isEmpty()) item { Text("Žádné doklady neodpovídají výběru.") }
            if (!state.loading && state.hasMore && state.error == null) item {
                OutlinedButton(onClick = model::nextPage, modifier = Modifier.fillMaxWidth()) { Text("Načíst další") }
            }
        } else {
            item {
                Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Ink)) {
                    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(detail.recipient ?: "—", color = Color.White, style = MaterialTheme.typography.titleLarge)
                        Text(money(detail.total, "Kč"), color = Color.White, style = MaterialTheme.typography.headlineMedium)
                    }
                }
            }
            if (state.detailLoading) item { Loading("Načítám detail…") }
            state.detailError?.let { error -> item { ErrorCard(error) { model.open(detail) } } }
            item {
                Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        DetailRow("Datum", dateLabel(detail.date))
                        DetailRow("Externí číslo", detail.externalNumber ?: "—")
                        DetailRow("Účet", detail.account ?: "—")
                        DetailRow("Základ", money(detail.base, "Kč"))
                    }
                }
            }
            detail.description?.let { item { Text(it) } }
            items(detail.items, key = { it.id }) { line ->
                Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(line.name, style = MaterialTheme.typography.titleMedium)
                        Text(listOfNotNull(line.quantity, line.unit).joinToString(" "))
                        Text(money(line.total, "Kč"), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}