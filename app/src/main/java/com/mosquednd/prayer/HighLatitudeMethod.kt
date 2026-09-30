package com.mosquednd.prayer

enum class HighLatitudeMethod(val displayName: String) {
    NONE("None"),
    NEAREST_LATITUDE("Nearest Good Day"),
    ANGLE_BASED("Angle-Based"),
    MIDDLE_OF_NIGHT("Middle of the Night")
}
