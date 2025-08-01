package com.loopers.domain.product.dto.result

import com.loopers.domain.common.PageResult
import com.loopers.domain.product.entity.Product
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.math.BigDecimal
import java.time.ZonedDateTime

class ProductResult {
    data class ProductDetail(
        val id: Long,
        val brandId: Long,
        val name: String,
        val description: String,
        val price: BigDecimal,
        val createAt: ZonedDateTime,
        val updateAt: ZonedDateTime,
    ) {
        companion object {
            fun from(product: Product): ProductDetail {
                return ProductDetail(
                    product.id,
                    product.brandId,
                    product.name.value,
                    product.description.value,
                    product.price.value,
                    product.createdAt,
                    product.updatedAt,
                )
            }
        }
    }

    data class ProductDetails(
        val products: List<ProductDetail>,
    ) {
        companion object {
            fun from(products: List<Product>): ProductDetails {
                return ProductDetails(
                    products.map { ProductDetail.from(it) },
                )
            }
        }
    }

    data class ProductPageDetails(
        val products: PageResult<ProductDetail>,
    ) {
        companion object {
            fun from(products: Page<Product>): ProductPageDetails {
                return ProductPageDetails(
                    PageResult.from(products) { ProductDetail.from(it) },
                )
            }

            fun from(products: List<Product>, page: PageRequest): ProductPageDetails {
                val page = PageImpl(products)
                return ProductPageDetails(
                    PageResult.from(page) { ProductDetail.from(it) },
                )
            }
        }
    }
}
