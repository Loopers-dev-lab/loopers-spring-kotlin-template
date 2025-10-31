package com.loopers.domain.user

import jakarta.persistence.Column
import jakarta.persistence.Embeddable

@Embeddable
data class UserId(
    @Column(name = "user_id", unique = true, nullable = false)
    val value: String,
) {

    init {
        validate(value)
    }

    private fun validate(userId: String) {
        require(userId.isNotBlank()) { ERROR_MESSAGE_BLANK }
        require(userId.length <= MAX_LENGTH) {
            "$ERROR_MESSAGE_LENGTH_EXCEEDED: 최대 ${MAX_LENGTH}자"
        }
        require(userId.matches(PATTERN)) { ERROR_MESSAGE_FORMAT }
    }

    override fun toString(): String = value

    companion object {
        private const val MAX_LENGTH = 10
        private val PATTERN = Regex("^[a-zA-Z0-9]+$")
        private const val ERROR_MESSAGE_BLANK = "userId는 필수입니다"
        private const val ERROR_MESSAGE_LENGTH_EXCEEDED = "userId는 10자 이내여야 합니다"
        private const val ERROR_MESSAGE_FORMAT = "userId는 영문 및 숫자만 포함해야 합니다"
    }
}
