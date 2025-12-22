package com.loopers.infrastructure.product

import com.loopers.application.product.ProductInfo
import com.loopers.application.ranking.ProductWithBrand
import com.loopers.domain.brand.QBrandModel
import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.QProductModel
import com.loopers.domain.product.signal.QProductTotalSignalModel
import com.loopers.domain.product.stock.QStockModel
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.querydsl.core.types.OrderSpecifier
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

@Component
class ProductRepositoryImpl(
    private val productJpaRepository: ProductJpaRepository,
    private val productJpaQueryFactory: JPAQueryFactory,
) : ProductRepository {

    override fun findById(productId: Long): ProductModel? = productJpaRepository.findByIdOrNull(productId)

    override fun save(product: ProductModel): ProductModel = productJpaRepository.save(product)

    override fun findAllProductInfos(pageable: Pageable, brandId: Long?): Page<ProductInfo> {
        val product = QProductModel.productModel
        val brand = QBrandModel.brandModel
        val stock = QStockModel.stockModel
        val signal = QProductTotalSignalModel.productTotalSignalModel

        val orderSpecifiers = mutableListOf<OrderSpecifier<*>>()

        pageable.sort.forEach { order ->
            val orderSpecifier = when (order.property) {
                "likeCount" -> if (order.isAscending) {
                    signal.likeCount.coalesce(0L).asc()
                } else {
                    signal.likeCount.coalesce(0L).desc()
                }

                "name" -> if (order.isAscending) product.name.asc() else product.name.desc()
                "price" -> if (order.isAscending) product.price.asc() else product.price.desc()
                "createdAt" -> if (order.isAscending) product.createdAt.asc() else product.createdAt.desc()
                else -> null
            }
            orderSpecifier?.let { orderSpecifiers.add(it) }
        }

        val query = productJpaQueryFactory
            .select(
                Projections.constructor(
                    ProductInfo::class.java,
                    product.id,
                    product.name,
                    stock.amount.coalesce(0L),
                    product.price,
                    signal.likeCount.coalesce(0L),
                    product.refBrandId,
                    brand.name,
                ),
            )
            .from(product)
            .leftJoin(signal).on(signal.refProductId.eq(product.id))
            .leftJoin(brand).on(brand.id.eq(product.refBrandId))
            .leftJoin(stock).on(stock.refProductId.eq(product.id))

        // 브랜드 필터링
        brandId?.let {
            query.where(product.refBrandId.eq(it))
        }

        if (orderSpecifiers.isNotEmpty()) {
            query.orderBy(*orderSpecifiers.toTypedArray())
        }

        val results = query
            .offset(pageable.offset)
            .limit(pageable.pageSize.toLong())
            .fetch()

        val countQuery = productJpaQueryFactory
            .select(product.count())
            .from(product)

        brandId?.let {
            countQuery.where(product.refBrandId.eq(it))
        }

        val total = countQuery.fetchOne() ?: 0L

        return PageImpl(results, pageable, total)
    }

    override fun getProductBy(productId: Long): ProductModel =
        productJpaRepository.findByIdOrNull(productId) ?: throw CoreException(ErrorType.BAD_REQUEST, "존재하지 않는 상품입니다.")

    override fun findByIdsIn(productIds: List<Long>): List<ProductModel> = productJpaRepository.findAllById(productIds)

    override fun findByIdsInWithBrand(productIds: List<Long>): List<ProductWithBrand> {
        if (productIds.isEmpty()) return emptyList()

        val product = QProductModel.productModel
        val brand = QBrandModel.brandModel

        return productJpaQueryFactory
            .select(
                Projections.constructor(
                    ProductWithBrand::class.java,
                    product,
                    brand,
                ),
            )
            .from(product)
            .innerJoin(brand).on(brand.id.eq(product.refBrandId))
            .where(product.id.`in`(productIds))
            .fetch()
    }
}
