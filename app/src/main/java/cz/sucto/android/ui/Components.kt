package cz.sucto.android.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.sucto.android.data.Invoice
import cz.sucto.android.ui.theme.*

// Sdílené stavební prvky vzhledu (odpovídají iOS Theme: karta 20, ovládací prvky 14, hero 28, značkový přechod).

private val CardShape = RoundedCornerShape(20.dp)

/** Zaoblená karta s jemným okrajem a stínem; s `onClick` je celá klepací. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuctoCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    val border = BorderStroke(0.5.dp, colors.onSurface.copy(alpha = .06f))
    val inner: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().padding(padding), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
    if (onClick != null) Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = CardShape, color = colors.surface,
        border = border, shadowElevation = 3.dp, content = inner)
    else Surface(modifier = modifier.fillMaxWidth(), shape = CardShape, color = colors.surface, border = border, shadowElevation = 3.dp, content = inner)
}

/** Nadpis sekce nad kartou: malá velká písmena v tlumené barvě. */
@Composable
fun SectionLabel(title: String, modifier: Modifier = Modifier) {
    Text(title.uppercase(), modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp), style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Řádek „popisek – hodnota“ ve dvou sloupcích; dlouhá hodnota se zalomí. */
@Composable
fun InfoRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(1.2f), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End)
    }
}

/** Hlavní plocha stránky: značkový (nebo červený) přechod, velké zaoblení a jemný světlý kruh v rohu. */
@Composable
fun HeroCard(modifier: Modifier = Modifier, colors: List<Color> = BrandGradient, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth()
        .shadow(14.dp, RoundedCornerShape(28.dp), ambientColor = colors.first(), spotColor = colors.first())
        .clip(RoundedCornerShape(28.dp))
        .background(Brush.linearGradient(colors))
        .drawBehind { drawCircle(Color.White.copy(alpha = .08f), radius = 100.dp.toPx(), center = Offset(size.width - 10.dp.toPx(), 0f)) }
        .padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

/** Hlavní tlačítko s přechodem. */
@Composable
fun GradientButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val shape = RoundedCornerShape(14.dp)
    Box(modifier.fillMaxWidth().height(52.dp).clip(shape)
        .background(Brush.linearGradient(BrandGradient.map { it.copy(alpha = if (enabled) 1f else .4f) }))
        .clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Text(text, color = Color.White, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
    }
}

/** Sekundární tlačítko: obrys v barvě karty. */
@Composable
fun SoftButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, destructive: Boolean = false) {
    val color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    OutlinedButton(onClick = onClick, modifier = modifier.fillMaxWidth().height(48.dp), enabled = enabled, shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, color.copy(alpha = if (enabled) .35f else .15f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = color)) {
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

/** Plovoucí tlačítko ve tvaru kapsle s přechodem (např. „Nová faktura“). */
@Composable
fun GradientFab(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Row(modifier.shadow(12.dp, CircleShape, ambientColor = Brand, spotColor = Brand).clip(CircleShape)
        .background(Brush.linearGradient(BrandGradient)).clickable(onClick = onClick).padding(horizontal = 22.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        icon?.let { Icon(it, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp)) }
        Text(text, color = Color.White, fontWeight = FontWeight.SemiBold)
    }
}

/** Kapsle pro filtr: vybraná má přechod a bílý text. */
@Composable
fun FilterPill(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(modifier.clip(CircleShape)
        .then(if (selected) Modifier.background(Brush.linearGradient(BrandGradient)) else Modifier.background(colors.surface)
            .border(0.5.dp, colors.onSurface.copy(alpha = .1f), CircleShape))
        .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
        Text(text, color = if (selected) Color.White else colors.onSurface, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

/** Přepínač dvou nebo více stran v kapsli (Vydané / Přijaté, Příjmové / Výdajové). */
@Composable
fun SegmentedTabs(titles: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth().clip(CircleShape).background(colors.surface).border(0.5.dp, colors.onSurface.copy(alpha = .06f), CircleShape)
        .padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        titles.forEachIndexed { index, title ->
            val isSelected = index == selected
            Box(Modifier.weight(1f).clip(CircleShape)
                .then(if (isSelected) Modifier.background(Brush.linearGradient(BrandGradient)) else Modifier)
                .clickable { onSelect(index) }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                Text(title, color = if (isSelected) Color.White else colors.onSurfaceVariant, maxLines = 1,
                    style = MaterialTheme.typography.labelLarge, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium)
            }
        }
    }
}

/** Vyhledávací pole se zaoblením 14 a ikonou lupy. */
@Composable
fun SearchField(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    OutlinedTextField(value, onChange, modifier.fillMaxWidth(), singleLine = true, placeholder = { Text(hint, maxLines = 1) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }, shape = RoundedCornerShape(14.dp),
        keyboardOptions = KeyboardOptions(), colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = colors.surface, unfocusedContainerColor = colors.surface,
            unfocusedBorderColor = colors.onSurface.copy(alpha = .08f), focusedBorderColor = colors.primary))
}

/** Odznak stavu faktury: barva a ikona podle významu, po splatnosti červeně. */
@Composable
fun StatusBadge(invoice: Invoice, modifier: Modifier = Modifier) {
    val (color, icon) = when {
        invoice.isOverdue -> MaterialTheme.colorScheme.error to Icons.Default.Warning
        invoice.statusId in listOf(8, 11) -> Color(0xFF2F9E55) to Icons.Default.CheckCircle
        invoice.statusId == 10 -> MaterialTheme.colorScheme.error to Icons.Default.Warning
        invoice.statusId in listOf(5, 6, 7) -> Amber to Icons.Default.Warning
        invoice.statusId in listOf(2, 3, 4) -> Info to Icons.Default.Send
        else -> MaterialTheme.colorScheme.onSurfaceVariant to Icons.Default.Edit
    }
    Row(modifier.clip(CircleShape).background(color.copy(alpha = .14f)).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(13.dp))
        Text(if (invoice.isOverdue) "Po splatnosti" else invoice.status, color = color, style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** Kruhová ikona na tlumeném značkovém pozadí (u položek bez loga). */
@Composable
fun IconBadge(icon: ImageVector, modifier: Modifier = Modifier, size: Dp = 44.dp, tint: Color = MaterialTheme.colorScheme.primary) {
    Box(modifier.size(size).clip(RoundedCornerShape(size * .3f)).background(Brand.copy(alpha = .15f)), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * .5f))
    }
}

/** Načítání uprostřed obsahu. */
@Composable
internal fun Loading(label: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(12.dp))
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Chyba s tlačítkem pro opakování. */
@Composable
internal fun ErrorCard(error: String, onRetry: () -> Unit) {
    SuctoCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
        }
        SoftButton("Zkusit znovu", onRetry)
    }
}

/** Prázdný stav: ikona a krátká zpráva. */
@Composable
internal fun EmptyState(icon: ImageVector, message: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        IconBadge(icon, size = 64.dp)
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

/** Info v pruhu (např. offline data). */
@Composable
internal fun Notice(text: String) {
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}
