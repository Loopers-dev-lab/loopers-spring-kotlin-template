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
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
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
    ): Page<Product> {
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

        val results = queryFactory
            .select(product)
            .from(product)
            .innerJoin(likeCount)
            .on(
                likeCount.target.targetId.eq(product.id)
                    .and(likeCount.target.type.eq(PRODUCT)),
            )
            .where(product.deletedAt.isNull)
            .orderBy(*sortSpecifiers.toTypedArray())
            .offset(offset)
            .limit(limit)
            .fetch()

        val totalCount = queryFactory
            .select(product.count())
            .from(product)
            .innerJoin(likeCount)
            .on(
                likeCount.target.targetId.eq(product.id)
                    .and(likeCount.target.type.eq(PRODUCT)),
            )
            .where(product.deletedAt.isNull)
            .fetchOne() ?: 0L

        return PageImpl(results, pageable, totalCount)
    }

    override fun save(product: Product): Product {
        return productJpaRepository.save(product)
    }
}
