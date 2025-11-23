package com.loopers.application.like

import com.loopers.domain.like.ProductLikeService
import com.loopers.domain.product.ProductService
import com.loopers.domain.user.UserService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class ProductLikeFacade(
    private val productLikeService: ProductLikeService,
    private val productService: ProductService,
    private val userService: UserService,
) {

    private val log = LoggerFactory.getLogger(ProductLikeFacade::class.java)

    @Transactional
    fun like(productId: Long, userId: String) {
        val user = userService.getMyInfo(userId)

        val product =
            productService.getProduct(productId) ?: throw CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다: $productId")

        try {
            productLikeService.like(product, user)
        } catch (e: DataIntegrityViolationException) {
            // 이미 좋아요가 존재하는 경우 - 무시 (멱등성)
            // 예외를 상위로 전파하지 않고 조용히 처리
            log.debug("중복 좋아요 시도 무시: productId=${product.id}, userId=${user.id}")
        }
    }

    @Transactional
    fun unlike(productId: Long, userId: String) {
        val user = userService.getMyInfo(userId)

        val product =
            productService.getProduct(productId) ?: throw CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다: $productId")

        productLikeService.unlike(product, user)
    }
}
