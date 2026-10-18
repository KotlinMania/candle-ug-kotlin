package io.github.kotlinmania.candleug

public class Layout(
    public val shape: Shape,
    public val strides: List<Int>,
    public var offset: Int = 0,
) : Comparable<Layout> {
    public fun canBeCompressed(): Boolean {
        val dims = shape.dims
        if (dims.size <= 1) return false
        for (i in 0 until dims.size - 1) {
            if (strides[i] != strides[i + 1] * dims[i + 1]) {
                return false
            }
        }
        return true
    }

    public fun compressAll(): Layout {
        val dims = shape.dims
        for (i in 0 until dims.size - 1) {
            if (strides[i] != strides[i + 1] * dims[i + 1]) {
                throw CandleUgException("cannot collapse dims, $this")
            }
        }
        val stride = strides.lastOrNull() ?: 1
        val dim = numElements()
        return Layout(Shape(listOf(dim)), listOf(stride), offset)
    }

    public fun transpose(): Layout {
        val r = rank()
        if (r < 2) return Layout(shape, strides, offset)
        val dims = shape.dims.toMutableList()
        val str = strides.toMutableList()
        val tempD = dims[r - 2]
        dims[r - 2] = dims[r - 1]
        dims[r - 1] = tempD
        val tempS = str[r - 2]
        str[r - 2] = str[r - 1]
        str[r - 1] = tempS
        return Layout(Shape(dims), str, offset)
    }

    public fun numElements(): Int = shape.numElements()

    public val dims: List<Int> get() = shape.dims

    public fun rank(): Int = shape.rank()

    public fun cContiguous(): Boolean {
        var prodL = 1
        val dims = shape.dims
        for (i in dims.indices.reversed()) {
            if (strides[i] != prodL) {
                return false
            }
            prodL *= dims[i]
        }
        return true
    }

    override fun compareTo(other: Layout): Int {
        val sc = shape.compareTo(other.shape)
        if (sc != 0) return sc
        val oc = offset.compareTo(other.offset)
        if (oc != 0) return oc
        val strC = strides.size.compareTo(other.strides.size)
        if (strC != 0) return strC
        for (i in strides.indices) {
            val c = strides[i].compareTo(other.strides[i])
            if (c != 0) return c
        }
        return 0
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Layout) return false
        return shape == other.shape && strides == other.strides && offset == other.offset
    }

    override fun hashCode(): Int {
        var result = shape.hashCode()
        result = 31 * result + strides.hashCode()
        result = 31 * result + offset
        return result
    }

    override fun toString(): String = "Layout(shape=$shape, strides=$strides, offset=$offset)"

    public companion object {
        public fun fromShape(shape: Shape): Layout {
            val strides = ArrayList<Int>(shape.rank())
            var stride = 1
            for (l in shape.dims.reversed()) {
                strides.add(stride)
                stride *= l
            }
            strides.reverse()
            return Layout(shape, strides, 0)
        }

        public fun fromShape(vararg dims: Int): Layout = fromShape(Shape(dims.toList()))
    }
}
