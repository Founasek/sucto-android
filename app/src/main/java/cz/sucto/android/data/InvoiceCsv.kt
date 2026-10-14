package cz.sucto.android.data

import java.math.BigDecimal
import java.time.LocalDate

/** Export seznamu faktur do CSV pro Excel / účetní: oddělovač `;`, desetinná čárka, UTF-8 s BOM, řádky CRLF. */
object InvoiceCsv {
    private val header = listOf("Číslo", "Typ", "Protistrana", "Datum vystavení", "Datum UZP", "Splatnost", "Stav",
        "Základ", "Celkem", "Zbývá uhradit", "Měna", "Variabilní symbol")

    fun bytes(invoices: List<Invoice>): ByteArray {
        val lines = mutableListOf(header.joinToString(";") { escape(it) })
        invoices.forEach { invoice ->
            lines += listOf(invoice.number, invoice.type.orEmpty(), invoice.partner.orEmpty(), invoice.issue.orEmpty(), invoice.uzp.orEmpty(),
                invoice.due.orEmpty(), invoice.status, number(invoice.base), number(invoice.total), number(invoice.remaining),
                invoice.currencyIso, invoice.variableSymbol.orEmpty()).joinToString(";") { escape(it) }
        }
        // BOM, aby Excel poznal UTF-8 (jinak rozbije češtinu).
        return byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + (lines.joinToString("\r\n") + "\r\n").toByteArray(Charsets.UTF_8)
    }

    /** „200.0“ → „200,00“ (bez oddělovačů tisíců, aby šlo číst jako číslo). */
    private fun number(value: BigDecimal?) = value?.setScale(2, java.math.RoundingMode.HALF_UP)?.toPlainString()?.replace('.', ',').orEmpty()

    /** Hodnoty s oddělovačem, uvozovkou nebo zalomením se uzavřou do uvozovek; texty začínající `=`, `+`, `-`, `@` dostanou apostrof (CSV injection). */
    internal fun escape(value: String): String {
        var text = value
        if (text.firstOrNull()?.let { it in "=+-@" } == true) text = "'$text"
        return if (text.any { it == ';' || it == '"' || it == '\n' || it == '\r' }) "\"" + text.replace("\"", "\"\"") + "\"" else text
    }

    fun fileName(outgoing: Boolean, date: LocalDate = LocalDate.now()) = "faktury-${if (outgoing) "vydane" else "prijate"}-$date.csv"
}
