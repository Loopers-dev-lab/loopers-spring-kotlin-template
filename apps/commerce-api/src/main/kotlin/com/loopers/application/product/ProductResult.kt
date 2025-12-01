package com.loopers.application.product

import com.loopers.domain.brand.Brand
import com.loopers.domain.like.ProductLike
import com.loopers.domain.like.ProductLikeCount
import com.loopers.domain.product.Product
import java.time.ZonedDateTime

object ProductResult {

    data class ListInfo(
        val id: Long,
        val name: String,
        val price: Long,
        val brandName: String,
        val likeCount: Long,
    ) {
        companion object {
            fun from(
                product: Product,
                productLikeCounts: List<ProductLikeCount>,
                brands: List<Brand>,
            ): ListInfo {
                val brand = brands.find { it.id == product.brandId }!!
                val likeCount = productLikeCounts.find { it.productId == product.id }!!.likeCount

                return ListInfo(
                    id = product.id,
                    name = product.name,
                    price = product.price,
                    brandName = brand.name,
                    likeCount = likeCount,
                )
            }
        }
    }

    data class DetailInfo(
        val id: Long,
        val name: String,
        val price: Long,
        val brandName: String,
        val likeCount: Long,
        val likedByMe: Boolean,
    ) {
        companion object {
            fun from(
                product: Product,
                userLiked: Boolean,
                productLikeCount: Long,
                brand: Brand,
            ): DetailInfo {
                return DetailInfo(
                    id = product.id,
                    name = product.name,
                    price = product.price,
                    brandName = brand.name,
                    likeCount = productLikeCount,
                    likedByMe = userLiked,
                )
            }
        }
    }

    data class LikedInfo(
        val id: Long,
        val name: String,
        val price: Long,
        val brandName: String,
        val likedCreatedAt: ZonedDateTime,
    ) {
        companion object {
            fun from(
                productLike: ProductLike,
                products: List<Product>,
                brands: List<Brand>,
            ): LikedInfo {
                val product = products.find { it.id == productLike.productId }!!
                val brand = brands.find { it.id == product.brandId }!!

                return LikedInfo(
                    id = product.id,
                    name = product.name,
                    price = product.price,
                    brandName = brand.name,
                    likedCreatedAt = productLike.createdAt,
                )
            }
        }
    }
}
