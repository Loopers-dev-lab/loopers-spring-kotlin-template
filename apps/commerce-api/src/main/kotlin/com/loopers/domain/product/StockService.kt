package com.loopers.domain.product

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.PessimisticLockException
import org.springframework.dao.CannotAcquireLockException
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class StockService(
    private val stockRepository: StockRepository,
) {
    fun getStockByProductId(productId: Long): Stock {
        return stockRepository.findByProductId(productId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "재고 정보를 찾을 수 없습니다: $productId")
    }

    fun validateStockAvailability(stock: Stock, productName: String, requestedQuantity: Int) {
        if (!stock.isAvailable(requestedQuantity)) {
            throw CoreException(
                ErrorType.BAD_REQUEST,
                "재고 부족: 상품 $productName, 현재 재고 ${stock.quantity}, 요청 수량 $requestedQuantity",
            )
        }
    }

    @Transactional(timeout = 5)
    @Retryable(
        retryFor = [PessimisticLockException::class, CannotAcquireLockException::class],
        maxAttempts = 3,
        backoff = Backoff(delay = 100, multiplier = 2.0),
    )
    fun decreaseStock(productId: Long, quantity: Int): Stock {
        val stock = stockRepository.findByProductIdWithLock(productId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "재고 정보를 찾을 수 없습니다: $productId")
        stock.decrease(quantity)
        return stockRepository.save(stock)
    }

    @Transactional(timeout = 5)
    @Retryable(
        retryFor = [PessimisticLockException::class, CannotAcquireLockException::class],
        maxAttempts = 3,
        backoff = Backoff(delay = 100, multiplier = 2.0),
    )
    fun increaseStock(productId: Long, quantity: Int): Stock {
        val stock = stockRepository.findByProductIdWithLock(productId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "재고 정보를 찾을 수 없습니다: $productId")
        stock.increase(quantity)
        return stockRepository.save(stock)
    }
}
