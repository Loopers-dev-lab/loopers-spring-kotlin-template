package com.loopers.batch.listener

import org.slf4j.LoggerFactory
import org.springframework.batch.core.listener.ChunkListener
import org.springframework.batch.infrastructure.item.Chunk
import org.springframework.stereotype.Component

@Component
class ChunkMonitorListener : ChunkListener<Any, Any> {
    private val log = LoggerFactory.getLogger(ChunkListener::class.java)

    override fun beforeChunk(chunk: Chunk<Any>) {
        log.debug("Chunk 시작: size=${chunk.items.size}")
    }

    override fun afterChunk(chunk: Chunk<Any>) {
        log.debug("Chunk 완료: size=${chunk.items.size}")
    }

    override fun onChunkError(exception: Exception, chunk: Chunk<Any>) {
        log.error("Chunk 에러 발생: size=${chunk.items.size}, error=${exception.message}", exception)
    }
}
