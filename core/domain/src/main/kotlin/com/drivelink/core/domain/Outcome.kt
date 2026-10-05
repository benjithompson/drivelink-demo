package com.drivelink.core.domain

import com.drivelink.core.domain.error.AppError

/** Result of a repository call: a value or a typed [AppError]. Never throws for HTTP or parse errors. */
sealed interface Outcome<out T> {
    data class Ok<out T>(val value: T) : Outcome<T>
    data class Err(val error: AppError) : Outcome<Nothing>
}

inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> = when (this) {
    is Outcome.Ok -> Outcome.Ok(transform(value))
    is Outcome.Err -> this
}
