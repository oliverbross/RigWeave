package app.rigweave.mobile

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid
import com.goterl.lazysodium.interfaces.AEAD
import com.goterl.lazysodium.interfaces.Box
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

data class MobileCiphertext(val ciphertext: ByteArray, val nonce: ByteArray)
data class MobileBoxKeyPair(val publicKey: ByteArray, val secretKey: ByteArray)

/** Audited libsodium adapter: XChaCha20-Poly1305 event bodies and sealed-box key envelopes. */
class MobileSyncCrypto {
    private val sodium = LazySodiumAndroid(SodiumAndroid())

    fun newSpaceKey(): ByteArray = ByteArray(AEAD.XCHACHA20POLY1305_IETF_KEYBYTES).also {
        sodium.cryptoAeadXChaCha20Poly1305IetfKeygen(it)
    }

    fun newDeviceBoxKeyPair(): MobileBoxKeyPair {
        val publicKey = ByteArray(Box.PUBLICKEYBYTES)
        val secretKey = ByteArray(Box.SECRETKEYBYTES)
        check(sodium.cryptoBoxKeypair(publicKey, secretKey))
        return MobileBoxKeyPair(publicKey, secretKey)
    }

    fun encrypt(plaintext: ByteArray, associatedData: ByteArray, key: ByteArray, nonce: ByteArray = randomNonce()): MobileCiphertext {
        require(key.size == AEAD.XCHACHA20POLY1305_IETF_KEYBYTES)
        require(nonce.size == AEAD.XCHACHA20POLY1305_IETF_NPUBBYTES)
        val result = ByteArray(plaintext.size + AEAD.XCHACHA20POLY1305_IETF_ABYTES)
        val length = LongArray(1)
        check(sodium.cryptoAeadXChaCha20Poly1305IetfEncrypt(
            result, length, plaintext, plaintext.size.toLong(), associatedData, associatedData.size.toLong(),
            null, nonce, key,
        ))
        check(length[0].toInt() == result.size)
        return MobileCiphertext(result, nonce.copyOf())
    }

    fun decrypt(ciphertext: ByteArray, associatedData: ByteArray, key: ByteArray, nonce: ByteArray): ByteArray {
        require(ciphertext.size >= AEAD.XCHACHA20POLY1305_IETF_ABYTES)
        require(key.size == AEAD.XCHACHA20POLY1305_IETF_KEYBYTES)
        require(nonce.size == AEAD.XCHACHA20POLY1305_IETF_NPUBBYTES)
        val result = ByteArray(ciphertext.size - AEAD.XCHACHA20POLY1305_IETF_ABYTES)
        val length = LongArray(1)
        check(sodium.cryptoAeadXChaCha20Poly1305IetfDecrypt(
            result, length, null, ciphertext, ciphertext.size.toLong(), associatedData,
            associatedData.size.toLong(), nonce, key,
        )) { "SYNC_CIPHERTEXT_AUTHENTICATION_FAILED" }
        check(length[0].toInt() == result.size)
        return result
    }

    fun sealSpaceKey(spaceKey: ByteArray, recipientPublicKey: ByteArray): ByteArray {
        require(recipientPublicKey.size == Box.PUBLICKEYBYTES)
        val sealed = ByteArray(spaceKey.size + Box.SEALBYTES)
        check(sodium.cryptoBoxSeal(sealed, spaceKey, spaceKey.size.toLong(), recipientPublicKey))
        return sealed
    }

    fun openSpaceKey(envelope: ByteArray, recipient: MobileBoxKeyPair): ByteArray {
        require(envelope.size >= Box.SEALBYTES)
        val opened = ByteArray(envelope.size - Box.SEALBYTES)
        check(sodium.cryptoBoxSealOpen(opened, envelope, envelope.size.toLong(), recipient.publicKey, recipient.secretKey)) {
            "SYNC_KEY_ENVELOPE_REJECTED"
        }
        return opened
    }

    private fun randomNonce() = ByteArray(AEAD.XCHACHA20POLY1305_IETF_NPUBBYTES).also(SecureRandom()::nextBytes)
}

/** Stable request-signing identity. Its P-256 private key is non-exportable in Android Keystore. */
class MobileDeviceIdentity(private val alias: String = "rigweave.m9.device.identity.v1") {
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    init {
        if (!keyStore.containsAlias(alias)) {
            val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
            generator.initialize(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setUserAuthenticationRequired(false)
                .build())
            generator.generateKeyPair()
        }
    }

    val deviceId: String get() = "rw-" + fingerprintSha256().take(32)
    fun publicKeyPem(): String {
        val encoded = Base64.encodeToString(keyStore.getCertificate(alias).publicKey.encoded, Base64.NO_WRAP)
        return "-----BEGIN PUBLIC KEY-----\n${encoded.chunked(64).joinToString("\n")}\n-----END PUBLIC KEY-----"
    }
    fun fingerprintSha256(): String = sha256(keyStore.getCertificate(alias).publicKey.encoded)
    fun sign(message: ByteArray): ByteArray = Signature.getInstance("SHA256withECDSA").run {
        initSign(keyStore.getKey(alias, null) as java.security.PrivateKey); update(message); sign()
    }
}

/** Refresh tokens and exportable libsodium secret material are encrypted by a non-exportable AES key. */
class MobileSyncSecureStore(context: Context, private val alias: String = "rigweave.m9.secure.storage.v1") {
    private val preferences = context.getSharedPreferences("rigweave.m9.secure", Context.MODE_PRIVATE)
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    fun put(name: String, value: ByteArray) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        preferences.edit().putString("$name.ciphertext", Base64.encodeToString(cipher.doFinal(value), Base64.NO_WRAP))
            .putString("$name.iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP)).apply()
    }

    fun get(name: String): ByteArray? {
        val ciphertext = preferences.getString("$name.ciphertext", null) ?: return null
        val iv = preferences.getString("$name.iv", null) ?: return null
        return Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
            doFinal(Base64.decode(ciphertext, Base64.NO_WRAP))
        }
    }

    fun remove(name: String) { preferences.edit().remove("$name.ciphertext").remove("$name.iv").apply() }

    private fun key(): javax.crypto.SecretKey {
        (keyStore.getKey(alias, null) as? javax.crypto.SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            generateKey()
        }
    }
}

internal fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
