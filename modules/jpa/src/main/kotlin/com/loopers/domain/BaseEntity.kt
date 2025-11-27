package com.loopers.domain

import jakarta.persistence.Column
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import java.time.ZonedDateTime

@MappedSuperclass
abstract class BaseEntity(
    id: Long = 0,
    createdAt: ZonedDateTime? = null,
    updatedAt: ZonedDateTime? = null,
    deletedAt: ZonedDateTime? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = id
        private set

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: ZonedDateTime = createdAt ?: ZonedDateTime.now()
        private set

    @Column(name = "updated_at", nullable = false)
    var updatedAt: ZonedDateTime = updatedAt ?: ZonedDateTime.now()
        private set

    @Column(name = "deleted_at")
    var deletedAt: ZonedDateTime? = deletedAt
        private set

    open fun guard() = Unit

    @PrePersist
    private fun prePersist() {
        guard()
        val now = ZonedDateTime.now()
        if (id == 0L) {
            createdAt = now
            updatedAt = now
        }
    }

    @PreUpdate
    private fun preUpdate() {
        guard()
        updatedAt = ZonedDateTime.now()
    }

    fun delete() {
        deletedAt ?: run { deletedAt = ZonedDateTime.now() }
    }

    fun restore() {
        deletedAt?.let { deletedAt = null }
    }
}
