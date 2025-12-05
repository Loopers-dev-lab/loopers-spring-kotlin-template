package com.loopers.domain.product

import com.loopers.domain.BaseEntity
import com.loopers.support.error.CoreException
import com.loopers.support.values.Money
import jakarta.persistence.AttributeOverride
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table

@Entity
@Table(
    name = "products",
    indexes = [
        Index(
            name = "idx_products_price",
            columnList = "price ASC, id DESC",
        ),
        Index(
            name = "idx_products_brand_id",
            columnList = "brand_id",
        ),
    ],
)
class Product(
    brandId: Long,
    name: String,
    price: Money,
    status: ProductStatus,
    stock: Stock,
) : BaseEntity() {
    @Column(name = "brand_id", nullable = false)
    var brandId: Long = brandId
        private set

    @Column(name = "name", nullable = false)
    var name: String = name
        private set

    @Embedded
    @AttributeOverride(name = "amount", column = Column(name = "price", nullable = false))
    var price: Money = price
        private set

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    var status: ProductStatus = status
        private set

    @Column(name = "stock", nullable = false)
    @AttributeOverride(name = "amount", column = Column(name = "stock", nullable = false))
    var stock: Stock = stock
        private set

    companion object {
        fun create(
            name: String,
            price: Money,
            stock: Stock,
            brand: Brand,
        ): Product {
            val status = determineStatus(stock)
            return Product(
                brandId = brand.id,
                name = name,
                price = price,
                status = status,
                stock = stock,
            )
        }

        fun of(
            brandId: Long,
            name: String,
            price: Money,
            status: ProductStatus,
            stock: Stock,
        ): Product {
            return Product(
                brandId = brandId,
                name = name,
                price = price,
                status = status,
                stock = stock,
            )
        }

        /**
         * Stock 양에 따라 적절한 ProductStatus를 결정합니다.
         *
         * - 재고 0: OUT_OF_STOCK
         * - 재고 1 이상: ACTIVE
         */
        private fun determineStatus(stock: Stock): ProductStatus {
            return if (stock.amount == 0) {
                ProductStatus.OUT_OF_STOCK
            } else {
                ProductStatus.ACTIVE
            }
        }
    }

    /**
     * 재고를 감소시키고, 재고가 소진되면 품절 상태로 변경합니다.
     *
     * @param amount 감소시킬 재고량
     * @throws CoreException 재고가 부족하거나 유효하지 않은 값인 경우
     */
    fun decreaseStock(amount: Int) {
        val decreasedStock = this.stock.decrease(amount)
        this.stock = decreasedStock
        this.status = determineStatus(this.stock)
    }

    /**
     * 재고를 복구합니다. 결제 실패 시 감소했던 재고를 되돌립니다.
     *
     * @param amount 복구할 재고량
     */
    fun increaseStock(amount: Int) {
        val increasedStock = this.stock.increase(amount)
        this.stock = increasedStock
        this.status = determineStatus(this.stock)
    }
}
