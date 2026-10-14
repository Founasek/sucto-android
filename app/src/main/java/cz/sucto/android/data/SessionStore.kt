package cz.sucto.android.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Trvalé uložení přihlašovacího tokenu. Token je zašifrovaný AES-GCM klíčem z Android Keystore (klíč neopustí zařízení),
 * v SharedPreferences leží jen šifrový text. Zálohy aplikace jsou vypnuté (`allowBackup=false`).
 * Nic se nelogguje. Při jakékoli chybě dešifrování se token zahodí a uživatel se přihlásí znovu.
 */
class SessionStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun saveToken(token: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        prefs.edit().putString(TOKEN, Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)).apply()
    }

    fun loadToken(): String? {
        val stored = prefs.getString(TOKEN, null) ?: return null
        return try {
            val bytes = Base64.decode(stored, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES))
            }
            String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8)
        } catch (error: Exception) {
            clear()
            null
        }
    }

    /** Naposledy vybraná firma (jen číslo), ať se po obnovení relace otevře rovnou. */
    var lastCompanyId: Int?
        get() = if (prefs.contains(COMPANY)) prefs.getInt(COMPANY, 0) else null
        set(value) { prefs.edit().apply { if (value == null) remove(COMPANY) else putInt(COMPANY, value) }.apply() }

    fun clear() { prefs.edit().clear().apply() }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).build())
        return generator.generateKey()
    }

    private companion object {
        const val FILE = "session"
        const val TOKEN = "token"
        const val COMPANY = "company"
        const val PROVIDER = "AndroidKeyStore"
        const val ALIAS = "cz.sucto.android.session"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
