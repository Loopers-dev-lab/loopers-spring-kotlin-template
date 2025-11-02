package com.loopers.domain.user.vo

import jakarta.persistence.Column
import jakarta.persistence.Embeddable

@Embeddable
data class Email(
    @Column(name = "email", nullable = false)
    val value: String,
) {
    init {
        require(value.matches(Regex("^[^@]+@[^@]+\\.[^@]+$"))) {
            "이메일은 xx@yy.zz 형식이어야 합니다"
        }
    }
}
