package io.github.kotlinmania.candleug.cuda

import io.github.kotlinmania.candleug.CandleUgException
import io.github.kotlinmania.candleug.Const
import io.github.kotlinmania.candleug.DType
import io.github.kotlinmania.candleug.op.ArgType
import io.github.kotlinmania.candleug.op.BinaryOp
import io.github.kotlinmania.candleug.op.ReduceOp
import io.github.kotlinmania.candleug.op.UnaryOp
import io.github.kotlinmania.candleug.ssa.A
import io.github.kotlinmania.candleug.ssa.Instr
import io.github.kotlinmania.candleug.ssa.Kernel
import io.github.kotlinmania.candleug.ssa.Special
import io.github.kotlinmania.candleug.ssa.VarId

public object CudaCodeGen {
    private fun fmtVar(v: VarId): String = "__var${v.id}"

    private fun fmtConst(c: Const): String =
        when (c) {
            is Const.BF16 -> {
                c.bits.toString()
            }

            is Const.F16 -> {
                c.bits.toString()
            }

            is Const.F32 -> {
                val v = c.value
                when {
                    v.isNaN() -> "0. / 0."
                    v == Float.POSITIVE_INFINITY -> "1. / 0."
                    v == Float.NEGATIVE_INFINITY -> "-1. / 0."
                    else -> v.toString()
                }
            }

            is Const.I32 -> {
                c.value.toString()
            }

            is Const.I64 -> {
                c.value.toString()
            }
        }

    private fun fmtA(a: A): String =
        when (a) {
            is A.Var -> fmtVar(a.id)
            is A.Constant -> fmtConst(a.value)
        }

    private fun fmtDType(dtype: DType): String =
        when (dtype) {
            DType.BF16 -> "__nv_bfloat16"
            DType.F16 -> "__half"
            DType.F32 -> "float"
            DType.I32 -> "int"
            DType.I64 -> "long long"
        }

    public fun gen(w: Appendable, funcName: String, kernel: Kernel) {
        val instrs = kernel.instrs

        w.append("extern \"C\" __global__ void $funcName(\n")
        for ((argIdx, pair) in kernel.args.withIndex()) {
            val (arg, varId) = pair
            val isLast = argIdx == kernel.args.size - 1
            val delim = if (isLast) "" else ","
            val ty =
                when (val t = arg.type) {
                    is ArgType.Value -> fmtDType(t.dtype)
                    is ArgType.Ptr -> "${fmtDType(t.dtype)}*"
                }
            w.append("  $ty ${fmtVar(VarId(varId))}$delim\n")
        }
        w.append(") {\n")

        var depth = 0
        for ((varIndex, instr) in instrs.withIndex()) {
            val varId = fmtVar(VarId(varIndex))
            val indent = "  " + "  ".repeat(depth)
            when (instr) {
                is Instr.DefineGlobal -> {}

                is Instr.DefineLocal -> {
                    w.append("$indent __shared__ ${fmtDType(instr.dtype)} $varId[${instr.size}];\n")
                }

                is Instr.DefineAcc -> {
                    w.append("$indent${fmtDType(instr.value.dtype)} $varId = ${fmtConst(instr.value)};\n")
                }

                is Instr.Constant -> {
                    w.append("$indent${fmtDType(instr.value.dtype)} $varId = ${fmtConst(instr.value)};\n")
                }

                is Instr.If -> {
                    w.append("$indent if (${fmtA(instr.cond)}) {\n")
                    depth += 1
                }

                is Instr.Range -> {
                    w.append("$indent for (int $varId = ${fmtA(instr.lo)}; $varId < ${fmtA(instr.up)}; $varId+=${instr.step}) {\n")
                    depth += 1
                }

                is Instr.EndIf, is Instr.EndRange -> {
                    if (depth == 0) {
                        throw CandleUgException("unmatched EndRange")
                    }
                    depth -= 1
                    val closeIndent = "  " + "  ".repeat(depth)
                    w.append("$closeIndent}\n")
                }

                is Instr.Load -> {
                    w.append("$indent${fmtDType(instr.dtype)} $varId = ${fmtVar(instr.src)}[${fmtA(instr.offset)}];\n")
                }

                is Instr.Assign -> {
                    w.append("$indent${fmtVar(instr.dst)} = ${fmtA(instr.src)};\n")
                }

                is Instr.Store -> {
                    w.append("$indent${fmtVar(instr.dst)}[${fmtA(instr.offset)}] = ${fmtA(instr.value)};\n")
                }

                is Instr.Binary -> {
                    val expr =
                        when (instr.op) {
                            BinaryOp.Add -> "${fmtA(instr.lhs)} + ${fmtA(instr.rhs)}"
                            BinaryOp.Mul -> "${fmtA(instr.lhs)} * ${fmtA(instr.rhs)}"
                            BinaryOp.Sub -> "${fmtA(instr.lhs)} - ${fmtA(instr.rhs)}"
                            BinaryOp.Div -> "${fmtA(instr.lhs)} / ${fmtA(instr.rhs)}"
                            BinaryOp.Min -> "min(${fmtA(instr.lhs)}, ${fmtA(instr.rhs)})"
                            BinaryOp.Max -> "max(${fmtA(instr.lhs)}, ${fmtA(instr.rhs)})"
                            BinaryOp.Mod -> "${fmtA(instr.lhs)} % ${fmtA(instr.rhs)}"
                        }
                    w.append("$indent${fmtDType(instr.dtype)} $varId = $expr;\n")
                }

                is Instr.Unary -> {
                    val expr =
                        when (instr.op) {
                            UnaryOp.Exp -> {
                                "exp(${fmtA(instr.arg)})"
                            }

                            UnaryOp.Sqrt -> {
                                "sqrt(${fmtA(instr.arg)})"
                            }

                            UnaryOp.Sin -> {
                                "sin(${fmtA(instr.arg)})"
                            }

                            UnaryOp.Cos -> {
                                "cos(${fmtA(instr.arg)})"
                            }

                            UnaryOp.Neg -> {
                                "-(${fmtA(instr.arg)})"
                            }

                            UnaryOp.Id -> {
                                fmtA(instr.arg)
                            }

                            is UnaryOp.Cast -> {
                                when (instr.dtype) {
                                    DType.BF16 -> "static_cast<__nv_bfloat16>(${fmtA(instr.arg)})"
                                    DType.F16 -> "static_cast<__half>(${fmtA(instr.arg)})"
                                    DType.F32 -> "static_cast<float>(${fmtA(instr.arg)})"
                                    DType.I32 -> "static_cast<int>(${fmtA(instr.arg)})"
                                    DType.I64 -> "static_cast<long long>(${fmtA(instr.arg)})"
                                }
                            }
                        }
                    w.append("$indent${fmtDType(instr.dtype)} $varId = $expr;\n")
                }

                is Instr.SpecialInstr -> {
                    when (instr.special) {
                        Special.ThreadIdx -> w.append("$indent int $varId = threadIdx.x;\n")
                        Special.BlockIdx -> w.append("$indent int $varId = blockIdx.x;\n")
                    }
                }

                is Instr.Barrier -> {
                    w.append("$indent __syncthreads();\n")
                }

                is Instr.ReduceLocal -> {
                    val fnName =
                        when (instr.op) {
                            ReduceOp.Sum -> "block_reduce_sum"
                            ReduceOp.Min -> "block_reduce_min"
                            ReduceOp.Max -> "block_reduce_max"
                        }
                    w.append("$indent${fmtDType(instr.dtype)} $varId = $fnName(${fmtA(instr.arg)});\n")
                }
            }
        }
        w.append("}\n")
        if (depth > 0) {
            throw CandleUgException("unmatched Range")
        }
    }
}
