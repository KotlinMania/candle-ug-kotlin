package io.github.kotlinmania.candleug

import io.github.kotlinmania.candleug.interpreter.Buffer
import io.github.kotlinmania.candleug.interpreter.evalSsa
import io.github.kotlinmania.candleug.ssa.Kernel

public class UgIOp1(
    public val opName: String,
    public val kernel: Kernel,
    public val func: Any? = null,
) {
    public fun name(): String = opName

    public fun eval(buffer: Buffer) {
        evalSsa(kernel, listOf(buffer))
    }

    public fun eval(data: FloatArray) {
        val buf = Buffer.F32(data)
        evalSsa(kernel, listOf(buf))
    }

    public fun eval(data: IntArray) {
        val buf = Buffer.I32(data)
        evalSsa(kernel, listOf(buf))
    }
}
