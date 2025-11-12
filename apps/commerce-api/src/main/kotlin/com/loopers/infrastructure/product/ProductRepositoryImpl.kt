package com.loopers.infrastructure.product

import com.loopers.domain.common.PageCommand
import com.loopers.domain.common.PageResult
import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.ProductResult
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

@Component
class ProductRepositoryImpl(
    private val jpa: ProductJpaRepository,
    private val querydsl: ProductQuerydslRepository,
) : ProductRepository {
    override fun save(product: Product): Product {
        return jpa.save(product)
    }

    override fun findById(id: Long): Product? {
        return jpa.findByIdOrNull(id)
    }

    override fun getProducts(pageCommand: PageCommand): PageResult<ProductResult.ProductInfo> {
        val projectionResult = querydsl.findProducts(pageCommand)

        val productInfoItems = projectionResult.items.map { projection ->
            ProductResult.ProductInfo(
                id = projection.productId,
                name = projection.productName,
                price = projection.price,
                brand = ProductResult.ProductInfo.BrandInfo(
                    id = projection.brandId,
                    name = projection.brandName,
                ),
                likeCount = projection.likeCount,
                createdAt = projection.createdAt,
                updatedAt = projection.updatedAt,
            )
        }

        return PageResult(
            items = productInfoItems,
            pageNumber = projectionResult.pageNumber,
            pageSize = projectionResult.pageSize,
            hasNext = projectionResult.hasNext,
            totalCount = projectionResult.totalCount,
        )
    }
}
