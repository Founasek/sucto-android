package cz.sucto.android.data

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import kotlinx.coroutines.async
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/** Contract from iOS APIService. Never log credentials, tokens or response bodies. */
class SuctoApi(private val cache: ResponseCache? = null) {
    /** Čas uložení dat, která se právě zobrazují z cache při výpadku připojení; `null`, pokud jsou aktuální. */
    val cachedDataDate = kotlinx.coroutines.flow.MutableStateFlow<Long?>(null)
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    suspend fun login(email: String, password: String): String {
        val body = JSONObject().put("email", email).put("password", password).toString()
        val response = request("sessions/create", body = body)
        return JSONObject(response).getString("authentication_token").also {
            if (it.isBlank()) throw ApiException("Server nevrátil přihlašovací token.")
        }
    }

    suspend fun companies(token: String): List<Company> {
        val array = JSONArray(request("companies", token))
        return List(array.length()) { index ->
            val item = array.getJSONObject(index)
            Company(item.getInt("id"), item.getString("name"), item.getString("ic"), item.getBoolean("is_taxable"), item.getInt("country_id"))
        }
    }

    /** GET companies/{id}: údaje o firmě včetně adresy (DIČ endpoint nevrací). */
    suspend fun companyDetail(token: String, companyId: Int): CompanyDetail {
        val item = JSONObject(request("companies/$companyId", token))
        val address = item.optJSONObject("address")
        val street = listOfNotNull(address?.text("street"), address?.text("city_part")).filter { it.isNotBlank() }.joinToString(", ")
        val city = listOfNotNull(address?.text("zip"), address?.text("city")).filter { it.isNotBlank() }.joinToString(" ")
        return CompanyDetail(item.getInt("id"), item.getString("name"), item.getString("ic"), item.getBoolean("is_taxable"),
            item.text("email"), listOf(street, city).filter { it.isNotEmpty() })
    }

    /** GET companies/{id}/accounts: bankovní i pokladní účty (account_type 1 = pokladna). */
    suspend fun accounts(token: String, companyId: Int): List<CompanyAccount> {
        val values = JSONArray(request("companies/$companyId/accounts", token))
        return List(values.length()) {
            val item = values.getJSONObject(it)
            val bank = item.optJSONObject("bank_account")
            CompanyAccount(item.optInt("id", -(it + 1)), item.getString("name"), item.optInt("account_type") == 1,
                item.optBoolean("is_deactivated"), item.text("prefix"), item.text("opening_balance"),
                bank?.text("account"), bank?.text("bank_code"), bank?.text("iban"), bank?.text("swift"), bank?.text("bank_name"))
        }
    }

    suspend fun invoices(token: String, companyId: Int, direction: InvoiceDirection, params: Map<String, String>): List<Invoice> {
        val array = JSONArray(request(queryPath("companies/$companyId/${direction.path}", params), token))
        return List(array.length()) { Invoice.fromJson(array.getJSONObject(it), direction) }
    }
    suspend fun invoice(token: String, companyId: Int, direction: InvoiceDirection, id: Int): Invoice =
        Invoice.fromJson(JSONObject(request("companies/$companyId/${direction.path}/$id", token)), direction)

    suspend fun cashVouchers(token: String, companyId: Int, direction: CashDirection, search: String, page: Int): List<CashVoucher> {
        val params = mutableMapOf("page" to page.toString())
        val term = search.trim()
        if (term.isNotEmpty()) params[if (term.any { it.isDigit() }) "q[number_cont]" else "q[partner_name_cont]"] = term
        val values = JSONArray(request(queryPath("companies/$companyId/${direction.path}", params), token))
        return List(values.length()) { CashVoucher.fromJson(values.getJSONObject(it)) }
    }
    suspend fun cashVoucher(token: String, companyId: Int, direction: CashDirection, id: Int): CashVoucher =
        CashVoucher.fromJson(JSONObject(request("companies/$companyId/${direction.path}/$id", token)))
    suspend fun newInvoice(token: String, companyId: Int, direction: InvoiceDirection): JSONObject =
        JSONObject(request("companies/$companyId/${direction.path}/new", token))
    suspend fun createInvoice(token: String, companyId: Int, direction: InvoiceDirection, body: JSONObject): Int =
        JSONObject(request("companies/$companyId/${direction.path}", token, body.toString())).getInt("id")
    suspend fun partners(token: String, companyId: Int, search: String): List<PartnerChoice> {
        val params = mutableMapOf("page" to "1", "limit" to "50")
        if (search.isNotBlank()) params["q[name_or_ic_or_dic_or_address_street_or_address_city_cont]"] = search.trim()
        val array = JSONArray(request(queryPath("companies/$companyId/partners", params), token))
        return List(array.length()) {
            val item = array.getJSONObject(it)
            PartnerChoice(item.getInt("id"), item.getString("name"), item.text("ic"), item.text("invoice_due")?.toBigDecimalOrNull()?.toInt(),
                item.optJSONObject("currency")?.let { c -> if (c.isNull("id")) null else c.getInt("id") })
        }
    }
    /** Jedna stránka partnerů (hledání na serveru podle názvu, IČ, DIČ a adresy). */
    suspend fun partnerRecords(token: String, companyId: Int, search: String, page: Int, limit: Int): List<PartnerRecord> {
        val params = mutableMapOf("page" to page.toString(), "limit" to limit.toString())
        if (search.isNotBlank()) params["q[name_or_ic_or_dic_or_address_street_or_address_city_cont]"] = search.trim()
        val array = JSONArray(request(queryPath("companies/$companyId/partners", params), token))
        return List(array.length()) { PartnerRecord.fromJson(array.getJSONObject(it)) }
    }
    suspend fun partnerRecord(token: String, companyId: Int, partnerId: Int): PartnerRecord =
        PartnerRecord.fromJson(JSONObject(request("companies/$companyId/partners/$partnerId", token)))
    /** Vytvoří partnera podle IČO z ARES (POST s prázdným JSON tělem). */
    suspend fun createPartnerByAres(token: String, companyId: Int, ic: String): PartnerRecord =
        PartnerRecord.fromJson(JSONObject(request("companies/$companyId/partners/create_by_ares/$ic", token, "{}")))
    /** Úhrada vydané faktury je v API `GET .../pay` (vytvoří pokladní doklad); nikdy se nevolá automaticky ani z cache. Vrací id dokladu. */
    suspend fun payInvoice(token: String, companyId: Int, invoiceId: Int): Int =
        JSONObject(request("companies/$companyId/actuarials_outs/$invoiceId/pay", token)).optInt("cash_voucher_id")
    suspend fun sendInvoiceEmail(token: String, companyId: Int, invoiceId: Int, email: String, comment: String) {
        val body = JSONObject().put("email", email).apply { if (comment.isNotBlank()) put("comment", comment) }.toString()
        request("companies/$companyId/actuarials_outs/$invoiceId/send_to_email", token, body, method = "PATCH")
    }
    suspend fun deletePartner(token: String, companyId: Int, partnerId: Int) {
        request("companies/$companyId/partners/$partnerId", token, method = "DELETE")
    }

    suspend fun draftReferences(token: String, company: Company): DraftReferences = kotlinx.coroutines.coroutineScope {
        val currencies = async { choices(JSONArray(request("currencies", token)), currency = true) }
        val accounts = async {
            val values = JSONArray(request("companies/${company.id}/accounts", token))
            List(values.length()) {
                val item = values.getJSONObject(it)
                val bank = item.optJSONObject("bank_account")
                AccountChoice(item.getInt("id"), item.getString("name"), bank?.text("iban"), bank?.text("swift"), bank?.text("bank_code"))
            }
        }
        val payments = async { choices(JSONArray(request("companies/${company.id}/payment_types", token))) }
        val regimes = async { choices(JSONArray(request("countries/${company.countryId}/vat_regimes", token))) }
        val vats = async {
            val values = JSONArray(request("countries/${company.countryId}/vats", token))
            (0 until values.length()).map { values.getJSONObject(it) }.filter { it.isNull("valid_to") }
                .map { VatChoice(it.getInt("id"), it.decimal("value") ?: java.math.BigDecimal.ZERO) }
        }
        val types = async {
            try { val array = JSONArray(request("actuarial_types", token)); if (array.length() > 0) array.getJSONObject(0).getInt("id") else 1
            } catch (error: kotlinx.coroutines.CancellationException) { throw error
            } catch (error: ApiException) { if (error.unauthorized) throw error else 1 }
        }
        DraftReferences(currencies.await(), accounts.await(), payments.await(), regimes.await(), vats.await(), types.await())
    }
    suspend fun canCreateInvoice(token: String, companyId: Int, direction: InvoiceDirection): Boolean {
        val resource = if (direction == InvoiceDirection.Outgoing) "ActuarialsOut" else "ActuarialsIn"
        return JSONObject(request(queryPath("companies/$companyId/api_permissions", mapOf("resource" to resource)), token)).getBoolean("create")
    }
    private fun queryPath(path: String, params: Map<String, String>): String = path + "?" + params.entries.joinToString("&") {
        java.net.URLEncoder.encode(it.key, "UTF-8") + "=" + java.net.URLEncoder.encode(it.value, "UTF-8")
    }
    private suspend fun accounting(token: String, companyId: Int, year: Int, month: Int? = null): Map<Int, AccountingValue> {
        val params = mutableMapOf("q[year_eq]" to year.toString())
        if (month != null) params["q[month_eq]"] = month.toString()
        val groups = JSONObject(request(queryPath("companies/$companyId/accounting_diaries", params), token)).getJSONArray("groups")
        return buildMap {
            for (index in 0 until groups.length()) {
                val group = groups.getJSONObject(index)
                val id = if (group.has("group_id") && !group.isNull("group_id")) group.getInt("group_id") else {
                    val name = group.text("group_name").orEmpty().lowercase()
                    when { "náklad" in name -> 0; "výnos" in name -> 1; "výsled" in name -> 2; else -> -1 }
                }
                accountingValue(group.text("total"))?.let { put(id, it) }
            }
        }
    }
    suspend fun overview(token: String, companyId: Int, year: Int): Overview {
        val zero = java.math.BigDecimal.ZERO
        try {
            val annual = accounting(token, companyId, year)
            val months = kotlinx.coroutines.coroutineScope {
                val semaphore = kotlinx.coroutines.sync.Semaphore(4)
                val last = if (year == java.time.LocalDate.now().year) java.time.LocalDate.now().monthValue else 12
                (1..last).map { month -> async {
                    semaphore.acquire()
                    try {
                        val report = accounting(token, companyId, year, month)
                        MonthlyFigure(month, report[1]?.value ?: zero, report[0]?.value ?: zero)
                    } finally { semaphore.release() }
                } }.map { it.await() }
            }
            val revenue = annual[1]?.value ?: zero
            val cost = annual[0]?.value ?: zero
            val section = OverviewSection(annual[1]?.currency ?: annual[0]?.currency ?: "Kč", revenue, cost,
                annual[2]?.value ?: revenue - cost, (1..12).map { month -> months.find { it.month == month } ?: MonthlyFigure(month, zero, zero) })
            return Overview(if (revenue.signum() != 0 || cost.signum() != 0 || months.any { it.revenue.signum() != 0 || it.cost.signum() != 0 }) listOf(section) else emptyList(), false)
        } catch (error: ApiException) {
            if (error.statusCode != 403) throw error
        }
        suspend fun annual(direction: InvoiceDirection): List<Invoice> {
            val all = mutableListOf<Invoice>()
            val seen = mutableSetOf<Int>()
            for (page in 1..100) {
                val values = invoices(token, companyId, direction, mapOf("page" to page.toString(),
                    "q[issue_date_at_gteq]" to "$year-01-01", "q[issue_date_at_lteq]" to "$year-12-31"))
                val fresh = values.filter { seen.add(it.id) }
                if (fresh.isEmpty()) return all.filter { it.statusId !in listOf(1, 10) }
                all.addAll(fresh)
            }
            throw ApiException("Přehled obsahuje příliš mnoho stránek. Úplná data se nepodařilo načíst.")
        }
        val (issued, received) = kotlinx.coroutines.coroutineScope {
            val out = async { annual(InvoiceDirection.Outgoing) }
            val inc = async { annual(InvoiceDirection.Incoming) }
            out.await() to inc.await()
        }
        val currencies = (issued + received).groupingBy { it.currency }.eachCount()
            .entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.key == "Kč" || it.key.equals("CZK", true) }
                .thenByDescending { it.value }.thenBy { it.key }).map { it.key }
        val sections = currencies.map { currency ->
            fun sum(list: List<Invoice>, month: Int) = list.filter {
                it.currency == currency && parseApiDate(it.issue)?.monthValue == month
            }.fold(zero) { acc, invoice -> acc + (invoice.base ?: zero) }
            val months = (1..12).map { MonthlyFigure(it, sum(issued, it), sum(received, it)) }
            val revenue = months.fold(zero) { acc, m -> acc + m.revenue }
            val cost = months.fold(zero) { acc, m -> acc + m.cost }
            OverviewSection(currency, revenue, cost, revenue - cost, months)
        }.filter { it.months.any { m -> m.revenue.signum() != 0 || m.cost.signum() != 0 } }
        return Overview(sections, true)
    }
    private suspend fun request(path: String, token: String? = null, body: String? = null, method: String? = null): String =
        suspendCancellableCoroutine { continuation ->
            val builder = Request.Builder().url(BASE_URL + path).header("Accept", "application/json")
            if (token != null) builder.header("Auth-Token", token)
            if (method == "DELETE") builder.delete()
            else if (method == "PATCH") builder.patch((body ?: "{}").toRequestBody("application/json; charset=utf-8".toMediaType()))
            else if (body != null) builder.post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
            val call = client.newCall(builder.build())
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, error: IOException) {
                    if (!continuation.isActive) return
                    // Při výpadku připojení se čtení vrátí z cache (zašifrované, nejvýš 30 dní stará).
                    val saved = if (call.isCanceled() || !ResponseCache.isCacheable(path, method, body)) null else cache?.load(path)
                    if (saved != null) { cachedDataDate.value = saved.savedAt; continuation.resume(saved.payload) }
                    else continuation.resumeWithException(ApiException("Nepodařilo se připojit. Zkontrolujte připojení k internetu."))
                }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val payload = response.use {
                            val value = it.body?.string().orEmpty()
                            when (it.code) {
                                in 200..299 -> value
                                401 -> throw ApiException("Přihlášení není platné. Přihlaste se znovu.", unauthorized = true)
                                403 -> throw ApiException(serverMessage(value) ?: "K této akci nemáte oprávnění.", statusCode = 403)
                                400, 422 -> throw ApiException(serverMessage(value) ?: "Zkontrolujte zadané údaje.", statusCode = it.code)
                                else -> throw ApiException("Server není dostupný (${it.code}). Zkuste to znovu.", statusCode = it.code)
                            }
                        }
                        if (ResponseCache.isCacheable(path, method, body)) cache?.store(path, payload)
                        cachedDataDate.value = null
                        if (continuation.isActive) continuation.resume(payload)
                    } catch (error: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(
                            if (error is IOException) ApiException("Nepodařilo se připojit. Zkontrolujte připojení k internetu.") else error)
                    }
                }
            })
        }
    private fun serverMessage(payload: String): String? = runCatching {
        when (val errors = JSONObject(payload).opt("errors")) {
            is String -> errors
            is JSONArray -> errors.optString(0).takeIf { it.isNotBlank() }
            is JSONObject -> errors.keys().asSequence().sorted().firstOrNull()?.let { field ->
                errors.optJSONArray(field)?.optString(0)?.takeIf { it.isNotBlank() }?.let { "$field: $it" }
            }
            else -> null
        }
    }.getOrNull()

    companion object { const val BASE_URL = "https://www.sucto.cz/api/" }
}
data class Company(val id: Int, val name: String, val ic: String, val isTaxable: Boolean, val countryId: Int)
data class CompanyDetail(val id: Int, val name: String, val ic: String, val isTaxable: Boolean, val email: String?, val addressLines: List<String>)
data class CompanyAccount(val id: Int, val name: String, val isCash: Boolean, val isDeactivated: Boolean, val prefix: String?,
    val openingBalance: String?, val account: String?, val bankCode: String?, val iban: String?, val swift: String?, val bankName: String?) {
    val accountNumber: String? get() = account?.let { it + (bankCode?.let { code -> "/$code" } ?: "") }
}
class ApiException(message: String, val unauthorized: Boolean = false, val statusCode: Int? = null) : Exception(message)