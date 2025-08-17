package com.loopers.infrastructure.product

import com.loopers.domain.like.entity.QLikeCount
import com.loopers.domain.like.vo.LikeTarget.Type.PRODUCT
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.dto.criteria.ProductCriteria
import com.loopers.domain.product.dto.criteria.ProductCriteria.FindAll.ProductSortCondition
import com.loopers.domain.product.entity.Product
import com.loopers.domain.product.entity.QProduct
import com.querydsl.core.types.OrderSpecifier
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

@Component
class ProductRepositoryImpl(
    private val productJpaRepository: ProductJpaRepository,
    private val queryFactory: JPAQueryFactory,
) : ProductRepository {
    override fun find(id: Long): Product? {
        return productJpaRepository.findByIdOrNull(id)
    }

    override fun findAll(ids: List<Long>): List<Product> {
        return productJpaRepository.findAllById(ids)
    }

    override fun findAll(
        criteria: ProductCriteria.FindAll,
    ): List<Product> {
        val product = QProduct.product
        val likeCount = QLikeCount.likeCount

        val sort = criteria.sort
        val pageable = criteria.toPageRequest()
        val offset = pageable.offset
        val limit = pageable.pageSize.toLong()

        // 정렬 안정성 확보: 각 정렬 조건에 product.id를 서브 정렬로 추가
        val sortSpecifiers: List<OrderSpecifier<*>> = when (sort) {
            ProductSortCondition.LATEST,
            ProductSortCondition.CREATED_AT_DESC,
            ->
                listOf(product.createdAt.desc(), product.id.desc())

            ProductSortCondition.CREATED_AT_ASC ->
                listOf(product.createdAt.asc(), product.id.asc())

            ProductSortCondition.PRICE_DESC ->
                listOf(product.price.desc(), product.id.desc())

            ProductSortCondition.PRICE_ASC ->
                listOf(product.price.asc(), product.id.asc())

            ProductSortCondition.LIKES_DESC ->
                listOf(likeCount.count.value.desc(), likeCount.target.targetId.desc())

            ProductSortCondition.LIKES_ASC ->
                listOf(likeCount.count.value.asc(), likeCount.target.targetId.asc())
        }

        return queryFactory
            .select(product)
            .from(product)
            .innerJoin(likeCount)
            .on(
                likeCount.target.targetId.eq(product.id)
                    .and(likeCount.target.type.eq(PRODUCT)),
            )
            .where(
                product.deletedAt.isNull,
                criteria.brandIds
                    .takeIf { it.isNotEmpty() }
                    ?.let { product.brandId.`in`(it) },
            )
            .orderBy(*sortSpecifiers.toTypedArray())
            .offset(offset)
            .limit(limit)
            .fetch()
    }

    override fun count(criteria: ProductCriteria.FindAll): Long {
        val product = QProduct.product
        return queryFactory
            .select(product.count())
            .from(product)
            .where(
                product.deletedAt.isNull,
                criteria.brandIds
                    .takeIf { it.isNotEmpty() }
                    ?.let { product.brandId.`in`(it) },
            )
            .fetchOne() ?: 0L
    }

    override fun save(product: Product): Product {
        return productJpaRepository.save(product)
    }

    override fun delete(id: Long) {
        productJpaRepository.deleteById(id)
    }
}
