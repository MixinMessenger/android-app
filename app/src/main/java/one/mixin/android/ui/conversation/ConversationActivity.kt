package one.mixin.android.ui.conversation

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
import androidx.lifecycle.lifecycleScope
import kotlinx.android.synthetic.main.activity_chat.*
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch
import one.mixin.android.R
import one.mixin.android.extension.booleanFromAttribute
import one.mixin.android.extension.replaceFragment
import one.mixin.android.repository.ConversationRepository
import one.mixin.android.repository.UserRepository
import one.mixin.android.ui.common.BlazeBaseActivity
import one.mixin.android.ui.conversation.ConversationFragment.Companion.CONVERSATION_ID
import one.mixin.android.ui.conversation.ConversationFragment.Companion.MESSAGE_ID
import one.mixin.android.ui.conversation.ConversationFragment.Companion.RECIPIENT
import one.mixin.android.ui.conversation.ConversationFragment.Companion.RECIPIENT_ID
import one.mixin.android.ui.conversation.ConversationFragment.Companion.SCROLL_MESSAGE_ID
import one.mixin.android.ui.conversation.ConversationFragment.Companion.UNREAD_COUNT
import one.mixin.android.util.Session
import one.mixin.android.vo.ForwardMessage
import one.mixin.android.vo.generateConversationId
import timber.log.Timber
import javax.inject.Inject

class ConversationActivity : BlazeBaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.d("@@@ onCreate ${SystemClock.uptimeMillis() - start}")
        setContentView(R.layout.activity_chat)
        Timber.d("@@@ setContentView ${SystemClock.uptimeMillis() - start}")
        if (booleanFromAttribute(R.attr.flag_night)) {
            container.backgroundImage = resources.getDrawable(R.drawable.bg_chat_night, theme)
        } else {
            container.backgroundImage = resources.getDrawable(R.drawable.bg_chat, theme)
        }
        Timber.d("@@@ backgroundImage ${SystemClock.uptimeMillis() - start}")
        window.decorView.systemUiVisibility =
            window.decorView.systemUiVisibility or SYSTEM_UI_FLAG_LAYOUT_STABLE
        showConversation(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        showConversation(intent)
    }

    @Inject
    lateinit var conversationRepository: ConversationRepository
    @Inject
    lateinit var userRepository: UserRepository

    private fun showConversation(intent: Intent) {
        Timber.d("@@@ showConversation ${SystemClock.uptimeMillis() - start}")
        val bundle = intent.extras ?: return
        lifecycleScope.launch(
            CoroutineExceptionHandler { _, _ ->
                replaceFragment(
                    ConversationFragment.newInstance(intent.extras!!),
                    R.id.container,
                    ConversationFragment.TAG
                )
            }
        ) {
            Timber.d("@@@ after launch ${SystemClock.uptimeMillis() - start}")
            val messageId = bundle.getString(MESSAGE_ID)
            val conversationId = bundle.getString(CONVERSATION_ID)
            val userId = bundle.getString(RECIPIENT_ID)
            var unreadCount = bundle.getInt(UNREAD_COUNT)
            val cid: String
            if (conversationId == null) {
                val user = userRepository.suspendFindUserById(userId!!)!!
                Timber.d("@@@ findUser cost ${SystemClock.uptimeMillis() - start}")
                cid = conversationId ?: generateConversationId(Session.getAccountId()!!, user.userId)
                require(user.userId != Session.getAccountId()) { "error data" }
                bundle.putParcelable(RECIPIENT, user)
            } else {
                val user = if (userId != null) {
                    userRepository.suspendFindUserById(userId)!!
                } else {
                    userRepository.suspendFindContactByConversationId(conversationId)
                }
                Timber.d("@@@ userId: $userId, findUser cost ${SystemClock.uptimeMillis() - start}")
                if (user == null || user.userId == Session.getAccountId()) {
                    throw IllegalArgumentException(
                        "error data ${bundle.getString(
                            CONVERSATION_ID
                        )}"
                    )
                }
                cid = conversationId
                bundle.putParcelable(RECIPIENT, user)
            }
            if (unreadCount == -1) {
                unreadCount = if (!messageId.isNullOrEmpty()) {
                    conversationRepository.findMessageIndex(cid, messageId)
                } else {
                    conversationRepository.indexUnread(cid) ?: -1
                }
            }
            Timber.d("@@@ find unread cost: ${SystemClock.uptimeMillis() - start}")
            bundle.putInt(UNREAD_COUNT, unreadCount)
            val msgId = messageId ?: if (unreadCount <= 0) {
                null
            } else {
                conversationRepository.findFirstUnreadMessageId(cid, unreadCount - 1)
            }
            Timber.d("@@@ find findFirstUnreadMessageId cost: ${SystemClock.uptimeMillis() - start}")
            bundle.putString(SCROLL_MESSAGE_ID, msgId)
            conversationRepository.conversationZeroClear(cid)
            Timber.d("@@@ zero clear: ${SystemClock.uptimeMillis() - start}")
            replaceFragment(
                ConversationFragment.newInstance(bundle),
                R.id.container,
                ConversationFragment.TAG
            )
        }
    }

    companion object {

        var start = 0L

        fun show(
            context: Context,
            conversationId: String? = null,
            recipientId: String? = null,
            messageId: String? = null,
            keyword: String? = null,
            messages: ArrayList<ForwardMessage>? = null,
            unreadCount: Int = -1
        ) {
            require(!(conversationId == null && recipientId == null)) { "lose data" }
            require(recipientId != Session.getAccountId()) { "error data $conversationId" }
            Intent(context, ConversationActivity::class.java).apply {
                putExtras(
                    ConversationFragment.putBundle(
                        conversationId,
                        recipientId,
                        messageId,
                        keyword,
                        messages,
                        unreadCount
                    )
                )
            }.run {
                context.startActivity(this)
            }
        }

        fun putIntent(
            context: Context,
            conversationId: String? = null,
            recipientId: String? = null,
            messageId: String? = null,
            keyword: String? = null,
            messages: ArrayList<ForwardMessage>? = null
        ): Intent {
            require(!(conversationId == null && recipientId == null)) { "lose data" }
            require(recipientId != Session.getAccountId()) { "error data $conversationId" }
            return Intent(context, ConversationActivity::class.java).apply {
                putExtras(
                    ConversationFragment.putBundle(
                        conversationId,
                        recipientId,
                        messageId,
                        keyword,
                        messages
                    )
                )
            }
        }

        fun showAndClear(
            context: Context,
            conversationId: String? = null,
            recipientId: String? = null,
            messageId: String? = null,
            keyword: String? = null,
            messages: ArrayList<ForwardMessage>? = null
        ) {
            val mainIntent = Intent(context, ConversationActivity::class.java)
            mainIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val conversationIntent = putIntent(context, conversationId, recipientId, messageId, keyword, messages)
            context.startActivities(arrayOf(mainIntent, conversationIntent))
        }
    }
}
