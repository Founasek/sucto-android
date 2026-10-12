package cz.sucto.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import cz.sucto.android.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CashState(val direction: CashDirection = CashDirection.Income, val search: String = "",
    val items: List<CashVoucher> = emptyList(), val nextPage: Int = 1, val hasMore: Boolean = true,
    val loading: Boolean = false, val error: String? = null,
    val detail: CashVoucher? = null, val detailLoading: Boolean = false, val detailError: String? = null)
class CashViewModel(private val api: SuctoApi, private val token: String, private val companyId: Int,
    private val onUnauthorized: () -> Unit) : ViewModel() {
    private val mutableState = MutableStateFlow(CashState())
    val state = mutableState.asStateFlow()
    private var listJob: Job? = null
    private var detailJob: Job? = null
    private var generation = 0
    private var detailGeneration = 0
    fun start() { refresh(); state.value.detail?.let { open(it) } }
    fun select(direction: CashDirection) {
        if (state.value.direction == direction) return
        mutableState.update { it.copy(direction = direction) }
        refresh()
    }
    fun search(text: String) {
        mutableState.update { it.copy(search = text) }
        refresh(debounce = true)
    }
    fun refresh(debounce: Boolean = false) {
        listJob?.cancel()
        val current = ++generation
        mutableState.update { it.copy(items = emptyList(), nextPage = 1, hasMore = true, loading = true, error = null) }
        listJob = viewModelScope.launch { if (debounce) delay(400); fetch(current) }
    }
    fun nextPage() {
        if (state.value.loading || !state.value.hasMore) return
        mutableState.update { it.copy(loading = true, error = null) }
        val current = generation
        listJob = viewModelScope.launch { fetch(current) }
    }
    private suspend fun fetch(current: Int) {
        val snapshot = state.value
        try {
            val values = api.cashVouchers(token, companyId, snapshot.direction, snapshot.search, snapshot.nextPage)
            if (current == generation) mutableState.update { it.copy(items = (snapshot.items + values).distinctBy { v -> v.id },
                nextPage = snapshot.nextPage + 1, hasMore = values.isNotEmpty(), loading = false) }
        } catch (error: CancellationException) { throw error
        } catch (error: Exception) {
            if (current == generation) mutableState.update { it.copy(loading = false, error = message(error)) }
        }
    }
    fun open(voucher: CashVoucher) {
        detailJob?.cancel()
        val current = ++detailGeneration
        val direction = state.value.direction
        mutableState.update { it.copy(detail = voucher, detailLoading = true, detailError = null) }
        detailJob = viewModelScope.launch {
            try {
                val detail = api.cashVoucher(token, companyId, direction, voucher.id)
                if (current == detailGeneration) mutableState.update { it.copy(detail = detail, detailLoading = false) }
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) {
                if (current == detailGeneration) mutableState.update { it.copy(detailLoading = false, detailError = message(error)) }
            }
        }
    }
    fun close() { detailGeneration++; detailJob?.cancel(); mutableState.update { it.copy(detail = null, detailLoading = false, detailError = null) } }
    fun stop() { generation++; detailGeneration++; listJob?.cancel(); detailJob?.cancel() }
    private fun message(error: Exception): String {
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
            require(modelClass.isAssignableFrom(CashViewModel::class.java))
            return CashViewModel(api, token, companyId, onUnauthorized) as T
        }
    }
}