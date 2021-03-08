package one.mixin.android.job

import com.birbit.android.jobqueue.Params
import com.bugsnag.android.Bugsnag
import one.mixin.android.RxBus
import one.mixin.android.api.ChecksumException
import one.mixin.android.event.RecallEvent
import one.mixin.android.extension.base64Encode
import one.mixin.android.extension.base64RawUrlDecode
import one.mixin.android.extension.findLastUrl
import one.mixin.android.extension.getFilePath
import one.mixin.android.extension.notNullWithElse
import one.mixin.android.session.Session
import one.mixin.android.util.GsonHelper
import one.mixin.android.util.MessageFts4Helper
import one.mixin.android.util.hyperlink.parsHyperlink
import one.mixin.android.util.mention.parseMentionData
import one.mixin.android.vo.*
import one.mixin.android.websocket.BlazeMessage
import one.mixin.android.websocket.BlazeMessageParam
import one.mixin.android.websocket.KrakenParam
import one.mixin.android.websocket.ResendData
import one.mixin.android.websocket.createCallMessage
import one.mixin.android.websocket.createKrakenMessage
import one.mixin.android.websocket.createParamBlazeMessage
import java.io.File

open class SendMessageJob(
    val message: Message,
    private val resendData: ResendData? = null,
    private val alreadyExistMessage: Boolean = false,
    private var recipientId: String? = null,
    private var recipientIds: List<String>? = null,
    private val recallMessageId: String? = null,
    private val krakenParam: KrakenParam? = null,
    messagePriority: Int = PRIORITY_SEND_MESSAGE
) : MixinJob(Params(messagePriority).groupBy("send_message_group").requireWebSocketConnected().persist(), message.id) {

    companion object {
        private const val serialVersionUID = 1L
    }

    override fun cancel() {
        isCancelled = true
        removeJob()
    }

    override fun onAdded() {
        super.onAdded()
        if (chatWebSocket.connected) {
            jobManager.start()
        }
        if (message.isCall()) {
            return
        }
        if (alreadyExistMessage) {
            return
        }
        val conversation = conversationDao.findConversationById(message.conversationId)
        if (conversation != null) {
            if (message.isRecall()) {
                recallMessage()
            } else {
                if (message.isText()) {
                    message.content?.let { content ->
                        content.findLastUrl()?.let { parsHyperlink(it, message.id, hyperlinkDao, messageDao) }
                        parseMentionData(content, message.id, message.conversationId, userDao, messageMentionDao, message.userId)
                    }
                }

                messageDao.insert(message)
                MessageFts4Helper.insertOrReplaceMessageFts4(message, message.name)
            }
        } else {
            Bugsnag.notify(Throwable("Insert failed, no conversation $alreadyExistMessage"))
        }
    }

    private fun recallMessage() {
        messageDao.recallMessage(recallMessageId!!)
        messageMentionDao.deleteMessage(recallMessageId)
        messageDao.findMessageById(recallMessageId)?.let { msg ->
            RxBus.publish(RecallEvent(msg.id))
            messageDao.recallFailedMessage(msg.id)
            messageDao.takeUnseen(Session.getAccountId()!!, msg.conversationId)
            msg.mediaUrl?.getFilePath()?.let {
                File(it).let { file ->
                    if (file.exists() && file.isFile) {
                        file.delete()
                    }
                }
            }
            messageDao.findMessageItemById(message.conversationId, msg.id)?.let { quoteMsg ->
                messageDao.updateQuoteContentByQuoteId(
                    message.conversationId,
                    msg.id,
                    GsonHelper.customGson.toJson(quoteMsg)
                )
            }
            jobManager.cancelJobByMixinJobId(msg.id)
        }
    }

    override fun onCancel(cancelReason: Int, throwable: Throwable?) {
        super.onCancel(cancelReason, throwable)
        removeJob()
    }

    override fun onRun() {
        if (isCancelled) {
            removeJob()
            return
        }
        jobManager.saveJob(this)
        if (message.isPlain() || message.isCall() || message.isRecall() || message.category == MessageCategory.APP_CARD.name) {
            sendPlainMessage()
        } else if (message.isEncrypted()) {
            sendEncryptedMessage()
        } else {
            sendSignalMessage()
        }
        removeJob()
    }

    private fun sendPlainMessage() {
        val conversation = conversationDao.getConversation(message.conversationId) ?: return
        checkConversationExist(conversation)
        var content = message.content
        if (message.category == MessageCategory.PLAIN_TEXT.name ||
            message.category == MessageCategory.PLAIN_POST.name ||
            message.category == MessageCategory.PLAIN_LOCATION.name ||
            message.isCall() ||
            message.category == MessageCategory.APP_CARD.name
        ) {
            if (message.content != null) {
                content = message.content!!.base64Encode()
            }
        }
        val blazeParam = BlazeMessageParam(
            message.conversationId,
            recipientId,
            message.id,
            message.category,
            content,
            quote_message_id = message.quoteMessageId,
            mentions = getMentionData(message.id),
            recipient_ids = recipientIds
        )
        val blazeMessage = if (message.isCall()) {
            if (message.isKraken()) {
                blazeParam.jsep = krakenParam?.jsep?.base64Encode()
                blazeParam.candidate = krakenParam?.candidate?.base64Encode()
                blazeParam.track_id = krakenParam?.track_id
                createKrakenMessage(blazeParam)
            } else {
                createCallMessage(blazeParam)
            }
        } else {
            createParamBlazeMessage(blazeParam)
        }
        deliver(blazeMessage)
    }

    @ExperimentalUnsignedTypes
    private fun sendEncryptedMessage() {
        val conversation = conversationDao.getConversation(message.conversationId) ?: return
        checkConversationExist(conversation)
        val participantSessionKey = participantSessionDao.getParticipantSessionKeyWithoutSelf(message.conversationId, Session.getAccountId()!!)
        if (participantSessionKey == null) {
            syncConversation(message.conversationId)
            throw ChecksumException()
        }
        if (participantSessionKey.publicKey.isNullOrBlank()) {
            message.category = message.category.replace("ENCRYPTED_", "PLAIN_")
            sendPlainMessage()
            return
        }

        val extensionSessionKey =
            Session.getExtensionSessionId().notNullWithElse({ participantSessionDao.getParticipantSessionKeyBySessionId(message.conversationId, it) }, null)

        val privateKey = Session.getEd25519PrivateKey() ?: return
        val content = encryptedProtocol.encryptMessage(
            privateKey, message.content!!.toByteArray(), participantSessionKey.publicKey.base64RawUrlDecode(), participantSessionKey.sessionId,
            extensionSessionKey?.publicKey?.base64RawUrlDecode(), extensionSessionKey?.sessionId
        )

        val blazeParam = BlazeMessageParam(
            message.conversationId,
            recipientId,
            message.id,
            message.category,
            content.base64Encode(),
            quote_message_id = message.quoteMessageId,
            mentions = getMentionData(message.id),
            recipient_ids = recipientIds
        )
        val blazeMessage = createParamBlazeMessage(blazeParam)
        deliver(blazeMessage)
    }

    private fun sendSignalMessage() {
        if (resendData != null) {
            if (checkSignalSession(resendData.userId, resendData.sessionId)) {
                deliver(encryptNormalMessage())
            }
            return
        }
        if (!signalProtocol.isExistSenderKey(message.conversationId, message.userId)) {
            checkConversation(message.conversationId)
        }
        checkSessionSenderKey(message.conversationId)
        deliver(encryptNormalMessage())
    }

    private fun encryptNormalMessage(): BlazeMessage {
        return if (resendData != null) {
            signalProtocol.encryptSessionMessage(
                message,
                resendData.userId,
                resendData.messageId,
                resendData.sessionId,
                getMentionData(message.id)
            )
        } else {
            signalProtocol.encryptGroupMessage(message, getMentionData(message.id))
        }
    }

    private fun getMentionData(messageId: String): List<String>? {
        return messageMentionDao.getMentionData(messageId)?.run {
            GsonHelper.customGson.fromJson(this, Array<MentionUser>::class.java).map {
                it.identityNumber
            }.toSet()
        }?.run {
            userDao.findMultiUserIdsByIdentityNumbers(this)
        }
    }
}
