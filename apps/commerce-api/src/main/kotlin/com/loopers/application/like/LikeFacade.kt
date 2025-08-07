package com.loopers.application.like

import com.loopers.domain.brand.BrandService
import com.loopers.domain.brand.dto.result.BrandResult
import com.loopers.domain.like.LikeCountService
import com.loopers.domain.like.LikeService
import com.loopers.domain.like.dto.command.LikeCommand.AddLike
import com.loopers.domain.like.dto.command.LikeCommand.RemoveLike
import com.loopers.domain.like.dto.criteria.LikeCriteria
import com.loopers.domain.like.dto.result.LikeCountResult
import com.loopers.domain.like.dto.result.LikeResult
import com.loopers.domain.like.dto.result.LikeResult.LikeDetail
import com.loopers.domain.like.dto.result.LikeWithResult.PageWithProductDetails
import com.loopers.domain.product.ProductService
import com.loopers.domain.product.dto.result.ProductResult
import com.loopers.domain.user.UserService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Transactional(readOnly = true)
@Component
class LikeFacade(
    private val likeService: LikeService,
    private val likeCountService: LikeCountService,
    private val userService: UserService,
    private val productService: ProductService,
    private val brandService: BrandService,
) {
    fun getLikesForProduct(criteria: LikeCriteria.FindAll): PageWithProductDetails {
        val likePage = likeService.findAll(criteria)
        val likeDetails = LikeResult.LikePageDetails.from(likePage)

        val productIds = likeDetails.likes.data.map { it.targetId }
        val products = productService.findAll(productIds)
        val productDetails = ProductResult.ProductDetails.from(products)

        val brandIds = productDetails.products.map { it.brandId }.distinct()
        val brands = brandService.findAll(brandIds)
        val brandDetails = BrandResult.BrandDetails.from(brands)

        val likes = likeCountService.getLikeCounts(productIds, criteria.type)
        val likeCountDetails = LikeCountResult.LikeCountDetails.from(likes)

        return PageWithProductDetails.from(likeDetails, productDetails, brandDetails, likeCountDetails)
    }

    @Transactional
    fun addLike(command: AddLike): LikeDetail {
        productService.get(command.targetId)

        return likeService.add(command).let {
            if (it.isNew) {
                val likeCount = likeCountService.getLikeCountWithLock(command.targetId, command.type)
                likeCount.increase()
            }
            LikeDetail.from(it.like)
        }
    }

    @Transactional
    fun removeLike(command: RemoveLike) {
        productService.get(command.targetId)

        val likeCount = likeCountService.getLikeCountWithLock(command.targetId, command.type)
        likeCount.decrease()
    }
}
