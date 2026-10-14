package cz.sucto.android.data

import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Nezaplacená faktura se splatností – podklad pro prognózu a stáří pohledávek. */
data class DueItem(val id: Int, val isIncoming: Boolean, val number: String, val counterparty: String?, val due: LocalDate,
    val amount: BigDecimal, val currency: String)

fun isCzechCrown(currency: String) = currency == "Kč" || currency.equals("CZK", ignoreCase = true)

/** Měny se nepřepočítávají; česká koruna je vždy první, ostatní podle objemu. */
private fun <T> orderCurrencies(map: Map<String, T>, weight: (T) -> BigDecimal): List<Map.Entry<String, T>> =
    map.entries.sortedWith(compareByDescending<Map.Entry<String, T>> { isCzechCrown(it.key) }.thenByDescending { weight(it.value) })

/** Stáří prošlých pohledávek (vydané) a závazků (přijaté): 1–30, 31–60, 61–90 a přes 90 dní, po měnách. */
class AgingSummary(items: List<DueItem>, today: LocalDate = LocalDate.now()) {
    class Amounts(val count: Int = 0, val amount: BigDecimal = BigDecimal.ZERO) {
        operator fun plus(other: Amounts) = Amounts(count + other.count, amount + other.amount)
        val has: Boolean get() = count != 0
    }
    class Row(val currency: String, val issued: List<Amounts>, val received: List<Amounts>) {
        val issuedTotal: Amounts get() = issued.fold(Amounts()) { a, b -> a + b }
        val receivedTotal: Amounts get() = received.fold(Amounts()) { a, b -> a + b }
    }
    val rows: List<Row>
    val isEmpty: Boolean get() = rows.isEmpty()
    init {
        val issued = mutableMapOf<String, MutableList<Amounts>>()
        val received = mutableMapOf<String, MutableList<Amounts>>()
        items.forEach { item ->
            val days = ChronoUnit.DAYS.between(item.due, today).toInt()
            if (days < 1) return@forEach
            val bucket = when { days <= 30 -> 0; days <= 60 -> 1; days <= 90 -> 2; else -> 3 }
            val target = if (item.isIncoming) received else issued
            issued.getOrPut(item.currency) { MutableList(4) { Amounts() } }
            received.getOrPut(item.currency) { MutableList(4) { Amounts() } }
            val list = target.getValue(item.currency)
            list[bucket] = list[bucket] + Amounts(1, item.amount)
        }
        val byCurrency = issued.mapValues { (currency, values) -> Row(currency, values, received.getValue(currency)) }
        rows = orderCurrencies(byCurrency) { (it.issuedTotal + it.receivedTotal).amount }.map { it.value }
    }
    companion object { val BUCKETS = listOf("1–30 dní", "31–60 dní", "61–90 dní", "přes 90 dní") }
}

/** Prognóza splatností na dalších 30 dní po týdnech (příjmy = vydané faktury, platby = přijaté) a nejbližší splatné faktury. */
class ForecastSummary(items: List<DueItem>, today: LocalDate = LocalDate.now()) {
    class Week(val start: LocalDate, val incoming: BigDecimal, val outgoing: BigDecimal)
    class Row(val currency: String, val weeks: List<Week>) {
        val incomingTotal: BigDecimal get() = weeks.fold(BigDecimal.ZERO) { a, w -> a + w.incoming }
        val outgoingTotal: BigDecimal get() = weeks.fold(BigDecimal.ZERO) { a, w -> a + w.outgoing }
        val net: BigDecimal get() = incomingTotal - outgoingTotal
    }
    val rows: List<Row>
    val upcoming: List<DueItem>
    val isEmpty: Boolean get() = rows.isEmpty()
    init {
        val inRange = items.filter { ChronoUnit.DAYS.between(today, it.due) in 0..HORIZON_DAYS }
        val byCurrency = inRange.groupBy { it.currency }.mapValues { (_, list) ->
            Row(list.first().currency, (0 until 5).map { index ->
                val inWeek = list.filter { minOf(ChronoUnit.DAYS.between(today, it.due).toInt() / 7, 4) == index }
                Week(today.plusDays(index * 7L), inWeek.filter { !it.isIncoming }.fold(BigDecimal.ZERO) { a, i -> a + i.amount },
                    inWeek.filter { it.isIncoming }.fold(BigDecimal.ZERO) { a, i -> a + i.amount })
            })
        }
        rows = orderCurrencies(byCurrency) { it.incomingTotal + it.outgoingTotal }.map { it.value }
        upcoming = inRange.sortedBy { it.due }.take(6)
    }
    companion object { const val HORIZON_DAYS = 30L }
}
