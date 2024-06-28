package ru.cian.rustore.publish.service

import com.google.gson.Gson
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import ru.cian.rustore.publish.PublishType
import ru.cian.rustore.publish.models.request.AccessTokenRustoreRequest
import ru.cian.rustore.publish.models.request.AppDraftRequest
import ru.cian.rustore.publish.models.response.AccessTokenResponse
import ru.cian.rustore.publish.models.response.AppDraftResponse
import ru.cian.rustore.publish.models.response.DeleteAppDraftResponse
import ru.cian.rustore.publish.models.response.SubmitPublicationResponse
import ru.cian.rustore.publish.models.response.UploadAppFileResponse
import ru.cian.rustore.publish.utils.Logger
import ru.cian.rustore.publish.utils.ONE_DAY_IN_MILLIS
import ru.cian.rustore.publish.utils.ONE_HOUR_IN_MILLIS
import ru.cian.rustore.publish.utils.SIMPLE_DATE_TIME_WITH_TIMEZONE
import java.io.File
import java.util.Calendar

private const val DOMAIN_URL = "https://public-api.rustore.ru"

@SuppressWarnings("StringLiteralDuplication", "TooManyFunctions")
internal class RustoreServiceImpl constructor(
    private val logger: Logger
) : RustoreService {

    private val gson = Gson()
    private val httpClient = HttpClientHelper(logger)

    override fun getToken(
        keyId: String,
        timestamp: String,
        signature: String,
    ): String {

        val bodyRequest = AccessTokenRustoreRequest(
            keyId = keyId,
            timestamp = timestamp,
            signature = signature,
        )

        logger.i("""
            curl --location --request POST \
            $DOMAIN_URL/public/auth/ \
            --header 'Content-Type: application/json' \
            --data-raw '{
                "keyId": "$keyId",
                "timestamp": "$timestamp",
                "signature": "$signature"
            }'            
        """.trimIndent())

        val response = httpClient.post<AccessTokenResponse>(
            url = "$DOMAIN_URL/public/auth/",
            body = gson.toJson(bodyRequest).toRequestBody(),
            headers = mapOf(
                "Content-Type" to "application/json",
            ),
        )
        return response.body.jwe
    }

    override fun createDraft(
        token: String,
        applicationId: String,
        publishType: PublishType?,
        releaseTime: String?,
        whatsNew: String?,
    ): Int {
        fun getCurrentTimePlusOneDay(): String {
            val result = Calendar.getInstance().apply {
                timeInMillis += ONE_DAY_IN_MILLIS + ONE_HOUR_IN_MILLIS
            }
            return SIMPLE_DATE_TIME_WITH_TIMEZONE.format(result.time)
        }

        val publishDate = try {
            if (publishType == PublishType.DELAYED) {
                if (releaseTime != null) {
                    SIMPLE_DATE_TIME_WITH_TIMEZONE.parse(releaseTime)?.let { safeDate ->
                        if (Calendar.getInstance().timeInMillis - safeDate.time <= ONE_DAY_IN_MILLIS) {
                            getCurrentTimePlusOneDay()
                        } else {
                            releaseTime
                        }
                    }
                } else {
                    getCurrentTimePlusOneDay()
                }
            } else {
                null
            }
        } catch (ex: Exception) {
            logger.v("Exception occurred: ${ex.message ?: "Empty"}")
            null
        }

        val type = if (publishDate != null) PublishType.DELAYED else PublishType.MANUAL

        val bodyRequest = AppDraftRequest(
            whatsNew = whatsNew,
            publishType = type.name,
            publishDateTime = publishDate,
        )

        logger.i("""
            curl --location --request POST \
            $DOMAIN_URL/public/v1/application/$applicationId/version \
            --header 'Content-Type: application/json' \
            --header 'Public-Token: $token' \
            --data-raw '{
                "whatsNew": "$whatsNew"
            }'            
        """.trimIndent())

        val response = httpClient.post<AppDraftResponse>(
            url = "$DOMAIN_URL/public/v1/application/$applicationId/version",
            body = gson.toJson(bodyRequest).toRequestBody(),
            headers = mapOf(
                "Content-Type" to "application/json",
                "Public-Token" to token,
            ),
        )

        if (response.code == "ERROR" && response.message != null) {

            val searchString = "ID ="
            val indexOf = response.message.indexOf(searchString)

            check(indexOf > 0) {
                "Can't detect previous app versionId. " +
                    "Server response message must contain '$searchString'"
            }

            val previousAppId = response.message.substring(indexOf + searchString.length + 1)
            logger.v("previousAppId='$previousAppId'")
            val deletePreviousVersionIdResult = deletePreviousDraft(
                token = token,
                packageName = applicationId,
                previousAppId = previousAppId,
            )

            check(deletePreviousVersionIdResult) {
                "Can't remove previous app versionId on server side."
            }

            return createDraft(
                token = token,
                applicationId = applicationId,
                publishType = publishType,
                releaseTime = releaseTime,
                whatsNew = whatsNew,
            )
        }

        logger.v("response=$response")
        return response.body
    }

    override fun uploadBuildFile(
        token: String,
        applicationId: String,
        mobileServicesType: String,
        versionId: Int,
        buildFile: File
    ) {

        val fileBody = buildFile.asRequestBody(HttpClientHelper.MEDIA_TYPE_AAB)
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", buildFile.name, fileBody)
            .addFormDataPart("servicesType", mobileServicesType)
            .addFormDataPart("isMainApk", "true")
            .build()

        val headers = mutableMapOf(
            "accept" to "application/json",
            "Public-Token" to token,
        )

        logger.i("""
            curl --location --request POST \
            --header 'Content-Type: application/json' \
            --header 'Public-Token: $token' \            
            --form servicesType=$mobileServicesType \
            --form isMainApk=true \
            --form file='@${buildFile.absolutePath}' \
            $DOMAIN_URL/public/v1/application/$applicationId/version/$versionId/apk
        """.trimIndent())

        val response = httpClient.post<UploadAppFileResponse>(
            url = "$DOMAIN_URL/public/v1/application/$applicationId/version/$versionId/apk",
            body = requestBody,
            headers = headers
        )

        logger.v("response=$response")

        check(response.code == "OK") {
            "Build file uploading is failed! " +
                "Reason code: ${response.code}, " +
                "message: ${response.message}"
        }
    }

    override fun uploadAabBuildFile(token: String, applicationId: String, versionId: Int, buildFile: File) {
        val fileBody = buildFile.asRequestBody(HttpClientHelper.MEDIA_TYPE_AAB)
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", buildFile.name, fileBody)
            .build()

        val headers = mutableMapOf(
            "accept" to "application/json",
            "Public-Token" to token,
        )
        logger.i("""
            curl --location --request POST \
            --header 'Content-Type: application/json' \
            --header 'Public-Token: $token' \            
            --form file='@${buildFile.absolutePath}' \
            $DOMAIN_URL/public/v1/application/$applicationId/version/$versionId/aab
        """.trimIndent())

        val response = httpClient.post<UploadAppFileResponse>(
            url = "$DOMAIN_URL/public/v1/application/$applicationId/version/$versionId/aab",
            body = requestBody,
            headers = headers,
        )

        logger.v("response=$response")
        check(response.code == "OK") {
            "Build file uploading is failed! " +
                    "Reason code: ${response.code}, " +
                    "message: ${response.message}"
        }
    }

    override fun submit(
        token: String,
        applicationId: String,
        versionId: Int,
        priorityUpdate: Int
    ): Boolean {
        logger.i("""
            curl --location --request POST \
            $DOMAIN_URL/public/v1/application/$applicationId/version/$versionId/commit?priorityUpdate=$priorityUpdate \
            --header 'Content-Type: application/json'
        """.trimIndent())

        val response = httpClient.post<SubmitPublicationResponse>(
            url = "$DOMAIN_URL/public/v1/application/$applicationId/version/$versionId/commit" +
                "?priorityUpdate=$priorityUpdate",
            body = "".toRequestBody(),
            headers = mapOf(
                "Content-Type" to "application/json",
                "Public-Token" to token,
            ),
        )
        return response.code == "OK"
    }

    private fun deletePreviousDraft(
        token: String,
        packageName: String,
        previousAppId: String,
    ): Boolean {

        logger.i("""
            curl --location --request DELETE $DOMAIN_URL/public/v1/application/$packageName/version/$previousAppId \
            --header 'Content-Type: application/json' \
            --header 'Public-Token: $token'            
        """.trimIndent())

        val response = httpClient.delete<DeleteAppDraftResponse>(
            url = "$DOMAIN_URL/public/v1/application/$packageName/version/$previousAppId",
            body = "".toRequestBody(),
            headers = mapOf(
                "Content-Type" to "application/json",
                "Public-Token" to token,
            ),
        )

        logger.v("response=$response")

        return response.code == "OK"
    }
}
