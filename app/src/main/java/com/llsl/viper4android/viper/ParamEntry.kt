package com.llsl.viper4android.viper

data class ParamEntry(
    val paramId: Int,
    val values: IntArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ParamEntry) return false
        return paramId == other.paramId && values.contentEquals(other.values)
    }

    override fun hashCode(): Int = 31 * paramId + values.contentHashCode()
}

data class ByteArrayParam(
    val paramId: Int,
    val data: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ByteArrayParam) return false
        return paramId == other.paramId && data.contentEquals(other.data)
    }

    override fun hashCode(): Int = 31 * paramId + data.contentHashCode()
}
