package com.loopers.support.config

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.loopers.application.dto.PageResult
import com.loopers.application.product.ProductResult
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.StringRedisSerializer

@Configuration
class RedisTemplateConfig {

    @Bean
    fun redisObjectMapper(): ObjectMapper {
        return ObjectMapper()
            .registerKotlinModule()
            .registerModule(JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }

    @Bean
    fun productListRedisTemplate(
        connectionFactory: RedisConnectionFactory,
        redisObjectMapper: ObjectMapper,
    ): RedisTemplate<String, PageResult<ProductResult.ListInfo>> {
        val pageType = redisObjectMapper.typeFactory.constructParametricType(
            PageResult::class.java,
            ProductResult.ListInfo::class.java,
        )
        return redisTemplate(connectionFactory, redisObjectMapper, pageType)
    }

    @Bean
    fun productDetailRedisTemplate(
        connectionFactory: RedisConnectionFactory,
        objectMapper: ObjectMapper,
    ): RedisTemplate<String, ProductResult.DetailInfo> {
        return redisTemplate(connectionFactory, objectMapper, ProductResult.DetailInfo::class.java)
    }

    @Bean
    fun productLikedListRedisTemplate(
        connectionFactory: RedisConnectionFactory,
        redisObjectMapper: ObjectMapper,
    ): RedisTemplate<String, PageResult<ProductResult.LikedInfo>> {
        val pageType = redisObjectMapper.typeFactory.constructParametricType(
            PageResult::class.java,
            ProductResult.LikedInfo::class.java,
        )
        return redisTemplate(connectionFactory, redisObjectMapper, pageType)
    }

    // 단일 객체용
    private fun <T> redisTemplate(
        connectionFactory: RedisConnectionFactory,
        redisObjectMapper: ObjectMapper,
        clazz: Class<T>,
    ): RedisTemplate<String, T> {
        val serializer = Jackson2JsonRedisSerializer(redisObjectMapper, clazz)
        return RedisTemplate<String, T>().apply {
            setConnectionFactory(connectionFactory)
            keySerializer = StringRedisSerializer()
            valueSerializer = serializer
            hashKeySerializer = StringRedisSerializer()
            hashValueSerializer = serializer
        }
    }

    // 리스트/컬렉션 타입용
    private fun <T> redisTemplate(
        connectionFactory: RedisConnectionFactory,
        redisObjectMapper: ObjectMapper,
        javaType: JavaType,
    ): RedisTemplate<String, T> {
        val serializer = Jackson2JsonRedisSerializer<T>(redisObjectMapper, javaType)
        return RedisTemplate<String, T>().apply {
            setConnectionFactory(connectionFactory)
            keySerializer = StringRedisSerializer()
            valueSerializer = serializer
            hashKeySerializer = StringRedisSerializer()
            hashValueSerializer = serializer
        }
    }
}
