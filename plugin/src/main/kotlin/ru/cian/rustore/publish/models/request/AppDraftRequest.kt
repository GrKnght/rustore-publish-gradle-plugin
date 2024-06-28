package ru.cian.rustore.publish.models.request

import com.google.gson.annotations.SerializedName

internal data class AppDraftRequest(
    @SerializedName("whatsNew")
    val whatsNew: String? = null,
    @SerializedName("publishType")
    val publishType: String,
    @SerializedName("publishDateTime")
    val publishDateTime: String?,
)
