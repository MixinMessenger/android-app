package one.mixin.android.ui.group

import android.arch.lifecycle.ViewModel
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import one.mixin.android.api.MixinResponse
import one.mixin.android.api.request.ConversationRequest
import one.mixin.android.api.request.ParticipantRequest
import one.mixin.android.api.response.ConversationResponse
import one.mixin.android.extension.nowInUtc
import one.mixin.android.job.ConversationJob
import one.mixin.android.job.ConversationJob.Companion.TYPE_CREATE
import one.mixin.android.job.ConversationJob.Companion.TYPE_EXIT
import one.mixin.android.job.ConversationJob.Companion.TYPE_MAKE_ADMIN
import one.mixin.android.job.MixinJobManager
import one.mixin.android.repository.ConversationRepository
import one.mixin.android.repository.UserRepository
import one.mixin.android.vo.Conversation
import one.mixin.android.vo.ConversationBuilder
import one.mixin.android.vo.ConversationCategory
import one.mixin.android.vo.Participant
import one.mixin.android.vo.User
import java.util.UUID
import javax.inject.Inject

class GroupViewModel @Inject
internal constructor(
    private val userRepository: UserRepository,
    private val conversationRepository: ConversationRepository,
    private val jobManager: MixinJobManager
) : ViewModel() {

    fun getFriends() = userRepository.findFriends()

    fun createGroupConversation(
        groupName: String,
        announcement: String,
        icon: String?,
        users: List<User>,
        sender: User
    ): Conversation {
        val conversationId = UUID.randomUUID().toString()
        val createdAt = nowInUtc()
        val conversation = ConversationBuilder(conversationId, createdAt, 0)
            .setCategory(ConversationCategory.GROUP.name)
            .setName(groupName)
            .setAnnouncement(announcement)
            .setOwnerId(sender.userId)
            .setUnseenMessageCount(0)
            .build()
        val mutableList = mutableListOf<Participant>()
        users.mapTo(mutableList) { Participant(conversationId, it.userId, "", createdAt) }
        conversationRepository.insertConversation(conversation, mutableList)

        val participantRequestList = mutableListOf<ParticipantRequest>()
        mutableList.mapTo(participantRequestList) { ParticipantRequest(it.userId, it.role) }
        val request = ConversationRequest(conversationId, ConversationCategory.GROUP.name,
            groupName, icon, announcement, participantRequestList)
        jobManager.addJobInBackground(ConversationJob(request, type = TYPE_CREATE))

        return conversation
    }

    fun getConversationStatusById(id: String) = conversationRepository.getConversationById(id)

    /**
     * @param type only support 2 types
     * @see ConversationJob.TYPE_ADD
     * @see ConversationJob.TYPE_REMOVE
     */
    fun modifyGroupMembers(conversationId: String, users: List<User>, type: Int) {
        startGroupJob(conversationId, users, type)
    }

    fun getGroupParticipantsLiveData(conversationId: String) =
        conversationRepository.getGroupParticipantsLiveData(conversationId)

    fun getConversationById(conversationId: String) =
        conversationRepository.getConversationById(conversationId)

    fun findSelf() = userRepository.findSelf()

    fun updateGroup(conversationId: String, announcement: String): Observable<MixinResponse<ConversationResponse>> {
        val request = ConversationRequest(conversationId, name = null,
            iconBase64 = null, announcement = announcement)
        return conversationRepository.updateAsync(conversationId, request)
            .subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
    }

    fun makeAdmin(conversationId: String, user: User) {
        startGroupJob(conversationId, listOf(user), TYPE_MAKE_ADMIN, "ADMIN")
    }

    private fun startGroupJob(conversationId: String, users: List<User>, type: Int, role: String = "") {
        val participantRequests = mutableListOf<ParticipantRequest>()
        users.mapTo(participantRequests) {
            ParticipantRequest(it.userId, role)
        }
        jobManager.addJobInBackground(ConversationJob(conversationId = conversationId,
            participantRequests = participantRequests, type = type))
    }

    fun getRealParticipants(conversationId: String) = conversationRepository.getRealParticipants(conversationId)

    fun exitGroup(conversationId: String) {
        jobManager.addJobInBackground(ConversationJob(conversationId = conversationId, type = TYPE_EXIT))
    }

    fun deleteMessageByConversationId(conversationId: String) {
        conversationRepository.deleteMessageByConversationId(conversationId)
    }

    fun mute(conversationId: String, duration: Long) {
        jobManager.addJobInBackground(ConversationJob(conversationId = conversationId,
            request = ConversationRequest(conversationId, ConversationCategory.GROUP.name, duration = duration),
            type = ConversationJob.TYPE_MUTE))
    }

    fun updateAnnouncement(conversationId: String, announcement: String?) {
        announcement?.let {
            conversationRepository.updateAnnouncement(conversationId, announcement)
        }
    }
}