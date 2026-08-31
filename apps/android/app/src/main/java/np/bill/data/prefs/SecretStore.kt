package np.bill.data.prefs

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypts the session token with a key held in the Android keystore, so a token cannot
 * be lifted off a rooted or backed-up device by reading the preferences file. The key
 * never leaves the keystore; only the ciphertext is stored.
 */
@Singleton
class SecretStore @Inject constructor() {

  private val key: SecretKey by lazy { loadOrCreateKey() }

  fun encrypt(plaintext: String): String {
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.ENCRYPT_MODE, key)
    val encrypted = cipher.doFinal(plaintext.toByteArray())
    // The IV is generated per encryption and prefixed, since GCM must never reuse one.
    return Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
  }

  fun decrypt(stored: String): String? = runCatching {
    val bytes = Base64.decode(stored, Base64.NO_WRAP)
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES))
    String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES))
  }.getOrNull()

  private fun loadOrCreateKey(): SecretKey {
    val keyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }
    (keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

    val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
    generator.init(
      KeyGenParameterSpec.Builder(
        ALIAS,
        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
      )
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .build(),
    )
    return generator.generateKey()
  }

  private companion object {
    const val PROVIDER = "AndroidKeyStore"
    const val ALIAS = "np.bill.session"
    const val TRANSFORMATION = "AES/GCM/NoPadding"
    const val IV_BYTES = 12
    const val TAG_BITS = 128
  }
}
