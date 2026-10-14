package cz.sucto.android.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cz.sucto.android.data.*
import cz.sucto.android.ui.theme.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

private val weekFormat = DateTimeFormatter.ofPattern("d. M.")

/** Částka bez haléřů („12 345 Kč“), jako v přehledu na iOS. */
internal fun moneyWhole(value: BigDecimal, currency: String): String =
    NumberFormat.getNumberInstance(Locale.forLanguageTag("cs-CZ")).apply { maximumFractionDigits = 0 }
        .format(value.setScale(0, RoundingMode.HALF_UP)) + " " + currency

/**
 * Splatnosti na 30 dní: nejdřív nejbližší splatné faktury (klepnutím detail), pak graf po týdnech a součty;
 * při více měnách je graf se součty v posuvném pageru a nahoře kapsle s měnou.
 */
@Composable
internal fun ForecastCard(forecast: ForecastSummary, onOpen: (DueItem) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel("Splatnosti na 30 dní")
        SuctoCard {
            if (forecast.upcoming.isNotEmpty()) {
                Text("NEJBLIŽŠÍ SPLATNOSTI", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                forecast.upcoming.forEach { item -> UpcomingRow(item) { onOpen(item) } }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
            }
            CurrencyPages(forecast.rows.map { it.currency }) { index -> ForecastPage(forecast.rows[index]) }
            Text("Příjmy jsou vydané faktury, platby přijaté. Částky jsou zbývající k úhradě, měny se nepřepočítávají.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun UpcomingRow(item: DueItem, onClick: () -> Unit) {
    val color = if (item.isIncoming) Amber else Brand
    Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickableRow(onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(if (item.isIncoming) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = if (item.isIncoming) "Platba" else "Příjem",
            tint = color, modifier = Modifier.size(28.dp).clip(CircleShape).background(color.copy(alpha = .15f)).padding(4.dp))
        Column(Modifier.weight(1f)) {
            Text(item.counterparty ?: item.number, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            Text(item.due.format(DateTimeFormatter.ofPattern("EEE d. M.", Locale.forLanguageTag("cs-CZ"))), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(moneyWhole(item.amount, item.currency), style = MaterialTheme.typography.bodyMedium.merge(MoneyStyle), maxLines = 1)
        Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
    }
}

private fun Modifier.clickableRow(onClick: () -> Unit): Modifier = this.then(Modifier.clickable(onClick = onClick))

/** Jedna měna = jedna stránka; u jedné měny bez pageru a bez kapslí. */
@Composable
private fun CurrencyPages(currencies: List<String>, page: @Composable (Int) -> Unit) {
    if (currencies.size == 1) { page(0); return }
    val pager = rememberPagerState { currencies.size }
    val scope = rememberCoroutineScope()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(currencies.indices.toList()) { index ->
                FilterPill(currencies[index], pager.currentPage == index, { scope.launch { pager.animateScrollToPage(index) } })
            }
        }
        HorizontalPager(pager, Modifier.fillMaxWidth(), pageSpacing = 16.dp, verticalAlignment = Alignment.Top) { index -> page(index) }
    }
}

@Composable
private fun ForecastPage(row: ForecastSummary.Row) {
    val axis = MaterialTheme.colorScheme.outlineVariant
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) { LegendDot("Příjmy", Brand); LegendDot("Platby", Amber) }
        Canvas(Modifier.fillMaxWidth().height(130.dp)) {
            val maximum = maxOf(1f, row.weeks.maxOf { maxOf(it.incoming.toFloat(), it.outgoing.toFloat()) })
            val slot = size.width / row.weeks.size
            val width = slot * .26f
            drawLine(axis, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
            row.weeks.forEachIndexed { index, week ->
                listOf(week.incoming.toFloat() to Brand, week.outgoing.toFloat() to Amber).forEachIndexed { side, (amount, color) ->
                    val height = amount / maximum * size.height
                    drawRoundRect(color, Offset(slot * index + slot * .18f + side * width * 1.3f, size.height - height), Size(width, height), CornerRadius(3.dp.toPx()))
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            row.weeks.forEach { Text(it.start.format(weekFormat), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        InfoRow("Očekávané příjmy", moneyWhole(row.incomingTotal, row.currency))
        InfoRow("Platby dodavatelům", moneyWhole(row.outgoingTotal, row.currency))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Saldo", fontWeight = FontWeight.Bold)
            Text(moneyWhole(row.net, row.currency), style = MaterialTheme.typography.bodyLarge.merge(MoneyStyle),
                color = if (row.net.signum() >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Stáří po splatnosti: kolik pohledávek (vydané) a závazků (přijaté) je prošlých 1–30, 31–60, 61–90 a přes 90 dní. */
@Composable
internal fun AgingCard(aging: AgingSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel("Po splatnosti")
        SuctoCard {
            CurrencyPages(aging.rows.map { it.currency }) { index -> AgingPage(aging.rows[index]) }
            Text("Vydané: dluží nám odběratelé. Přijaté: dlužíme my. Částky jsou zbývající k úhradě, měny se nepřepočítávají.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AgingPage(row: AgingSummary.Row) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(1f))
            Text("Vydané", Modifier.weight(1.3f), style = MaterialTheme.typography.labelMedium, color = Brand, fontWeight = FontWeight.SemiBold)
            Text("Přijaté", Modifier.weight(1.3f), style = MaterialTheme.typography.labelMedium, color = Amber, fontWeight = FontWeight.SemiBold)
        }
        AgingSummary.BUCKETS.forEachIndexed { index, title ->
            val issued = row.issued[index]
            val received = row.received[index]
            if (issued.has || received.has) AgingRow(title, issued, received, row.currency)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
        AgingRow("Celkem", row.issuedTotal, row.receivedTotal, row.currency, bold = true)
    }
}

@Composable
private fun AgingRow(title: String, issued: AgingSummary.Amounts, received: AgingSummary.Amounts, currency: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
        AgingCell(issued, currency, Modifier.weight(1.3f))
        AgingCell(received, currency, Modifier.weight(1.3f))
    }
}

@Composable
private fun AgingCell(amounts: AgingSummary.Amounts, currency: String, modifier: Modifier) {
    Column(modifier) {
        if (amounts.has) {
            Text(moneyWhole(amounts.amount, currency), style = MaterialTheme.typography.bodySmall.merge(MoneyStyle), maxLines = 1)
            Text("${amounts.count}×", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else Text("—", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
