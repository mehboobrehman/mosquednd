package com.mosquednd.hijri

/**
 * Represents a Hijri (Islamic) date.
 *
 * @property year  Hijri year (e.g., 1445)
 * @property month Hijri month (1-12)
 * @property day   Hijri day (1-30)
 */
data class HijriDate(
    val year: Int,
    val month: Int,
    val day: Int
) {
    companion object {
        /**
         * Hijri epoch JDN (July 16, 622 CE).
         */
        const val HIJRI_EPOCH_JD = 1948439.5
    }

    /**
     * Checks if this date is valid.
     *
     * @return true if the date is valid
     */
    fun isValid(): Boolean {
        return year >= 1 && month in 1..12 && day in 1..30
    }

    override fun toString(): String {
        return "$day/$month/$year"
    }
}