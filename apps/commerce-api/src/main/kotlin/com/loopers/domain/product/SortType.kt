package com.loopers.domain.product

enum class SortType(val value: String) {
    LATEST("latest"),
    LIKES_DESC("likes_desc"),
    PRICE_ASC("price_asc"),
    ;

    companion object {
        fun from(value: String): SortType =
            entries.find { it.value == value }
                ?: throw IllegalArgumentException("Invalid sort type: $value. Allowed values: ${entries.map { it.value }}")
    }
}
