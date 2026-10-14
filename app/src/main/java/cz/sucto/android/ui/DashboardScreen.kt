package cz.sucto.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.sucto.android.data.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

enum class DashboardTab(val title: String) { Overview("Přehled"), Invoices("Faktury"), Cash("Pokladna"), Company("Firma") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(company: Company, companies: List<Company>, model: DashboardViewModel, cashModel: CashViewModel,
    companyModel: CompanyViewModel, partnersModel: PartnersViewModel, offlineSince: Long?, onSwitchCompany: (Company) -> Unit,
    onCompanies: () -> Unit, onLogout: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val cash by cashModel.state.collectAsStateWithLifecycle()
    val profile by companyModel.state.collectAsStateWithLifecycle()
    val partners by partnersModel.state.collectAsStateWithLifecycle()
    var switcher by remember { mutableStateOf(false) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var partnersOpen by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { mutableStateOf(DashboardTab.Overview) }
    var direction by rememberSaveable { mutableStateOf(InvoiceDirection.Outgoing) }
    var menu by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    // Přepínač upomínek se načte znovu po návratu z Nastavení.
    val remindersEnabled = remember(settingsOpen) { AppSettings.remindersEnabled(context) }
    // Oprávnění Partner se při neznámém stavu nechává na serveru, jako v iOS.
    val canManagePartners = true

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

    val inCompany = tab == DashboardTab.Company
    val canGoBack = settingsOpen || state.detail != null || cash.detail != null || (inCompany && (profile.selectedAccount != null || partnersOpen))
    val goBack: () -> Unit = {
        when {
            settingsOpen -> settingsOpen = false
            state.detail != null -> model.closeInvoice()
            cash.detail != null -> cashModel.close()
            inCompany && partners.detail != null -> partnersModel.closeDetail()
            inCompany && partnersOpen -> partnersOpen = false
            inCompany && profile.selectedAccount != null -> companyModel.openAccount(null)
        }
    }
    BackHandler { if (canGoBack) goBack() else onCompanies() }

    val title = when {
        settingsOpen -> "Nastavení"
        state.detail != null -> state.detail?.number
        cash.detail != null -> cash.detail?.number
        inCompany && partnersOpen -> partners.detail?.name ?: "Partneři"
        inCompany -> profile.selectedAccount?.name
        else -> null
    } ?: company.name

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background),
                navigationIcon = {
                    if (canGoBack) IconButton(onClick = goBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Zpět") }
                },
                actions = {
                    if (state.detail == null && cash.detail == null) IconButton(onClick = {
                        when (tab) {
                            DashboardTab.Overview -> model.refreshOverview()
                            DashboardTab.Invoices -> model.refresh(direction)
                            DashboardTab.Cash -> cashModel.refresh()
                            DashboardTab.Company -> if (partnersOpen) partnersModel.refresh() else companyModel.refresh()
                        }
                    }) { Icon(Icons.Default.Refresh, contentDescription = "Obnovit") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Menu") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            if (tab == DashboardTab.Invoices && state.detail == null && cash.detail == null) DropdownMenuItem(
                                text = { Text(if (exporting) "Exportuji…" else "Exportovat seznam do CSV") }, enabled = !exporting,
                                onClick = {
                                    menu = false; exporting = true
                                    scope.launch {
                                        try {
                                            shareInvoicesCsv(context, model.fetchAllMatching(direction), direction == InvoiceDirection.Outgoing)
                                        } catch (error: CancellationException) {
                                            throw error
                                        } catch (error: Exception) {
                                            model.showMessage((error as? ApiException)?.message ?: "Export se nezdařil.")
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
            val canCreate = if (direction == InvoiceDirection.Outgoing) state.canCreateOutgoing else state.canCreateIncoming
            if (tab == DashboardTab.Invoices && !settingsOpen && state.detail == null && cash.detail == null && canCreate) {
                GradientFab(if (direction == InvoiceDirection.Outgoing) "Nová faktura" else "Nová přijatá", { model.openCreate(direction) },
                    icon = Icons.Default.Add)
            }
        },
        bottomBar = {
            if (state.detail == null && cash.detail == null) NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                DashboardTab.entries.forEach { item ->
                    // Opakované klepnutí na už otevřenou záložku Firma otevře přepínač firem.
                    NavigationBarItem(selected = tab == item && !settingsOpen,
                        onClick = { settingsOpen = false; if (item == DashboardTab.Company && tab == item) switcher = true else tab = item },
                        icon = {
                            Icon(when (item) {
                                DashboardTab.Overview -> Icons.Default.Home
                                DashboardTab.Company -> Icons.Default.AccountBox
                                else -> Icons.Default.List
                            }, contentDescription = null)
                        },
                        label = { Text(item.title) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = .16f),
                            selectedIconColor = MaterialTheme.colorScheme.primary, selectedTextColor = MaterialTheme.colorScheme.primary))
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (offlineSince != null) {
                Notice("Offline – data z " + Instant.ofEpochMilli(offlineSince).atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("d. M. yyyy H:mm")))
            }
            Box(Modifier.weight(1f)) {
                val modifier = Modifier.fillMaxSize()
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
                        lastReminder = AppSettings.lastReminder(context, detail.id)?.format(DateTimeFormatter.ofPattern("d. M. yyyy")),
                        onPay = { model.payInvoice(detail) },
                        onEmail = { email, comment, done -> model.sendEmail(detail, email, comment, done) },
                        onReminderSent = { AppSettings.recordReminder(context, detail.id) }) else null,
                    modifier = modifier)
                else if (inCompany && partnersOpen) {
                    if (partners.detail != null) PartnerDetailScreen(partners, partnersModel, canManagePartners, modifier)
                    else PartnersScreen(partners, partnersModel, canManagePartners, modifier)
                } else if (inCompany) CompanyScreen(company, profile, companyModel::refresh, companyModel::openAccount, { switcher = true },
                    { partnersOpen = true; partnersModel.start() }, modifier)
                else if (cash.detail != null || tab == DashboardTab.Cash) CashScreen(cash, cashModel, modifier)
                else if (tab == DashboardTab.Overview) OverviewScreen(state, model::selectYear, model::refreshOverview, modifier)
                else {
                    val list = if (direction == InvoiceDirection.Outgoing) state.outgoing else state.incoming
                    InvoiceListScreen(list, direction, onDirection = { direction = it }, onSearch = { model.search(direction, it) },
                        onFilter = { model.filter(direction, it) }, onNext = { model.nextPage(direction) },
                        onRetry = { if (list.items.isEmpty()) model.refresh(direction) else model.nextPage(direction) },
                        onOpen = { model.openInvoice(direction, it) }, onAdvanced = { model.advancedFilter(direction, it) }, modifier = modifier)
                }
            }
        }
    }
}
