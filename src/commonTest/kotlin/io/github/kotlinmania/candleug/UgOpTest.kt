package io.github.kotlinmania.candleug

import io.github.kotlinmania.candleug.cuda.CudaCodeGen
import io.github.kotlinmania.candleug.interpreter.Buffer
import io.github.kotlinmania.candleug.interpreter.evalSsa
import io.github.kotlinmania.candleug.lower.Opts
import io.github.kotlinmania.candleug.lower.lower
import io.github.kotlinmania.candleug.metal.MetalCodeGen
import io.github.kotlinmania.candleug.op.Arg
import io.github.kotlinmania.candleug.op.Kernel
import io.github.kotlinmania.candleug.op.UnaryOp
import io.github.kotlinmania.candleug.op.load
import io.github.kotlinmania.candleug.op.store
import io.github.kotlinmania.candleug.op.unary
import kotlin.math.round
import kotlin.test.Test
import kotlin.test.assertTrue

class UgOpTest {
    private fun round2(v: Float): Float = round(v * 100f) / 100f

    @Test
    fun testUgOpParity() {
        val layout = Layout.fromShape(12)
        val ptr = Arg.ptr(DType.F32)
        val src = load(ptr.id, layout, DType.F32)
        val srcExp = unary(UnaryOp.Exp, src)
        val st = store(ptr.id, layout, srcExp)
        val kernel = Kernel("exp", listOf(ptr), listOf(st))
        val opts = Opts()
        val ssaKernel = kernel.lower(opts)

        // 1. Verify Metal code generation
        val metalCode =
            buildString {
                MetalCodeGen.gen(this, "test_exp", ssaKernel)
            }
        assertTrue(metalCode.contains("#include <metal_stdlib>"))
        assertTrue(metalCode.contains("[[kernel]] void test_exp("))
        assertTrue(metalCode.contains("device float* __var0"))
        assertTrue(metalCode.contains("exp("))

        // 2. Verify CUDA code generation
        val cudaCode =
            buildString {
                CudaCodeGen.gen(this, "test_exp", ssaKernel)
            }
        assertTrue(cudaCode.contains("extern \"C\" __global__ void test_exp("))
        assertTrue(cudaCode.contains("float* __var0"))
        assertTrue(cudaCode.contains("exp("))

        // 3. Verify Interpreter numerical execution
        val data = FloatArray(12) { it.toFloat() }
        val buffer = Buffer.F32(data)
        evalSsa(ssaKernel, listOf(buffer))

        val expected =
            floatArrayOf(
                1.0f,
                2.72f,
                7.39f,
                20.09f,
                54.60f,
                148.41f,
                403.43f,
                1096.63f,
                2980.96f,
                8103.08f,
                22026.47f,
                59874.14f,
            )
        val actual = data.map { round2(it) }
        for (i in 0 until 12) {
            assertTrue(kotlin.math.abs(expected[i] - actual[i]) <= 0.02f, "Mismatch at index $i: expected ${expected[i]}, got ${actual[i]}")
        }

        // 4. Verify UgIOp1 execution
        val data2 = FloatArray(12) { it.toFloat() }
        val op = UgIOp1("test", ssaKernel)
        op.eval(data2)
        val actual2 = data2.map { round2(it) }
        for (i in 0 until 12) {
            assertTrue(kotlin.math.abs(expected[i] - actual2[i]) <= 0.02f, "Mismatch at index $i: expected ${expected[i]}, got ${actual2[i]}")
        }
    }
}
