package io.github.kotlinmania.candleug

public sealed class Const : Comparable<Const> {
    public abstract val dtype: DType

    public data class I32(
        public val value: Int,
    ) : Const() {
        override val dtype: DType get() = DType.I32

        override fun compareTo(other: Const): Int =
            when (other) {
                is I32 -> value.compareTo(other.value)
                else -> dtype.compareTo(other.dtype)
            }

        override fun toString(): String = value.toString()
    }

    public data class I64(
        public val value: Long,
    ) : Const() {
        override val dtype: DType get() = DType.I64

        override fun compareTo(other: Const): Int =
            when (other) {
                is I64 -> value.compareTo(other.value)
                else -> dtype.compareTo(other.dtype)
            }

        override fun toString(): String = value.toString()
    }

    public data class F32(
        public val value: Float,
    ) : Const() {
        init {
            if (value.isNaN()) {
                throw CandleUgException("cannot use nan in const")
            }
        }

        override val dtype: DType get() = DType.F32

        override fun compareTo(other: Const): Int =
            when (other) {
                is F32 -> value.compareTo(other.value)
                else -> dtype.compareTo(other.dtype)
            }

        override fun toString(): String = value.toString()
    }

    public data class F16(
        public val bits: Short,
    ) : Const() {
        override val dtype: DType get() = DType.F16

        override fun compareTo(other: Const): Int =
            when (other) {
                is F16 -> bits.compareTo(other.bits)
                else -> dtype.compareTo(other.dtype)
            }

        override fun toString(): String = "f16($bits)"
    }

    public data class BF16(
        public val bits: Short,
    ) : Const() {
        override val dtype: DType get() = DType.BF16

        override fun compareTo(other: Const): Int =
            when (other) {
                is BF16 -> bits.compareTo(other.bits)
                else -> dtype.compareTo(other.dtype)
            }

        override fun toString(): String = "bf16($bits)"
    }

    public companion object {
        public fun from(v: Int): Const = I32(v)

        public fun from(v: Long): Const = I64(v)

        public fun from(v: Float): Const = F32(v)

        public fun zero(dtype: DType): Const =
            when (dtype) {
                DType.I32 -> I32(0)
                DType.I64 -> I64(0L)
                DType.F32 -> F32(0.0f)
                DType.F16 -> F16(0)
                DType.BF16 -> BF16(0)
            }

        public fun minValue(dtype: DType): Const =
            when (dtype) {
                DType.I32 -> I32(Int.MIN_VALUE)

                DType.I64 -> I64(Long.MIN_VALUE)

                DType.F32 -> F32(Float.NEGATIVE_INFINITY)

                DType.F16 -> F16(0xfc00.toShort())

                // -infinity
                DType.BF16 -> BF16(0xff80.toShort()) // -infinity
            }

        public fun maxValue(dtype: DType): Const =
            when (dtype) {
                DType.I32 -> I32(Int.MAX_VALUE)

                DType.I64 -> I64(Long.MAX_VALUE)

                DType.F32 -> F32(Float.POSITIVE_INFINITY)

                DType.F16 -> F16(0x7c00.toShort())

                // +infinity
                DType.BF16 -> BF16(0x7f80.toShort()) // +infinity
            }
    }
}
