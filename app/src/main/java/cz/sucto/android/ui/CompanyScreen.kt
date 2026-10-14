package cz.sucto.android.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cz.sucto.android.data.*

/** Záložka Firma: údaje o firmě, účty, partneři a přepnutí firmy. Dvojí klepnutí na záložku otevře přepínač (řeší DashboardScreen). */
@Composable
fun CompanyScreen(company: Company, state: CompanyState, onRetry: () -> Unit, onAccount: (CompanyAccount?) -> Unit,
    onSwitch: () -> Unit, onPartners: () -> Unit, modifier: Modifier) {
    val account = state.selectedAccount
    if (account != null) { AccountDetail(account, modifier); return }
    val detail = state.detail
    LazyColumn(modifier, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CompanyLogo(company, 84.dp)
                Text(detail?.name ?: company.name, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                if (detail?.isTaxable ?: company.isTaxable) Text("Plátce DPH", color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge, modifier = Modifier.clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = .14f)).padding(horizontal = 12.dp, vertical = 4.dp))
            }
        }
        if (state.loading && detail == null) item { Loading("Načítám údaje o firmě…") }
        state.error?.let { error -> if (detail == null) item { ErrorCard(error, onRetry) } }
        if (detail != null) item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Údaje o firmě")
                SuctoCard {
                    CopyRow("IČO", detail.ic)
                    CopyRow("E-mail", detail.email)
                    if (detail.addressLines.isNotEmpty()) InfoRow("Adresa", detail.addressLines.joinToString("\n"))
                    InfoRow("Plátce DPH", if (detail.isTaxable) "Ano" else "Ne")
                }
            }
        }
        if (state.accounts.isNotEmpty()) {
            item { SectionLabel("Účty") }
            items(state.accounts, key = { it.id }) { account ->
                NavRow(if (account.isCash) Icons.Default.AccountBox else Icons.Default.Home, account.name,
                    if (account.isCash) "Hotovost" else account.accountNumber.orEmpty(), { onAccount(account) }, dimmed = account.isDeactivated)
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Partneři")
                NavRow(Icons.Default.Person, "Odběratelé a dodavatelé", "Seznam, vyhledávání a přidání partnera", onPartners)
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SoftButton("Přepnout firmu", onSwitch)
                Text("Firmu přepnete také dvojím klepnutím na záložku Firma.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/** Karta s ikonou, dvěma řádky textu a šipkou; klepnutím se otevře cíl. */
@Composable
internal fun NavRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit, dimmed: Boolean = false) {
    SuctoCard(onClick = onClick) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IconBadge(icon)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = if (dimmed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Řádek, jehož hodnotu lze klepnutím zkopírovat do schránky. */
@Composable
internal fun CopyRow(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clickable {
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(label, value))
        copied = true
    }, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(if (copied) "Zkopírováno" else value, Modifier.weight(1.2f), textAlign = TextAlign.End,
            color = if (copied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun AccountDetail(account: CompanyAccount, modifier: Modifier) {
    LazyColumn(modifier, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            HeroCard {
                Text(account.name, color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.titleLarge)
                Text(if (account.isCash) "Hotovost" else account.accountNumber.orEmpty(), color = androidx.compose.ui.graphics.Color.White.copy(alpha = .8f))
            }
        }
        if (!account.isCash) item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Bankovní údaje")
                SuctoCard {
                    CopyRow("Číslo účtu", account.accountNumber)
                    CopyRow("IBAN", account.iban)
                    CopyRow("SWIFT", account.swift)
                    account.bankName?.takeIf { it.isNotBlank() }?.let { InfoRow("Banka", it) }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Parametry")
                SuctoCard {
                    account.prefix?.takeIf { it.isNotBlank() }?.let { InfoRow("Prefix", it) }
                    InfoRow("Počáteční zůstatek", account.openingBalance ?: "-")
                    if (account.isDeactivated) Text("Účet je deaktivován", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

/** Seznam firem účtu pro rychlé přepnutí; aktuální je označená. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanySwitcherSheet(companies: List<Company>, current: Company, onSelect: (Company) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Přepnout firmu", style = MaterialTheme.typography.titleLarge)
            companies.forEach { company ->
                val selected = company.id == current.id
                SuctoCard(onClick = { if (!selected) onSelect(company) else onDismiss() }) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        CompanyLogo(company, 44.dp)
                        Column(Modifier.weight(1f)) {
                            Text(company.name, style = MaterialTheme.typography.titleMedium)
                            Text("IČ ${company.ic}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        }
                        if (selected) Text("Aktuální", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}
