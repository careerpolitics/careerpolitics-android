package com.murari.careerpolitics.common.domain

sealed interface AppError {
    data class Recoverable(val message: String) : AppError
    data class UserActionRequired(val message: String) : AppError
    data class Fatal(val message: String, val cause: Throwable? = null) : AppError
}
