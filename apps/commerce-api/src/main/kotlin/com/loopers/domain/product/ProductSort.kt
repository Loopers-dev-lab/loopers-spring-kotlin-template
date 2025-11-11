package com.loopers.domain.product

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

enum class ProductSort(@get:JsonValue val value: String) {
    LATEST("latest"),
    PRICE_ASC("price_asc"),
    LIKE_DESC("likes_desc"),
    ;

    companion object {
        @JvmStatic
        @JsonCreator
        fun from(value: String?): ProductSort {
            if (value == null) return LATEST

            return entries.find { it.value == value }
                ?: throw IllegalArgumentException("Invalid ProductSort value: $value")
        }
    }
}
