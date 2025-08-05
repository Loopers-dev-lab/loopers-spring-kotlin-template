package com.loopers.domain.brand.policy

object BrandPolicy {
    object Name {
        const val MESSAGE = "영문 및 숫자 및 한글 100자 이내"
        const val PATTERN = "^[a-zA-Z0-9가-힣]{1,100}\$"
    }

    object Description {
        const val MESSAGE = "영문 및 숫자 및 한글 100자 이내"
        const val PATTERN = "^[a-zA-Z0-9가-힣]{1,100}\$"
    }
}
