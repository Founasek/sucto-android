package cz.sucto.android.data

import org.json.JSONObject

/** Partner ze seznamu i z detailu (seznam vrací jen podmnožinu polí, ostatní zůstanou prázdná). */
data class PartnerRecord(val id: Int, val name: String, val ic: String?, val dic: String?, val email: String?, val phone: String?,
    val web: String?, val isCustomer: Boolean, val isSupplier: Boolean, val isTaxable: Boolean, val invoiceDueDays: Int?,
    val currency: String?, val address: String?) {
    /** Firma (má IČ nebo DIČ), jinak fyzická osoba. */
    val isCompany: Boolean get() = !ic.isNullOrBlank() || !dic.isNullOrBlank()

    companion object {
        fun fromJson(item: JSONObject): PartnerRecord {
            val address = item.optJSONObject("address")
            val place = listOfNotNull(address?.text("zip"), address?.text("city")).joinToString(" ")
            val line = listOfNotNull(address?.text("street"), place.takeIf { it.isNotBlank() }).joinToString(", ")
            val currency = item.optJSONObject("currency")
            return PartnerRecord(item.getInt("id"), item.getString("name"), item.text("ic"), item.text("dic"), item.text("email"),
                item.text("phone"), item.text("web"), item.optBoolean("is_customer"), item.optBoolean("is_supplier"),
                item.optBoolean("is_taxable"), item.text("invoice_due")?.toDoubleOrNull()?.toInt(),
                currency?.text("symbol") ?: currency?.text("name"), line.takeIf { it.isNotBlank() })
        }
    }
}
