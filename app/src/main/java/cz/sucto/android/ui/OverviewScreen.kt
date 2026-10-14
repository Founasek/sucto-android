package cz.sucto.android.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cz.sucto.android.data.*
import cz.sucto.android.ui.theme.*
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.max

/** Přehled hospodaření: výběr roku, hlavní karta s výsledkem, výnosy a náklady a graf po měsících (u každé měny). */
@Composable
internal fun OverviewScreen(state: DashboardState, onYear: (Int) -> Unit, onRetry: () -> Unit, onOpenDue: (DueItem) -> Unit, modifier: Modifier) {
    val overview = state.overview
    val current = LocalDate.now().year
    LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items((current downTo current - 5).toList()) { year -> FilterPill(year.toString(), state.year == year, { onYear(year) }) }
            }
        }
        if (state.overviewLoading && overview == null) item { Loading("Načítám přehled…") }
        state.overviewError?.let { error -> item { ErrorCard(error, onRetry) } }
        if (overview != null && overview.sections.isEmpty() && !state.overviewLoading) item {
            EmptyState(Icons.Default.DateRange, if (overview.fromInvoices) "Za rok ${state.year} nejsou vystavené ani přijaté faktury."
                else "Za rok ${state.year} nejsou v účetním deníku žádné údaje.")
        }
        if (overview != null && overview.sections.isNotEmpty()) {
            item(key = "result") { ResultCard(overview, state.year) }
            if (overview.sections.size == 1) {
                val section = overview.sections.first()
                item(key = "totals") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FigureCard(if (overview.fromInvoices) "Vydané" else "Výnosy", section.revenue, section.currency, Brand, true, Modifier.weight(1f))
                        FigureCard(if (overview.fromInvoices) "Přijaté" else "Náklady", section.cost, section.currency, Amber, false, Modifier.weight(1f))
                    }
                }
            }
            overview.sections.forEach { section -> item(key = "chart-${section.currency}") { MonthlyChart(section, overview.fromInvoices) } }
        }
        state.forecast?.takeIf { !it.isEmpty }?.let { forecast -> item(key = "forecast") { ForecastCard(forecast, onOpenDue) } }
        state.aging?.takeIf { !it.isEmpty }?.let { aging -> item(key = "aging") { AgingCard(aging) } }
    }
}

/** Hlavní karta: výsledek roku; při více měnách je v ní řádek pro každou měnu. */
@Composable
private fun ResultCard(overview: Overview, year: Int) {
    val single = overview.sections.size == 1
    val negative = single && overview.sections.first().result.signum() < 0
    HeroCard(colors = if (negative) NegativeGradient else BrandGradient) {
        Text("Hospodářský výsledek · $year", color = Color.White.copy(alpha = .75f), style = MaterialTheme.typography.labelLarge)
        if (overview.fromInvoices) Text("Přehled z faktur · částky bez DPH", color = Color.White.copy(alpha = .6f), style = MaterialTheme.typography.bodySmall)
        overview.sections.forEachIndexed { index, section ->
            if (index > 0) HorizontalDivider(color = Color.White.copy(alpha = .25f), modifier = Modifier.padding(vertical = 4.dp))
            val positive = section.result.signum() >= 0
            Text(money(section.result, section.currency), style = if (single) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold, color = if (positive || single) Color.White else Color(0xFFFFB4AB), maxLines = 1)
            if (!single) Text("${if (overview.fromInvoices) "Vydané" else "Výnosy"} ${money(section.revenue, section.currency)} · " +
                "${if (overview.fromInvoices) "Přijaté" else "Náklady"} ${money(section.cost, section.currency)}",
                color = Color.White.copy(alpha = .75f), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun FigureCard(title: String, amount: BigDecimal, currency: String, color: Color, income: Boolean, modifier: Modifier) {
    SuctoCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(if (income) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp, contentDescription = null, tint = color,
                modifier = Modifier.size(28.dp).clip(CircleShape).background(color.copy(alpha = .15f)).padding(4.dp))
            Text(title, color = color, style = MaterialTheme.typography.labelLarge)
        }
        Text(money(amount, currency), style = MaterialTheme.typography.titleMedium.merge(MoneyStyle), maxLines = 1)
    }
}

@Composable
private fun MonthlyChart(section: OverviewSection, fromInvoices: Boolean) {
    var selected by rememberSaveable(section.currency) { mutableStateOf(LocalDate.now().monthValue) }
    val revenueLabel = if (fromInvoices) "Vydané" else "Výnosy"
    val costLabel = if (fromInvoices) "Přijaté" else "Náklady"
    val figure = section.months.first { it.month == selected }
    val axisColor = MaterialTheme.colorScheme.outlineVariant
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel("Po měsících · ${section.currency}")
        SuctoCard {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Legend(revenueLabel, Brand); Legend(costLabel, Amber)
            }
            Canvas(Modifier.fillMaxWidth().height(160.dp).semantics {
                contentDescription = "Měsíční graf. Hodnoty zvoleného měsíce jsou uvedené pod grafem."
            }) {
                val values = section.months.flatMap { listOf(it.revenue.toFloat(), it.cost.toFloat()) }
                val maximum = max(1f, values.maxOrNull() ?: 1f)
                val minimum = kotlin.math.min(0f, values.minOrNull() ?: 0f)
                val span = maximum - minimum
                val baseline = size.height * maximum / span
                drawLine(axisColor, Offset(0f, baseline), Offset(size.width, baseline), 1.dp.toPx())
                val slot = size.width / 12f
                val width = slot * .28f
                section.months.forEachIndexed { index, month ->
                    val dim = if (month.month == selected) 1f else .55f
                    listOf(month.revenue.toFloat() to Brand, month.cost.toFloat() to Amber).forEachIndexed { side, (amount, color) ->
                        val y = baseline - amount / span * size.height
                        drawRoundRect(color.copy(alpha = dim), Offset(slot * index + slot * .12f + side * width * 1.25f, kotlin.math.min(y, baseline)),
                            Size(width, abs(baseline - y)), CornerRadius(3.dp.toPx()))
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                (1..12).forEach { Text(it.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..12).forEach { month -> FilterPill("$month", selected == month, { selected = month }) }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
            InfoRow(revenueLabel, money(figure.revenue, section.currency))
            InfoRow(costLabel, money(figure.cost, section.currency))
            InfoRow("Výsledek", money(figure.revenue - figure.cost, section.currency))
        }
    }
}

@Composable
private fun Legend(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
