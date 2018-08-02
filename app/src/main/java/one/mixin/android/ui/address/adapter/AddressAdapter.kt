package one.mixin.android.ui.address.adapter

import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import kotlinx.android.synthetic.main.item_address.view.*
import one.mixin.android.R
import one.mixin.android.extension.notNullElse
import one.mixin.android.vo.Address
import one.mixin.android.vo.AssetItem

class AddressAdapter(private val asset: AssetItem, private val showIcon: Boolean = false, private val canSwipe: Boolean = false) :
    RecyclerView.Adapter<AddressAdapter.ItemHolder>() {
    var addresses: MutableList<Address>? = null
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    private var addrListener: AddressListener? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemHolder =
        ItemHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_address, parent, false))

    override fun onBindViewHolder(holder: ItemHolder, position: Int) {
        if (addresses == null || addresses!!.isEmpty()) {
            return
        }
        val addr = addresses!![position]
        holder.itemView.icon.visibility = if (showIcon) VISIBLE else GONE
        holder.itemView.background_rl.visibility = if (canSwipe) VISIBLE else GONE
        holder.itemView.name_tv.text = if (noPublicKey()) addr.accountName else addr.label
        holder.itemView.addr_tv.text = if (noPublicKey()) addr.accountTag else addr.publicKey
        holder.itemView.setOnClickListener { addrListener?.onAddrClick(addr) }
        holder.itemView.setOnLongClickListener {
            addrListener?.onAddrLongClick(holder.itemView, addr)
            return@setOnLongClickListener true
        }
    }

    override fun getItemCount(): Int = notNullElse(addresses, { it.size }, 0)

    private fun noPublicKey() = !asset.accountName.isNullOrEmpty()

    fun removeItem(pos: Int): Address? {
        val addr = addresses?.removeAt(pos)
        notifyItemRemoved(pos)
        return addr
    }

    fun restoreItem(item: Address, pos: Int) {
        addresses?.add(pos, item)
        notifyItemInserted(pos)
    }

    fun setAddrListener(listener: AddressListener) {
        addrListener = listener
    }

    interface AddressListener {
        fun onAddrClick(addr: Address)

        fun onAddrLongClick(view: View, addr: Address)

        fun onAddrDelete(viewHolder: RecyclerView.ViewHolder)
    }

    open class SimpleAddressListener : AddressListener {
        override fun onAddrClick(addr: Address) {}
        override fun onAddrLongClick(view: View, addr: Address) {}
        override fun onAddrDelete(viewHolder: RecyclerView.ViewHolder) {}
    }

    class ItemHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
}