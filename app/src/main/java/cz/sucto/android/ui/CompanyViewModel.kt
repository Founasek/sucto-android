package cz.sucto.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import cz.sucto.android.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Profil vybrané firmy: detail firmy a její účty. Účty jsou doplněk, jejich selhání neshodí profil. */
data class CompanyState(val detail: CompanyDetail? = null, val accounts: List<CompanyAccount> = emptyList(),
    val loading: Boolean = false, val error: String? = null, val selectedAccount: CompanyAccount? = null)
class CompanyViewModel(private val api: SuctoApi, private val token: String, private val companyId: Int,
    private val onUnauthorized: () -> Unit) : ViewModel() {
    private val mutableState = MutableStateFlow(CompanyState())
    val state = mutableState.asStateFlow()
    private var job: Job? = null
    fun start() { if (state.value.detail == null) refresh() }
    fun stop() { job?.cancel(); mutableState.update { it.copy(loading = false) } }
    fun refresh() {
        job?.cancel()
        mutableState.update { it.copy(loading = true, error = null) }
        job = viewModelScope.launch {
            try {
                coroutineScope {
                    val detail = async { api.companyDetail(token, companyId) }
                    val accounts = async { try { api.accounts(token, companyId) } catch (error: ApiException) { if (error.unauthorized) throw error else null } }
                    val loadedDetail = detail.await()
                    val loadedAccounts = accounts.await()
                    mutableState.update { it.copy(detail = loadedDetail, accounts = loadedAccounts ?: it.accounts, loading = false) }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: ApiException) {
                if (error.unauthorized) onUnauthorized() else mutableState.update { it.copy(loading = false, error = error.message) }
            } catch (error: Exception) {
                mutableState.update { it.copy(loading = false, error = "Odpověď serveru se nepodařilo zpracovat.") }
            }
        }
    }
    fun openAccount(account: CompanyAccount?) = mutableState.update { it.copy(selectedAccount = account) }
    class Factory(private val api: SuctoApi, private val token: String, private val companyId: Int,
        private val onUnauthorized: () -> Unit) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(CompanyViewModel::class.java))
            return CompanyViewModel(api, token, companyId, onUnauthorized) as T
        }
    }
}
