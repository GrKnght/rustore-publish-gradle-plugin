package ru.cian.rustore.publish.service

import ru.cian.rustore.publish.PublishType
import java.io.File

/**
 * See documentation:
 * https://help.rustore.ru/rustore/for_developers/work_with_RuStore_API/publish_RuStore_API
 */
internal interface RustoreService {

    fun getToken(
        keyId: String,
        timestamp: String,
        signature: String,
    ): String

    fun createDraft(
        token: String,
        applicationId: String,
        publishType: PublishType?,
        releaseTime: String?,
        whatsNew: String?,
    ): Int

    @Suppress("LongParameterList")
    fun uploadBuildFile(
        token: String,
        applicationId: String,
        mobileServicesType: String,
        versionId: Int,
        buildFile: File,
    )

    fun uploadAabBuildFile(
        token: String,
        applicationId: String,
        versionId: Int,
        buildFile: File,
    )

    fun submit(
        token: String,
        applicationId: String,
        versionId: Int,
        priorityUpdate: Int,
    ): Boolean
}
