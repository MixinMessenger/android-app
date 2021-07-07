package one.mixin.android.crypto

import android.content.Context
import android.util.Log
import one.mixin.android.MixinApplication
import one.mixin.android.crypto.db.SessionDao
import one.mixin.android.crypto.db.SignalDatabase
import one.mixin.android.crypto.storage.MixinSenderKeyStore
import one.mixin.android.crypto.storage.SignalProtocolStoreImpl
import one.mixin.android.extension.base64Encode
import one.mixin.android.extension.getDeviceId
import one.mixin.android.session.Session
import one.mixin.android.vo.Message
import one.mixin.android.vo.MessageCategory
import one.mixin.android.websocket.BlazeMessage
import one.mixin.android.websocket.BlazeMessageParam
import one.mixin.android.websocket.createParamBlazeMessage
import org.whispersystems.libsignal.DecryptionCallback
import org.whispersystems.libsignal.InvalidMessageException
import org.whispersystems.libsignal.NoSessionException
import org.whispersystems.libsignal.SessionBuilder
import org.whispersystems.libsignal.SessionCipher
import org.whispersystems.libsignal.SignalProtocolAddress
import org.whispersystems.libsignal.UntrustedIdentityException
import org.whispersystems.libsignal.ecc.DjbECPublicKey
import org.whispersystems.libsignal.groups.GroupCipher
import org.whispersystems.libsignal.groups.GroupSessionBuilder
import org.whispersystems.libsignal.groups.SenderKeyName
import org.whispersystems.libsignal.protocol.CiphertextMessage
import org.whispersystems.libsignal.protocol.CiphertextMessage.CURRENT_VERSION
import org.whispersystems.libsignal.protocol.CiphertextMessage.PREKEY_TYPE
import org.whispersystems.libsignal.protocol.CiphertextMessage.SENDERKEY_TYPE
import org.whispersystems.libsignal.protocol.CiphertextMessage.WHISPER_TYPE
import org.whispersystems.libsignal.protocol.PreKeySignalMessage
import org.whispersystems.libsignal.protocol.SenderKeyDistributionMessage
import org.whispersystems.libsignal.protocol.SignalMessage
import org.whispersystems.libsignal.state.PreKeyBundle
import java.lang.Exception

class SignalProtocol(ctx: Context) {

    data class ComposeMessageData(
        val keyType: Int,
        val cipher: ByteArray,
        val resendMessageId: String? = null
    )

    companion object {

        val TAG = SignalProtocol::class.java.simpleName
        const val DEFAULT_DEVICE_ID = 1

        suspend fun initSignal(context: Context) {
            IdentityKeyUtil.generateIdentityKeys(context)
        }

        fun encodeMessageData(data: ComposeMessageData): String {
            return if (data.resendMessageId == null) {
                val header = byteArrayOf(CURRENT_VERSION.toByte(), data.keyType.toByte(), 0, 0, 0, 0, 0, 0)
                val cipherText = header + data.cipher
                cipherText.base64Encode()
            } else {
                val header = byteArrayOf(CURRENT_VERSION.toByte(), data.keyType.toByte(), 1, 0, 0, 0, 0, 0)
                val messageId = data.resendMessageId.toByteArray()
                val cipherText = header + messageId + data.cipher
                cipherText.base64Encode()
            }
        }

        fun decodeMessageData(encoded: String): ComposeMessageData {
            val cipherText = Base64.decode(encoded)
            val header = cipherText.sliceArray(IntRange(0, 7))
            val version = header[0].toInt()
            if (version != CURRENT_VERSION) {
                throw InvalidMessageException("Unknown version: $version")
            }
            val dataType = header[1].toInt()
            val isResendMessage = header[2].toInt() == 1
            return if (isResendMessage) {
                val messageId = String(cipherText.sliceArray(IntRange(8, 43)))
                val data = cipherText.sliceArray(IntRange(44, cipherText.size - 1))
                ComposeMessageData(dataType, data, messageId)
            } else {
                val data = cipherText.sliceArray(IntRange(8, cipherText.size - 1))
                ComposeMessageData(dataType, data, null)
            }
        }
    }

    private val signalProtocolStore = SignalProtocolStoreImpl(MixinApplication.appContext)
    private val senderKeyStore: MixinSenderKeyStore = MixinSenderKeyStore(ctx)
    private val sessionDao: SessionDao = SignalDatabase.getDatabase(MixinApplication.appContext).sessionDao()

    fun getSenderKeyPublic(groupId: String, userId: String, sessionId: String? = null): ByteArray? {
        val senderKeyName = SenderKeyName(groupId, SignalProtocolAddress(userId, sessionId.getDeviceId()))
        val sender = senderKeyStore.loadSenderKey(senderKeyName)
        return try {
            (sender.senderKeyState.signingKeyPublic as DjbECPublicKey).publicKey
        } catch (e: Exception) {
            null
        }
    }

    private fun getSenderKeyDistribution(groupId: String, senderId: String): SenderKeyDistributionMessage {
        val senderKeyName = SenderKeyName(groupId, SignalProtocolAddress(senderId, DEFAULT_DEVICE_ID))
        val builder = GroupSessionBuilder(senderKeyStore)
        return builder.create(senderKeyName)
    }

    fun encryptSenderKey(conversationId: String, recipientId: String, deviceId: Int = DEFAULT_DEVICE_ID): EncryptResult {
        val senderKeyDistributionMessage = getSenderKeyDistribution(conversationId, Session.getAccountId()!!)
        return try {
            val cipherMessage = encryptSession(senderKeyDistributionMessage.serialize(), recipientId, deviceId)
            val compose = ComposeMessageData(cipherMessage.type, cipherMessage.serialize())
            val cipher = encodeMessageData(compose)
            EncryptResult(cipher, false)
        } catch (e: UntrustedIdentityException) {
            val remoteAddress = SignalProtocolAddress(recipientId, deviceId)
            signalProtocolStore.removeIdentity(remoteAddress)
            signalProtocolStore.deleteSession(remoteAddress)
            EncryptResult(null, true)
        }
    }

    private fun encryptSession(content: ByteArray, destination: String, deviceId: Int): CiphertextMessage {
        val remoteAddress = SignalProtocolAddress(destination, deviceId)
        val sessionCipher = SessionCipher(signalProtocolStore, remoteAddress)
        return sessionCipher.encrypt(content)
    }

    fun decrypt(groupId: String, senderId: String, dataType: Int, cipherText: ByteArray, category: String, sessionId: String?, callback: DecryptionCallback) {
        val address = SignalProtocolAddress(senderId, sessionId.getDeviceId())
        val sessionCipher = SessionCipher(signalProtocolStore, address)
        if (category == MessageCategory.SIGNAL_KEY.name) {
            if (dataType == PREKEY_TYPE) {
                sessionCipher.decrypt(PreKeySignalMessage(cipherText)) { plaintext ->
                    processGroupSession(groupId, address, SenderKeyDistributionMessage(plaintext))
                    callback.handlePlaintext(plaintext)
                }
            } else if (dataType == WHISPER_TYPE) {
                sessionCipher.decrypt(SignalMessage(cipherText)) { plaintext ->
                    processGroupSession(groupId, address, SenderKeyDistributionMessage(plaintext))
                    callback.handlePlaintext(plaintext)
                }
            }
        } else {
            when (dataType) {
                PREKEY_TYPE -> sessionCipher.decrypt(PreKeySignalMessage(cipherText), callback)
                WHISPER_TYPE -> sessionCipher.decrypt(SignalMessage(cipherText), callback)
                SENDERKEY_TYPE -> decryptGroupMessage(groupId, address, cipherText, callback)
                else -> throw InvalidMessageException("Unknown type: $dataType")
            }
        }
    }

    fun isExistSenderKey(groupId: String, senderId: String): Boolean {
        val senderKeyName = SenderKeyName(groupId, SignalProtocolAddress(senderId, DEFAULT_DEVICE_ID))
        val senderKeyRecord = senderKeyStore.loadSenderKey(senderKeyName)
        return !senderKeyRecord.isEmpty
    }

    fun containsUserSession(recipientId: String): Boolean {
        val sessions = sessionDao.getSessions(recipientId)
        return sessions.isNotEmpty()
    }

    fun containsSession(recipientId: String, deviceId: Int = DEFAULT_DEVICE_ID): Boolean {
        val signalProtocolAddress = SignalProtocolAddress(recipientId, deviceId)
        return signalProtocolStore.containsSession(signalProtocolAddress)
    }

    fun clearSenderKey(groupId: String, senderId: String) {
        val senderKeyName = SenderKeyName(groupId, SignalProtocolAddress(senderId, DEFAULT_DEVICE_ID))
        senderKeyStore.removeSenderKey(senderKeyName)
    }

    fun deleteSession(userId: String) {
        sessionDao.deleteSession(userId)
    }

    fun processSession(userId: String, preKeyBundle: PreKeyBundle) {
        val signalProtocolAddress = SignalProtocolAddress(userId, preKeyBundle.deviceId)
        val sessionBuilder = SessionBuilder(signalProtocolStore, signalProtocolAddress)
        try {
            sessionBuilder.process(preKeyBundle)
        } catch (e: UntrustedIdentityException) {
            signalProtocolStore.removeIdentity(signalProtocolAddress)
            sessionBuilder.process(preKeyBundle)
        }
    }

    fun encryptSessionMessage(
        message: Message,
        recipientId: String,
        resendMessageId: String? = null,
        sessionId: String? = null,
        mentionData: List<String>? = null
    ): BlazeMessage {
        val cipher = encryptSession(message.content!!.toByteArray(), recipientId, sessionId.getDeviceId())
        val data = encodeMessageData(ComposeMessageData(cipher.type, cipher.serialize(), resendMessageId))
        val blazeParam = BlazeMessageParam(
            message.conversationId,
            recipientId,
            message.id,
            message.category,
            data,
            quote_message_id = message.quoteMessageId,
            session_id = sessionId,
            mentions = mentionData
        )
        return createParamBlazeMessage(blazeParam)
    }

    fun encryptGroupMessage(message: Message, mentionData: List<String>?, isSilent: Boolean? = null): BlazeMessage {
        val address = SignalProtocolAddress(message.userId, DEFAULT_DEVICE_ID)
        val senderKeyName = SenderKeyName(message.conversationId, address)
        val groupCipher = GroupCipher(senderKeyStore, senderKeyName)
        var cipher = byteArrayOf(0)
        try {
            cipher = groupCipher.encrypt(message.content!!.toByteArray())
        } catch (e: NoSessionException) {
            Log.e(TAG, "NoSessionException", e)
        }

        val data = encodeMessageData(ComposeMessageData(SENDERKEY_TYPE, cipher))
        val blazeParam = BlazeMessageParam(
            message.conversationId,
            null,
            message.id,
            message.category,
            data,
            quote_message_id = message.quoteMessageId,
            mentions = mentionData,
            silent = isSilent
        )
        return createParamBlazeMessage(blazeParam)
    }

    private fun processGroupSession(groupId: String, address: SignalProtocolAddress, senderKeyDM: SenderKeyDistributionMessage) {
        val builder = GroupSessionBuilder(senderKeyStore)
        val senderKeyName = SenderKeyName(groupId, address)
        builder.process(senderKeyName, senderKeyDM)
    }

    private fun decryptGroupMessage(groupId: String, address: SignalProtocolAddress, cipherText: ByteArray, callback: DecryptionCallback): ByteArray {
        val senderKeyName = SenderKeyName(groupId, address)
        val groupCipher = GroupCipher(senderKeyStore, senderKeyName)
        return groupCipher.decrypt(cipherText, callback)
    }
}
