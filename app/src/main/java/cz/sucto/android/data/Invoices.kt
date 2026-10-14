package cz.sucto.android.data

import java.math.BigDecimal
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.json.JSONObject

enum class InvoiceDirection(val path: String, val title: String) {
    Outgoing("actuarials_outs", "Vydané"), Incoming("actuarials_ins", "Přijaté")
}
enum class InvoiceFilter(val title: String) { All("Vše"), Overdue("Po splatnosti"), Paid("Zaplacené"), Concept("Koncepty") }
data class InvoiceAdvancedFilter(val status: Int? = null, val kind: Int? = null,
    val issueFrom: LocalDate? = null, val issueTo: LocalDate? = null, val dueFrom: LocalDate? = null,
    val dueTo: LocalDate? = null, val uzpFrom: LocalDate? = null, val uzpTo: LocalDate? = null,
    val minPrice: String = "", val maxPrice: String = "") {
    val activeCount: Int get() = listOf(status != null, kind != null, issueFrom != null || issueTo != null,
        dueFrom != null || dueTo != null, uzpFrom != null || uzpTo != null, minPrice.isNotBlank() || maxPrice.isNotBlank()).count { it }
    fun validation(): String? {
        if (listOf(issueFrom to issueTo, dueFrom to dueTo, uzpFrom to uzpTo).any { (from, to) -> from != null && to != null && from > to }) return "Datum od musí být před datem do."
        if ((minPrice.isNotBlank() && decimalInput(minPrice) == null) || (maxPrice.isNotBlank() && decimalInput(maxPrice) == null)) return "Částku zadejte jako číslo."
        val min = decimalInput(minPrice); val max = decimalInput(maxPrice)
        if (min != null && max != null && min > max) return "Minimální částka nesmí přesáhnout maximální."
        return null
    }
}
val InvoiceStatuses = listOf("Koncept", "Připraveno", "Odesláno", "Zobrazeno", "Okomentováno", "Opraveno", "Částečně uhrazeno", "Zaplaceno", "Archivováno", "Stornováno", "Dokončeno")
data class InvoiceQuery(val search: String = "", val filter: InvoiceFilter = InvoiceFilter.All, val advanced: InvoiceAdvancedFilter = InvoiceAdvancedFilter()) {
    fun parameters(page: Int): Map<String, String> = buildMap {
        put("page", page.toString())
        val term = search.trim()
        if (term.isNotEmpty()) put(if (term.any { it.isDigit() }) "q[actuarial_number_cont]" else "q[partner_name_cont]", term)
        advanced.status?.let { put("q[status_eq]", it.toString()) }
        advanced.kind?.let { put("q[actuarial_type_id_eq]", it.toString()) }
        var dueTo = advanced.dueTo
        when (filter) {
            InvoiceFilter.Paid -> if (advanced.status == null) put("q[status_eq]", "8")
            InvoiceFilter.Concept -> if (advanced.status == null) put("q[status_eq]", "1")
            InvoiceFilter.Overdue -> { val yesterday = LocalDate.now().minusDays(1); dueTo = dueTo?.let { if (it < yesterday) it else yesterday } ?: yesterday }
            else -> Unit
        }
        listOf("issue_date_at_gteq" to advanced.issueFrom, "issue_date_at_lteq" to advanced.issueTo,
            "due_date_at_gteq" to advanced.dueFrom, "due_date_at_lteq" to dueTo,
            "uzp_date_at_gteq" to advanced.uzpFrom, "uzp_date_at_lteq" to advanced.uzpTo).forEach { (key, date) -> date?.let { put("q[$key]", it.toString()) } }
        decimalInput(advanced.minPrice)?.let { put("q[end_price_gteq]", it.toPlainString()) }
        decimalInput(advanced.maxPrice)?.let { put("q[end_price_lteq]", it.toPlainString()) }
    }
}data class Invoice(
    val id: Int, val number: String, val statusId: Int, val status: String,
    val partner: String?, val total: BigDecimal?, val base: BigDecimal?, val currency: String,
    val issue: String?, val due: String?, val variableSymbol: String?, val note: String?,
    val items: List<InvoiceLine>, val partnerId: Int? = null, val partnerIc: String? = null,
    val currencyId: Int? = null, val currencyIso: String = "CZK", val accountId: Int? = null,
    val iban: String? = null, val bankIban: String? = null, val bankNumber: String? = null, val bankAccountNumber: String? = null,
    val bankCode: String? = null, val remaining: BigDecimal? = null, val footer: String? = null, val order: String? = null,
    val partnerEmail: String? = null, val uzp: String? = null, val type: String? = null,
) {
    val isOverdue: Boolean get() = statusId !in listOf(1, 8, 10, 11) &&
        parseApiDate(due)?.isBefore(LocalDate.now()) == true
    companion object {
        fun fromJson(value: JSONObject, direction: InvoiceDirection): Invoice {
            val partner = value.optJSONObject(if (direction == InvoiceDirection.Outgoing) "customer" else "supplier")
            val lines = value.optJSONArray("items")
            val bank = value.optJSONObject("account")?.optJSONObject("bank_account")
            return Invoice(value.getInt("id"), value.getString("actuarial_number"), value.getInt("status_id"),
                value.text("status") ?: "—", partner?.text("name"), value.decimal("end_price"), value.decimal("base_price"),
                value.optJSONObject("currency")?.text("symbol") ?: "Kč", value.text("issue_date_at"), value.text("due_date_at"),
                value.text("variable_symbol"), value.text("print_notice"),
                if (lines == null) emptyList() else List(lines.length()) { index ->
                    val line = lines.getJSONObject(index)
                    InvoiceLine(line.getInt("id"), line.getString("name"), line.text("quantity"), line.text("unit_name"),
                        line.decimal("total_price"), line.text("vat"), line.decimal("unit_price"), line.optInt("vat_id", 0))
                }, partner?.let { if (it.isNull("id")) null else it.getInt("id") }, partner?.text("ic"),
                value.optJSONObject("currency")?.let { if (it.isNull("id")) null else it.getInt("id") },
                value.optJSONObject("currency")?.text("iso_code") ?: "CZK",
                value.optJSONObject("account")?.let { if (it.isNull("id")) null else it.getInt("id") },
                lossyText(value.opt("iban")), bank?.text("iban"), lossyText(value.opt("bank_number")), bank?.text("account"),
                bank?.text("bank_code"), value.decimal("remaining"), value.text("foot_notice"), value.text("order_number"),
                partner?.text("email"), value.text("uzp_date_at"), value.text("actuarial_type"))
        }
    }
}
data class InvoiceLine(val id: Int, val name: String, val quantity: String?, val unit: String?, val total: BigDecimal?, val vat: String?, val unitPrice: BigDecimal? = null, val vatId: Int = 0)
internal fun JSONObject.text(key: String): String? = when (val v = opt(key)) {
    is String -> v.takeIf { it.isNotBlank() }
    is Number -> v.toString()
    else -> null
}
internal fun JSONObject.decimal(key: String): BigDecimal? = text(key)?.replace(',', '.')?.toBigDecimalOrNull()
fun money(value: BigDecimal?, currency: String): String = value?.let {
    NumberFormat.getNumberInstance(Locale.forLanguageTag("cs-CZ")).apply {
        minimumFractionDigits = 2; maximumFractionDigits = 2
    }.format(it) + " " + currency
} ?: "—"
fun parseApiDate(value: String?): LocalDate? {
    if (value.isNullOrBlank()) return null
    val czech = DateTimeFormatter.ofPattern("d. M. uuuu").withResolverStyle(java.time.format.ResolverStyle.STRICT)
    val normalized = value.trim().replace(Regex("""\s+"""), " ")
    return runCatching { LocalDate.parse(normalized, czech) }.getOrNull()
        ?: runCatching { LocalDate.parse(normalized.take(10)) }.getOrNull()
}
fun dateLabel(value: String?): String = parseApiDate(value)?.format(DateTimeFormatter.ofPattern("d. M. yyyy")) ?: value ?: "—"
data class MonthlyFigure(val month: Int, val revenue: BigDecimal, val cost: BigDecimal)
data class OverviewSection(val currency: String, val revenue: BigDecimal, val cost: BigDecimal,
    val result: BigDecimal, val months: List<MonthlyFigure>)
data class Overview(val sections: List<OverviewSection>, val fromInvoices: Boolean)
internal data class AccountingValue(val value: BigDecimal, val currency: String)
internal fun accountingValue(text: String?): AccountingValue? {
    if (text.isNullOrBlank()) return null
    val numeric = text.replace('−', '-').filter { it in "0123456789,.-" }
    val normalized = if (',' in numeric) numeric.replace(".", "").replace(',', '.') else numeric
    val value = normalized.toBigDecimalOrNull() ?: return null
    return AccountingValue(value, text.filter { it.isLetter() || it in "$€£" })
}
internal fun lossyText(value: Any?): String? = when (value) {
    is String -> value.takeIf { it.isNotBlank() }
    is Number -> value.toString()
    is JSONObject -> lossyText(value.opt("name")) ?: lossyText(value.opt("title")) ?: lossyText(value.opt("value"))
    else -> null
}