package one.mixin.android.di.module

import dagger.Module
import dagger.android.ContributesAndroidInjector
import one.mixin.android.ui.auth.AuthBottomSheetDialogFragment
import one.mixin.android.ui.common.QrScanBottomSheetDialogFragment
import one.mixin.android.ui.contacts.ProfileFragment
import one.mixin.android.ui.conversation.ConversationFragment
import one.mixin.android.ui.conversation.FriendsFragment
import one.mixin.android.ui.conversation.StickerAddFragment
import one.mixin.android.ui.conversation.StickerAlbumFragment
import one.mixin.android.ui.conversation.StickerFragment
import one.mixin.android.ui.conversation.StickerManagementFragment
import one.mixin.android.ui.conversation.TransferFragment
import one.mixin.android.ui.group.GroupEditFragment
import one.mixin.android.ui.group.GroupInfoFragment
import one.mixin.android.ui.wallet.TransactionFragment
import one.mixin.android.ui.wallet.WalletPasswordFragment

@Module
abstract class ConversationActivityModule {
    @ContributesAndroidInjector
    internal abstract fun contributeConversationFragment(): ConversationFragment

    @ContributesAndroidInjector
    internal abstract fun contributeTransferFragment(): TransferFragment

    @ContributesAndroidInjector
    internal abstract fun contributeGroupInfoFragment(): GroupInfoFragment

    @ContributesAndroidInjector
    internal abstract fun contributeAuthBottomSheetDialogFragment(): AuthBottomSheetDialogFragment

    @ContributesAndroidInjector
    internal abstract fun contributeWalletPasswordFragment(): WalletPasswordFragment

    @ContributesAndroidInjector
    internal abstract fun contributeStickerAlbumFragment(): StickerAlbumFragment

    @ContributesAndroidInjector
    internal abstract fun contributeStickerFragment(): StickerFragment

    @ContributesAndroidInjector
    internal abstract fun contributeWalletTransactionFragment(): TransactionFragment

    @ContributesAndroidInjector
    internal abstract fun contributeProfileFragment(): ProfileFragment

    @ContributesAndroidInjector
    internal abstract fun contributeGroupEditFragment(): GroupEditFragment

    @ContributesAndroidInjector
    internal abstract fun contributeQrBottomSheetDialogFragment(): QrScanBottomSheetDialogFragment

    @ContributesAndroidInjector
    internal abstract fun contributeFriendsFragment(): FriendsFragment

    @ContributesAndroidInjector
    internal abstract fun contributeStickerManagementFragment(): StickerManagementFragment

    @ContributesAndroidInjector
    internal abstract fun contributeStickerAddFragment(): StickerAddFragment
}