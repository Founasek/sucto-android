package cz.sucto.android.data

import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

data class Choice(val id: Int, val name: String, val symbol: String? = null)
data class PartnerChoice(val id: Int, val name: String, val ic: String?, val dueDays: Int?, val currencyId: Int?)
data class AccountChoice(val id: Int, val name: String, val iban: String?, val swift: String?, val bankCode: String?)
data class VatChoice(val id: Int, val rate: BigDecimal)
data class DraftLine(val key: String = UUID.randomUUID().toString(), val name: String = "", val quantity: String = "0",
    val unitPrice: String = "0", val unit: String = "", val vatId: Int = 0) {
    fun amount(vats: List<VatChoice>): LineAmount {
        val base = decimalInput(quantity)?.multiply(decimalInput(unitPrice) ?: BigDecimal.ZERO) ?: BigDecimal.ZERO
        val tax = base.multiply(vats.find { it.id == vatId }?.rate ?: BigDecimal.ZERO).divide(BigDecimal(100))
        return LineAmount(base, tax, base + tax)
    }
    fun json(vats: List<VatChoice>): JSONObject {
        val amounts = amount(vats)
        return JSONObject().put("vat_id", vatId).put("lineable_type", "Actuarial").put("name", name.trim())
            .put("quantity", decimalInput(quantity)).put("unit_price", decimalInput(unitPrice))
            .put("base_price", amounts.base).put("tax", amounts.tax).put("total_price", amounts.total)
            .apply { if (unit.isNotBlank()) put("unit_name", unit.trim()) }
    }
}
data class LineAmount(val base: BigDecimal, val tax: BigDecimal, val total: BigDecimal)
fun decimalInput(value: String): BigDecimal? = value.trim().replace(',', '.').toBigDecimalOrNull()
data class InvoiceDraft(val number: String = "", val variableSymbol: String = "", val typeId: Int = 1,
    val partnerId: Int? = null, val accountId: Int? = null, val currencyId: Int? = null, val paymentTypeId: Int? = null,
    val vatRegimeId: Int? = null, val issue: LocalDate = LocalDate.now(), val due: LocalDate = LocalDate.now(),
    val uzp: LocalDate = LocalDate.now(), val notice: String = "", val footer: String = "", val order: String = "",
    val lines: List<DraftLine> = emptyList()) {
    fun json(direction: InvoiceDirection, taxable: Boolean, accounts: List<AccountChoice>, vats: List<VatChoice>): JSONObject {
        val account = accounts.first { it.id == accountId }
        return JSONObject().put("actuarial_number", number.trim()).put("variable_symbol", variableSymbol)
            .put("actuarial_type_id", typeId).put("partner_id", partnerId).put("account_id", accountId)
            .put("currency_id", currencyId).put("issue_date_at", issue.toString()).put("due_date_at", due.toString())
            .put("uzp_date_at", uzp.toString()).put("print_notice", notice).put("foot_notice", footer).put("order_number", order)
            .put("lines", JSONArray().apply { lines.forEach { put(it.json(vats)) } })
            .apply {
                paymentTypeId?.let { put("payment_type_id", it) }
                if (taxable) vatRegimeId?.let { put("vat_regime_id", it) }
                if (direction == InvoiceDirection.Outgoing) {
                    put("iban", account.iban.orEmpty()); put("swift", account.swift.orEmpty()); put("bank_number", account.bankCode.orEmpty())
                }
            }
    }
}
data class DraftReferences(val currencies: List<Choice>, val accounts: List<AccountChoice>, val payments: List<Choice>,
    val regimes: List<Choice>, val vats: List<VatChoice>, val typeId: Int)
internal fun choices(array: JSONArray, currency: Boolean = false): List<Choice> = List(array.length()) {
    val item = array.getJSONObject(it)
    Choice(item.getInt("id"), item.text("name") ?: (if (currency) item.text("symbol") else null) ?: item.getInt("id").toString(), if (currency) item.text("symbol") else null)
}