package io.github.kotlinmania.candleug

public class Shape(
    dims: List<Int>,
) : Comparable<Shape> {
    public val dims: List<Int> = dims.toList()

    public constructor(vararg dims: Int) : this(dims.toList())

    public fun numElements(): Int = if (dims.isEmpty()) 1 else dims.fold(1) { acc, d -> acc * d }

    public fun elemCount(): Int = numElements()

    public fun rank(): Int = dims.size

    public fun intoDims(): List<Int> = dims

    public fun strideContiguous(): List<Int> {
        if (dims.isEmpty()) return emptyList()
        val result = ArrayList<Int>(dims.size)
        var acc = 1
        for (i in dims.indices.reversed()) {
            result.add(acc)
            acc *= dims[i]
        }
        result.reverse()
        return result
    }

    public fun isContiguous(stride: List<Int>): Boolean {
        if (dims.size != stride.size) return false
        var acc = 1
        for (i in dims.indices.reversed()) {
            val dim = dims[i]
            val s = stride[i]
            if (dim > 1 && s != acc) return false
            acc *= dim
        }
        return true
    }

    public fun isFortranContiguous(stride: List<Int>): Boolean {
        if (dims.size != stride.size) return false
        var acc = 1
        for (i in dims.indices) {
            val dim = dims[i]
            val s = stride[i]
            if (dim > 1 && s != acc) return false
            acc *= dim
        }
        return true
    }

    public fun extend(additionalDims: List<Int>): Shape = Shape(dims + additionalDims)

    public fun broadcastShapeBinaryOp(rhs: Shape, op: String): Shape {
        val lhsDims = dims
        val rhsDims = rhs.dims
        val bcastNdims = maxOf(lhsDims.size, rhsDims.size)
        val bcastDims = IntArray(bcastNdims)
        for (idx in 0 until bcastNdims) {
            val revIdx = bcastNdims - idx
            val lValue = if (lhsDims.size < revIdx) 1 else lhsDims[lhsDims.size - revIdx]
            val rValue = if (rhsDims.size < revIdx) 1 else rhsDims[rhsDims.size - revIdx]
            bcastDims[idx] =
                when {
                    lValue == rValue -> lValue
                    lValue == 1 -> rValue
                    rValue == 1 -> lValue
                    else -> throw CandleUgException("shape mismatch in binary op '$op', lhs: $this rhs: $rhs")
                }
        }
        return Shape(bcastDims.toList())
    }

    override fun compareTo(other: Shape): Int {
        val r = dims.size.compareTo(other.dims.size)
        if (r != 0) return r
        for (i in dims.indices) {
            val c = dims[i].compareTo(other.dims[i])
            if (c != 0) return c
        }
        return 0
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Shape) return false
        return dims == other.dims
    }

    override fun hashCode(): Int = dims.hashCode()

    override fun toString(): String = dims.toString()

    public companion object {
        public val SCALAR: Shape = Shape(emptyList())

        public fun fromDims(dims: List<Int>): Shape = Shape(dims)

        public fun fromDims(vararg dims: Int): Shape = Shape(dims.toList())
    }
}
