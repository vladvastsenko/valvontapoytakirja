package fi.vladi.valvontapoytakirja.billing

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal class RecoveryStore(context: Context) {
    private val dir = context.noBackupFilesDir
    private val alias = "pro_recovery_v1"
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (ks.getKey(alias, null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            generateKey()
        }
    }
    private fun file(uid: String) = File(dir, "pro-recovery-" + PremiumVerification.accountId(uid))
    fun read(uid: String?): String? = runCatching {
        if (uid == null) return null
        val bytes = android.util.AtomicFile(file(uid)).readFully()
        require(bytes.size in 29..256)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0,12)))
        cipher.updateAAD(uid.toByteArray(Charsets.UTF_8))
        cipher.doFinal(bytes.copyOfRange(12,bytes.size)).toString(Charsets.UTF_8).takeIf { it.matches(Regex("[A-Za-z0-9_-]{43}")) }
    }.getOrNull()
    fun write(uid: String, code: String): Boolean = runCatching {
        require(code.matches(Regex("[A-Za-z0-9_-]{43}")))
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(uid.toByteArray(Charsets.UTF_8))
        require(cipher.iv.size == 12)
        val target = android.util.AtomicFile(file(uid))
        val stream = target.startWrite()
        try { stream.write(cipher.iv + cipher.doFinal(code.toByteArray(Charsets.UTF_8))); target.finishWrite(stream) }
        catch (e: Exception) { target.failWrite(stream); throw e }
        true
    }.getOrDefault(false)
    fun clear(uid: String?) { if (uid != null) android.util.AtomicFile(file(uid)).delete() }
}
