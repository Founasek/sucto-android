package cz.sucto.android.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cz.sucto.android.data.AppSettings
import cz.sucto.android.data.Reminders

/** Nastavení: zámek (nejde vypnout, jen prodleva), upomínky (ve výchozím stavu vypnuté), odhlášení a verze. */
@Composable
fun SettingsScreen(onLogout: () -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val lockAvailable = remember { AppLockViewModel.isAvailable(context) }
    var delay by remember { mutableLongStateOf(AppSettings.lockDelayMs(context)) }
    var reminders by remember { mutableStateOf(AppSettings.remindersEnabled(context)) }
    var template by remember { mutableStateOf(AppSettings.reminderTemplate(context)) }
    LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Header("Zabezpečení") }
        item {
            Panel {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Zámek aplikace"); Text(if (lockAvailable) "Vždy zapnutý" else "Nedostupný", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (lockAvailable) {
                    Text("Zamknout", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    listOf(0L to "Ihned", 60_000L to "Po 1 minutě", 300_000L to "Po 5 minutách").forEach { (value, title) ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = delay == value, onClick = { delay = value; AppSettings.setLockDelayMs(context, value) })
                            Text(title)
                        }
                    }
                }
                Text(if (lockAvailable) "Zámek nejde vypnout, aplikace ukazuje finanční údaje. Po studeném startu je aplikace zamčená vždy."
                    else "V zařízení není nastavený zámek obrazovky (kód, vzor nebo biometrie), aplikaci proto nelze zamknout. Nastavte ho v nastavení Androidu.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item { Header("Upomínky") }
        item {
            Panel {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Upomínky odběratelům")
                    Switch(checked = reminders, onCheckedChange = { reminders = it; AppSettings.setRemindersEnabled(context, it) })
                }
                Text("Ve výchozím stavu vypnuto. Po zapnutí se u vydaných faktur po splatnosti objeví tlačítko „Odeslat upomínku“. " +
                    "Nic se neodesílá automaticky – každou upomínku potvrdíte a text můžete upravit.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (reminders) {
                    OutlinedTextField(template, { template = it; AppSettings.setReminderTemplate(context, it) }, modifier = Modifier.fillMaxWidth(),
                        label = { Text("Text upomínky") }, minLines = 6, maxLines = 14)
                    Text("Zástupné znaky: " + Reminders.PLACEHOLDERS.joinToString(", ") { "${it.first} (${it.second})" },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { AppSettings.setReminderTemplate(context, null); template = Reminders.DEFAULT_TEMPLATE }) { Text("Obnovit výchozí text") }
                }
            }
        }
        item { Header("O aplikaci") }
        item { Panel { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Verze"); Text(version(context)) } } }
        item { OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Odhlásit se") } }
    }
}

private fun version(context: Context): String = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "—"

@Composable
private fun Header(title: String) = Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))

@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) = Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
}
