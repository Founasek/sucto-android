package cz.sucto.android.data

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.Normalizer
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Port of iOS PaymentQR, using SPAYD: https://qr-platba.cz/pro-vyvojare/specifikace-formatu/ */
object PaymentQr {
    fun normalizedIban(value: String?): String? {
        val iban = value?.filterNot { it.isWhitespace() }?.uppercase(Locale.ROOT) ?: return null
        if (!Regex("[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}").matches(iban)) return null
        return iban.takeIf { remainder(iban.drop(4) + iban.take(4)) == 1 }
    }
    fun czechIban(account: String?, bankCode: String?): String? {
        if (account.isNullOrBlank()) return null
        val text = account.filterNot { it.isWhitespace() }
        val numberPart = text.substringBefore('/')
        val code = if ('/' in text) text.substringAfter('/') else bankCode.orEmpty()
        if (!Regex("[0-9]{4}").matches(code)) return null
        val pieces = numberPart.split('-')
        if (pieces.size !in 1..2) return null
        val prefix = if (pieces.size == 2) pieces[0] else ""
        val number = pieces.last()
        if (!Regex("[0-9]{0,6}").matches(prefix) || !Regex("[0-9]{1,10}").matches(number)) return null
        val bban = code + prefix.padStart(6, '0') + number.padStart(10, '0')
        val check = (98 - remainder(bban + "123500")).toString().padStart(2, '0')
        return "CZ$check$bban"
    }
    private fun remainder(value: String): Int {
        var result = 0
        value.forEach { c ->
            val digits = if (c in '0'..'9') c.toString() else (c.code - 55).toString()
            digits.forEach { result = (result * 10 + (it - '0')) % 97 }
        }
        return result
    }
    private fun clean(value: String?, limit: Int): String? = value?.let {
        Normalizer.normalize(it, Normalizer.Form.NFD).filterNot { c -> Character.getType(c) == Character.NON_SPACING_MARK.toInt() }
            .replace('*', ' ').filter { c -> c.code in 32..126 }.trim().take(limit).takeIf { text -> text.isNotEmpty() }
    }
    fun payload(invoice: Invoice, recipient: String?): String? {
        if (invoice.statusId in listOf(1, 8, 10, 11)) return null
        val iban = normalizedIban(invoice.iban) ?: normalizedIban(invoice.bankIban)
            ?: czechIban(invoice.bankNumber, invoice.bankCode) ?: czechIban(invoice.bankAccountNumber, invoice.bankCode) ?: return null
        val amount = invoice.remaining?.takeIf { it.signum() > 0 } ?: invoice.total ?: return null
        val rounded = amount.setScale(2, RoundingMode.HALF_UP)
        if (rounded.signum() <= 0 || rounded > BigDecimal("9999999.99")) return null
        val currency = invoice.currencyIso.uppercase(Locale.ROOT)
        if (!Regex("[A-Z]{3}").matches(currency)) return null
        return buildList {
            add("SPD"); add("1.0"); add("ACC:$iban"); add("AM:${rounded.toPlainString()}"); add("CC:$currency")
            clean(recipient, 35)?.let { add("RN:$it") }
            (invoice.variableSymbol ?: invoice.number).filter { it in '0'..'9' }.take(10).takeIf { it.isNotEmpty() }?.let { add("X-VS:$it") }
            clean("Faktura ${invoice.number}", 60)?.let { add("MSG:$it") }
            parseApiDate(invoice.due)?.format(DateTimeFormatter.BASIC_ISO_DATE)?.let { add("DT:$it") }
        }.joinToString("*")
    }
}