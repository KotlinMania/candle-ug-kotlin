# Rust callers — `candle-ug-kotlin`

## What this crate is

`candle-ug` was an upstream re-export crate in `huggingface/candle` that re-exported `ug`, `ug-cuda`, and `ug-metal`.
Upstream `huggingface/candle` subsequently removed `candle-ug` in PR #3954 ("Remove ug").

## Porting & Caller Discipline

Per KotlinMania workspace policy (AGENTS.md §3):
- We do not port `lib.rs` or `mod.rs` as re-export wrappers or typealias bridges in Kotlin (no `Lib.kt`, no `Mod.kt`).
- Upstream `candle-ug/src/lib.rs` contained solely `pub use ug::*;`, `pub mod cuda { pub use ug_cuda::*; }`, and `pub mod metal { pub use ug_metal::*; }` with no independent types, interfaces, or functions.
- Downstream callers consume canonical sibling ports directly:
  - `ug-kotlin` (`io.github.kotlinmania.ug.*`)
  - `ug-cuda-kotlin` (`io.github.kotlinmania.ug.cuda.*`)
  - `ug-metal-kotlin` (`io.github.kotlinmania.ug.metal.*`)

## Inbound callers across workspace

Workspace-wide audit across all `*-kotlin` repositories found zero inbound usages of `candle_ug` or `candleug`.
