package app.rigweave.mobile

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MobileSyncSecurityInstrumentedTest {
    @Test fun xChaChaFixtureMatchesCrossPlatformContractAndRejectsTampering() {
        val crypto = MobileSyncCrypto()
        val key = hex("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f")
        val nonce = hex("000102030405060708090a0b0c0d0e0f1011121314151617")
        val plaintext = "RigWeave M9".toByteArray()
        val associatedData = "space-1|event-1".toByteArray()
        val encrypted = crypto.encrypt(plaintext, associatedData, key, nonce)
        assertEquals("ccab6828f5b3fbcb13091f99be8cc97642c6d08039c64e17995628", encrypted.ciphertext.hex())
        assertArrayEquals(plaintext, crypto.decrypt(encrypted.ciphertext, associatedData, key, nonce))
        val tampered = encrypted.ciphertext.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        assertThrows { crypto.decrypt(tampered, associatedData, key, nonce) }
    }

    @Test fun sealedSpaceKeyOpensOnlyForTheApprovedDevice() {
        val crypto = MobileSyncCrypto(); val approved = crypto.newDeviceBoxKeyPair(); val other = crypto.newDeviceBoxKeyPair()
        val key = crypto.newSpaceKey(); val envelope = crypto.sealSpaceKey(key, approved.publicKey)
        assertArrayEquals(key, crypto.openSpaceKey(envelope, approved))
        assertThrows { crypto.openSpaceKey(envelope, other) }
        approved.secretKey.fill(0); other.secretKey.fill(0); key.fill(0)
    }

    @Test fun stableIdentityAndEncryptedSecretStoreNeverExposePrivateKey() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val identity = MobileDeviceIdentity("rigweave.m9.test.identity")
        val first = identity.deviceId; val second = MobileDeviceIdentity("rigweave.m9.test.identity").deviceId
        assertEquals(first, second); assertTrue(identity.publicKeyPem().startsWith("-----BEGIN PUBLIC KEY-----"))
        assertTrue(identity.sign("request".toByteArray()).isNotEmpty())
        val secure = MobileSyncSecureStore(context, "rigweave.m9.test.storage")
        secure.put("refresh", "secret-token".toByteArray())
        assertArrayEquals("secret-token".toByteArray(), secure.get("refresh"))
        val preferences = context.getSharedPreferences("rigweave.m9.secure", 0).all.values.joinToString("|")
        assertFalse(preferences.contains("secret-token")); secure.remove("refresh")
    }

    private fun hex(value: String) = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }
    private fun assertThrows(block: () -> Unit) { var thrown=false; try { block() } catch (_: Exception) { thrown=true }; assertTrue(thrown) }
}
