package cz.sucto.android.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.sucto.android.data.ApiException
import cz.sucto.android.data.Company
import cz.sucto.android.data.SessionStore
import cz.sucto.android.data.SuctoApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Token se ukládá zašifrovaný klíčem z Android Keystore (SessionStore); zamyká ho AppLock. */
data class AuthState(
    val signedIn: Boolean = false,
    val sessionId: Long = 0,
    val selectedCompany: Company? = null,
    val loading: Boolean = false,
    val companies: List<Company> = emptyList(),
    val error: String? = null,
)
class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val api = SuctoApi()
    private val store = SessionStore(application)
    private val companyStore = ViewModelStore()
    val companyOwner: ViewModelStoreOwner = object : ViewModelStoreOwner { override val viewModelStore = companyStore }
    override fun onCleared() { companyStore.clear() }
    private var token: String? = null
    private val mutableState = MutableStateFlow(AuthState())
    val state = mutableState.asStateFlow()

    init {
        // Obnovení relace po spuštění: token ze Keystore, firmy se načtou na pozadí.
        store.loadToken()?.let { saved ->
            token = saved
            mutableState.value = AuthState(signedIn = true, sessionId = System.nanoTime(), loading = true)
            viewModelScope.launch { loadCompanies(restore = true) }
        }
    }

    fun login(email: String, password: String) {
        if (state.value.loading || email.isBlank() || password.isEmpty()) return
        viewModelScope.launch {
            mutableState.value = AuthState(loading = true)
            try {
                token = api.login(email.trim(), password)
                token?.let { runCatching { store.saveToken(it) } }
                mutableState.value = AuthState(signedIn = true, sessionId = System.nanoTime(), loading = true)
                loadCompanies()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutableState.update { it.copy(loading = false, error = message(error)) }
            }
        }
    }

    fun refreshCompanies() {
        if (state.value.loading || token == null) return
        viewModelScope.launch {
            mutableState.update { it.copy(loading = true, error = null) }
            loadCompanies()
        }
    }

    private suspend fun loadCompanies(restore: Boolean = false) {
        val currentToken = token ?: return
        try {
            val companies = api.companies(currentToken)
            val remembered = if (restore) companies.find { it.id == store.lastCompanyId } else null
            mutableState.update { it.copy(signedIn = true, loading = false, companies = companies, error = null, selectedCompany = remembered ?: it.selectedCompany) }
        } catch (error: CancellationException) {
            throw error
        } catch (error: ApiException) {
            if (error.unauthorized) {
                token = null
                store.clear()
                mutableState.value = AuthState(error = error.message)
            } else mutableState.update { it.copy(loading = false, error = message(error)) }
        } catch (error: Exception) {
            mutableState.update { it.copy(loading = false, error = message(error)) }
        }
    }

    fun selectCompany(company: Company?) {
        if (company?.id != state.value.selectedCompany?.id) companyStore.clear()
        store.lastCompanyId = company?.id
        mutableState.update { it.copy(selectedCompany = company) }
    }
    fun dashboardFactory(companyId: Int): DashboardViewModel.Factory {
        val sessionToken = requireNotNull(token)
        return DashboardViewModel.Factory(api, sessionToken, companyId) { expireSession(sessionToken) }
    }
    fun cashFactory(companyId: Int): CashViewModel.Factory {
        val sessionToken = requireNotNull(token)
        return CashViewModel.Factory(api, sessionToken, companyId) { expireSession(sessionToken) }
    }
    fun companyFactory(companyId: Int): CompanyViewModel.Factory {
        val sessionToken = requireNotNull(token)
        return CompanyViewModel.Factory(api, sessionToken, companyId) { expireSession(sessionToken) }
    }
    fun partnersFactory(companyId: Int): PartnersViewModel.Factory {
        val sessionToken = requireNotNull(token)
        return PartnersViewModel.Factory(api, sessionToken, companyId) { expireSession(sessionToken) }
    }
    private fun expireSession(expectedToken: String) {
        if (token != expectedToken) return
        token = null
        store.clear()
        companyStore.clear()
        mutableState.value = AuthState(error = "Přihlášení vypršelo. Přihlaste se znovu.")
    }
    fun logout() {
        if (state.value.loading) return
        token = null
        store.clear()
        companyStore.clear()
        mutableState.value = AuthState()
    }

    private fun message(error: Exception): String =
        if (error is ApiException) error.message.orEmpty() else "Odpověď serveru se nepodařilo zpracovat."
}