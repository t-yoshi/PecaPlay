package org.peercast.pecaplay.yp4g.net

import okhttp3.ResponseBody
import org.peercast.pecaplay.yp4g.Yp4gConfig
import retrofit2.Response
import retrofit2.http.*

interface Yp4gService {
    @GET("index.txt")
    suspend fun getIndex(
        @Query("host") host: String
    ): List<Yp4gChannelBinder>

    @GET("yp4g.xml")
    suspend fun getConfig(): Response<Yp4gConfig>

    @POST("{object}")
    suspend fun speedTest(
        @Path("object", encoded = true) obj: String,
        @Body data: RandomDataBody,
    ): ResponseBody
}