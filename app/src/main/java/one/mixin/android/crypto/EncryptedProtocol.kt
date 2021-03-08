package one.mixin.android.crypto

import net.i2p.crypto.eddsa.EdDSAPrivateKey
import net.i2p.crypto.eddsa.EdDSAPublicKey
import net.i2p.crypto.eddsa.spec.EdDSAPublicKeySpec
import one.mixin.android.extension.leByteArrayToInt
import one.mixin.android.extension.toByteArray
import one.mixin.android.extension.toLeByteArray
import java.util.UUID

class EncryptedProtocol {

    @ExperimentalUnsignedTypes
    fun encryptMessage(
        privateKey: EdDSAPrivateKey,
        plaintext: ByteArray,
        otherPublicKey: ByteArray,
        otherSessionId: String,
        extensionSessionKey: ByteArray? = null,
        extensionSessionId: String? = null
    ): ByteArray {
        val aesGcmKey = generateAesKey()
        val encryptedMessageData = aesGcmEncrypt(plaintext, aesGcmKey)
        val messageKey = encryptCipherMessageKey(privateKey.seed, otherPublicKey, aesGcmKey)
        val messageKeyWithSession = UUID.fromString(otherSessionId).toByteArray().plus(messageKey)
        val pub = EdDSAPublicKey(EdDSAPublicKeySpec(privateKey.a, ed25519))
        val senderPublicKey = publicKeyToCurve25519(pub)
        val version = byteArrayOf(0x01)

        return version.run {
            if (extensionSessionKey != null && extensionSessionId != null) {
                plus(toLeByteArray(2.toUInt()))
            } else {
                plus(toLeByteArray(1.toUInt()))
            }
        }.plus(senderPublicKey).run {
            extensionSessionKey ?: return this
            extensionSessionId ?: return this
            val emergencyMessageKey = encryptCipherMessageKey(privateKey.seed, extensionSessionKey, aesGcmKey)
            plus(UUID.fromString(extensionSessionId).toByteArray().plus(emergencyMessageKey))
        }.plus(messageKeyWithSession).plus(encryptedMessageData)
    }

    private fun encryptCipherMessageKey(seed: ByteArray, publicKey: ByteArray, aesGcmKey: ByteArray): ByteArray {
        val private = privateKeyToCurve25519(seed)
        val sharedSecret = calculateAgreement(publicKey, private)
        return aesEncrypt(sharedSecret, aesGcmKey)
    }

    private fun decryptCipherMessageKey(seed: ByteArray, publicKey: ByteArray, iv: ByteArray, ciphertext: ByteArray): ByteArray {
        val private = privateKeyToCurve25519(seed)
        val sharedSecret = calculateAgreement(publicKey, private)
        return aesDecrypt(sharedSecret, iv, ciphertext)
    }

    @ExperimentalUnsignedTypes
    fun decryptMessage(privateKey: EdDSAPrivateKey, sessionId: ByteArray, ciphertext: ByteArray): ByteArray {
        val sessionSize = leByteArrayToInt(ciphertext.slice(IntRange(1, 2)).toByteArray()).toInt()
        val senderPublicKey = ciphertext.slice(IntRange(3, 34)).toByteArray()
        var key: ByteArray? = null
        repeat(sessionSize) {
            val offset = it * 64
            val sid = ciphertext.slice(IntRange(35 + offset, 50 + offset)).toByteArray()
            if (sessionId.contentEquals(sid)) {
                key = ciphertext.slice(IntRange(51 + offset, 98 + offset)).toByteArray()
            }
        }
        val messageKey = requireNotNull(key)
        val message = ciphertext.slice(IntRange(35 + 64 * sessionSize, ciphertext.size - 1)).toByteArray()
        val iv = messageKey.slice(IntRange(0, 15)).toByteArray()
        val content = messageKey.slice(IntRange(16, messageKey.size - 1)).toByteArray()
        val decodedMessageKey = decryptCipherMessageKey(privateKey.seed, senderPublicKey, iv, content)

        return aesGcmDecrypt(message, decodedMessageKey)
    }
}
