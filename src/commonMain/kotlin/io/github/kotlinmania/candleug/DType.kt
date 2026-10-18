package io.github.kotlinmania.candleug

public enum class DType(
    public val asStr: String,
    public val sizeInBytes: Int,
) {
    BF16("bf16", 2),
    F16("f16", 2),
    F32("f32", 4),
    I32("i32", 4),
    I64("i64", 8),
    ;

    public fun isInt(): Boolean =
        when (this) {
            I32, I64 -> true
            BF16, F16, F32 -> false
        }

    public fun isFloat(): Boolean =
        when (this) {
            I32, I64 -> false
            BF16, F16, F32 -> true
        }

    public companion object {
        public fun fromStr(s: String): DType =
            when (s) {
                "i32" -> I32
                "i64" -> I64
                "bf16" -> BF16
                "f16" -> F16
                "f32" -> F32
                else -> throw CandleUgException("cannot parse '$s' as a dtype")
            }
    }
}
