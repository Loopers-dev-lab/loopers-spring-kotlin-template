package com.loopers.domain.brand

import com.loopers.domain.BaseEntity
import com.loopers.domain.product.Product
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.OneToMany
import jakarta.persistence.Table

@Entity
@Table(name = "brands")
class Brand(
    name: String,
    description: String?,
): BaseEntity(){
    @Column(name="name", nullable = false, length = 100)
    var name: String = name
        protected set

    @Column(name="description", columnDefinition = "TEXT")
    var description: String? = description
        protected set

    @OneToMany(mappedBy = "brand", cascade = [CascadeType.ALL], orphanRemoval = true)
    protected val mutableProducts: MutableList<Product> = mutableListOf()

    val products: List<Product>
        get() = mutableProducts.toList()

    fun addProduct(product: Product) {
        mutableProducts.add(product)
    }

}
