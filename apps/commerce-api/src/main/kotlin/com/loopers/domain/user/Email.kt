package com.loopers.domain.user

import jakarta.persistence.Column
import jakarta.persistence.Embeddable

@Embeddable
data class Email(
    @Column(name = "email", nullable = false)
    val value: String,
) {

    init {
        validate(value)
    }

    private fun validate(email: String) {
        require(email.isNotBlank()) { ERROR_MESSAGE_BLANK }
        require(!email.contains(" ")) { ERROR_MESSAGE_WHITESPACE }
        require(email.matches(EMAIL_PATTERN)) { ERROR_MESSAGE_FORMAT }
    }

    override fun toString(): String = value

    companion object {
        private val EMAIL_PATTERN = Regex("^[^@]+@[^@]+\\.[^@]+$")
        private const val ERROR_MESSAGE_BLANK = "email은 필수입니다"
        private const val ERROR_MESSAGE_WHITESPACE = "email에 공백이 포함될 수 없습니다"
        private const val ERROR_MESSAGE_FORMAT = "email은 xx@yy.zz 형식이어야 합니다"
    }
}
