package cz.sucto.android.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import cz.sucto.android.data.Invoice
import cz.sucto.android.data.InvoiceCsv
import java.io.File
import java.util.UUID

/** Akce detailu vydané faktury. `onEmail` dostane příjemce, text a zpětné volání s chybou (nebo `null`). */
class InvoiceActions(val canPay: Boolean, val busy: Boolean, val remindersEnabled: Boolean, val companyName: String,
    val lastReminder: String?, val onPay: () -> Unit, val onEmail: (String, String, (String?) -> Unit) -> Unit, val onReminderSent: () -> Unit)

/** Okno pro odeslání faktury e-mailem; stejné okno slouží i pro upomínku (předvyplněný a upravitelný text). */
@Composable
fun SendEmailDialog(title: String, initialEmail: String, initialComment: String, commentLabel: String, hint: String?,
    onSend: (String, String, (String?) -> Unit) -> Unit, onDismiss: () -> Unit) {
    var email by rememberSaveable { mutableStateOf(initialEmail) }
    var comment by rememberSaveable { mutableStateOf(initialComment) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val valid = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(email.trim())
    AlertDialog(onDismissRequest = { if (!sending) onDismiss() }, title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(email, { email = it }, singleLine = true, label = { Text("E-mail příjemce") }, enabled = !sending,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Email))
                OutlinedTextField(comment, { comment = it }, label = { Text(commentLabel) }, enabled = !sending, minLines = 4, maxLines = 12)
                hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = valid && !sending, onClick = {
                sending = true; error = null
                onSend(email, comment) { failure -> sending = false; if (failure == null) onDismiss() else error = failure }
            }) { Text(if (sending) "Odesílám…" else "Odeslat") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !sending) { Text("Zrušit") } })
}

/** Zapíše CSV do cache a otevře sdílení. */
fun shareInvoicesCsv(context: Context, invoices: List<Invoice>, outgoing: Boolean) {
    val directory = File(context.cacheDir, "exports").apply { mkdirs() }
    // Staré exporty se mažou, ať v cache nezůstávají finanční data.
    directory.listFiles()?.forEach { it.delete() }
    val file = File(directory, "${UUID.randomUUID()}-${InvoiceCsv.fileName(outgoing)}").apply { writeBytes(InvoiceCsv.bytes(invoices)) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"; putExtra(Intent.EXTRA_STREAM, uri)
        clipData = android.content.ClipData.newUri(context.contentResolver, "Faktury", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }, "Sdílet export faktur"))
}
