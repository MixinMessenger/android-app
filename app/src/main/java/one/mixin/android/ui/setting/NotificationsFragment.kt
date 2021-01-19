package one.mixin.android.ui.setting

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.InputType
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import one.mixin.android.Constants.Account.PREF_DUPLICATE_TRANSFER
import one.mixin.android.Constants.Account.PREF_STRANGER_TRANSFER
import one.mixin.android.R
import one.mixin.android.api.handleMixinResponse
import one.mixin.android.api.request.AccountUpdateRequest
import one.mixin.android.databinding.FragmentNotificationsBinding
import one.mixin.android.extension.indeterminateProgressDialog
import one.mixin.android.extension.openNotificationSetting
import one.mixin.android.extension.supportsOreo
import one.mixin.android.extension.toast
import one.mixin.android.extension.viewDestroyed
import one.mixin.android.session.Session
import one.mixin.android.ui.common.BaseFragment
import one.mixin.android.ui.common.editDialog
import one.mixin.android.util.ChannelManager
import one.mixin.android.util.PropertyHelper
import one.mixin.android.util.viewBinding
import one.mixin.android.vo.Fiats

@AndroidEntryPoint
class NotificationsFragment : BaseFragment(R.layout.fragment_notifications) {
    companion object {
        const val TAG = "NotificationsFragment"
        fun newInstance(): NotificationsFragment {
            return NotificationsFragment()
        }
    }

    private val accountSymbol = Fiats.getSymbol()

    private val viewModel by viewModels<SettingViewModel>()
    private val binding by viewBinding(FragmentNotificationsBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.apply {
            titleView.leftIb.setOnClickListener { activity?.onBackPressed() }
            transferRl.setOnClickListener {
                showDialog(transferTv.text.toString().removePrefix(accountSymbol), true)
            }
            refreshNotification(Session.getAccount()!!.transferNotificationThreshold)
            systemNotification.setOnClickListener {
                context?.openNotificationSetting()
            }

            largeAmountRl.setOnClickListener {
                showDialog(Session.getAccount()!!.transferConfirmationThreshold.toString(), false)
            }
            refreshLargeAmount(Session.getAccount()!!.transferConfirmationThreshold)

            lifecycleScope.launch {
                duplicateTransferSc.isChecked = PropertyHelper.findValueByKey(requireContext(), PREF_DUPLICATE_TRANSFER)?.toBoolean() ?: true
                strangerTransferSc.isChecked = PropertyHelper.findValueByKey(requireContext(), PREF_STRANGER_TRANSFER)?.toBoolean() ?: true
            }
            duplicateTransferSc.setOnCheckedChangeListener { _, isChecked ->
                updateKeyValue(PREF_DUPLICATE_TRANSFER, isChecked.toString())
            }

            strangerTransferSc.setOnCheckedChangeListener { _, isChecked ->
                updateKeyValue(PREF_STRANGER_TRANSFER, isChecked.toString())
            }

            supportsOreo {
                notificationReset.isVisible = true
                notificationReset.setOnClickListener {
                    ChannelManager.resetChannelSound(requireContext())
                    toast(R.string.successful)
                }
            }
        }
    }

    private fun updateKeyValue(key: String, value: String) = lifecycleScope.launch {
        PropertyHelper.updateKeyValue(requireContext(), key, value)
    }

    @SuppressLint("RestrictedApi")
    private fun showDialog(amount: String?, isNotification: Boolean) {
        if (context == null || !isAdded) {
            return
        }
        editDialog {
            titleText = this@NotificationsFragment.getString(
                if (isNotification) {
                    R.string.setting_notification_transfer_amount
                } else {
                    R.string.wallet_transaction_tip_title_with_symbol
                },
                accountSymbol
            )
            editText = amount
            editHint = this@NotificationsFragment.getString(
                if (isNotification) {
                    R.string.wallet_transfer_amount
                } else {
                    R.string.wallet_transaction_tip_title
                }
            )
            editInputType = InputType.TYPE_NUMBER_FLAG_DECIMAL + InputType.TYPE_CLASS_NUMBER
            allowEmpty = false
            rightAction = {
                savePreference(it.toDouble(), isNotification)
            }
        }
    }

    private fun savePreference(threshold: Double, isNotification: Boolean) = lifecycleScope.launch {
        val pb = indeterminateProgressDialog(
            message = R.string.pb_dialog_message,
            title = if (isNotification) R.string.setting_notification_transfer else R.string.wallet_transaction_tip_title
        ).apply {
            setCancelable(false)
        }
        pb.show()

        handleMixinResponse(
            invokeNetwork = {
                viewModel.preferences(
                    if (isNotification) {
                        AccountUpdateRequest(
                            fiatCurrency = Session.getFiatCurrency(),
                            transferNotificationThreshold = threshold
                        )
                    } else {
                        AccountUpdateRequest(
                            fiatCurrency = Session.getFiatCurrency(),
                            transferConfirmationThreshold = threshold
                        )
                    }
                )
            },
            successBlock = {
                it.data?.let { account ->
                    Session.storeAccount(account)
                    if (isNotification) {
                        refreshNotification(account.transferNotificationThreshold)
                    } else {
                        refreshLargeAmount(account.transferConfirmationThreshold)
                    }
                }
            },
            doAfterNetworkSuccess = {
                pb.dismiss()
            },
            exceptionBlock = {
                pb.dismiss()
                return@handleMixinResponse false
            }
        )
    }

    @SuppressLint("SetTextI18n")
    private fun refreshNotification(threshold: Double) {
        if (viewDestroyed()) return
        binding.apply {
            transferTv.text = "$accountSymbol$threshold"
            transferDescTv.text = getString(
                R.string.setting_notification_transfer_desc,
                "$accountSymbol$threshold"
            )
        }
    }

    @SuppressLint("SetTextI18n")
    private fun refreshLargeAmount(largeAmount: Double) {
        if (viewDestroyed()) return
        binding.apply {
            largeAmountTv.text = "$accountSymbol$largeAmount"
            largeAmountDescTv.text = getString(
                R.string.setting_transfer_large_summary,
                "$accountSymbol$largeAmount"
            )
        }
    }
}
