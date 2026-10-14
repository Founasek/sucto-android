package cz.sucto.android.data

import android.content.Context
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Nastavení aplikace (SharedPreferences, bez citlivých údajů). */
object AppSettings {
    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Upomínky jsou ve výchozím stavu VYPNUTÉ; uživatel je musí vědomě zapnout. */
    fun remindersEnabled(context: Context) = prefs(context).getBoolean("remindersEnabled", false)
    fun setRemindersEnabled(context: Context, value: Boolean) = prefs(context).edit().putBoolean("remindersEnabled", value).apply()

    fun reminderTemplate(context: Context): String =
        prefs(context).getString("reminderTemplate", null)?.takeIf { it.isNotBlank() } ?: Reminders.DEFAULT_TEMPLATE
    fun setReminderTemplate(context: Context, value: String?) =
        prefs(context).edit().apply { if (value.isNullOrBlank()) remove("reminderTemplate") else putString("reminderTemplate", value) }.apply()

    /** Denní upozornění na splatnosti jsou ve výchozím stavu vypnutá; podrobnosti (firma, počty) také. */
    fun dueNotificationsEnabled(context: Context) = prefs(context).getBoolean("dueNotifications", false)
    fun setDueNotificationsEnabled(context: Context, value: Boolean) = prefs(context).edit().putBoolean("dueNotifications", value).apply()
    fun dueNotificationDetails(context: Context) = prefs(context).getBoolean("dueNotificationDetails", false)
    fun setDueNotificationDetails(context: Context, value: Boolean) = prefs(context).edit().putBoolean("dueNotificationDetails", value).apply()
    /** Název naposledy otevřené firmy pro podrobné upozornění (úloha na pozadí nemá celý objekt firmy). */
    fun lastCompanyName(context: Context): String? = prefs(context).getString("lastCompanyName", null)
    fun setLastCompanyName(context: Context, value: String?) = prefs(context).edit().apply { if (value == null) remove("lastCompanyName") else putString("lastCompanyName", value) }.apply()

    /** Za jak dlouho v pozadí se aplikace zamkne (ihned / 1 min / 5 min). */
    fun lockDelayMs(context: Context): Long = prefs(context).getLong("lockDelayMs", 60_000L)
    fun setLockDelayMs(context: Context, value: Long) = prefs(context).edit().putLong("lockDelayMs", value).apply()

    /** Kdy byla k faktuře naposledy odeslána upomínka (jen na tomto zařízení), ať se neposílá dvakrát po sobě. */
    fun lastReminder(context: Context, invoiceId: Int): LocalDate? =
        prefs(context).getString("reminder-$invoiceId", null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    fun recordReminder(context: Context, invoiceId: Int) = prefs(context).edit().putString("reminder-$invoiceId", LocalDate.now().toString()).apply()
}

object Reminders {
    val PLACEHOLDERS = listOf("{číslo}" to "číslo faktury", "{částka}" to "zbývající částka k úhradě", "{splatnost}" to "datum splatnosti",
        "{dní_po_splatnosti}" to "počet dní po splatnosti, např. „3 dny“", "{firma}" to "název vaší firmy")

    const val DEFAULT_TEMPLATE = "Dobrý den,\n\ndovolujeme si Vás upozornit, že faktura {číslo} na částku {částka} byla splatná dne {splatnost} " +
        "({dní_po_splatnosti} po splatnosti) a v naší evidenci ji dosud nemáme uhrazenou.\n\n" +
        "Pokud jste platbu již odeslali, považujte prosím tuto zprávu za bezpředmětnou. V opačném případě Vás prosíme o její úhradu.\n\n" +
        "Děkujeme.\n{firma}"

    /** „1 den“, „3 dny“, „7 dnů“. */
    fun days(count: Int) = when (count) { 1 -> "1 den"; in 2..4 -> "$count dny"; else -> "$count dnů" }

    fun render(template: String, invoice: Invoice, companyName: String?): String {
        val overdue = parseApiDate(invoice.due)?.let { ChronoUnit.DAYS.between(it, LocalDate.now()).toInt() } ?: 0
        val amount = invoice.remaining?.takeIf { it.signum() > 0 } ?: invoice.total
        return mapOf("{číslo}" to invoice.number, "{částka}" to money(amount, invoice.currency), "{splatnost}" to (invoice.due ?: ""),
            "{dní_po_splatnosti}" to days(maxOf(overdue, 0)), "{firma}" to (companyName ?: ""))
            .entries.fold(template) { text, (key, value) -> text.replace(key, value) }
    }
}
