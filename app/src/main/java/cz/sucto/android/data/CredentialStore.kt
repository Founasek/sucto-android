package cz.sucto.android.data

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

/**
 * Uložené přihlášení pro biometrické přihlášení. E-mail a heslo jsou zašifrované klíčem z Android Keystore, který lze použít
 * jen po biometrickém ověření (každé použití vyžaduje otisk/obličej; klíč se zneplatní i při změně biometrie). Uložení je výslovné:
 * nabídne se po úspěšném ručním přihlášení a uživatel ho může kdykoli smazat. Bez biometrie se nic neukládá.
 */
class CredentialStore(context: Context) {
    class Credentials(val email: String, val password: String)

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("login", Context.MODE_PRIVATE)

    val hasSavedLogin: Boolean get() = prefs.contains(DATA)
    /** Uživatel nabídku uložení odmítl natrvalo („Nikdy“). */
    var offerDeclined: Boolean
        get() = prefs.getBoolean(DECLINED, false)
        set(value) { prefs.edit().putBoolean(DECLINED, value).apply() }

    /** Nabídka uložení dává smysl jen s biometrií, bez uloženého přihlášení a bez odmítnutí. */
    val canOffer: Boolean get() = isSupported(appContext) && !hasSavedLogin && !offerDeclined

    /** Cipher pro šifrování; musí projít biometrickým ověřením (BiometricPrompt s CryptoObject). */
    fun encryptCipher(): Cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key(create = true)) }

    /** Cipher pro dešifrování uloženého přihlášení; `null`, když nic není uloženo nebo se klíč zneplatnil (pak se uložení smaže). */
    fun decryptCipher(): Cipher? {
        val iv = prefs.getString(IV, null)?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return null
        return try {
            Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, key(create = false) ?: return null, GCMParameterSpec(TAG_BITS, iv)) }
        } catch (error: KeyPermanentlyInvalidatedException) { clear(); null
        } catch (error: Exception) { clear(); null }
    }

    fun save(cipher: Cipher, email: String, password: String) {
        val payload = JSONObject().put("email", email).put("password", password).toString().toByteArray(Charsets.UTF_8)
        prefs.edit().putString(IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(DATA, Base64.encodeToString(cipher.doFinal(payload), Base64.NO_WRAP)).apply()
    }

    fun load(cipher: Cipher): Credentials? = runCatching {
        val data = Base64.decode(prefs.getString(DATA, null) ?: return null, Base64.NO_WRAP)
        val json = JSONObject(String(cipher.doFinal(data), Charsets.UTF_8))
        Credentials(json.getString("email"), json.getString("password"))
    }.getOrNull()

    fun clear() {
        prefs.edit().remove(IV).remove(DATA).apply()
        runCatching { KeyStore.getInstance(PROVIDER).apply { load(null) }.deleteEntry(ALIAS) }
    }

    private fun key(create: Boolean): SecretKey? {
        val store = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        if (!create) return null
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256)
            .setUserAuthenticationRequired(true).setInvalidatedByBiometricEnrollment(true)
        requireAuthPerUse(spec)
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER).apply { init(spec.build()) }.generateKey()
    }

    /** Každé použití klíče vyžaduje biometrii (od Androidu 11 přes parametry, dřív přes dobu platnosti -1). */
    @Suppress("DEPRECATION")
    private fun requireAuthPerUse(spec: KeyGenParameterSpec.Builder) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) spec.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        else spec.setUserAuthenticationValidityDurationSeconds(-1)
    }

    companion object {
        private const val PROVIDER = "AndroidKeyStore"
        private const val ALIAS = "cz.sucto.android.login"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val TAG_BITS = 128
        private const val IV = "iv"
        private const val DATA = "data"
        private const val DECLINED = "declined"

        /** Zařízení má nastavenou silnou biometrii (otisk, obličej). */
        fun isSupported(context: Context) = BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    }
}
