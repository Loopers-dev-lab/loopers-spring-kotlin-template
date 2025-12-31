package com.loopers.batch.listener

import org.slf4j.LoggerFactory
import org.springframework.batch.core.annotation.AfterChunk
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.stereotype.Component

@Component
class ChunkListener {
    private val log = LoggerFactory.getLogger(ChunkListener::class.java)

    @AfterChunk
    fun afterChunk(chunkContext: ChunkContext) {
        log.info(
            "청크 종료: readCount: ${chunkContext.stepContext.stepExecution.readCount}, " +
                    "writeCount: ${chunkContext.stepContext.stepExecution.writeCount}",
        )
    }
}
