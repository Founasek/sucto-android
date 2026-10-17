package cz.sucto.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewModelScope
import cz.sucto.android.data.*
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class InvoiceListState(val query: InvoiceQuery = InvoiceQuery(), val items: List<Invoice> = emptyList(),
    val loading: Boolean = false, val hasMore: Boolean = true, val nextPage: Int = 1, val error: String? = null)
data class DashboardState(val year: Int = LocalDate.now().year, val overview: Overview? = null,
    val message: String? = null,
    val canCreateOutgoing: Boolean = true, val canCreateIncoming: Boolean = true,
    val overviewLoading: Boolean = false, val overviewError: String? = null,
    val outgoing: InvoiceListState = InvoiceListState(), val incoming: InvoiceListState = InvoiceListState(),
    val createDirection: InvoiceDirection? = null, val copyInvoiceId: Int? = null, val detailDirection: InvoiceDirection = InvoiceDirection.Outgoing,
    val detail: Invoice? = null, val detailLoading: Boolean = false, val detailError: String? = null, val actionBusy: Boolean = false,
    val aging: AgingSummary? = null, val forecast: ForecastSummary? = null)

class DashboardViewModel(private val api: SuctoApi, private val token: String, private val companyId: Int,
    private val onUnauthorized: () -> Unit) : ViewModel() {
    private val mutableState = MutableStateFlow(DashboardState())
    val state = mutableState.asStateFlow()
    private val createStore = ViewModelStore()
    val createOwner: ViewModelStoreOwner = object : ViewModelStoreOwner { override val viewModelStore = createStore }
    override fun onCleared() { createStore.clear() }
    private var permissionsJob: Job? = null
    private var overviewJob: Job? = null
    private var detailJob: Job? = null
    private var dueJob: Job? = null
    private val listJobs = mutableMapOf<InvoiceDirection, Job>()
    private val generations = mutableMapOf<InvoiceDirection, Int>()
    private var overviewGeneration = 0
    private var detailGeneration = 0

    fun start() {
        loadPermissions()
        refreshOverview()
        loadDueItems()
        refresh(InvoiceDirection.Outgoing)
        refresh(InvoiceDirection.Incoming)
        state.value.detail?.let { openInvoice(state.value.detailDirection, it) }
    }
    fun stop() {
        overviewGeneration++; detailGeneration++
        InvoiceDirection.entries.forEach { generations[it] = (generations[it] ?: 0) + 1 }
        permissionsJob?.cancel(); dueJob?.cancel(); overviewJob?.cancel(); detailJob?.cancel(); listJobs.values.forEach { it.cancel() }
    }
    private fun loadPermissions() {
        permissionsJob?.cancel()
        permissionsJob = viewModelScope.launch {
            for (direction in InvoiceDirection.entries) {
                try {
                    val allowed = api.canCreateInvoice(token, companyId, direction)
                    mutableState.update { if (direction == InvoiceDirection.Outgoing) it.copy(canCreateOutgoing = allowed) else it.copy(canCreateIncoming = allowed) }
                } catch (error: CancellationException) { throw error
                } catch (error: ApiException) { if (error.unauthorized) onUnauthorized()
                } catch (error: Exception) { /* Unknown permissions leave final authorization to server, matching iOS. */ }
            }
        }
    }
    /** Stáří po splatnosti a prognóza na 30 dní; jen doplněk – při chybě nebo při datech z cache zůstane předchozí stav. */
    fun loadDueItems() {
        dueJob?.cancel()
        dueJob = viewModelScope.launch {
            try {
                val items = api.dueItems(token, companyId, LocalDate.now().plusDays(ForecastSummary.HORIZON_DAYS))
                if (api.cachedDataDate.value == null) mutableState.update { it.copy(aging = AgingSummary(items), forecast = ForecastSummary(items)) }
            } catch (error: CancellationException) { throw error
            } catch (error: ApiException) { if (error.unauthorized) onUnauthorized()
            } catch (error: Exception) { /* doplněk přehledu */ }
        }
    }
    /** Otevře detail faktury podle id a směru (z karty prognózy). */
    fun openDue(item: DueItem) {
        val direction = if (item.isIncoming) InvoiceDirection.Incoming else InvoiceDirection.Outgoing
        openInvoice(direction, Invoice(item.id, item.number, 2, "—", item.counterparty, item.amount, null, item.currency, null, null, null, null, emptyList()))
    }
    fun selectYear(year: Int) {
        if (year == state.value.year) return
        mutableState.update { it.copy(year = year, overview = null) }
        refreshOverview()
    }
    fun refreshOverview() {
        loadDueItems()
        overviewJob?.cancel()
        val generation = ++overviewGeneration
        val year = state.value.year
        overviewJob = viewModelScope.launch {
            mutableState.update { it.copy(overviewLoading = true, overviewError = null) }
            try {
                val overview = api.overview(token, companyId, year)
                if (generation == overviewGeneration) mutableState.update { it.copy(overview = overview, overviewLoading = false) }
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) {
                if (generation == overviewGeneration) mutableState.update { it.copy(overviewLoading = false, overviewError = handle(error)) }
            }
        }
    }
    fun list(direction: InvoiceDirection): InvoiceListState = if (direction == InvoiceDirection.Outgoing) state.value.outgoing else state.value.incoming
    private fun updateList(direction: InvoiceDirection, action: (InvoiceListState) -> InvoiceListState) {
        mutableState.update { if (direction == InvoiceDirection.Outgoing) it.copy(outgoing = action(it.outgoing)) else it.copy(incoming = action(it.incoming)) }
    }
    fun search(direction: InvoiceDirection, text: String) {
        updateList(direction) { it.copy(query = it.query.copy(search = text)) }
        refresh(direction, debounce = true)
    }
    fun filter(direction: InvoiceDirection, filter: InvoiceFilter) {
        updateList(direction) { it.copy(query = it.query.copy(filter = filter, advanced = if (filter == InvoiceFilter.Paid || filter == InvoiceFilter.Concept) it.query.advanced.copy(status = null) else it.query.advanced)) }
        refresh(direction)
    }
    fun advancedFilter(direction: InvoiceDirection, filter: InvoiceAdvancedFilter) {
        if (filter.validation() != null) return
        updateList(direction) { it.copy(query = it.query.copy(advanced = filter,
            filter = if (filter.status != null && it.query.filter in listOf(InvoiceFilter.Paid, InvoiceFilter.Concept)) InvoiceFilter.All else it.query.filter)) }
        refresh(direction)
    }
    fun refresh(direction: InvoiceDirection, debounce: Boolean = false) {
        listJobs[direction]?.cancel()
        val generation = (generations[direction] ?: 0) + 1
        generations[direction] = generation
        updateList(direction) { it.copy(items = emptyList(), nextPage = 1, hasMore = true, error = null, loading = true) }
        listJobs[direction] = viewModelScope.launch {
            if (debounce) delay(400)
            fetch(direction, generation)
        }
    }
    fun nextPage(direction: InvoiceDirection) {
        if (list(direction).loading || !list(direction).hasMore) return
        updateList(direction) { it.copy(loading = true, error = null) }
        val generation = generations[direction] ?: 0
        listJobs[direction] = viewModelScope.launch { fetch(direction, generation) }
    }
    private suspend fun fetch(direction: InvoiceDirection, generation: Int) {
        val snapshot = list(direction)
        var page = snapshot.nextPage
        var more = true
        val collected = mutableListOf<Invoice>()
        try {
            var loaded = 0
            while (loaded < 5 && more && collected.size < 10) {
                val values = api.invoices(token, companyId, direction, snapshot.query.parameters(page))
                if (generation != generations[direction]) return
                page++; loaded++; more = values.isNotEmpty()
                collected += if (snapshot.query.filter == InvoiceFilter.Overdue) values.filter { it.isOverdue } else values
            }
            updateList(direction) { it.copy(items = (snapshot.items + collected).distinctBy { invoice -> invoice.id },
                nextPage = page, hasMore = more, loading = false, error = null) }
        } catch (error: CancellationException) { throw error
        } catch (error: Exception) {
            if (generation == generations[direction]) updateList(direction) { it.copy(loading = false, error = handle(error)) }
        }
    }
    fun openInvoice(direction: InvoiceDirection, invoice: Invoice) {
        detailJob?.cancel()
        val generation = ++detailGeneration
        mutableState.update { it.copy(detail = invoice, detailDirection = direction, detailLoading = true, detailError = null) }
        detailJob = viewModelScope.launch {
            try {
                val detail = api.invoice(token, companyId, direction, invoice.id)
                if (generation == detailGeneration) mutableState.update { it.copy(detail = detail, detailLoading = false) }
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) {
                if (generation == detailGeneration) mutableState.update { it.copy(detailLoading = false, detailError = handle(error)) }
            }
        }
    }
    /** Uhradí vydanou fakturu (po potvrzení v UI): vytvoří pokladní doklad, pak obnoví detail, seznam i přehled. */
    fun payInvoice(invoice: Invoice) {
        if (state.value.actionBusy) return
        mutableState.update { it.copy(actionBusy = true) }
        viewModelScope.launch {
            try {
                val voucher = api.payInvoice(token, companyId, invoice.id)
                mutableState.update { it.copy(actionBusy = false, message = "Zaplaceno" + if (voucher > 0) " – doklad č. $voucher" else "") }
                openInvoice(InvoiceDirection.Outgoing, invoice); refresh(InvoiceDirection.Outgoing); refreshOverview()
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) { mutableState.update { it.copy(actionBusy = false, message = handle(error)) } }
        }
    }
    /** Pošle vydanou fakturu e-mailem (i jako upomínku). `done` dostane text chyby, nebo `null` při úspěchu. */
    fun sendEmail(invoice: Invoice, email: String, comment: String, done: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                api.sendInvoiceEmail(token, companyId, invoice.id, email.trim(), comment)
                mutableState.update { it.copy(message = "Odesláno: ${email.trim()}") }
                done(null)
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) { done(handle(error)) }
        }
    }
    /** Všechny faktury odpovídající hledání a filtrům daného směru (pro export do CSV). */
    suspend fun fetchAllMatching(direction: InvoiceDirection): List<Invoice> {
        val query = list(direction).query
        val all = mutableListOf<Invoice>()
        val seen = mutableSetOf<Int>()
        for (page in 1..100) {
            val values = api.invoices(token, companyId, direction, query.parameters(page))
            val fresh = values.filter { seen.add(it.id) }
            if (fresh.isEmpty()) return all
            all += if (query.filter == InvoiceFilter.Overdue) fresh.filter { it.isOverdue } else fresh
        }
        throw ApiException("Export obsahuje příliš mnoho stránek. Zpřesněte hledání nebo filtr.")
    }
    fun showMessage(text: String) = mutableState.update { it.copy(message = text) }
    fun closeInvoice() {
        detailGeneration++; detailJob?.cancel()
        mutableState.update { it.copy(detail = null, detailLoading = false, detailError = null) }
    }
    fun openCreate(direction: InvoiceDirection, copyId: Int? = null) {
        createStore.clear()
        mutableState.update { it.copy(createDirection = direction, copyInvoiceId = copyId) }
    }
    fun closeCreate() { createStore.clear(); mutableState.update { it.copy(createDirection = null, copyInvoiceId = null) } }
    fun invoiceCreated(id: Int) {
        val direction = state.value.createDirection ?: return
        closeCreate(); closeInvoice(); refresh(direction); refreshOverview()
        mutableState.update { it.copy(message = "Faktura byla vytvořena.") }
    }
    fun clearMessage() { mutableState.update { it.copy(message = null) } }
    fun createFactory(company: Company) = InvoiceCreateViewModel.Factory(api, token, company,
        requireNotNull(state.value.createDirection), state.value.copyInvoiceId, onUnauthorized)
    private fun handle(error: Exception): String {
        if (error is ApiException) {
            if (error.unauthorized) onUnauthorized()
            return error.message.orEmpty()
        }
        return "Odpověď serveru se nepodařilo zpracovat."
    }
    class Factory(private val api: SuctoApi, private val token: String, private val companyId: Int,
        private val onUnauthorized: () -> Unit) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(DashboardViewModel::class.java))
            return DashboardViewModel(api, token, companyId, onUnauthorized) as T
        }
    }
}