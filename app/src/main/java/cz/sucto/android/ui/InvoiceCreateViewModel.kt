package cz.sucto.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import cz.sucto.android.data.*
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

data class InvoiceCreateState(val draft: InvoiceDraft = InvoiceDraft(), val references: DraftReferences? = null,
    val partners: List<PartnerChoice> = emptyList(), val selectedPartner: PartnerChoice? = null, val partnerSearch: String = "",
    val partnersLoading: Boolean = false, val loading: Boolean = true, val submitting: Boolean = false,
    val error: String? = null, val partnerError: String? = null, val copyWarning: String? = null, val createdId: Int? = null)
class InvoiceCreateViewModel(private val api: SuctoApi, private val token: String, val company: Company,
    val direction: InvoiceDirection, val copyId: Int?, private val onUnauthorized: () -> Unit) : ViewModel() {
    private val mutableState = MutableStateFlow(InvoiceCreateState())
    val state = mutableState.asStateFlow()
    private var initJob: Job? = null
    private var searchJob: Job? = null
    private var searchGeneration = 0
    init { load() }
    fun load() {
        initJob?.cancel()
        initJob = viewModelScope.launch {
            mutableState.value = InvoiceCreateState()
            try {
                val (seed, references) = coroutineScope {
                    val initial = async { api.newInvoice(token, company.id, direction) }
                    val refs = async { api.draftReferences(token, company) }
                    initial.await() to refs.await()
                }
                val number = seed.text("actuarial_number").orEmpty()
                val today = LocalDate.now()
                val issue = parseApiDate(seed.text("issue_date_at")) ?: today
                val items = seed.optJSONArray("items")
                val defaultVat = references.vats.firstOrNull()?.id ?: 0
                val lines = if (items == null) emptyList() else List(items.length()) { index ->
                    val line = items.getJSONObject(index)
                    DraftLine(name = line.text("name").orEmpty(), quantity = line.text("quantity") ?: "1",
                        unitPrice = line.text("unit_price") ?: "0", unit = line.text("unit_name").orEmpty(),
                        vatId = line.optInt("vat_id", 0).takeIf { it != 0 } ?: defaultVat)
                }
                val draft = InvoiceDraft(number = number, variableSymbol = if (direction == InvoiceDirection.Outgoing) number.filter { it.isDigit() } else "",
                    typeId = references.typeId, issue = issue, due = parseApiDate(seed.text("due_date_at")) ?: today,
                    uzp = parseApiDate(seed.text("uzp_date_at")) ?: issue,
                    notice = if (direction == InvoiceDirection.Outgoing) "Fakturujeme Vám následující položky:" else "", lines = lines)
                val partners = api.partners(token, company.id, "")
                mutableState.value = InvoiceCreateState(draft = draft, references = references, partners = partners, loading = copyId != null)
                copyId?.let { applyCopy(it) }
                mutableState.update { it.copy(loading = false) }
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) {
                mutableState.update { it.copy(loading = false, error = message(error)) }
            }
        }
    }
    private suspend fun applyCopy(id: Int) {
        try {
            val source = api.invoice(token, company.id, direction, id)
            val refs = requireNotNull(state.value.references)
            val term = source.partnerIc?.takeIf { it.isNotBlank() } ?: source.partner.orEmpty()
            val partners = if (term.isBlank()) state.value.partners else api.partners(token, company.id, term)
            val partner = partners.firstOrNull { source.partnerId != null && it.id == source.partnerId }
                ?: partners.firstOrNull { source.partnerIc != null && it.ic == source.partnerIc }
                ?: partners.firstOrNull { source.partner != null && it.name == source.partner }
            val days = parseApiDate(source.issue)?.let { issue -> parseApiDate(source.due)?.let { ChronoUnit.DAYS.between(issue, it) } }
            mutableState.update { current -> current.copy(partners = partners, selectedPartner = partner,
                copyWarning = if (partner == null) "Partnera ze zdrojové faktury vyberte ručně." else null,
                draft = current.draft.copy(partnerId = partner?.id,
                    accountId = source.accountId?.takeIf { account -> refs.accounts.any { it.id == account } },
                    currencyId = source.currencyId?.takeIf { currency -> refs.currencies.any { it.id == currency } },
                    due = if (days != null && days >= 0) current.draft.issue.plusDays(days) else current.draft.due,
                    notice = source.note ?: current.draft.notice, footer = source.footer.orEmpty(), order = source.order.orEmpty(),
                    lines = if (source.items.isEmpty()) current.draft.lines else source.items.map {
                        DraftLine(name = it.name, quantity = it.quantity ?: "1", unitPrice = it.unitPrice?.toPlainString() ?: "0",
                            unit = it.unit.orEmpty(), vatId = it.vatId.takeIf { vat -> refs.vats.any { rate -> rate.id == vat } } ?: (refs.vats.firstOrNull()?.id ?: 0))
                    })) }
        } catch (error: CancellationException) { throw error
        } catch (error: Exception) {
            mutableState.update { it.copy(copyWarning = "Zdrojovou fakturu se nepodařilo načíst. Údaje vyplňte ručně.", error = message(error)) }
        }
    }
    fun edit(action: (InvoiceDraft) -> InvoiceDraft) {
        if (state.value.submitting || state.value.loading) return
        mutableState.update { it.copy(draft = action(it.draft), error = null) }
    }
    fun selectPartner(partner: PartnerChoice) {
        edit { draft -> draft.copy(partnerId = partner.id,
            due = partner.dueDays?.takeIf { it > 0 }?.let { draft.issue.plusDays(it.toLong()) } ?: draft.due,
            currencyId = draft.currencyId ?: partner.currencyId?.takeIf { id -> state.value.references?.currencies?.any { it.id == id } == true }) }
        mutableState.update { it.copy(selectedPartner = partner, copyWarning = null) }
    }
    fun searchPartners(text: String) {
        searchJob?.cancel()
        val generation = ++searchGeneration
        mutableState.update { it.copy(partnerSearch = text, partnersLoading = true, partnerError = null) }
        searchJob = viewModelScope.launch {
            delay(400)
            try {
                val partners = api.partners(token, company.id, text)
                if (generation == searchGeneration) mutableState.update { it.copy(partners = partners, partnersLoading = false) }
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) {
                if (generation == searchGeneration) mutableState.update { it.copy(partnersLoading = false, partnerError = message(error)) }
            }
        }
    }
    fun addLine() = edit { it.copy(lines = it.lines + DraftLine(vatId = state.value.references?.vats?.firstOrNull()?.id ?: 0)) }
    fun updateLine(line: DraftLine) = edit { it.copy(lines = it.lines.map { old -> if (old.key == line.key) line else old }) }
    fun removeLine(key: String) = edit { it.copy(lines = it.lines.filterNot { line -> line.key == key }) }
    fun submit() {
        val current = state.value
        val refs = current.references ?: return
        if (current.submitting || current.createdId != null) return
        val draft = current.draft
        val validation = when {
            draft.number.isBlank() -> "Vyplňte číslo faktury."
            draft.partnerId == null || draft.accountId == null || draft.currencyId == null -> "Vyberte partnera, účet a měnu."
            company.isTaxable && draft.vatRegimeId == null -> "Vyberte DPH režim."
            draft.lines.isEmpty() || draft.lines.any { it.name.isBlank() } -> "Přidejte alespoň jednu pojmenovanou položku."
            draft.lines.any { decimalInput(it.quantity) == null || decimalInput(it.unitPrice) == null } -> "Množství a jednotková cena musí být čísla."
            draft.lines.any { line -> refs.vats.none { it.id == line.vatId } } -> "Vyberte platnou sazbu DPH u všech položek."
            else -> null
        }
        if (validation != null) { mutableState.update { it.copy(error = validation) }; return }
        mutableState.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            try {
                val id = api.createInvoice(token, company.id, direction, draft.json(direction, company.isTaxable, refs.accounts, refs.vats))
                mutableState.update { it.copy(submitting = false, createdId = id) }
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) {
                val text = message(error)
                mutableState.update { it.copy(submitting = false, error = if ((error !is ApiException || (error.statusCode !in listOf(400, 401, 403, 422) && !error.unauthorized)))
                    "$text Před dalším odesláním zkontrolujte v seznamu, zda faktura vznikla." else text) }
            }
        }
    }
    private fun message(error: Exception): String {
        if (error is ApiException) {
            if (error.unauthorized) onUnauthorized()
            return error.message.orEmpty()
        }
        return "Odpověď serveru se nepodařilo zpracovat."
    }
    class Factory(private val api: SuctoApi, private val token: String, private val company: Company,
        private val direction: InvoiceDirection, private val copyId: Int?, private val onUnauthorized: () -> Unit) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(InvoiceCreateViewModel::class.java))
            return InvoiceCreateViewModel(api, token, company, direction, copyId, onUnauthorized) as T
        }
    }
}