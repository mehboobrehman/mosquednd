package com.mosquednd.util

/**
 * Formats an offset value in minutes to a display string.
 * Positive values get a "+" prefix (e.g. "+15 min"),
 * negative values get a "-" prefix (e.g. "-10 min"),
 * zero is shown as "0 min".
 */
fun formatOffset(offset: Int): String {
    return if (offset > 0) "+${offset} min"
    else if (offset < 0) "$offset min"
    else "0 min"
}
