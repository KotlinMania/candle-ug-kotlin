package io.github.kotlinmania.candleug.ssa

import io.github.kotlinmania.candleug.Const
import io.github.kotlinmania.candleug.DType
import io.github.kotlinmania.candleug.op.Arg
import io.github.kotlinmania.candleug.op.BinaryOp
import io.github.kotlinmania.candleug.op.ReduceOp
import io.github.kotlinmania.candleug.op.UnaryOp

public data class VarId(
    public val id: Int,
) : Comparable<VarId> {
    override fun compareTo(other: VarId): Int = id.compareTo(other.id)

    public companion object {
        public val NULL: VarId = VarId(Int.MAX_VALUE)
    }
}

public enum class Special {
    ThreadIdx,
    BlockIdx,
}

public sealed class A : Comparable<A> {
    public data class Var(
        public val id: VarId,
    ) : A()

    public data class Constant(
        public val value: Const,
    ) : A()

    override fun compareTo(other: A): Int = this.toString().compareTo(other.toString())

    public companion object {
        public fun from(v: VarId): A = Var(v)

        public fun from(v: Int): A = Constant(Const.I32(v))

        public fun from(v: Long): A = Constant(Const.I64(v))

        public fun from(v: Float): A = Constant(Const.F32(v))

        public fun from(c: Const): A = Constant(c)
    }
}

public data class LaunchConfig(
    public val gridDim: Int,
    public val blockDim: Int,
    public val sharedMem: Int = 0,
) {
    public companion object {
        public fun forNumElems(n: Int): LaunchConfig {
            val gridDim = (n + 31) / 32
            return LaunchConfig(gridDim = gridDim, blockDim = 32, sharedMem = 0)
        }
    }
}

public sealed class Instr {
    public data class DefineAcc(
        public val value: Const,
    ) : Instr()

    public data class DefineGlobal(
        public val index: Int,
        public val dtype: DType,
    ) : Instr()

    public data class DefineLocal(
        public val size: Int,
        public val dtype: DType,
    ) : Instr()

    public data class SpecialInstr(
        public val special: Special,
    ) : Instr()

    public data class Constant(
        public val value: Const,
    ) : Instr()

    public data class Unary(
        public val op: UnaryOp,
        public val arg: A,
        public val dtype: DType,
    ) : Instr()

    public data class Binary(
        public val op: BinaryOp,
        public val lhs: A,
        public val rhs: A,
        public val dtype: DType,
    ) : Instr()

    public data class Range(
        public val lo: A,
        public val up: A,
        public val step: Int,
        public val endIdx: VarId,
    ) : Instr()

    public data class Load(
        public val src: VarId,
        public val offset: A,
        public val dtype: DType,
    ) : Instr()

    public data class Assign(
        public val dst: VarId,
        public val src: A,
    ) : Instr()

    public data class EndRange(
        public val startIdx: VarId,
    ) : Instr()

    public data class If(
        public val cond: A,
        public val endIdx: VarId,
    ) : Instr()

    public data object EndIf : Instr()

    public data class Store(
        public val dst: VarId,
        public val offset: A,
        public val value: A,
        public val dtype: DType,
    ) : Instr()

    public data object Barrier : Instr()

    public data class ReduceLocal(
        public val op: ReduceOp,
        public val arg: A,
        public val dtype: DType,
    ) : Instr()
}

public data class KernelArg(
    public val arg: Arg,
    public val index: Int,
)

public data class Kernel(
    public val instrs: List<Instr>,
    public val args: List<KernelArg>,
    public val launchConfig: LaunchConfig,
)
