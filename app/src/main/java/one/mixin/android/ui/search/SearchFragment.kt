package one.mixin.android.ui.search

import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.timehop.stickyheadersrecyclerview.StickyRecyclerHeadersDecoration
import com.timehop.stickyheadersrecyclerview.StickyRecyclerHeadersTouchListener
import com.uber.autodispose.autoDispose
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import one.mixin.android.Constants.Account.PREF_RECENT_USED_BOTS
import one.mixin.android.R
import one.mixin.android.databinding.FragmentSearchBinding
import one.mixin.android.databinding.ItemSearchAppBinding
import one.mixin.android.databinding.ItemSearchHeaderBinding
import one.mixin.android.extension.addFragment
import one.mixin.android.extension.defaultSharedPreferences
import one.mixin.android.extension.deserialize
import one.mixin.android.extension.hideKeyboard
import one.mixin.android.extension.isUUID
import one.mixin.android.extension.toast
import one.mixin.android.extension.viewDestroyed
import one.mixin.android.session.Session
import one.mixin.android.ui.common.BaseFragment
import one.mixin.android.ui.common.UserBottomSheetDialogFragment
import one.mixin.android.ui.common.profile.ProfileBottomSheetDialogFragment
import one.mixin.android.ui.conversation.ConversationActivity
import one.mixin.android.ui.home.MainActivity
import one.mixin.android.ui.wallet.WalletActivity
import one.mixin.android.util.ErrorHandler
import one.mixin.android.util.viewBinding
import one.mixin.android.vo.App
import one.mixin.android.vo.AssetItem
import one.mixin.android.vo.ChatMinimal
import one.mixin.android.vo.SearchMessageItem
import one.mixin.android.vo.User

@AndroidEntryPoint
class SearchFragment : BaseFragment(R.layout.fragment_search) {

    private val searchViewModel by viewModels<SearchViewModel>()

    private val searchAdapter: SearchAdapter by lazy {
        SearchAdapter()
    }
    private val decoration by lazy { StickyRecyclerHeadersDecoration(searchAdapter) }

    companion object {
        const val TAG = "SearchFragment"
        const val SEARCH_DEBOUNCE = 300L
    }

    private var keyword: String? = null
        set(value) {
            if (field != value) {
                field = value
                bindData()
            }
        }

    fun setQueryText(text: String) {
        if (isAdded && text != keyword) {

            if (text.isNotBlank()) {
                showSearch()
            } else {
                showBots()
            }

            searchAdapter.query = text
            keyword = text
        }
    }

    private var searchJob: Job? = null
    private var messageSearchJob: Job? = null

    @Suppress("UNCHECKED_CAST")
    private fun bindData(keyword: String? = this@SearchFragment.keyword) {
        searchJob?.cancel()
        messageSearchJob?.cancel()
        searchJob = fuzzySearch(keyword)
    }

    private val appAdapter = AppAdapter()

    private val binding by viewBinding(FragmentSearchBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.setOnClickListener {
            if (keyword.isNullOrBlank()) {
                (requireActivity() as MainActivity).closeSearch()
            }
        }
        binding.searchRv.layoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, false)
        binding.searchRv.addItemDecoration(decoration)
        binding.searchRv.adapter = searchAdapter
        binding.searchRv.addOnItemTouchListener(
            StickyRecyclerHeadersTouchListener(binding.searchRv, decoration).apply {
                setOnHeaderClickListener { headerView, position, _, e ->

                    if (ItemSearchHeaderBinding.bind(headerView).searchHeaderMore.x > e.rawX) return@setOnHeaderClickListener

                    searchAdapter.getTypeData(position)?.let {
                        val f = SearchSingleFragment.newInstance(
                            arrayListOf<Parcelable>().apply {
                                addAll(it)
                            },
                            keyword ?: ""
                        )
                        requireActivity().addFragment(this@SearchFragment, f, SearchSingleFragment.TAG, R.id.root_view)
                        binding.searchRv.hideKeyboard()
                    }
                }
            }
        )

        binding.appRv.layoutManager = GridLayoutManager(requireContext(), 4)
        appAdapter.appListener = object : AppListener {
            override fun onItemClick(app: App) {
                (requireActivity() as MainActivity).closeSearch()
                ConversationActivity.show(requireContext(), null, app.appId)
            }
        }
        binding.appRv.adapter = appAdapter

        showBots()

        searchAdapter.onItemClickListener = object : OnSearchClickListener {
            override fun onTipClick() {
                binding.searchRv.hideKeyboard()
                searchAdapter.searchingId = true
                searchViewModel.search(searchAdapter.query).autoDispose(stopScope).subscribe(
                    { r ->
                        searchAdapter.searchingId = false
                        when {
                            r.isSuccess -> r.data?.let { data ->
                                if (data.userId == Session.getAccountId()) {
                                    ProfileBottomSheetDialogFragment.newInstance().showNow(
                                        parentFragmentManager,
                                        UserBottomSheetDialogFragment.TAG
                                    )
                                } else {
                                    searchViewModel.insertUser(user = data)
                                    UserBottomSheetDialogFragment.newInstance(data)?.showNow(parentFragmentManager, UserBottomSheetDialogFragment.TAG)
                                }
                            }
                            r.errorCode == ErrorHandler.NOT_FOUND -> context?.toast(R.string.error_user_not_found)
                            else -> ErrorHandler.handleMixinError(r.errorCode, r.errorDescription)
                        }
                    },
                    { t: Throwable ->
                        searchAdapter.searchingId = false
                        ErrorHandler.handleError(t)
                    }
                )
            }

            override fun onAsset(assetItem: AssetItem) {
                activity?.let { WalletActivity.show(it, assetItem) }
            }

            override fun onMessageClick(message: SearchMessageItem) {
                binding.searchRv.hideKeyboard()
                val f = SearchMessageFragment.newInstance(message, keyword ?: "")
                requireActivity().addFragment(this@SearchFragment, f, SearchMessageFragment.TAG, R.id.root_view)
            }

            override fun onChatClick(chatMinimal: ChatMinimal) {
                binding.searchRv.hideKeyboard()
                context?.let { ctx -> ConversationActivity.show(ctx, chatMinimal.conversationId) }
            }

            override fun onUserClick(user: User) {
                binding.searchRv.hideKeyboard()
                context?.let { ctx -> ConversationActivity.show(ctx, null, user.userId) }
            }
        }
        bindData()
    }

    override fun onResume() {
        super.onResume()
        loadRecentUsedApps()
    }

    private fun loadRecentUsedApps() = lifecycleScope.launch {
        if (viewDestroyed()) return@launch

        val apps = withContext(Dispatchers.IO) {
            var botsList = defaultSharedPreferences.getString(PREF_RECENT_USED_BOTS, null)?.split("=")
                ?: return@withContext null
            if (botsList.size == 1 && !botsList[0].isUUID()) {
                getPreviousVersionBotsList()?.let {
                    botsList = it
                }
            }
            if (botsList.isNullOrEmpty()) return@withContext null
            val result = searchViewModel.findAppsByIds(botsList.take(8))
            if (result.isNullOrEmpty()) return@withContext null
            result.sortedBy {
                botsList.indexOf(it.appId)
            }
        }
        if (apps.isNullOrEmpty()) return@launch

        appAdapter.submitList(apps)
    }

    private fun getPreviousVersionBotsList(): List<String>? {
        defaultSharedPreferences.getString(PREF_RECENT_USED_BOTS, null)?.let { botsString ->
            return botsString.deserialize<Array<String>>()?.toList()
        } ?: return null
    }

    private fun showSearch() {
        binding.searchRv.post {
            if (viewDestroyed()) return@post

            binding.searchRv.isVisible = true
            binding.appRv.isGone = true
        }
    }

    private fun showBots() {
        binding.searchRv.post {
            if (viewDestroyed()) return@post

            binding.searchRv.isGone = true
            binding.appRv.isVisible = true
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun fuzzySearch(keyword: String?) = lifecycleScope.launch {
        if (viewDestroyed()) return@launch

        (requireActivity() as MainActivity).showSearchLoading()
        searchAdapter.clear()

        messageSearchJob = launch {
            (searchViewModel.fuzzySearch<SearchMessageItem>(keyword, 10) as? List<SearchMessageItem>)?.let { searchMessageItems ->
                searchAdapter.setMessageData(searchMessageItems)
            }
        }
        val assetItems = searchViewModel.fuzzySearch<AssetItem>(keyword) as List<AssetItem>?
        val users = searchViewModel.fuzzySearch<User>(keyword) as List<User>?
        val chatMinimals = searchViewModel.fuzzySearch<ChatMinimal>(keyword) as List<ChatMinimal>?
        decoration.invalidateHeaders()
        searchAdapter.setData(assetItems, users, chatMinimals)

        messageSearchJob?.join()
        (requireActivity() as MainActivity).hideSearchLoading()
    }

    internal class AppAdapter : ListAdapter<App, AppHolder>(App.DIFF_CALLBACK) {
        var appListener: AppListener? = null

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            AppHolder(ItemSearchAppBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: AppHolder, position: Int) {
            getItem(position)?.let {
                holder.bind(it, appListener)
            }
        }
    }

    internal class AppHolder(val binding: ItemSearchAppBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(app: App, listener: AppListener?) {
            binding.iconIv.setInfo(app.name, app.iconUrl, app.appId)
            binding.nameTv.text = app.name
            binding.root.setOnClickListener {
                listener?.onItemClick(app)
            }
        }
    }

    interface AppListener {
        fun onItemClick(app: App)
    }

    interface OnSearchClickListener {
        fun onUserClick(user: User)
        fun onChatClick(chatMinimal: ChatMinimal)
        fun onMessageClick(message: SearchMessageItem)
        fun onAsset(assetItem: AssetItem)
        fun onTipClick()
    }
}
