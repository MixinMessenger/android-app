package one.mixin.android.ui.conversation.holder

import android.view.View
import androidx.core.view.isVisible
import kotlinx.android.synthetic.main.item_chat_stranger.view.*
import one.mixin.android.R
import one.mixin.android.ui.conversation.adapter.ConversationAdapter

class StrangerHolder constructor(containerView: View) : BaseViewHolder(containerView) {

    fun bind(onItemListener: ConversationAdapter.OnItemListener, isGroup: Boolean) {
        if (isGroup) {
            itemView.stranger_info.setText(R.string.stranger_inviter)
            itemView.stranger_block_bn.isVisible = false
            itemView.stranger_add_bn.isVisible = false
            itemView.stranger_exit_report_bn.isVisible = true
            itemView.stranger_exit_report_bn.setOnClickListener {
                onItemListener.onExitAndReportClick()
            }
        } else {
            itemView.stranger_info.setText(R.string.stranger_from)
            itemView.stranger_block_bn.isVisible = true
            itemView.stranger_add_bn.isVisible = true
            itemView.stranger_exit_report_bn.isVisible = false
            itemView.stranger_block_bn.setOnClickListener { onItemListener.onBlockClick() }
            itemView.stranger_add_bn.setOnClickListener { onItemListener.onAddClick() }
        }
    }
}
