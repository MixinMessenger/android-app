package one.mixin.android.api.service

import one.mixin.android.vo.github.Latest
import retrofit2.http.GET

interface GithubService {
    @GET("releases/latest")
    suspend fun latest(): Latest
}
