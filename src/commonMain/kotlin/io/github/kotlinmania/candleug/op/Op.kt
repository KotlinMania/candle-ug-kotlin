package io.github.kotlinmania.candleug.op

import io.github.kotlinmania.candleug.CandleUgException
import io.github.kotlinmania.candleug.Const
import io.github.kotlinmania.candleug.DType
import io.github.kotlinmania.candleug.Layout
import io.github.kotlinmania.candleug.Shape

public enum class ReduceOp {
    Sum,
    Max,
    Min,
}

public sealed class UnaryOp : Comparable<UnaryOp> {
    public data object Id : UnaryOp()

    public data object Exp : UnaryOp()

    public data object Sin : UnaryOp()

    public data object Cos : UnaryOp()

    public data object Neg : UnaryOp()

    public data object Sqrt : UnaryOp()

    public data class Cast(
        public val dtype: DType,
    ) : UnaryOp()

    override fun compareTo(other: UnaryOp): Int = this.toString().compareTo(other.toString())
}

public enum class BinaryOp(
    public val asStr: String,
) {
    Add("add"),
    Sub("sub"),
    Mul("mul"),
    Div("div"),
    Mod("mod"),
    Min("min"),
    Max("max"),
}

public sealed class ArgType : Comparable<ArgType> {
    public data class Value(
        public val dtype: DType,
    ) : ArgType()

    public data class Ptr(
        public val dtype: DType,
    ) : ArgType()

    public fun sizeInBytes(): Int =
        when (this) {
            is Ptr -> 8
            is Value -> dtype.sizeInBytes
        }

    override fun compareTo(other: ArgType): Int = this.toString().compareTo(other.toString())
}

public data class ArgId(
    public val id: Int,
) : Comparable<ArgId> {
    override fun compareTo(other: ArgId): Int = id.compareTo(other.id)

    public companion object {
        private var counter: Int = 1

        public fun new(): ArgId {
            val v = counter
            counter += 1
            return ArgId(v)
        }

        public fun fromInt(v: Int): ArgId = ArgId(v)
    }
}

public data class Arg(
    public val id: ArgId,
    public val type: ArgType,
) : Comparable<Arg> {
    override fun compareTo(other: Arg): Int = id.compareTo(other.id)

    public companion object {
        public fun value(dtype: DType): Arg = Arg(ArgId.new(), ArgType.Value(dtype))

        public fun ptr(dtype: DType): Arg = Arg(ArgId.new(), ArgType.Ptr(dtype))
    }
}

public sealed class LayoutOp : Comparable<LayoutOp> {
    public data class Broadcast(
        public val insertedDims: Int,
        public val broadcastedDims: List<Int>,
    ) : LayoutOp()

    public data class Narrow(
        public val dim: Int,
        public val offset: Int,
    ) : LayoutOp()

    public data class Transpose(
        public val dim1: Int,
        public val dim2: Int,
    ) : LayoutOp()

    public data class SplitDim(
        public val dim: Int,
        public val lhs: Int,
        public val rhs: Int,
    ) : LayoutOp()

    public data class MergeDims(
        public val dim: Int,
        public val lhs: Int,
        public val rhs: Int,
    ) : LayoutOp()

    override fun compareTo(other: LayoutOp): Int = this.toString().compareTo(other.toString())
}

public sealed class AstInner {
    public data class Id(
        public val id: Int,
    ) : AstInner()

    public data class Load(
        public val src: ArgId,
        public val layout: Layout,
    ) : AstInner()

    public data class Reduce(
        public val op: ReduceOp,
        public val arg: Ast,
        public val dim: Int,
    ) : AstInner()

    public data class Unary(
        public val op: UnaryOp,
        public val arg: Ast,
    ) : AstInner()

    public data class Binary(
        public val op: BinaryOp,
        public val lhs: Ast,
        public val rhs: Ast,
    ) : AstInner()

    public data class Constant(
        public val value: Const,
    ) : AstInner()

    public data class LayoutOperation(
        public val op: LayoutOp,
        public val arg: Ast,
    ) : AstInner()
}

public data class Ast(
    public val inner: AstInner,
    public val dtype: DType,
    public val shape: Shape,
) : Comparable<Ast> {
    public fun argIds(): Set<ArgId> {
        val ids = mutableSetOf<ArgId>()

        fun visit(ast: Ast) {
            when (val in_ = ast.inner) {
                is AstInner.LayoutOperation -> {
                    visit(in_.arg)
                }

                is AstInner.Reduce -> {
                    visit(in_.arg)
                }

                is AstInner.Unary -> {
                    visit(in_.arg)
                }

                is AstInner.Binary -> {
                    visit(in_.lhs)
                    visit(in_.rhs)
                }

                is AstInner.Load -> {
                    ids.add(in_.src)
                }

                is AstInner.Id, is AstInner.Constant -> {}
            }
        }
        visit(this)
        return ids
    }

    override fun compareTo(other: Ast): Int {
        val dc = dtype.compareTo(other.dtype)
        if (dc != 0) return dc
        return shape.compareTo(other.shape)
    }
}

public data class Store(
    public val dst: ArgId,
    public val layout: Layout,
    public val value: Ast,
) {
    public val dtype: DType get() = value.dtype
}

public data class Kernel(
    public val name: String,
    public val args: List<Arg>,
    public val ops: List<Store>,
)

public fun load(src: ArgId, layout: Layout, dtype: DType): Ast {
    val shape = layout.shape
    val inner = AstInner.Load(src, layout)
    return Ast(inner, dtype, shape)
}

public fun broadcast(arg: Ast, shape: Shape): Ast {
    val dtype = arg.dtype
    if (arg.shape.rank() > shape.rank()) {
        throw CandleUgException("target shape $shape has less dimensions than original shape ${arg.shape}")
    }
    val insertedDims = shape.rank() - arg.shape.rank()
    val broadcastedDims = (0 until insertedDims).toMutableList()
    for ((dimIdx, dimLen) in arg.shape.dims.withIndex()) {
        val actualIdx = dimIdx + insertedDims
        if (shape.dims[actualIdx] != dimLen) {
            if (dimLen == 1) {
                broadcastedDims.add(actualIdx)
            } else {
                throw CandleUgException("cannot broadcast from ${arg.shape} to $shape")
            }
        }
    }
    val op = LayoutOp.Broadcast(insertedDims, broadcastedDims)
    val inner = AstInner.LayoutOperation(op, arg)
    return Ast(inner, dtype, shape)
}

public fun layout(op: LayoutOp, arg: Ast, shape: Shape): Ast {
    val dtype = arg.dtype
    val inner = AstInner.LayoutOperation(op, arg)
    return Ast(inner, dtype, shape)
}

public fun unary(op: UnaryOp, arg: Ast): Ast {
    val dtype =
        when (op) {
            is UnaryOp.Cast -> op.dtype
            else -> arg.dtype
        }
    val shape = arg.shape
    val inner = AstInner.Unary(op, arg)
    return Ast(inner, dtype, shape)
}

public fun reduce(op: ReduceOp, arg: Ast, dim: Int): Ast {
    val dtype = arg.dtype
    val shapeDims = arg.shape.dims.toMutableList()
    if (dim >= shapeDims.size) {
        throw CandleUgException("no dim $dim in shape ${arg.shape}")
    }
    shapeDims[dim] = 1
    val inner = AstInner.Reduce(op, arg, dim)
    return Ast(inner, dtype, Shape(shapeDims))
}

public fun binary(op: BinaryOp, lhs: Ast, rhs: Ast): Ast {
    val dtype =
        if (lhs.dtype != rhs.dtype) {
            throw CandleUgException("dtype mismatch in $op, lhs: ${lhs.dtype}, rhs: ${rhs.dtype}")
        } else {
            lhs.dtype
        }
    if (lhs.shape != rhs.shape && lhs.shape.rank() != 0 && rhs.shape.rank() != 0) {
        throw CandleUgException("shape mismatch in $op, lhs: ${lhs.shape}, rhs: ${rhs.shape}")
    }
    val shape = lhs.shape
    val inner = AstInner.Binary(op, lhs, rhs)
    return Ast(inner, dtype, shape)
}

public fun broadcastBinary(op: BinaryOp, lhs: Ast, rhs: Ast): Ast {
    val shape = lhs.shape.broadcastShapeBinaryOp(rhs.shape, op.asStr)
    val l = broadcast(lhs, shape)
    val r = broadcast(rhs, shape)
    return binary(op, l, r)
}

public fun cst(v: Const): Ast {
    val dtype = v.dtype
    val inner = AstInner.Constant(v)
    return Ast(inner, dtype, Shape.SCALAR)
}

public fun cst(v: Int): Ast = cst(Const.I32(v))

public fun cst(v: Long): Ast = cst(Const.I64(v))

public fun cst(v: Float): Ast = cst(Const.F32(v))

public fun store(dst: ArgId, layout: Layout, value: Ast): Store = Store(dst, layout, value)
