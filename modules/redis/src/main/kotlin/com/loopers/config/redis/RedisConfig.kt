package com.loopers.config.redis

import io.lettuce.core.ReadFrom
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.data.redis.connection.RedisStaticMasterReplicaConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.StringRedisSerializer

@Configuration
@EnableConfigurationProperties(RedisProperties::class)
class RedisConfig(
    private val redisProperties: RedisProperties,
) {
    companion object {
        private const val CONNECTION_MASTER = "redisConnectionMaster"
        const val REDIS_TEMPLATE_MASTER = "redisTemplateMaster"
    }

    @Primary
    @Bean
    fun defaultRedisConnectionFactory(): LettuceConnectionFactory {
        val (database, master, replicas) = redisProperties
        return lettuceConnectionFactory(database, master, replicas) {
            readFrom(ReadFrom.REPLICA_PREFERRED)
        }
    }

    @Qualifier(CONNECTION_MASTER)
    @Bean
    fun masterRedisConnectionFactory(): LettuceConnectionFactory {
        val (database, master, replicas) = redisProperties
        return lettuceConnectionFactory(database, master, replicas) {
            readFrom(ReadFrom.MASTER)
        }
    }

    @Primary
    @Bean
    fun defaultRedisTemplate(
        lettuceConnectionFactory: LettuceConnectionFactory,
    ): RedisTemplate<*, *> {
        return RedisTemplate<String, String>()
            .defaultRedisTemplate(lettuceConnectionFactory)
    }

    @Qualifier(REDIS_TEMPLATE_MASTER)
    @Bean
    fun masterRedisTemplate(
        @Qualifier(CONNECTION_MASTER) lettuceConnectionFactory: LettuceConnectionFactory,
    ): RedisTemplate<*, *> {
        return RedisTemplate<String, String>()
            .defaultRedisTemplate(lettuceConnectionFactory)
    }

    private fun lettuceConnectionFactory(
        database: Int,
        master: RedisNodeInfo,
        replicas: List<RedisNodeInfo>,
        customizer: LettuceClientConfiguration.LettuceClientConfigurationBuilder.() -> Unit = {},
    ): LettuceConnectionFactory {
        val lettuceClientConfiguration = LettuceClientConfiguration.builder()
            .apply(customizer)
            .build()
        val masterReplicaConfig = RedisStaticMasterReplicaConfiguration(master.host, master.port)
        masterReplicaConfig.database = database
        replicas.forEach { replica ->
            masterReplicaConfig.addNode(replica.host, replica.port)
        }

        return LettuceConnectionFactory(masterReplicaConfig, lettuceClientConfiguration)
    }

    private fun <K, V> RedisTemplate<K, V>.defaultRedisTemplate(
        connectionFactory: LettuceConnectionFactory,
    ): RedisTemplate<K, V> {
        this.keySerializer = StringRedisSerializer()
        this.valueSerializer = StringRedisSerializer()
        this.hashKeySerializer = StringRedisSerializer()
        this.hashValueSerializer = StringRedisSerializer()
        setConnectionFactory(connectionFactory)
        return this
    }
}
