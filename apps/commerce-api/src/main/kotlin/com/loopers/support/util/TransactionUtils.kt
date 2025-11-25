package com.loopers.support.util

import org.slf4j.LoggerFactory
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

object TransactionUtils {

    private val log = LoggerFactory.getLogger(TransactionUtils::class.java)

    /**
     * 트랜잭션 커밋 후 실행할 작업을 등록합니다.
     * 트랜잭션이 활성화되지 않은 경우 즉시 실행됩니다.
     *
     * @param action 커밋 후 실행할 작업
     */
    fun executeAfterCommit(action: () -> Unit) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                object : TransactionSynchronization {
                    override fun afterCommit() {
                        try {
                            action()
                        } catch (e: Exception) {
                            log.error("트랜잭션 커밋 후 작업 실행 중 오류 발생", e)
                        }
                    }
                },
            )
        } else {
            try {
                action()
            } catch (e: Exception) {
                log.error("트랜잭션 없이 작업 실행 중 오류 발생", e)
            }
        }
    }
}
