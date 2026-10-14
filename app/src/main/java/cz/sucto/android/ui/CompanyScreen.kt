package cz.sucto.android.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cz.sucto.android.data.*

/** Záložka Firma: údaje o firmě, účty a přepnutí firmy. Dvojí klepnutí na záložku otevře přepínač (řeší DashboardScreen). */
@Composable
fun CompanyScreen(company: Company, state: CompanyState, onRetry: () -> Unit, onAccount: (CompanyAccount?) -> Unit,
    onSwitch: () -> Unit, onPartners: () -> Unit, modifier: Modifier) {
    val account = state.selectedAccount
    if (account != null) { AccountDetail(account, modifier); return }
    val detail = state.detail
    LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CompanyLogo(company, 84.dp)
                Text(detail?.name ?: company.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center)
                if (detail?.isTaxable ?: company.isTaxable) Text("Plátce DPH", color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge)
            }
        }
        if (state.loading && detail == null) item { Loading("Načítám údaje o firmě…") }
        state.error?.let { error -> if (detail == null) item { ErrorCard(error, onRetry) } }
        if (detail != null) {
            item { SectionHeader("Údaje o firmě") }
            item {
                SectionCard {
                    CopyRow("IČO", detail.ic)
                    CopyRow("E-mail", detail.email)
                    if (detail.addressLines.isNotEmpty()) InfoRow("Adresa", detail.addressLines.joinToString("\n"))
                    InfoRow("Plátce DPH", if (detail.isTaxable) "Ano" else "Ne")
                }
            }
        }
        if (state.accounts.isNotEmpty()) {
            item { SectionHeader("Účty") }
            items(state.accounts, key = { it.id }) { account -> AccountCard(account) { onAccount(account) } }
        }
        item { SectionHeader("Partneři") }
        item {
            Card(onClick = onPartners, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Odběratelé a dodavatelé", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Seznam, vyhledávání a přidání partnera", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            OutlinedButton(onClick = onSwitch, modifier = Modifier.fillMaxWidth()) { Text("Přepnout firmu") }
            Spacer(Modifier.height(8.dp))
            Text("Firmu přepnete také dvojím klepnutím na záložku Firma.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp))
}

@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(1f), textAlign = TextAlign.End)
    }
}

/** Řádek, jehož hodnotu lze klepnutím zkopírovat do schránky. */
@Composable
private fun CopyRow(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clickable {
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(label, value))
        copied = true
    }, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(if (copied) "Zkopírováno" else value, Modifier.weight(1f), textAlign = TextAlign.End)
    }
}

@Composable
private fun AccountCard(account: CompanyAccount, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(account.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(if (account.isCash) "Hotovost" else account.accountNumber.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (account.isDeactivated) Text("Neaktivní", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun AccountDetail(account: CompanyAccount, modifier: Modifier) {
    LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text(account.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        if (!account.isCash) {
            item { SectionHeader("Bankovní údaje") }
            item {
                SectionCard {
                    CopyRow("Číslo účtu", account.accountNumber)
                    CopyRow("IBAN", account.iban)
                    CopyRow("SWIFT", account.swift)
                    account.bankName?.takeIf { it.isNotBlank() }?.let { InfoRow("Banka", it) }
                }
            }
        }
        item { SectionHeader("Parametry") }
        item {
            SectionCard {
                account.prefix?.takeIf { it.isNotBlank() }?.let { InfoRow("Prefix", it) }
                InfoRow("Počáteční zůstatek", account.openingBalance ?: "-")
            }
        }
        if (account.isDeactivated) item { Text("Účet je deaktivován", color = MaterialTheme.colorScheme.error) }
    }
}

/** Seznam firem účtu pro rychlé přepnutí; aktuální je označená. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanySwitcherSheet(companies: List<Company>, current: Company, onSelect: (Company) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Přepnout firmu", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            companies.forEach { company ->
                val selected = company.id == current.id
                Card(onClick = { if (!selected) onSelect(company) else onDismiss() }, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CompanyLogo(company, 40.dp)
                        Column(Modifier.weight(1f)) {
                            Text(company.name, fontWeight = FontWeight.SemiBold)
                            Text("IČ ${company.ic}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        }
                        if (selected) Text("Aktuální", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}
