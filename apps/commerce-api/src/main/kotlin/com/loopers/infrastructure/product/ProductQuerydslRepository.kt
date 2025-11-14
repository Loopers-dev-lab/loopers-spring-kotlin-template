package com.loopers.infrastructure.product

import com.loopers.domain.brand.QBrand.brand
import com.loopers.domain.common.PageCommand
import com.loopers.domain.common.PageResult
import com.loopers.domain.like.QLike.like
import com.loopers.domain.product.QProduct.product
import com.querydsl.core.types.OrderSpecifier
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Repository

@Repository
class ProductQuerydslRepository(
    private val queryFactory: JPAQueryFactory,
) {

    fun findProducts(pageCommand: PageCommand): PageResult<ProductWithDetailsProjection> {
        val totalCount = queryFactory
            .select(product.count())
            .from(product)
            .where(product.deletedAt.isNull)
            .fetchOne() ?: 0L

        val items = queryFactory
            .select(
                QProductWithDetailsProjection(
                    product.id,
                    product.name,
                    product.price,
                    brand.id,
                    brand.name,
                    like.count(),
                    product.createdAt,
                    product.updatedAt,
                ),
            )
            .from(product)
            .leftJoin(brand)
            .on(
                product.brandId.eq(brand.id),
            )
            .leftJoin(like)
            .on(
                product.id.eq(like.productId),
                like.deletedAt.isNull,
            )
            .where(product.deletedAt.isNull)
            .groupBy(
                product.id,
                product.name,
                product.price,
                brand.id,
                brand.name,
                product.createdAt,
                product.updatedAt,
            )
            .orderBy(*createOrderSpecifier(pageCommand.sort))
            .offset(pageCommand.offset)
            .limit(pageCommand.pageSize)
            .fetch()

        return PageResult(
            items = items,
            pageNumber = pageCommand.pageNumber,
            pageSize = pageCommand.pageSize,
            hasNext = pageCommand.hasNext(totalCount),
            totalCount = totalCount,
        )
    }

    private fun createOrderSpecifier(sortBy: List<PageCommand.SortCondition>): Array<out OrderSpecifier<*>> {
        return buildList {
            sortBy.forEach { (field, sortDirection) ->
                val path = when (field) {
                    "createdAt" -> product.createdAt
                    "price" -> product.price
                    "likeCount" -> like.count()
                    "name" -> product.name
                    else -> product.createdAt
                }

                val orderSpecifier = when (sortDirection) {
                    PageCommand.SortDirection.ASC -> path.asc()
                    PageCommand.SortDirection.DESC -> path.desc()
                }

                add(orderSpecifier)
            }
        }.toTypedArray()
    }
}
