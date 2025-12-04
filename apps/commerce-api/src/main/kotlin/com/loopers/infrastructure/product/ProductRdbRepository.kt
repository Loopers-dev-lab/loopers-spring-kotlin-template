package com.loopers.infrastructure.product

import com.loopers.domain.product.PageQuery
import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.ProductSortType
import com.loopers.domain.product.QProduct
import com.loopers.domain.product.QProductStatistic
import com.querydsl.core.types.OrderSpecifier
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Slice
import org.springframework.data.domain.SliceImpl
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class ProductRdbRepository(
    private val queryFactory: JPAQueryFactory,
    private val productJpaRepository: ProductJpaRepository,
) : ProductRepository {
    @Transactional(readOnly = true)
    override fun findAllBy(query: PageQuery): Slice<Product> {
        val qProduct = QProduct.product
        val qStatistic = QProductStatistic.productStatistic

        val baseQuery = queryFactory
            .selectFrom(qProduct)
            .apply {
                if (query.sort == ProductSortType.LIKES_DESC) {
                    innerJoin(qStatistic)
                        .on(qProduct.id.eq(qStatistic.productId))
                }
            }
            .where(
                query.brandId?.let { qProduct.brandId.eq(it) },
            )
            .orderBy(*getOrderSpecifiers(query.sort, qProduct, qStatistic))
            .offset((query.page * query.size).toLong())
            .limit((query.size + 1).toLong())

        val results = baseQuery.fetch()

        val hasNext = results.size > query.size
        val content = if (hasNext) results.dropLast(1) else results

        return SliceImpl(
            content,
            PageRequest.of(query.page, query.size),
            hasNext,
        )
    }

    private fun getOrderSpecifiers(
        sort: ProductSortType,
        qProduct: QProduct,
        qStatistic: QProductStatistic,
    ): Array<OrderSpecifier<*>> {
        return when (sort) {
            ProductSortType.LATEST -> arrayOf(
                qProduct.id.desc(),
            )

            ProductSortType.PRICE_ASC -> arrayOf(
                qProduct.price.amount.asc(),
                qProduct.id.desc(),
            )

            ProductSortType.LIKES_DESC -> arrayOf(
                qStatistic.likeCount.desc(),
                qStatistic.productId.desc(),
            )
        }
    }

    @Transactional(readOnly = true)
    override fun findAllByIds(ids: List<Long>): List<Product> {
        if (ids.isEmpty()) return emptyList()
        return productJpaRepository.findAllById(ids)
    }

    @Transactional
    override fun findAllByIdsWithLock(ids: List<Long>): List<Product> {
        if (ids.isEmpty()) return emptyList()
        return productJpaRepository.findAllByIdsWithLock(ids)
    }

    @Transactional(readOnly = true)
    override fun findById(id: Long): Product? {
        return productJpaRepository.findById(id).orElse(null)
    }

    @Transactional
    override fun save(product: Product): Product {
        return productJpaRepository.save(product)
    }

    @Transactional
    override fun saveAll(products: List<Product>): List<Product> {
        if (products.isEmpty()) return emptyList()
        return productJpaRepository.saveAll(products)
    }
}
