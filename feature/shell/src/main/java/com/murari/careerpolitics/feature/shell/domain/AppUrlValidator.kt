package com.murari.careerpolitics.feature.shell.domain

interface AppUrlValidator {
    fun isValid(url: String): Boolean
}
