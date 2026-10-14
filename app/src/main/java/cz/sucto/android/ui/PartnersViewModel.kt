package cz.sucto.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import cz.sucto.android.data.*
import java.text.Collator
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PartnersState(val search: String = "", val items: List<PartnerRecord> = emptyList(), val loading: Boolean = false,
    val error: String? = null, val message: String? = null, val creating: Boolean = false,
    val detail: PartnerRecord? = null, val detailLoading: Boolean = false, val detailError: String? = null, val deleting: Boolean = false)

/**
 * Partneři firmy. Server nemá zdokumentované řazení, proto se načtou všechny stránky a seřadí se podle názvu A–Z
 * (české řazení bez ohledu na velikost písmen), ať položky při dalším načítání neskáčou.
 */
class PartnersViewModel(private val api: SuctoApi, private val token: String, private val companyId: Int,
    private val onUnauthorized: () -> Unit) : ViewModel() {
    private val mutableState = MutableStateFlow(PartnersState())
    val state = mutableState.asStateFlow()
    private var listJob: Job? = null
    private var generation = 0
    private val collator = Collator.getInstance(Locale.forLanguageTag("cs-CZ")).apply { strength = Collator.SECONDARY }

    fun start() { if (state.value.items.isEmpty()) refresh() }
    fun stop() { listJob?.cancel(); mutableState.update { it.copy(loading = false) } }

    fun search(text: String) {
        mutableState.update { it.copy(search = text) }
        refresh(debounce = true)
    }

    fun refresh(debounce: Boolean = false) {
        listJob?.cancel()
        val current = ++generation
        mutableState.update { it.copy(loading = true, error = null) }
        listJob = viewModelScope.launch {
            try {
                if (debounce) delay(400)
                val term = state.value.search.trim()
                val collected = mutableListOf<PartnerRecord>()
                val seen = mutableSetOf<Int>()
                for (page in 1..MAXIMUM_PAGES) {
                    val result = api.partnerRecords(token, companyId, term, page, PAGE_SIZE)
                    if (current != generation) return@launch
                    val fresh = result.filter { seen.add(it.id) }
                    if (fresh.isEmpty()) break
                    collected += fresh
                }
                mutableState.update { it.copy(items = collected.sortedWith(compareBy(collator) { p -> p.name }), loading = false) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: ApiException) {
                if (current != generation) return@launch
                if (error.unauthorized) onUnauthorized() else mutableState.update { it.copy(loading = false, error = error.message) }
            } catch (error: Exception) {
                if (current == generation) mutableState.update { it.copy(loading = false, error = "Odpověď serveru se nepodařilo zpracovat.") }
            }
        }
    }

    fun open(partner: PartnerRecord) {
        mutableState.update { it.copy(detail = partner, detailLoading = true, detailError = null) }
        viewModelScope.launch {
            try {
                val loaded = api.partnerRecord(token, companyId, partner.id)
                mutableState.update { if (it.detail?.id == partner.id) it.copy(detail = loaded, detailLoading = false) else it }
            } catch (error: CancellationException) {
                throw error
            } catch (error: ApiException) {
                if (error.unauthorized) onUnauthorized() else mutableState.update { it.copy(detailLoading = false, detailError = error.message) }
            } catch (error: Exception) {
                mutableState.update { it.copy(detailLoading = false, detailError = "Odpověď serveru se nepodařilo zpracovat.") }
            }
        }
    }
    fun closeDetail() = mutableState.update { it.copy(detail = null, detailError = null, deleting = false) }
    fun clearMessage() = mutableState.update { it.copy(message = null) }

    fun createFromAres(ic: String) {
        val digits = ic.filter(Char::isDigit)
        if (digits.length != 8) { mutableState.update { it.copy(message = "IČO má mít 8 číslic.") }; return }
        if (state.value.creating) return
        mutableState.update { it.copy(creating = true) }
        viewModelScope.launch {
            try {
                val created = api.createPartnerByAres(token, companyId, digits)
                mutableState.update { it.copy(creating = false, items = (it.items.filter { p -> p.id != created.id } + created)
                    .sortedWith(compareBy(collator) { p -> p.name }), message = "Přidáno: ${created.name}") }
            } catch (error: CancellationException) {
                throw error
            } catch (error: ApiException) {
                if (error.unauthorized) onUnauthorized()
                // Server při chybě vrací jen název neplatného pole – zpráva by uživateli nic neřekla.
                else mutableState.update { it.copy(creating = false, message = if (error.statusCode == 400 || error.statusCode == 422)
                    "Partnera se z ARES nepodařilo vytvořit. Zkontrolujte IČO, případně partner už může existovat." else error.message) }
            } catch (error: Exception) {
                mutableState.update { it.copy(creating = false, message = "Odpověď serveru se nepodařilo zpracovat.") }
            }
        }
    }

    fun delete() {
        val partner = state.value.detail ?: return
        if (state.value.deleting) return
        mutableState.update { it.copy(deleting = true) }
        viewModelScope.launch {
            try {
                api.deletePartner(token, companyId, partner.id)
                mutableState.update { it.copy(detail = null, deleting = false, items = it.items.filter { p -> p.id != partner.id }) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: ApiException) {
                if (error.unauthorized) onUnauthorized() else mutableState.update { it.copy(deleting = false, message = error.message) }
            } catch (error: Exception) {
                mutableState.update { it.copy(deleting = false, message = "Odpověď serveru se nepodařilo zpracovat.") }
            }
        }
    }

    class Factory(private val api: SuctoApi, private val token: String, private val companyId: Int,
        private val onUnauthorized: () -> Unit) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(PartnersViewModel::class.java))
            return PartnersViewModel(api, token, companyId, onUnauthorized) as T
        }
    }

    private companion object {
        const val PAGE_SIZE = 100
        const val MAXIMUM_PAGES = 30
    }
}
