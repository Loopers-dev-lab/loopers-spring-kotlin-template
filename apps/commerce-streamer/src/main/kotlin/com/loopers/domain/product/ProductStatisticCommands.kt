package com.loopers.domain.product

data class UpdateLikeCountCommand(
    val items: List<Item>,
) {
    data class Item(
        val productId: Long,
        val type: LikeType,
    )

    enum class LikeType { CREATED, CANCELED }
}

data class UpdateSalesCountCommand(
    val items: List<Item>,
) {
    data class Item(
        val productId: Long,
        val quantity: Int,
    )
}

data class UpdateViewCountCommand(
    val items: List<Item>,
) {
    data class Item(
        val productId: Long,
    )
}
