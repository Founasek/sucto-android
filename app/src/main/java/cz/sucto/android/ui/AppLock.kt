package cz.sucto.android.ui

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import cz.sucto.android.R
import cz.sucto.android.ui.theme.Ink
import cz.sucto.android.ui.theme.InkGlow
import cz.sucto.android.ui.theme.InkSoft
import cz.sucto.android.ui.theme.BrandButton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Zámek aplikace přes biometrii nebo kód zařízení. Vypnout ho nejde: je zapnutý vždy, když zařízení umí ověření.
 * Aplikace ukazuje finanční data, proto zámek není volba. Zamyká se po návratu z pozadí, pokud v něm byla aspoň
 * zvolenou prodlevu (Nastavení, výchozí 1 minuta). Relace je zatím jen v paměti, takže po studeném startu je stejně potřeba přihlášení.
 */
class AppLockViewModel : ViewModel() {
    data class State(val locked: Boolean = false, val authenticating: Boolean = false, val message: String? = null)

    private val mutableState = MutableStateFlow(State())
    val state = mutableState.asStateFlow()
    private var backgroundedAt: Long? = null
    /** Automatické ověření proběhlo jednou pro dané zamčení; po zrušení se čeká na tlačítko (žádná smyčka). */
    var autoPromptUsed = false
        private set

    fun onStop() {
        if (!state.value.authenticating) backgroundedAt = SystemClock.elapsedRealtime()
    }

    fun onStart(context: Context) {
        val started = backgroundedAt ?: return
        backgroundedAt = null
        if (!isAvailable(context)) { mutableState.value = State(); return }
        if (SystemClock.elapsedRealtime() - started >= cz.sucto.android.data.AppSettings.lockDelayMs(context)) lock()
    }

    fun lock() {
        if (state.value.locked) return
        autoPromptUsed = false
        mutableState.value = State(locked = true)
    }

    fun markAutoPromptUsed() { autoPromptUsed = true }
    fun setAuthenticating(value: Boolean) = mutableState.updateState { it.copy(authenticating = value) }
    fun unlocked() { mutableState.value = State() }
    fun failed(message: String?) = mutableState.updateState { it.copy(authenticating = false, message = message) }

    private fun MutableStateFlow<State>.updateState(transform: (State) -> State) { value = transform(value) }

    companion object {
        /** `true`, pokud zařízení umí ověření (biometrie nebo kód). Bez zabezpečení zařízení se zamknout nedá. */
        fun isAvailable(context: Context): Boolean =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS
            else (context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isDeviceSecure
    }
}

/** Zobrazí systémové ověření; po úspěchu odemkne. */
fun FragmentActivity.promptUnlock(lock: AppLockViewModel) {
    if (lock.state.value.authenticating) return
    lock.setAuthenticating(true)
    val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = lock.unlocked()
        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            val cancelled = errorCode == BiometricPrompt.ERROR_USER_CANCELED || errorCode == BiometricPrompt.ERROR_CANCELED ||
                errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON
            lock.failed(if (cancelled) null else "Ověření se nezdařilo.")
        }
    })
    val builder = BiometricPrompt.PromptInfo.Builder().setTitle("Odemkněte aplikaci sÚčto")
    allowDeviceCredential(builder)
    prompt.authenticate(builder.build())
}

/** Biometrie nebo kód zařízení; od Androidu 11 přes autentizátory, dřív přes starší příznak. */
@Suppress("DEPRECATION")
private fun allowDeviceCredential(builder: BiometricPrompt.PromptInfo.Builder) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) builder.setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
    else builder.setDeviceCredentialAllowed(true)
}

/** Zamčená obrazovka: logo a tlačítko dole; tlačítko se při probíhajícím ověření skryje. */
@Composable
fun LockScreen(lock: AppLockViewModel, activity: FragmentActivity) {
    val state by lock.state.collectAsStateWithLifecycle()
    BackHandler { }
    LaunchedEffect(state.locked) {
        if (state.locked && !lock.autoPromptUsed) { lock.markAutoPromptUsed(); activity.promptUnlock(lock) }
    }
    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Ink, InkSoft, InkGlow))).safeDrawingPadding()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(1f))
            Image(painterResource(R.drawable.sucto_logo), contentDescription = null, modifier = Modifier.size(96.dp).clip(RoundedCornerShape(24.dp)))
            Spacer(Modifier.height(16.dp))
            Text("sÚčto", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.height(8.dp))
            Text("Aplikace je zamčená", color = Color.White.copy(alpha = .7f), textAlign = TextAlign.Center)
            Spacer(Modifier.weight(1f))
            state.message?.let { Text(it, color = Color(0xFFFFB4AB), textAlign = TextAlign.Center); Spacer(Modifier.height(12.dp)) }
            if (!state.authenticating) Button(onClick = { activity.promptUnlock(lock) }, modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = BrandButton, contentColor = Color.White)) {
                Text("Odemknout", fontWeight = FontWeight.SemiBold)
            } else Spacer(Modifier.height(54.dp))
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * Biometrické ověření s kryptografickým objektem (uložené přihlášení). Bez náhradního kódu zařízení: klíč smí použít jen biometrie.
 * `onSuccess` dostane cipher odemčený ověřením; zrušení uživatelem nehlásí chybu (`onFailure(null)`).
 */
fun FragmentActivity.authenticateWithCipher(cipher: javax.crypto.Cipher, title: String, onSuccess: (javax.crypto.Cipher) -> Unit,
    onFailure: (String?) -> Unit) {
    val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            result.cryptoObject?.cipher?.let(onSuccess) ?: onFailure("Ověření se nezdařilo.")
        }
        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            val cancelled = errorCode == BiometricPrompt.ERROR_USER_CANCELED || errorCode == BiometricPrompt.ERROR_CANCELED ||
                errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON
            onFailure(if (cancelled) null else "Ověření se nezdařilo.")
        }
    })
    val info = BiometricPrompt.PromptInfo.Builder().setTitle(title).setNegativeButtonText("Zrušit")
        .setAllowedAuthenticators(BIOMETRIC_STRONG).build()
    prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
}
