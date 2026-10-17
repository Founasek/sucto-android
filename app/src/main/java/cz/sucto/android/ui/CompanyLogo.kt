package cz.sucto.android.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import cz.sucto.android.R
import androidx.compose.ui.unit.dp
import cz.sucto.android.data.Company
import cz.sucto.android.ui.theme.BrandGradient
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val logos = LruCache<String, Bitmap>(24)

/** Logo firmy z adresy v API; bez něj (nebo při selhání načtení) značkový čtverec s ikonou. Načítá se jen přes HTTPS. */
@Composable
fun CompanyLogo(company: Company?, size: Dp = 52.dp) {
    val url = company?.logo
    val bitmap by produceState<Bitmap?>(initialValue = url?.let { logos.get(it) }, key1 = url) {
        if (url != null && value == null) value = withContext(Dispatchers.IO) { loadLogo(url) }?.also { logos.put(url, it) }
    }
    val shape = RoundedCornerShape(size * 0.27f)
    Box(Modifier.size(size).clip(shape).border(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .08f), shape), contentAlignment = Alignment.Center) {
        val image = bitmap
        if (image != null) {
            Box(Modifier.size(size).background(Color.White).padding(size * 0.12f), contentAlignment = Alignment.Center) {
                Image(image.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Fit)
            }
        } else {
            Box(Modifier.size(size).background(Brush.linearGradient(BrandGradient)), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_tab_company), contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.5f))
            }
        }
    }
}

/**
 * Stáhne logo. Adresa v API není zdokumentovaná, proto se zkouší i relativní cesta (vůči sucto.cz) a odkaz `http` se zvedne na `https`;
 * přesměrování (např. na úložiště souborů) se sleduje ručně, každý krok musí být HTTPS.
 */
private fun loadLogo(address: String): Bitmap? = runCatching {
    var url = resolve(address) ?: return null
    repeat(5) {
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000; readTimeout = 10_000; instanceFollowRedirects = false
            setRequestProperty("Accept", "image/*")
        }
        try {
            val code = connection.responseCode
            Log.d("CompanyLogo", "${url.host} -> HTTP $code")
            if (code in 300..399) {
                url = resolve(connection.getHeaderField("Location"), url) ?: return null
            } else {
                if (code !in 200..299 || connection.contentLengthLong > 8_000_000) return null
                return connection.inputStream.use { BitmapFactory.decodeStream(it) }
            }
        } finally { connection.disconnect() }
    }
    null
}.getOrNull()

private fun resolve(address: String?, base: URL = URL("https://www.sucto.cz/")): URL? {
    if (address.isNullOrBlank()) return null
    val resolved = runCatching { URL(base, address.trim()) }.getOrNull() ?: return null
    return if (resolved.protocol == "https") resolved
    else if (resolved.protocol == "http") runCatching { URL("https", resolved.host, resolved.port.takeIf { it != 80 } ?: -1, resolved.file) }.getOrNull()
    else null
}
