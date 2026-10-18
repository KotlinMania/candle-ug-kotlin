package io.github.kotlinmania.candleug.lower

import io.github.kotlinmania.candleug.CandleUgException
import io.github.kotlinmania.candleug.Const
import io.github.kotlinmania.candleug.DType
import io.github.kotlinmania.candleug.Layout
import io.github.kotlinmania.candleug.Shape
import io.github.kotlinmania.candleug.op.ArgId
import io.github.kotlinmania.candleug.op.Ast
import io.github.kotlinmania.candleug.op.AstInner
import io.github.kotlinmania.candleug.op.BinaryOp
import io.github.kotlinmania.candleug.op.Kernel
import io.github.kotlinmania.candleug.op.LayoutOp
import io.github.kotlinmania.candleug.op.ReduceOp
import io.github.kotlinmania.candleug.op.Store
import io.github.kotlinmania.candleug.ssa.A
import io.github.kotlinmania.candleug.ssa.Instr
import io.github.kotlinmania.candleug.ssa.LaunchConfig
import io.github.kotlinmania.candleug.ssa.Special
import io.github.kotlinmania.candleug.ssa.VarId

public data class ThreadBlock(
    public val dim: Int,
    public val blockDim: Int,
)

public data class Opts(
    public val threadBlock: ThreadBlock? = null,
    public val blockAxis: Int? = null,
) {
    public fun withGlobalAxis(axis: Int, blockDim: Int): Opts =
        copy(
            blockAxis = axis,
            threadBlock = ThreadBlock(dim = axis, blockDim = blockDim),
        )

    public fun withBlockAxis(axis: Int): Opts = copy(blockAxis = axis)

    public fun withThreadBlock(dim: Int, blockDim: Int): Opts =
        copy(
            threadBlock = ThreadBlock(dim = dim, blockDim = blockDim),
        )
}

internal data class BlockId(
    val id: Int,
) {
    fun toVarId(): VarId = VarId(id)

    fun toA(): A = A.Var(VarId(id))

    companion object {
        private var counter: Int = 1

        fun new(): BlockId {
            val v = counter
            counter += 1
            return BlockId(v)
        }
    }
}

internal sealed class IndexFormula {
    data class Id(
        val id: BlockId,
    ) : IndexFormula()

    data class Constant(
        val value: Int,
    ) : IndexFormula()

    data class Add(
        val lhs: IndexFormula,
        val rhs: IndexFormula,
    ) : IndexFormula()

    data class Mul(
        val lhs: IndexFormula,
        val rhs: Int,
    ) : IndexFormula()

    data class Div(
        val lhs: IndexFormula,
        val rhs: Int,
    ) : IndexFormula()

    data class Mod(
        val lhs: IndexFormula,
        val rhs: Int,
    ) : IndexFormula()

    fun add(rhs: IndexFormula): IndexFormula = Add(this, rhs)

    fun mul(rhs: Int): IndexFormula = Mul(this, rhs)

    fun div(rhs: Int): IndexFormula = Div(this, rhs)

    fun mod(rhs: Int): IndexFormula = Mod(this, rhs)

    fun eval(block: Block): BlockId =
        when (this) {
            is Id -> {
                id
            }

            is Constant -> {
                block.cst(value)
            }

            is Add -> {
                val l = lhs.eval(block)
                val r = rhs.eval(block)
                block.binary(BinaryOp.Add, l.toA(), r.toA(), DType.I32)
            }

            is Mul -> {
                val l = lhs.eval(block)
                block.mul(l, rhs)
            }

            is Div -> {
                if (rhs == 1) {
                    lhs.eval(block)
                } else {
                    val l = lhs.eval(block)
                    block.binary(BinaryOp.Div, l.toA(), A.from(rhs), DType.I32)
                }
            }

            is Mod -> {
                if (rhs == 1) {
                    block.cst(0)
                } else {
                    val l = lhs.eval(block)
                    block.binary(BinaryOp.Mod, l.toA(), A.from(rhs), DType.I32)
                }
            }
        }
}

internal data class Indexes(
    val list: MutableList<IndexFormula>,
) {
    fun layoutOp(op: LayoutOp, shape: Shape, argShape: Shape): Indexes {
        val idxs = list.toMutableList()
        when (op) {
            is LayoutOp.Broadcast -> {
                for (dim in op.broadcastedDims) {
                    if (dim >= idxs.size) {
                        throw CandleUgException("unexpected dim for broadcast, $dim $shape")
                    }
                    idxs[dim] = IndexFormula.Constant(0)
                }
                repeat(op.insertedDims) {
                    if (idxs.isNotEmpty()) {
                        idxs.removeAt(0)
                    }
                }
            }

            is LayoutOp.Narrow -> {
                if (op.dim >= idxs.size) {
                    throw CandleUgException("unexpected dim for narrow, ${op.dim} $shape")
                }
                idxs[op.dim] = idxs[op.dim].add(IndexFormula.Constant(op.offset))
            }

            is LayoutOp.Transpose -> {
                if (op.dim1 >= idxs.size || op.dim2 >= idxs.size) {
                    throw CandleUgException("unexpected dims for transpose ${op.dim1} ${op.dim2}, $shape")
                }
                val tmp = idxs[op.dim1]
                idxs[op.dim1] = idxs[op.dim2]
                idxs[op.dim2] = tmp
            }

            is LayoutOp.SplitDim -> {
                if (op.dim >= argShape.rank()) {
                    throw CandleUgException("unexpected split dim ${op.dim} src $shape")
                }
                if (op.lhs >= shape.rank() || op.rhs >= shape.rank() || op.lhs == op.rhs) {
                    throw CandleUgException("unexpected split dims ${op.lhs}x${op.rhs} dst $shape")
                }
                val dims = shape.dims
                val (l, r) =
                    if (op.lhs < op.rhs) {
                        val r = idxs.removeAt(op.rhs)
                        val l = idxs.removeAt(op.lhs)
                        Pair(l, r)
                    } else {
                        val l = idxs.removeAt(op.lhs)
                        val r = idxs.removeAt(op.rhs)
                        Pair(l, r)
                    }
                idxs.add(op.dim, l.mul(dims[op.rhs]).add(r))
            }

            is LayoutOp.MergeDims -> {
                if (op.dim >= shape.rank()) {
                    throw CandleUgException("unexpected merge dim ${op.dim} dst $shape")
                }
                if (op.lhs >= argShape.rank() || op.rhs >= argShape.rank() || op.lhs == op.rhs) {
                    throw CandleUgException("unexpected merge dims ${op.lhs}x${op.rhs} src $argShape")
                }
                val argDims = argShape.dims
                val idx = idxs.removeAt(op.dim)
                val lhsIdx = if (argDims[op.lhs] <= 1) IndexFormula.Constant(0) else idx.div(argDims[op.rhs])
                val rhsIdx = if (argDims[op.rhs] <= 1) IndexFormula.Constant(0) else idx.mod(argDims[op.rhs])
                if (op.lhs < op.rhs) {
                    idxs.add(op.lhs, lhsIdx)
                    idxs.add(op.rhs, rhsIdx)
                } else {
                    idxs.add(op.rhs, rhsIdx)
                    idxs.add(op.lhs, lhsIdx)
                }
            }
        }
        return Indexes(idxs)
    }
}

internal data class RangeHandle(
    val rangeId: BlockId,
    val erangeId: BlockId,
)

internal class Block(
    val instrs: MutableList<Pair<BlockId, Instr>> = mutableListOf(),
) {
    fun range(lo: A, up: A, step: Int): RangeHandle {
        val rangeId = BlockId.new()
        val erangeId = BlockId.new()
        instrs.add(rangeId to Instr.Range(lo, up, step, erangeId.toVarId()))
        return RangeHandle(rangeId, erangeId)
    }

    fun endRange(handle: RangeHandle) {
        instrs.add(handle.erangeId to Instr.EndRange(handle.rangeId.toVarId()))
    }

    fun push(instr: Instr): BlockId {
        val id = BlockId.new()
        instrs.add(id to instr)
        return id
    }

    fun cst(v: Int): BlockId = push(Instr.Constant(Const.I32(v)))

    fun mul(srcId: BlockId, v: Int): BlockId {
        if (v == 1) return srcId
        val dstId = BlockId.new()
        instrs.add(dstId to Instr.Binary(BinaryOp.Mul, srcId.toA(), A.from(v), DType.I32))
        return dstId
    }

    fun binary(op: BinaryOp, lhs: A, rhs: A, dtype: DType): BlockId {
        val id = BlockId.new()
        instrs.add(id to Instr.Binary(op, lhs, rhs, dtype))
        return id
    }

    fun relocate(): List<Instr> {
        val perId = HashMap<Int, VarId>()
        for ((lineIdx, pair) in instrs.withIndex()) {
            perId[pair.first.id] = VarId(lineIdx)
        }

        fun getId(id: VarId): VarId = perId[id.id] ?: throw CandleUgException("id not found $id")

        fun getA(a: A): A =
            when (a) {
                is A.Var -> A.Var(getId(a.id))
                is A.Constant -> a
            }

        val result = ArrayList<Instr>(instrs.size)
        for ((_, instr) in instrs) {
            val relocated =
                when (instr) {
                    is Instr.DefineAcc, is Instr.DefineGlobal, is Instr.DefineLocal, is Instr.SpecialInstr, is Instr.Constant -> instr
                    is Instr.Unary -> Instr.Unary(instr.op, getA(instr.arg), instr.dtype)
                    is Instr.Binary -> Instr.Binary(instr.op, getA(instr.lhs), getA(instr.rhs), instr.dtype)
                    is Instr.Range -> Instr.Range(getA(instr.lo), getA(instr.up), instr.step, getId(instr.endIdx))
                    is Instr.Load -> Instr.Load(getId(instr.src), getA(instr.offset), instr.dtype)
                    is Instr.Assign -> Instr.Assign(getId(instr.dst), getA(instr.src))
                    is Instr.EndRange -> Instr.EndRange(getId(instr.startIdx))
                    is Instr.If -> Instr.If(getA(instr.cond), getId(instr.endIdx))
                    is Instr.EndIf -> Instr.EndIf
                    is Instr.Store -> Instr.Store(getId(instr.dst), getA(instr.offset), getA(instr.value), instr.dtype)
                    is Instr.Barrier -> Instr.Barrier
                    is Instr.ReduceLocal -> Instr.ReduceLocal(instr.op, getA(instr.arg), instr.dtype)
                }
            result.add(relocated)
        }
        return result
    }
}

internal fun Layout.lower(idxs: Indexes): Pair<BlockId, Block> {
    if (idxs.list.size != strides.size) {
        throw CandleUgException("len mismatch between strides $this and idxs $idxs")
    }
    val block = Block()
    var accId: BlockId? = if (offset == 0) null else block.cst(offset)
    for (i in idxs.list.indices) {
        val idx = idxs.list[i]
        val stride = strides[i]
        val dimId = idx.eval(block)
        val mulId = block.mul(dimId, stride)
        accId =
            if (accId != null) {
                block.binary(BinaryOp.Add, mulId.toA(), accId.toA(), DType.I32)
            } else {
                mulId
            }
    }
    return if (accId != null) {
        Pair(accId, block)
    } else {
        val id = block.cst(0)
        Pair(id, block)
    }
}

internal fun Ast.lower(
    idxs: Indexes,
    opts: Opts,
    perArg: Map<ArgId, VarId>,
): Pair<BlockId, Block> {
    val dtype = this.dtype
    return when (val in_ = this.inner) {
        is AstInner.Load -> {
            val dstI = BlockId.new()
            val ptrI = perArg[in_.src] ?: throw CandleUgException("unknown arg ${in_.src}")
            val (offI, offB) = in_.layout.lower(idxs)
            val load = Instr.Load(src = ptrI, offset = offI.toA(), dtype = dtype)
            offB.instrs.add(dstI to load)
            Pair(dstI, offB)
        }

        is AstInner.LayoutOperation -> {
            val nextIdxs = idxs.layoutOp(in_.op, this.shape, in_.arg.shape)
            in_.arg.lower(nextIdxs, opts, perArg)
        }

        is AstInner.Constant -> {
            val dstI = BlockId.new()
            val b = Block()
            b.instrs.add(dstI to Instr.Constant(in_.value))
            Pair(dstI, b)
        }

        is AstInner.Unary -> {
            val dstI = BlockId.new()
            val (argI, argB) = in_.arg.lower(idxs, opts, perArg)
            argB.instrs.add(dstI to Instr.Unary(in_.op, argI.toA(), dtype))
            Pair(dstI, argB)
        }

        is AstInner.Binary -> {
            val dstI = BlockId.new()
            val (lhsI, lhsB) = in_.lhs.lower(idxs, opts, perArg)
            val (rhsI, rhsB) = in_.rhs.lower(idxs, opts, perArg)
            val allInstrs = ArrayList<Pair<BlockId, Instr>>(lhsB.instrs.size + rhsB.instrs.size + 1)
            allInstrs.addAll(lhsB.instrs)
            allInstrs.addAll(rhsB.instrs)
            allInstrs.add(dstI to Instr.Binary(in_.op, lhsI.toA(), rhsI.toA(), dtype))
            Pair(dstI, Block(allInstrs))
        }

        is AstInner.Reduce -> {
            val dstI = BlockId.new()
            val block = Block()
            val initValue =
                when (in_.op) {
                    ReduceOp.Sum -> Const.zero(dtype)
                    ReduceOp.Min -> Const.maxValue(dtype)
                    ReduceOp.Max -> Const.minValue(dtype)
                }
            val foldOp =
                when (in_.op) {
                    ReduceOp.Sum -> BinaryOp.Add
                    ReduceOp.Max -> BinaryOp.Max
                    ReduceOp.Min -> BinaryOp.Min
                }
            block.instrs.add(dstI to Instr.DefineAcc(initValue))
            val reduceLen =
                in_.arg.shape.dims
                    .getOrNull(in_.dim)
                    ?: throw CandleUgException("unexpected dim for reduce, ${in_.dim} ${this.shape}")
            val r = block.range(A.from(0), A.from(reduceLen), 1)
            val reduceIdxs = Indexes(idxs.list.toMutableList())
            reduceIdxs.list[in_.dim] = IndexFormula.Id(r.rangeId)
            val (argI, argB) = in_.arg.lower(reduceIdxs, opts, perArg)
            block.instrs.addAll(argB.instrs)
            val foldInstr = Instr.Binary(foldOp, dstI.toA(), argI.toA(), dtype)
            val srcId = block.push(foldInstr)
            block.push(Instr.Assign(dst = dstI.toVarId(), src = srcId.toA()))
            block.endRange(r)
            Pair(dstI, block)
        }

        is AstInner.Id -> {
            Pair(BlockId(in_.id), Block())
        }
    }
}

public fun Kernel.lower(opts: Opts = Opts()): io.github.kotlinmania.candleug.ssa.Kernel {
    val block = Block()
    val perArg = HashMap<ArgId, VarId>()
    for ((index, arg) in args.withIndex()) {
        val dtype =
            when (val t = arg.type) {
                is io.github.kotlinmania.candleug.op.ArgType.Ptr -> t.dtype
                is io.github.kotlinmania.candleug.op.ArgType.Value -> throw CandleUgException("non-pointer arguments are not supported yet")
            }
        val id = block.push(Instr.DefineGlobal(index, dtype))
        perArg[arg.id] = id.toVarId()
    }

    val blockIdPair =
        opts.blockAxis?.let { dim ->
            val id = block.push(Instr.SpecialInstr(Special.BlockIdx))
            Pair(dim, id)
        }
    val threadIdPair =
        opts.threadBlock?.let { tb ->
            val id = block.push(Instr.SpecialInstr(Special.ThreadIdx))
            Pair(tb, id)
        }

    for (op in ops) {
        val ptrI = perArg[op.dst] ?: throw CandleUgException("unknown arg ${op.dst}")
        val ranges = ArrayList<RangeHandle>(op.layout.rank())
        val idxList = ArrayList<IndexFormula>(op.layout.rank())

        for ((dimIdx, len) in op.layout.dims.withIndex()) {
            val id =
                when {
                    blockIdPair != null && threadIdPair != null && blockIdPair.first == dimIdx && threadIdPair.first.dim == dimIdx -> {
                        val globalId = block.mul(blockIdPair.second, threadIdPair.first.blockDim)
                        val combined = block.binary(BinaryOp.Add, globalId.toA(), threadIdPair.second.toA(), DType.I32)
                        val r = block.range(combined.toA(), A.from(len), len)
                        ranges.add(r)
                        r.rangeId
                    }

                    blockIdPair != null && blockIdPair.first == dimIdx -> {
                        blockIdPair.second
                    }

                    threadIdPair != null && threadIdPair.first.dim == dimIdx -> {
                        if (len == threadIdPair.first.blockDim) {
                            threadIdPair.second
                        } else {
                            val r = block.range(threadIdPair.second.toA(), A.from(len), threadIdPair.first.blockDim)
                            ranges.add(r)
                            r.rangeId
                        }
                    }

                    else -> {
                        val r = block.range(A.from(0), A.from(len), 1)
                        ranges.add(r)
                        r.rangeId
                    }
                }
            idxList.add(IndexFormula.Id(id))
        }

        val idxs = Indexes(idxList)
        val (offI, offB) = op.layout.lower(idxs)
        block.instrs.addAll(offB.instrs)

        val (srcI, srcB) = op.value.lower(idxs, opts, perArg)
        block.instrs.addAll(srcB.instrs)

        val store =
            Instr.Store(
                dst = ptrI,
                offset = offI.toA(),
                value = srcI.toA(),
                dtype = op.value.dtype,
            )
        block.push(store)

        for (r in ranges.reversed()) {
            block.endRange(r)
        }
    }

    val instrs = block.relocate()
    val ssaArgs =
        args.mapIndexed { i, a ->
            io.github.kotlinmania.candleug.ssa
                .KernelArg(a, i)
        }
    val gridDim =
        opts.blockAxis?.let { idx ->
            val local = if (opts.threadBlock?.dim == idx) opts.threadBlock.blockDim else 1
            val dimLen =
                ops
                    .firstOrNull()
                    ?.layout
                    ?.dims
                    ?.getOrNull(idx) ?: 1
            (dimLen + local - 1) / local
        } ?: 1
    val blockDim = opts.threadBlock?.blockDim ?: 1
    val cfg = LaunchConfig(gridDim = gridDim, blockDim = blockDim, sharedMem = 0)
    return io.github.kotlinmania.candleug.ssa
        .Kernel(instrs, ssaArgs, cfg)
}
