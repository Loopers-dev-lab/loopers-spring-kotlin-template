package com.loopers.domain.user.vo

import jakarta.persistence.Column
import jakarta.persistence.Embeddable

@Embeddable
data class LoginId(
    @Column(name = "login_id", unique = true, nullable = false, length = 10)
    val value: String,
) {
    init {
        require(value.matches(Regex("^[a-zA-Z0-9]{1,10}$"))) {
            "ID는 영문 및 숫자 10자 이내여야 합니다"
        }
    }
}
