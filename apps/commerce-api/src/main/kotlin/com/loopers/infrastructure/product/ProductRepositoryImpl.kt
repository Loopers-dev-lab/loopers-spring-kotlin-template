package com.loopers.infrastructure.product

import com.loopers.domain.like.entity.QLikeCount
import com.loopers.domain.like.vo.LikeTarget.Type.PRODUCT
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.dto.criteria.ProductCriteria
import com.loopers.domain.product.dto.criteria.ProductCriteria.FindAll.ProductSortCondition
import com.loopers.domain.product.entity.Product
import com.loopers.domain.product.entity.QProduct
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

        val sortSpecifier = when (sort) {
            ProductSortCondition.LATEST, ProductSortCondition.CREATED_AT_DESC -> product.createdAt.desc()
            ProductSortCondition.CREATED_AT_ASC -> product.createdAt.asc()
            ProductSortCondition.PRICE_DESC -> product.price.desc()
            ProductSortCondition.PRICE_ASC -> product.price.asc()
            ProductSortCondition.LIKES_DESC -> likeCount.count.value.desc()
            ProductSortCondition.LIKES_ASC -> likeCount.count.value.asc()
        }

        val results = queryFactory
            .select(product)
            .from(product)
            .leftJoin(likeCount)
            .on(
                likeCount.target.targetId.eq(product.id)
                    .and(likeCount.target.type.eq(PRODUCT)),
            )
            .orderBy(sortSpecifier)
            .offset(offset)
            .limit(limit)
            .fetch()

        val totalCount = queryFactory
            .select(product.count())
            .from(product)
            .leftJoin(likeCount)
            .on(
                likeCount.target.targetId.eq(product.id)
                    .and(likeCount.target.type.eq(PRODUCT)),
            )
            .fetchOne() ?: 0L
        return PageImpl(results, pageable, totalCount)
    }

    override fun save(product: Product): Product {
        return productJpaRepository.save(product)
    }
}
