package one.mixin.android.api.service

import one.mixin.android.api.MixinResponse
import one.mixin.android.api.request.AddressRequest
import one.mixin.android.api.request.Pin
import one.mixin.android.vo.Address
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface AddressService {

    @POST("addresses")
    fun addresses(@Body request: AddressRequest): Call<MixinResponse<Address>>

    @POST("addresses/{id}/delete")
    fun delete(@Path("id") id: String, @Body pin: Pin): Call<MixinResponse<Unit>>

    @GET("addresses/{id}")
    fun address(@Path("id") id: String): Call<MixinResponse<Address>>
}