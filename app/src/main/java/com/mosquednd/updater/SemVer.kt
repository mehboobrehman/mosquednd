package com.mosquednd.updater

data class SemVer(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val preRelease: String? = null
) : Comparable<SemVer> {

    override fun compareTo(other: SemVer): Int {
        if (this.major != other.major) return this.major.compareTo(other.major)
        if (this.minor != other.minor) return this.minor.compareTo(other.minor)
        if (this.patch != other.patch) return this.patch.compareTo(other.patch)

        // Pre-release handling: a version with pre-release is lower than without
        if (this.preRelease == null && other.preRelease != null) return 1
        if (this.preRelease != null && other.preRelease == null) return -1
        if (this.preRelease != null && other.preRelease != null) {
            return this.preRelease.compareTo(other.preRelease)
        }
        return 0
    }

    companion object {
        fun parse(versionStr: String?): SemVer? {
            if (versionStr.isNullOrBlank()) return null
            val cleaned = versionStr.trim().removePrefix("v").removePrefix("V")
            val parts = cleaned.split("-", limit = 2)
            val versionNumbers = parts[0].split(".")
            if (versionNumbers.isEmpty()) return null

            try {
                val major = versionNumbers[0].toInt()
                val minor = if (versionNumbers.size > 1) versionNumbers[1].toInt() else 0
                val patch = if (versionNumbers.size > 2) versionNumbers[2].toInt() else 0
                val preRelease = if (parts.size > 1) parts[1] else null
                return SemVer(major, minor, patch, preRelease)
            } catch (e: NumberFormatException) {
                return null
            }
        }
    }
}
