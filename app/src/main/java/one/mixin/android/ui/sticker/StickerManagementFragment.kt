package one.mixin.android.ui.sticker

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import androidx.core.os.bundleOf
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tbruyelle.rxpermissions2.RxPermissions
import com.uber.autodispose.autoDispose
import dagger.hilt.android.AndroidEntryPoint
import one.mixin.android.R
import one.mixin.android.databinding.FragmentStickerManagementBinding
import one.mixin.android.extension.REQUEST_GALLERY
import one.mixin.android.extension.addFragment
import one.mixin.android.extension.colorFromAttribute
import one.mixin.android.extension.isWideScreen
import one.mixin.android.extension.loadSticker
import one.mixin.android.extension.openGalleryFromSticker
import one.mixin.android.extension.openPermissionSetting
import one.mixin.android.extension.realSize
import one.mixin.android.extension.viewDestroyed
import one.mixin.android.ui.common.BaseFragment
import one.mixin.android.ui.conversation.ConversationViewModel
import one.mixin.android.ui.conversation.StickerFragment.Companion.ARGS_ALBUM_ID
import one.mixin.android.ui.conversation.StickerFragment.Companion.PADDING
import one.mixin.android.ui.conversation.adapter.StickerSpacingItemDecoration
import one.mixin.android.util.bugsnag
import one.mixin.android.util.viewBinding
import one.mixin.android.vo.Sticker
import org.jetbrains.anko.dip
import org.jetbrains.anko.textColor

@AndroidEntryPoint
class StickerManagementFragment : BaseFragment() {
    companion object {
        const val TAG = "StickerManagementFragment"
        var COLUMN = 3

        fun newInstance(id: String?) = StickerManagementFragment().apply {
            arguments = bundleOf(ARGS_ALBUM_ID to id)
        }
    }

    private val stickerViewModel by viewModels<ConversationViewModel>()

    private val padding: Int by lazy { requireContext().dip(PADDING) }

    private val albumId: String? by lazy { requireArguments().getString(ARGS_ALBUM_ID) }

    private val stickers = mutableListOf<Sticker>()
    private val stickerAdapter: StickerAdapter by lazy {
        StickerAdapter(stickers)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        layoutInflater.inflate(R.layout.fragment_sticker_management, container, false)

    private val binding by viewBinding(FragmentStickerManagementBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        COLUMN = if (requireContext().isWideScreen()) {
            5
        } else {
            3
        }
        binding.titleView.leftIb.setOnClickListener { requireActivity().onBackPressed() }
        binding.titleView.rightTv.textColor = requireContext().colorFromAttribute(R.attr.text_primary)
        binding.titleView.rightAnimator.setOnClickListener {
            if (stickerAdapter.editing) {
                binding.titleView.rightTv.text = getString(R.string.select)
                if (stickerAdapter.checkedList.isNotEmpty()) {
                    stickerViewModel.removeStickers(stickerAdapter.checkedList)
                }
            } else {
                binding.titleView.rightTv.text = getString(R.string.conversation_delete)
            }
            stickerAdapter.editing = !stickerAdapter.editing
            stickerAdapter.notifyDataSetChanged()
        }
        binding.stickerRv.layoutManager = GridLayoutManager(context, COLUMN)
        binding.stickerRv.addItemDecoration(StickerSpacingItemDecoration(COLUMN, padding, true))
        stickerAdapter.size = (requireContext().realSize().x - (COLUMN + 1) * padding) / COLUMN
        binding.stickerRv.adapter = stickerAdapter
        stickerAdapter.setOnStickerListener(
            object : StickerListener {
                override fun onAddClick() {
                    RxPermissions(activity!!)
                        .request(
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.READ_EXTERNAL_STORAGE
                        )
                        .autoDispose(stopScope)
                        .subscribe(
                            { granted ->
                                if (granted) {
                                    openGalleryFromSticker()
                                } else {
                                    context?.openPermissionSetting()
                                }
                            },
                            {
                                bugsnag?.notify(it)
                            }
                        )
                }

                override fun onDelete() {
                    binding.titleView.rightTv.text = getString(R.string.conversation_delete)
                }
            }
        )

        if (albumId == null) { // not add any personal sticker yet
            stickerViewModel.observePersonalStickers().observe(
                viewLifecycleOwner,
                Observer {
                    it?.let { updateStickers(it) }
                }
            )
        } else {
            stickerViewModel.observeStickers(albumId!!).observe(
                viewLifecycleOwner,
                Observer {
                    it?.let { updateStickers(it) }
                }
            )
        }
    }

    override fun onBackPressed(): Boolean {
        if (stickerAdapter.editing) {
            stickerAdapter.editing = !stickerAdapter.editing
            stickerAdapter.checkedList.clear()
            stickerAdapter.notifyDataSetChanged()
            binding.titleView.rightTv.text = getString(R.string.select)

            return true
        }
        return super.onBackPressed()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_GALLERY && resultCode == Activity.RESULT_OK) {
            data?.data?.let {
                requireActivity().addFragment(
                    this@StickerManagementFragment,
                    StickerAddFragment.newInstance(it.toString(), true),
                    StickerAddFragment.TAG
                )
            }
        }
    }

    @Synchronized
    private fun updateStickers(list: List<Sticker>) {
        if (viewDestroyed()) return
        stickers.clear()
        stickers.addAll(list)
        stickerAdapter.notifyDataSetChanged()
    }

    private class StickerAdapter(private val stickers: List<Sticker>) : RecyclerView.Adapter<StickerViewHolder>() {
        private var listener: StickerListener? = null
        var size: Int = 0
        var editing = false
        val checkedList = arrayListOf<String>()

        override fun onBindViewHolder(holder: StickerViewHolder, position: Int) {
            val s = if (editing) {
                stickers[position]
            } else {
                if (stickers.isNotEmpty() && position != 0) {
                    stickers[position - 1]
                } else {
                    null
                }
            }
            val v = holder.itemView
            val ctx = v.context

            val params = v.layoutParams
            params.height = size
            params.width = size
            v.layoutParams = params
            val imageView = (v as ViewGroup).getChildAt(0) as ImageView
            val cover = v.getChildAt(1)
            val cb = v.getChildAt(2) as CheckBox
            if (editing) {
                cb.visibility = VISIBLE
            } else {
                cb.visibility = GONE
            }
            if (s != null && checkedList.contains(s.stickerId)) {
                cb.isChecked = true
                cover.visibility = VISIBLE
            } else {
                cb.isChecked = false
                cover.visibility = GONE
            }
            if (!editing && position == 0) {
                imageView.setImageResource(R.drawable.ic_add_stikcer)
                imageView.setOnClickListener { listener?.onAddClick() }
                imageView.updateLayoutParams<ViewGroup.LayoutParams> {
                    width = size - ctx.dip(50)
                    height = size - ctx.dip(50)
                }
            } else {
                imageView.updateLayoutParams<ViewGroup.LayoutParams> {
                    width = size
                    height = size
                }
                if (s != null) {
                    imageView.loadSticker(s.assetUrl, s.assetType)
                    imageView.setOnClickListener {
                        handleChecked(cb, cover, s.stickerId)
                    }
                    imageView.setOnLongClickListener {
                        editing = !editing
                        notifyDataSetChanged()
                        listener?.onDelete()
                        return@setOnLongClickListener true
                    }
                }
            }
        }

        private fun handleChecked(cb: CheckBox, cover: View, stickerId: String) {
            if (editing) {
                cb.isChecked = !cb.isChecked
                if (cb.isChecked) {
                    checkedList.add(stickerId)
                    cover.visibility = VISIBLE
                } else {
                    checkedList.remove(stickerId)
                    cover.visibility = GONE
                }
            }
        }

        override fun getItemCount() = if (editing) {
            stickers.size
        } else {
            stickers.size + 1
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StickerViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_sticker_management, parent, false)
            return StickerViewHolder(view)
        }

        fun setOnStickerListener(onStickerListener: StickerListener) {
            listener = onStickerListener
        }
    }

    private class StickerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    interface StickerListener {
        fun onAddClick()
        fun onDelete()
    }
}
