package one.mixin.android.ui.search

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.timehop.stickyheadersrecyclerview.StickyRecyclerHeadersAdapter
import one.mixin.android.R
import one.mixin.android.ui.search.holder.AssetHolder
import one.mixin.android.ui.search.holder.ChatHolder
import one.mixin.android.ui.search.holder.ContactHolder
import one.mixin.android.ui.search.holder.HeaderHolder
import one.mixin.android.ui.search.holder.MessageHolder
import one.mixin.android.vo.AssetItem
import one.mixin.android.vo.ChatMinimal
import one.mixin.android.vo.ConversationCategory
import one.mixin.android.vo.SearchMessageItem
import one.mixin.android.vo.User

class SearchAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>(), StickyRecyclerHeadersAdapter<HeaderHolder> {

    var onItemClickListener: SearchFragment.OnSearchClickListener? = null

    override fun getHeaderId(position: Int): Long = getItemViewType(position).toLong()

    override fun onBindHeaderViewHolder(holder: HeaderHolder, position: Int) {
        val context = holder.itemView.context
        when {
            getItemViewType(position) == 0 -> holder.bind(context.getText(R.string.search_title_assets).toString(), data.assetShowMore()) {
                data.assetLimit = false
                notifyDataSetChanged()
            }
            getItemViewType(position) == 1 -> holder.bind(context.getText(R.string.search_title_chat).toString(), data.chatShowMore()) {
                data.chatLimit = false
                notifyDataSetChanged()
            }
            getItemViewType(position) == 2 -> holder.bind(context.getText(R.string.search_title_contacts).toString(), data.userShowMore()) {
                data.userLimit = false
                notifyDataSetChanged()
            }
            else -> holder.bind(context.getText(R.string.search_title_messages).toString(), data.messageShowMore()) {
                data.messageLimit = false
                notifyDataSetChanged()
            }
        }
    }

    override fun onCreateHeaderViewHolder(parent: ViewGroup): HeaderHolder {
        val item = LayoutInflater.from(parent.context).inflate(R.layout.item_search_header, parent, false)
        return HeaderHolder(item)
    }

    private var data = SearchDataPackage()

    fun setDefaultData(list: List<User>?) {
        data.contactList = list
        if (list != null) {
            data.assetList = null
            data.chatList = null
            data.userList = null
            data.messageList = null
        }
        notifyDataSetChanged()
    }

    fun setAssetData(list: List<AssetItem>?) {
        data.assetList = list
        notifyDataSetChanged()
    }

    fun setUserData(list: List<User>?) {
        data.userList = list?.filter { item ->
            data.chatList?.any { it.category == ConversationCategory.CONTACT.name && it.userId == item.userId } != true
        }
        notifyDataSetChanged()
    }

    fun setChatData(list: List<ChatMinimal>?) {
        data.chatList = list
        data.userList = data.userList?.filter { item ->
            data.chatList?.any { it.category == ConversationCategory.CONTACT.name && it.userId == item.userId } != true
        }

        notifyDataSetChanged()
    }

    fun setMessageData(list: List<SearchMessageItem>?) {
        data.messageList = list
        notifyDataSetChanged()
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (getItemViewType(position)) {
            0 -> {
                data.getItem(position).let {
                    (holder as AssetHolder).bind(it as AssetItem, onItemClickListener)
                }
            }
            1 -> {
                data.getItem(position).let {
                    (holder as ChatHolder).bind(it as ChatMinimal, onItemClickListener)
                }
            }
            2 -> {
                data.getItem(position).let {
                    (holder as ContactHolder).bind(it as User, onItemClickListener)
                }
            }
            3 -> {
                data.getItem(position).let {
                    (holder as MessageHolder).bind(it as SearchMessageItem, onItemClickListener)
                }
            }
        }
    }

    override fun getItemCount(): Int = data.getCount()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        when (viewType) {
            0 -> {
                val item = LayoutInflater.from(parent.context).inflate(R.layout.item_search_asset, parent, false)
                AssetHolder(item)
            }
            1 -> {
                val item = LayoutInflater.from(parent.context).inflate(R.layout.item_search_contact, parent, false)
                ChatHolder(item)
            }
            2 -> {
                val item = LayoutInflater.from(parent.context).inflate(R.layout.item_search_contact, parent, false)
                ContactHolder(item)
            }
            3 -> {
                val item = LayoutInflater.from(parent.context).inflate(R.layout.item_search_message, parent, false)
                MessageHolder(item)
            }
            else -> {
                val item = LayoutInflater.from(parent.context).inflate(R.layout.item_search_contact, parent, false)
                ContactHolder(item)
            }
        }

    override fun getItemViewType(position: Int): Int =
        when (data.getItem(position)) {
            is AssetItem -> 0
            is ChatMinimal -> 1
            is User -> 2
            else -> 3
        }
}