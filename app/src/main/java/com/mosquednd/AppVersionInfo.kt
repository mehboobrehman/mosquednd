package com.mosquednd

import java.io.Serializable

data class AppVersionInfo(
    val versionName: String,
    val versionCode: Int,
    val gitCommitHash: String,
    val buildTimestamp: String,
    val environment: String
) : Serializable {
    companion object {
        fun getCurrent(): AppVersionInfo {
            return AppVersionInfo(
                versionName = try { BuildConfig.APP_VERSION } catch (e: Exception) { "1.0.0" },
                versionCode = try { BuildConfig.VERSION_CODE } catch (e: Exception) { 1 },
                gitCommitHash = try { BuildConfig.GIT_COMMIT_HASH } catch (e: Exception) { "unknown" },
                buildTimestamp = try { BuildConfig.BUILD_TIMESTAMP } catch (e: Exception) { "unknown" },
                environment = try { BuildConfig.ENVIRONMENT } catch (e: Exception) { "debug" }
            )
        }
    }
}
