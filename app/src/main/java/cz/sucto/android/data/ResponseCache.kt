package cz.sucto.android.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Diskový cache odpovědí GET, ze které se čte jen při výpadku připojení. Data jsou finanční, proto jsou šifrovaná
 * AES-GCM klíčem z Android Keystore, platí 30 dní a při odhlášení se mažou.
 */
class ResponseCache(context: Context) {
    class Entry(val payload: String, val savedAt: Long)

    private val directory = File(context.applicationContext.cacheDir, "responses").apply { mkdirs() }

    init { removeExpired() }

    fun store(endpoint: String, payload: String) {
        runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
            val plain = ByteBuffer.allocate(8).putLong(System.currentTimeMillis()).array() + payload.toByteArray(Charsets.UTF_8)
            fileFor(endpoint).writeBytes(cipher.iv + cipher.doFinal(plain))
        }
    }

    fun load(endpoint: String): Entry? {
        val file = fileFor(endpoint)
        if (!file.exists()) return null
        return try {
            val bytes = file.readBytes()
            val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES)) }
            val plain = cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES)
            val savedAt = ByteBuffer.wrap(plain, 0, 8).long
            if (System.currentTimeMillis() - savedAt > MAX_AGE_MS) { file.delete(); null }
            else Entry(String(plain, 8, plain.size - 8, Charsets.UTF_8), savedAt)
        } catch (error: Exception) { file.delete(); null }
    }

    fun clear() { directory.listFiles()?.forEach { it.delete() } }

    private fun removeExpired() {
        val limit = System.currentTimeMillis() - MAX_AGE_MS
        directory.listFiles()?.forEach { if (it.lastModified() < limit) it.delete() }
    }

    private fun fileFor(endpoint: String) = File(directory, MessageDigest.getInstance("SHA-256").digest(endpoint.toByteArray())
        .joinToString("") { "%02x".format(it) })

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
        return generator.generateKey()
    }

    companion object {
        private const val PROVIDER = "AndroidKeyStore"
        private const val ALIAS = "cz.sucto.android.cache"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
        private const val MAX_AGE_MS = 30L * 24 * 3600 * 1000

        /**
         * Cachují se jen čtení bez hledání, bez předvyplněných formulářů a bez akcí. Úhrada faktury je v API `GET .../pay`
         * (vytvoří pokladní doklad) – z cache by při výpadku připojení vrátila falešný úspěch.
         */
        fun isCacheable(path: String, method: String?, body: String?): Boolean {
            val clean = path.substringBefore('?')
            return method == null && body == null && !path.contains("_cont") && !clean.endsWith("/new") && !clean.endsWith("/pay")
        }
    }
}
