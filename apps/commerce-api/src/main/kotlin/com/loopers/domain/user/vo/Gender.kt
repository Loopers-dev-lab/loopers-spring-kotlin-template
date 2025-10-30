package com.loopers.domain.user.vo

enum class Gender {
    MALE,
    FEMALE,
    ;

    companion object {
        fun from(value: String): Gender = valueOf(value.uppercase())
    }
}
