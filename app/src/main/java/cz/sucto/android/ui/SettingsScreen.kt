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
import cz.sucto.android.data.DueNotifications
import cz.sucto.android.data.Reminders
import cz.sucto.android.ui.theme.AppAccent
import cz.sucto.android.ui.theme.ThemeState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Nastavení: zámek (nejde vypnout, jen prodleva), upomínky (ve výchozím stavu vypnuté), odhlášení a verze. */
@Composable
fun SettingsScreen(onLogout: () -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val lockAvailable = remember { AppLockViewModel.isAvailable(context) }
    var delay by remember { mutableLongStateOf(AppSettings.lockDelayMs(context)) }
    var notifications by remember { mutableStateOf(AppSettings.dueNotificationsEnabled(context)) }
    var details by remember { mutableStateOf(AppSettings.dueNotificationDetails(context)) }
    var reminders by remember { mutableStateOf(AppSettings.remindersEnabled(context)) }
    var template by remember { mutableStateOf(AppSettings.reminderTemplate(context)) }
    LazyColumn(modifier, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Header("Vzhled") }
        item {
            Panel {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(AppAccent.entries.toList()) { accent -> AccentSwatch(accent, ThemeState.accent == accent) { ThemeState.select(context, accent) } }
                }
                Text("Barva aplikace platí pro ovládací prvky, tlačítka, hlavní karty a částky výnosů. Stavy faktur (např. zaplaceno) a upozornění zůstávají ve svých barvách.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
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
        item {
            Panel {
                val credentials = remember { cz.sucto.android.data.CredentialStore(context) }
                var saved by remember { mutableStateOf(credentials.hasSavedLogin) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Rychlé přihlášení")
                    if (saved) TextButton(onClick = { credentials.clear(); saved = false }) {
                        Text("Zapomenout", color = MaterialTheme.colorScheme.error)
                    } else Text("Nastaví se při přihlášení", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("Biometrické přihlášení ukládá e-mail a heslo zašifrované jen do zabezpečeného úložiště tohoto zařízení; použít je půjde jen po ověření biometrií.",
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
        item { Header("Upozornění") }
        item {
            Panel {
                val permission = androidx.activity.compose.rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted ->
                    notifications = granted
                    AppSettings.setDueNotificationsEnabled(context, granted)
                    DueNotifications.schedule(context)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Upozornění na splatnost")
                    Switch(checked = notifications, onCheckedChange = { enabled ->
                        if (enabled && DueNotifications.needsPermission(context)) permission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        else { notifications = enabled; AppSettings.setDueNotificationsEnabled(context, enabled); DueNotifications.schedule(context) }
                    })
                }
                if (notifications) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Podrobnosti v upozornění")
                    Switch(checked = details, onCheckedChange = { details = it; AppSettings.setDueNotificationDetails(context, it) })
                }
                Text("Denně v 9:00 vás upozorní na faktury splatné ten den a po splatnosti. Upozornění se může zobrazit i na zamčeném telefonu, proto je ve " +
                    "výchozím stavu bez názvu firmy a počtů. Podrobnosti si zapněte jen tehdy, když vám to nevadí; na zamčené obrazovce se i tak ukáže jen " +
                    "neutrální text. Data se načtou v pozadí, když to Android dovolí, takže se upozornění může zpozdit.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item { Header("O aplikaci") }
        item { Panel { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Verze"); Text(version(context)) } } }
        item { SoftButton("Odhlásit se", onLogout, destructive = true) }
    }
}

@Composable
private fun AccentSwatch(accent: AppAccent, selected: Boolean, onClick: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(Brush.linearGradient(accent.gradient))
            .border(if (selected) 2.dp else 0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = if (selected) .5f else .1f), CircleShape),
            contentAlignment = Alignment.Center) {
            if (selected) Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
        }
        Text(accent.title, style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun version(context: Context): String = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "—"

@Composable
private fun Header(title: String) = SectionLabel(title)

@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) = SuctoCard { content() }
