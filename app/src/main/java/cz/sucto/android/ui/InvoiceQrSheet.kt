package cz.sucto.android.ui

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import cz.sucto.android.data.*
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceQrSheet(invoice: Invoice, recipient: String?, onDismiss: () -> Unit) {
    val payload = remember(invoice, recipient) { PaymentQr.payload(invoice, recipient) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var error by remember { mutableStateOf<String?>(null) }
    var generationFailed by remember(payload) { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = payload) {
        if (payload != null) value = withContext(Dispatchers.Default) {
            runCatching {
                val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 600, 600,
                    mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 4, EncodeHintType.CHARACTER_SET to "UTF-8"))
                val pixels = IntArray(600 * 600) { position -> if (matrix[position % 600, position / 600]) android.graphics.Color.BLACK else android.graphics.Color.WHITE }
                Bitmap.createBitmap(pixels, 600, 600, Bitmap.Config.ARGB_8888)
            }.getOrNull()
        }
        generationFailed = payload != null && value == null
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("QR platba", style = MaterialTheme.typography.titleLarge)
            Text(invoice.number)
            val image = bitmap
            if (payload == null) Text("Pro tuto fakturu nejsou dostupné údaje pro QR platbu.")
            else if (generationFailed) Text("QR kód se nepodařilo vytvořit. Zavřete okno a zkuste to znovu.")
            else if (image == null) CircularProgressIndicator()
            else {
                Image(image.asImageBitmap(), contentDescription = "Platební QR kód faktury ${invoice.number}", filterQuality = FilterQuality.None,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f).background(Color.White))
                Text(money(invoice.remaining?.takeIf { it.signum() > 0 } ?: invoice.total, invoice.currency))
                Button(onClick = {
                    sharing = true
                    scope.launch {
                        try {
                            val uri = withContext(Dispatchers.IO) {
                                val directory = File(context.cacheDir, "payment-qr").apply { mkdirs() }
                                val file = File(directory, "qr-${UUID.randomUUID()}.png")
                                file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
                                FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                            }
                            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                type = "image/png"; putExtra(Intent.EXTRA_STREAM, uri)
                                clipData = android.content.ClipData.newUri(context.contentResolver, "QR platba", uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }, "Sdílet QR platbu"))
                        } catch (exception: kotlinx.coroutines.CancellationException) { throw exception
                        } catch (exception: Exception) { error = "QR kód se nepodařilo sdílet."
                        } finally { sharing = false }
                    }
                }, enabled = !sharing, modifier = Modifier.fillMaxWidth()) { Text("Sdílet QR kód") }
                TextButton(onClick = { clipboard.setText(AnnotatedString(payload)) }, modifier = Modifier.fillMaxWidth()) { Text("Kopírovat platební údaje") }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}