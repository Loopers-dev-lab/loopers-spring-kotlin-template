package com.loopers.domain.member

import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Embeddable

@Embeddable
data class MemberId(
    @Column(name = "member_id")
    val value: String
) {

    init {
        require(value.isNotEmpty() && value.length <= 10 && value.all { it.isLetterOrDigit() }) {
            throw InvalidMemberIdException(ErrorType.BAD_REQUEST, "memberId는 영문 또는 숫자 10자이내이어야 합니다.")
        }
    }
}
