package com.loopers.domain.product

import org.slf4j.LoggerFactory
import org.springframework.data.redis.RedisConnectionFailureException
import org.springframework.data.redis.RedisSystemException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ProductLikeCountService(
    private val productLikeCountRedisRepository: ProductLikeCountRedisRepository,
    private val productRepository: ProductRepository,
) {
    companion object {
        private val logger = LoggerFactory.getLogger(ProductLikeCountService::class.java)
    }

    @Transactional
    fun increment(productId: Long): Long =
        executeWithFallback(
            productId = productId,
            operation = { incrementInRedis(productId) },
            fallback = { incrementInDatabase(productId) },
        )

    @Transactional
    fun decrement(productId: Long): Long =
        executeWithFallback(
            productId = productId,
            operation = { decrementInRedis(productId) },
            fallback = { decrementInDatabase(productId) },
        )

    fun getLikeCount(productId: Long): Long =
        executeWithFallback(
            productId = productId,
            operation = { getFromRedisOrInitialize(productId) },
            fallback = { getFromDatabase(productId) },
        )

    private fun incrementInRedis(productId: Long): Long {
        val result = productLikeCountRedisRepository.incrementIfExists(productId)

        return if (isKeyExists(result)) {
            requireNotNull(result) { "Result should not be null when key exists for productId=$productId" }
        } else {
            initializeAndIncrement(productId)
        }
    }

    private fun decrementInRedis(productId: Long): Long {
        val result = productLikeCountRedisRepository.decrementIfPositive(productId)

        return if (isKeyExists(result)) {
            requireNotNull(result) { "Result should not be null when key exists for productId=$productId" }
        } else {
            initializeAndDecrement(productId)
        }
    }

    private fun getFromRedisOrInitialize(productId: Long): Long {
        val count = productLikeCountRedisRepository.get(productId)
        return count ?: initializeFromDatabase(productId)
    }

    private fun initializeAndIncrement(productId: Long): Long {
        val initialValue = getFromDatabase(productId)
        val result = productLikeCountRedisRepository.initAndIncrement(productId, initialValue)

        return if (result != null && isKeyExists(result)) {
            result
        } else {
            logger.warn("Redis initAndIncrement returned null or KEY_NOT_FOUND, falling back to DB: productId=$productId")
            incrementInDatabase(productId)
        }
    }

    private fun initializeAndDecrement(productId: Long): Long {
        val initialValue = getFromDatabase(productId)
        val result = productLikeCountRedisRepository.initAndDecrementIfPositive(productId, initialValue)

        return if (result != null && isKeyExists(result)) {
            result
        } else {
            logger.warn(
                "Redis initAndDecrementIfPositive returned null or KEY_NOT_FOUND, " +
                        "falling back to DB: productId=$productId",
            )
            decrementInDatabase(productId)
        }
    }

    private fun initializeFromDatabase(productId: Long): Long {
        val likeCount = getFromDatabase(productId)
        val setResult = productLikeCountRedisRepository.setIfAbsent(productId, likeCount)

        if (setResult == null) {
            logger.warn("Redis setIfAbsent returned null, cache initialization may have failed: productId=$productId")
        }

        return productLikeCountRedisRepository.getAfterSetIfAbsent(productId) ?: likeCount
    }

    private fun incrementInDatabase(productId: Long): Long =
        updateLikeCountWithLock(productId) { it + 1 }

    private fun decrementInDatabase(productId: Long): Long =
        updateLikeCountWithLock(productId) { maxOf(0, it - 1) }

    private fun updateLikeCountWithLock(
        productId: Long,
        update: (Long) -> Long,
    ): Long {
        val product = findProductWithLock(productId)
        val newCount = update(product.likeCount)
        product.setLikeCount(newCount)
        productRepository.save(product)
        return newCount
    }

    private fun getFromDatabase(productId: Long): Long =
        productRepository.findById(productId)?.likeCount ?: 0L

    private fun findProductWithLock(productId: Long): Product =
        productRepository.findByIdWithLock(productId)
            ?: throw IllegalArgumentException("Product not found: $productId")

    private fun executeWithFallback(
        productId: Long,
        operation: () -> Long,
        fallback: () -> Long,
    ): Long =
        try {
            operation()
        } catch (e: RedisConnectionFailureException) {
            logger.warn("Redis connection failed, falling back to DB: productId=$productId", e)
            fallback()
        } catch (e: RedisSystemException) {
            logger.error("Redis operation failed, falling back to DB: productId=$productId", e)
            fallback()
        }

    private fun isKeyExists(result: Long?): Boolean =
        result != null && result != ProductLikeCountRedisRepository.KEY_NOT_FOUND
}
