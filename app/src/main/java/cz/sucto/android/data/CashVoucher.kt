package cz.sucto.android.data

import java.math.BigDecimal
import org.json.JSONObject

enum class CashDirection(val path: String, val title: String) {
    Income("cash_vouchers_ins", "Příjmové"), Expense("cash_vouchers_outs", "Výdajové")
}
data class CashVoucher(val id: Int, val number: String, val recipient: String?, val description: String?,
    val total: BigDecimal?, val base: BigDecimal?, val date: String?, val externalNumber: String?, val account: String?,
    val items: List<InvoiceLine>) {
    companion object {
        fun fromJson(value: JSONObject): CashVoucher {
            val items = value.optJSONArray("items")
            return CashVoucher(value.getInt("id"), value.text("number") ?: "—", value.optJSONObject("recipient")?.text("name"),
                value.text("description"), value.decimal("total_price"), value.decimal("base_price"), value.text("transaction_date"),
                value.text("external_number"), value.text("account"),
                if (items == null) emptyList() else List(items.length()) { index ->
                    val line = items.getJSONObject(index)
                    InvoiceLine(line.getInt("id"), line.getString("name"), line.text("quantity"), line.text("unit_name"),
                        line.decimal("total_price"), line.text("vat"))
                })
            // API status is an action menu, not an actual voucher status (see iOS model).
        }
    }
}