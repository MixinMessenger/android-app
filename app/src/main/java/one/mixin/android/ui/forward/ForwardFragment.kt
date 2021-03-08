package one.mixin.android.ui.forward

import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.collection.ArraySet
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.tbruyelle.rxpermissions2.RxPermissions
import com.timehop.stickyheadersrecyclerview.StickyRecyclerHeadersDecoration
import com.uber.autodispose.autoDispose
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import one.mixin.android.R
import one.mixin.android.databinding.FragmentForwardBinding
import one.mixin.android.extension.hideKeyboard
import one.mixin.android.extension.openPermissionSetting
import one.mixin.android.extension.toast
import one.mixin.android.session.Session
import one.mixin.android.ui.common.BaseFragment
import one.mixin.android.ui.conversation.ConversationActivity
import one.mixin.android.ui.conversation.ConversationViewModel
import one.mixin.android.ui.forward.ForwardActivity.Companion.ARGS_ACTION
import one.mixin.android.ui.forward.ForwardActivity.Companion.ARGS_MESSAGES
import one.mixin.android.ui.home.MainActivity
import one.mixin.android.util.GsonHelper
import one.mixin.android.util.ShortcutInfo
import one.mixin.android.util.generateDynamicShortcut
import one.mixin.android.util.maxDynamicShortcutCount
import one.mixin.android.util.viewBinding
import one.mixin.android.vo.ConversationItem
import one.mixin.android.vo.ForwardAction
import one.mixin.android.vo.ForwardCategory
import one.mixin.android.vo.ForwardMessage
import one.mixin.android.vo.ShareCategory
import one.mixin.android.vo.ShareImageData
import one.mixin.android.vo.User
import one.mixin.android.vo.generateConversationId
import one.mixin.android.vo.isContactConversation
import one.mixin.android.vo.isGroupConversation
import one.mixin.android.vo.toUser
import one.mixin.android.webrtc.SelectItem
import one.mixin.android.websocket.AudioMessagePayload
import one.mixin.android.websocket.ContactMessagePayload
import one.mixin.android.websocket.DataMessagePayload
import one.mixin.android.websocket.LiveMessagePayload
import one.mixin.android.websocket.LocationPayload
import one.mixin.android.websocket.StickerMessagePayload
import one.mixin.android.websocket.VideoMessagePayload
import java.io.File
import java.util.concurrent.TimeUnit

@AndroidEntryPoint
class ForwardFragment : BaseFragment(R.layout.fragment_forward) {
    companion object {
        const val TAG = "ForwardFragment"

        inline fun newInstance(
            messages: ArrayList<ForwardMessage>,
            action: ForwardAction
        ): ForwardFragment {
            val fragment = ForwardFragment()
            val b = bundleOf(
                ARGS_MESSAGES to messages,
                ARGS_ACTION to action
            )
            fragment.arguments = b
            return fragment
        }
    }

    private val chatViewModel by viewModels<ConversationViewModel>()

    private val adapter by lazy {
        ForwardAdapter()
    }

    private val messages: ArrayList<ForwardMessage> by lazy {
        requireArguments().getParcelableArrayList(ARGS_MESSAGES)!!
    }
    private val action: ForwardAction by lazy {
        requireArguments().getParcelable(ARGS_ACTION)!!
    }

    private val sender = Session.getAccount()?.toUser()

    private val binding by viewBinding(FragmentForwardBinding::bind)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        layoutInflater.inflate(R.layout.fragment_forward, container, false)

    private fun setForwardText() {
        if (adapter.selectItem.size > 0) {
            binding.forwardGroup.visibility = View.VISIBLE
        } else {
            binding.forwardGroup.visibility = View.GONE
        }
        val str = StringBuffer()
        for (i in adapter.selectItem.size - 1 downTo 0) {
            adapter.selectItem[i].let {
                if (it is ConversationItem) {
                    if (it.isGroupConversation()) {
                        str.append(it.groupName)
                    } else {
                        str.append(it.name)
                    }
                    if (i != 0) {
                        str.append(getString(R.string.divide))
                    }
                } else if (it is User) {
                    str.append(it.fullName)
                    if (i != 0) {
                        str.append(getString(R.string.divide))
                    }
                }
            }
        }
        binding.forwardTv.text = str
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val cid = action.conversationId
        if (cid != null) {
            if (action is ForwardAction.System) {
                sendDirectMessages(cid)
            } else {
                sendMessage(listOf(SelectItem(cid, null)))
            }
            requireActivity().finish()
            return
        }

        if (!action.name.isNullOrBlank()) {
            binding.titleView.titleTv.text = action.name
        }
        binding.titleView.setOnClickListener {
            binding.searchEt.hideKeyboard()
            activity?.onBackPressed()
        }
        binding.forwardRv.adapter = adapter
        binding.forwardRv.addItemDecoration(StickyRecyclerHeadersDecoration(adapter))
        binding.forwardBn.setOnClickListener {
            binding.searchEt.hideKeyboard()
            checkPermission {
                updateDynamicShortcuts(adapter.selectItem)

                val selectItem = adapter.selectItem.mapNotNull {
                    when (it) {
                        is ConversationItem -> {
                            SelectItem(it.conversationId, null)
                        }
                        is User -> {
                            SelectItem(null, it.userId)
                        }
                        else -> {
                            null
                        }
                    }
                }

                sendMessage(selectItem)
            }
        }
        adapter.setForwardListener(
            object : ForwardAdapter.ForwardListener {
                override fun onConversationItemClick(item: ConversationItem) {
                    if (adapter.selectItem.contains(item)) {
                        adapter.selectItem.remove(item)
                    } else {
                        adapter.selectItem.add(item)
                    }
                    setForwardText()
                }

                override fun onUserItemClick(user: User) {
                    if (adapter.selectItem.contains(user)) {
                        adapter.selectItem.remove(user)
                    } else {
                        adapter.selectItem.add(user)
                    }
                    setForwardText()
                }
            }
        )
        binding.searchEt.addTextChangedListener(mWatcher)

        loadData()
    }

    private fun loadData() = lifecycleScope.launch {
        val conversations = chatViewModel.successConversationList()
        adapter.sourceConversations = conversations
        val set = ArraySet<String>()
        conversations.forEach { item ->
            if (item.isContactConversation()) {
                set.add(item.ownerId)
            }
        }

        val friends = mutableListOf<User>()
        val bots = mutableListOf<User>()
        chatViewModel.getFriends().filter { item ->
            !set.contains(item.userId)
        }.forEach {
            if (it.isBot()) {
                bots.add(it)
            } else {
                friends.add(it)
            }
        }
        adapter.sourceFriends = friends
        adapter.sourceBots = bots

        adapter.changeData()
    }

    private fun sendMessage(selectItems: List<SelectItem>) = lifecycleScope.launch {
        val errList = arrayListOf<String>()
        selectItems.forEach { item ->
            val err = sendMessageInternal(item)
            if (err != null) {
                errList.addAll(err)
            }
        }
        if (errList.isEmpty()) {
            toast(R.string.message_sent)
        }
        if (action is ForwardAction.System) {
            MainActivity.reopen(requireContext())
            requireActivity().finish()
            val firstItem = selectItems[0]
            if (firstItem.conversationId != null) {
                ConversationActivity.show(requireContext(), conversationId = firstItem.conversationId)
            } else {
                ConversationActivity.show(requireContext(), recipientId = firstItem.userId)
            }
        } else {
            if (action is ForwardAction.App.Resultful) {
                val result = Intent().apply {
                    putParcelableArrayListExtra(ForwardActivity.ARGS_RESULT, ArrayList(selectItems))
                }
                requireActivity().setResult(Activity.RESULT_OK, result)
            }
            requireActivity().finish()
        }
    }

    private fun checkPermission(afterGranted: () -> Job) {
        RxPermissions(requireActivity())
            .request(
                WRITE_EXTERNAL_STORAGE,
                READ_EXTERNAL_STORAGE
            )
            .autoDispose(stopScope)
            .subscribe(
                { granted ->
                    if (granted) {
                        afterGranted.invoke()
                    } else {
                        requireContext().openPermissionSetting()
                    }
                },
                {}
            )
    }

    private suspend fun sendMessageInternal(item: SelectItem): ArrayList<String>? {
        if (sender == null) return null

        val errList = arrayListOf<String>()
        messages.forEach { m ->
            chatViewModel.checkData(item) { conversationId: String, isEncrypted: Boolean ->
                val content = m.content
                var errorString: String? = null
                when (m.category) {
                    ShareCategory.Text -> {
                        chatViewModel.sendTextMessage(conversationId, sender, content, isEncrypted)
                    }
                    ShareCategory.Post -> {
                        chatViewModel.sendPostMessage(conversationId, sender, content, isEncrypted)
                    }
                    ShareCategory.Image -> {
                        val shareImageData = GsonHelper.customGson.fromJson(content, ShareImageData::class.java)
                        val code = withContext(Dispatchers.IO) {
                            if (shareImageData.url.startsWith("http", true)) {
                                val file: File? = try {
                                    Glide.with(requireContext()).asFile().load(shareImageData.url).submit().get()
                                } catch (t: Throwable) {
                                    null
                                }
                                if (file == null) {
                                    chatViewModel.sendImageMessage(conversationId, sender, shareImageData.url.toUri(), isEncrypted)
                                } else {
                                    chatViewModel.sendImageMessage(conversationId, sender, file.toUri(), isEncrypted)
                                }
                            } else {
                                chatViewModel.sendImageMessage(conversationId, sender, shareImageData.url.toUri(), isEncrypted)
                            }
                        }
                        val errorRes = ShareCategory.Image.getErrorStringOrNull(code)
                        if (errorRes != null) {
                            errorString = requireContext().getString(errorRes)
                            toast(errorString)
                        }
                    }
                    ShareCategory.Contact -> {
                        val contactData = GsonHelper.customGson.fromJson(content, ContactMessagePayload::class.java)
                        chatViewModel.sendContactMessage(conversationId, sender, contactData.userId, isEncrypted)
                    }
                    ShareCategory.Contact -> {
                        chatViewModel.sendPostMessage(conversationId, sender, content, isEncrypted)
                    }
                    ShareCategory.AppCard -> {
                        chatViewModel.sendAppCardMessage(conversationId, sender, content)
                    }
                    ShareCategory.Live -> {
                        val liveData = GsonHelper.customGson.fromJson(content, LiveMessagePayload::class.java)
                        chatViewModel.sendLiveMessage(conversationId, sender, liveData, isEncrypted)
                    }
                    ForwardCategory.Video -> {
                        val videoData = GsonHelper.customGson.fromJson(content, VideoMessagePayload::class.java)
                        chatViewModel.sendVideoMessage(conversationId, sender.userId, videoData, isEncrypted)
                    }
                    ForwardCategory.Data -> {
                        val dataMessagePayload = GsonHelper.customGson.fromJson(content, DataMessagePayload::class.java)
                        chatViewModel.sendAttachmentMessage(conversationId, sender, dataMessagePayload.toAttachment(), isEncrypted)
                    }
                    ForwardCategory.Audio -> {
                        val audioData = GsonHelper.customGson.fromJson(content, AudioMessagePayload::class.java)
                        chatViewModel.sendAudioMessage(conversationId, sender, audioData, isEncrypted)
                    }
                    ForwardCategory.Sticker -> {
                        val stickerData = GsonHelper.customGson.fromJson(content, StickerMessagePayload::class.java)
                        chatViewModel.sendStickerMessage(conversationId, sender, stickerData, isEncrypted)
                    }
                    ForwardCategory.Location -> {
                        val locationPayload = GsonHelper.customGson.fromJson(content, LocationPayload::class.java)
                        chatViewModel.sendLocationMessage(conversationId, sender.userId, locationPayload, isEncrypted)
                    }
                }
                if (errorString != null) {
                    errList.add(errorString)
                }
            }
        }
        return errList
    }

    private fun sendDirectMessages(cid: String) = lifecycleScope.launch {
        val err = sendMessageInternal(SelectItem(cid, null))
        if (err.isNullOrEmpty()) {
            toast(R.string.message_sent)
        }
        MainActivity.reopen(requireContext())
        activity?.finish()
        ConversationActivity.show(requireContext(), cid)
    }

    private fun updateDynamicShortcuts(selectItems: ArrayList<Any>) = lifecycleScope.launch {
        val shortcuts = mutableListOf<ShortcutInfoCompat>()
        for (i in 0 until selectItems.size) {
            val s = selectItems[i]
            if (shortcuts.size >= maxDynamicShortcutCount) {
                break
            }

            val shortcutInfo = if (s is ConversationItem) {
                val bitmap = loadBitmap(s.iconUrl()) ?: continue
                val intent = ConversationActivity.getShortcutIntent(
                    requireContext(),
                    s.conversationId,
                    null
                )
                ShortcutInfo(s.conversationId, s.getConversationName(), bitmap, intent)
            } else {
                s as User
                val bitmap = loadBitmap(s.avatarUrl) ?: continue
                val cid = generateConversationId(
                    Session.getAccountId()!!,
                    s.userId
                )
                val intent = ConversationActivity.getShortcutIntent(
                    requireContext(),
                    cid,
                    s.userId
                )
                ShortcutInfo(cid, s.fullName ?: "", bitmap, intent)
            }
            shortcuts.add(generateDynamicShortcut(requireContext(), shortcutInfo))
        }
        val exists = ShortcutManagerCompat.getDynamicShortcuts(requireContext())
        val keepSize = maxDynamicShortcutCount - shortcuts.size
        if (keepSize >= 0 && exists.size > keepSize) {
            val removeIds = mutableListOf<String>()
            exists.take(exists.size - keepSize)
                .mapTo(removeIds) { it.id }
            ShortcutManagerCompat.removeDynamicShortcuts(requireContext(), removeIds)
        }
        ShortcutManagerCompat.addDynamicShortcuts(requireContext(), shortcuts)
    }

    private suspend fun loadBitmap(url: String?): Bitmap? {
        if (url.isNullOrBlank()) return null

        return try {
            withContext(Dispatchers.IO) {
                Glide.with(requireContext())
                    .asBitmap()
                    .load(url)
                    .submit()
                    .get(2, TimeUnit.SECONDS)
            }
        } catch (t: Throwable) {
            null
        }
    }

    private val mWatcher: TextWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
        }

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
        }

        override fun afterTextChanged(s: Editable?) {
            adapter.keyword = s
            adapter.changeData()
        }
    }
}
