package com.loopers.domain.like

class ProductResult {

    data class AddLike(
        val isChanged: Boolean,
    )

    data class RemoveLike(
        val isChanged: Boolean,
    )
}
