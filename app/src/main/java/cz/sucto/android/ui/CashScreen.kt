package cz.sucto.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cz.sucto.android.data.*
import cz.sucto.android.ui.theme.*

/** Pokladna: přepínač Příjmové/Výdajové, hledání a seznam dokladů; po klepnutí detail. Stav dokladu se nezobrazuje (API ho nevrací spolehlivě). */
@Composable
internal fun CashScreen(state: CashState, model: CashViewModel, modifier: Modifier) {
    val detail = state.detail
    LazyColumn(modifier, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (detail == null) {
            item { SegmentedTabs(CashDirection.entries.map { it.title }, state.direction.ordinal, { model.select(CashDirection.entries[it]) }) }
            item { SearchField(state.search, model::search, "Číslo dokladu nebo partner") }
            items(state.items, key = { it.id }) { voucher ->
                SuctoCard(onClick = { model.open(voucher) }) {
                    Text(voucher.number, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(voucher.recipient ?: voucher.description ?: "—", style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                        Text(money(voucher.total, "Kč"), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge.merge(MoneyStyle), maxLines = 1)
                        voucher.date?.let {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(dateLabel(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            if (state.loading) item { Loading("Načítám doklady…") }
            state.error?.let { error -> item { ErrorCard(error) { if (state.items.isEmpty()) model.refresh() else model.nextPage() } } }
            if (!state.loading && state.error == null && state.items.isEmpty()) item { EmptyState(Icons.Default.List, "Žádné doklady neodpovídají výběru.") }
            if (!state.loading && state.hasMore && state.error == null && state.items.isNotEmpty()) item(key = "more-${state.nextPage}") {
                LaunchedEffect(state.nextPage) { model.nextPage() }
                Spacer(Modifier.height(1.dp))
            }
        } else {
            item {
                HeroCard {
                    Text(detail.recipient ?: "—", color = Color.White, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(money(detail.total, "Kč"), color = Color.White, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
            if (state.detailLoading) item { Loading("Načítám detail…") }
            state.detailError?.let { error -> item { ErrorCard(error) { model.open(detail) } } }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("Údaje dokladu")
                    SuctoCard {
                        InfoRow("Datum", dateLabel(detail.date))
                        InfoRow("Externí číslo", detail.externalNumber ?: "—")
                        InfoRow("Účet", detail.account ?: "—")
                        InfoRow("Základ", money(detail.base, "Kč"))
                    }
                }
            }
            detail.description?.let { text -> item { TextBlock("Popis", text) } }
            if (detail.items.isNotEmpty()) item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("Položky")
                    SuctoCard {
                        detail.items.forEachIndexed { index, line ->
                            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text(line.name, fontWeight = FontWeight.Medium)
                                    Text(listOfNotNull(line.quantity, line.unit).joinToString(" "), style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(money(line.total, "Kč"), style = MaterialTheme.typography.bodyLarge.merge(MoneyStyle))
                            }
                        }
                    }
                }
            }
        }
    }
}
