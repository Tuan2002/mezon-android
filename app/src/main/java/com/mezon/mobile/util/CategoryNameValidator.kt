package com.mezon.mobile.util

object CategoryNameValidator {

    private val VALID_NAME_REGEX = Regex(
        """^(?![_\-\u0020])[a-zA-Z0-9\p{L}\p{N}_\-\s]{1,64}$"""
    )

    fun isValid(trimmedInput: String): Boolean =
        trimmedInput.isNotEmpty() && VALID_NAME_REGEX.matches(trimmedInput)
}
