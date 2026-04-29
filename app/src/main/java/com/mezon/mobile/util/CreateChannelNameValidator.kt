package com.mezon.mobile.util

object CreateChannelNameValidator {

    private val VALID_NAME_REGEX = Regex(
        """^(?![_\-\u0020])(?:(?!')[a-zA-Z0-9\p{L}\p{N}\p{So}_\-\s]){1,64}$"""
    )

    fun isValid(trimmedInput: String): Boolean =
        trimmedInput.isNotEmpty() && VALID_NAME_REGEX.matches(trimmedInput)
}
