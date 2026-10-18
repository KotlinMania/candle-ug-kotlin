package io.github.kotlinmania.candleug

public class CandleUgException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
