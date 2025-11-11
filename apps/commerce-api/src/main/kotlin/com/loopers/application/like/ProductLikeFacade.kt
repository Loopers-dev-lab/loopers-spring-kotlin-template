package com.loopers.application.like

import com.loopers.domain.like.ProductLikeService
import com.loopers.domain.product.ProductService
import com.loopers.domain.user.UserService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class ProductLikeFacade(
    private val productLikeService: ProductLikeService,
    private val productService: ProductService,
    private val userService: UserService,
) {

    @Transactional
    fun like(productId: Long, userId: String) {
        val user = userService.getMyInfo(userId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "유저를 찾을 수 없습니다: $userId")

        val product =
            productService.getProduct(productId) ?: throw CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다: $productId")

        productLikeService.like(product, user)
    }

    @Transactional
    fun unlike(productId: Long, userId: String) {
        val user = userService.getMyInfo(userId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "유저를 찾을 수 없습니다: $userId")

        val product =
            productService.getProduct(productId) ?: throw CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다: $productId")

        productLikeService.unlike(product, user)
    }
}
