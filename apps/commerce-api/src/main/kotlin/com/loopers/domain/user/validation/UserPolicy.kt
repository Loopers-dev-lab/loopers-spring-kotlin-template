package com.loopers.domain.user.validation

object UserPolicy {
    object UserName {
        const val MESSAGE = "영문 및 숫자 10자 이내"
        const val PATTERN = "^[a-zA-Z0-9]{1,10}$"
    }

    object BirthDate {
        const val MESSAGE = "yyyy-MM-dd"
        const val PATTERN = "^\\d{4}-\\d{2}-\\d{2}$"
    }

    object Email {
        const val MESSAGE = "xx@yy.zz"
        const val PATTERN = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}\$"
    }
}
