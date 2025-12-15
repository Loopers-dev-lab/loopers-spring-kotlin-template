package com.loopers.infrastructure.product

import com.loopers.domain.product.ProductEvent
import com.loopers.domain.product.ProductEventPublisher
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

@Component
class ProductApplicationEventPublisher(
        private val applicationEventPublisher: ApplicationEventPublisher
) : ProductEventPublisher {
    override fun publish(event: ProductEvent) = applicationEventPublisher.publishEvent(event)
}
