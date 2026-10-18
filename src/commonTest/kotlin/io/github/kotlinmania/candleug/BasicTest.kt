package io.github.kotlinmania.candleug

import io.github.kotlinmania.candleug.interpreter.Buffer
import io.github.kotlinmania.candleug.interpreter.evalSsa
import io.github.kotlinmania.candleug.lower.lower
import io.github.kotlinmania.candleug.op.Arg
import io.github.kotlinmania.candleug.op.BinaryOp
import io.github.kotlinmania.candleug.op.Kernel
import io.github.kotlinmania.candleug.op.binary
import io.github.kotlinmania.candleug.op.load
import io.github.kotlinmania.candleug.op.store
import io.github.kotlinmania.candleug.ssa.A
import io.github.kotlinmania.candleug.ssa.Instr
import io.github.kotlinmania.candleug.ssa.LaunchConfig
import io.github.kotlinmania.candleug.ssa.VarId
import kotlin.test.Test
import kotlin.test.assertEquals

class BasicTest {
    @Test
    fun evalAdd() {
        // Simple SSA add: a[i] = b[i] + c[i]
        val instrs =
            listOf(
                Instr.DefineGlobal(0, DType.I32), // a
                Instr.DefineGlobal(1, DType.I32), // b
                Instr.DefineGlobal(2, DType.I32), // c
                Instr.Range(A.from(0), A.from(2), 1, VarId(9)),
                Instr.Load(VarId(1), A.from(VarId(3)), DType.I32),
                Instr.Load(VarId(2), A.from(VarId(3)), DType.I32),
                Instr.Binary(BinaryOp.Add, A.from(VarId(4)), A.from(VarId(5)), DType.I32),
                Instr.Store(VarId(0), A.from(VarId(3)), A.from(VarId(6)), DType.I32),
                Instr.EndRange(VarId(3)),
            )
        val args =
            listOf(
                io.github.kotlinmania.candleug.ssa
                    .KernelArg(Arg.ptr(DType.I32), 0),
                io.github.kotlinmania.candleug.ssa
                    .KernelArg(Arg.ptr(DType.I32), 1),
                io.github.kotlinmania.candleug.ssa
                    .KernelArg(Arg.ptr(DType.I32), 2),
            )
        val kernel =
            io.github.kotlinmania.candleug.ssa
                .Kernel(instrs, args, LaunchConfig.forNumElems(2))

        val a = Buffer.I32(intArrayOf(0, 0))
        val b = Buffer.I32(intArrayOf(3, 4))
        val c = Buffer.I32(intArrayOf(1, 2))

        evalSsa(kernel, listOf(a, b, c))
        assertEquals(4, a.data[0])
        assertEquals(6, a.data[1])
    }

    @Test
    fun lowerAdd() {
        val layout = Layout.fromShape(2)
        val a = Arg.ptr(DType.F32)
        val b = Arg.ptr(DType.F32)
        val c = Arg.ptr(DType.F32)

        val srcA = load(a.id, layout, DType.F32)
        val srcB = load(b.id, layout, DType.F32)
        val sum = binary(BinaryOp.Add, srcA, srcB)
        val st = store(c.id, layout, sum)

        val kernel = Kernel("add", listOf(a, b, c), listOf(st))
        val ssaKernel = kernel.lower()

        val bufA = Buffer.F32(floatArrayOf(1f, 2f))
        val bufB = Buffer.F32(floatArrayOf(3f, 4f))
        val bufC = Buffer.F32(floatArrayOf(0f, 0f))

        evalSsa(ssaKernel, listOf(bufA, bufB, bufC))
        assertEquals(4f, bufC.data[0])
        assertEquals(6f, bufC.data[1])
    }

    @Test
    fun stride() {
        val s0 = Shape.SCALAR
        assertEquals(emptyList(), s0.strideContiguous())

        val s1 = Shape(42)
        assertEquals(listOf(1), s1.strideContiguous())

        val s2 = Shape(42, 1337)
        assertEquals(listOf(1337, 1), s2.strideContiguous())

        val s3 = Shape(299, 792, 458)
        assertEquals(listOf(458 * 792, 458, 1), s3.strideContiguous())
    }
}
