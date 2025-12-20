package com.loopers.domain

import jakarta.persistence.Column
import jakarta.persistence.EntityListeners
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.ZonedDateTime

@MappedSuperclass
@EntityListeners(AuditingEntityListener::class)
abstract class BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @CreatedDate
    @Column(nullable = false, updatable = false)
    lateinit var createdAt: ZonedDateTime

    @LastModifiedDate
    @Column(nullable = false)
    lateinit var updatedAt: ZonedDateTime

    @Column
    var deletedAt: ZonedDateTime? = null

    /**
     * 소프트 삭제 (멱등)
     */
    fun delete() {
        if (deletedAt == null) {
            deletedAt = ZonedDateTime.now()
        }
    }

    /**
     * 복구 (멱등)
     */
    fun restore() {
        deletedAt = null
    }

    /**
     * 삭제 여부 확인
     */
    fun isDeleted(): Boolean = deletedAt != null
}
