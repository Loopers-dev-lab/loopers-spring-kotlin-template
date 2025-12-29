package com.loopers.domain.product

import com.loopers.domain.event.StockDepletedEvent
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.PessimisticLockException
import org.springframework.context.ApplicationEventPublisher
import org.springframework.dao.CannotAcquireLockException
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class StockService(private val stockRepository: StockRepository, private val eventPublisher: ApplicationEventPublisher) {
    fun getStockByProductId(productId: Long): Stock = stockRepository.findByProductId(productId)
        ?: throw CoreException(ErrorType.NOT_FOUND, "재고 정보를 찾을 수 없습니다: $productId")

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

        val previousQuantity = stock.quantity
        stock.decrease(quantity)
        val updatedStock = stockRepository.save(stock)

        // 재고가 소진되었을 때 이벤트 발행
        if (updatedStock.quantity == 0 && previousQuantity > 0) {
            eventPublisher.publishEvent(
                StockDepletedEvent.create(
                    productId = productId,
                    previousQuantity = previousQuantity,
                ),
            )
        }

        return updatedStock
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
