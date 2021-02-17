package jun.example.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnection;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class SerializerConfig {

    /**
     * ObjectMapper provides functionality for reading and writing JSON,
     * either to and from basic POJOs (Plain Old Java Objects), or to and
     * from a general-purpose JSON Tree Model (JsonNode), as well as related
     * functionality for performing conversions.
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        objectMapper.configure(SerializationFeature
                .WRITE_DATES_AS_TIMESTAMPS, false);
        objectMapper.configure(DeserializationFeature
                .ADJUST_DATES_TO_CONTEXT_TIME_ZONE, false);
        return objectMapper;
    }

    private LettuceConnectionFactory redisConnectionFactory;

    private RedisConnectionFactory redisConnectionFactory() {
        return this.redisConnectionFactory;
    }

    @Autowired
    private void configureLettuceConnectionFactory(
            LettuceConnectionFactory redisConnectionFactory) {
        this.redisConnectionFactory = redisConnectionFactory;
        redisConnectionFactory.setShareNativeConnection(false);
        redisConnectionFactory.setPipeliningFlushPolicy(
                LettuceConnection.PipeliningFlushPolicy.flushOnClose());
    }

    @Bean
    @ConditionalOnMissingBean(name = "redisTemplate")
    public RedisTemplate<String, Object> redisTemplate() {

        Jackson2JsonRedisSerializer<Object> jackson2JsonRedisSerializer
                = new Jackson2JsonRedisSerializer<>(Object.class);
        jackson2JsonRedisSerializer.setObjectMapper(this.objectMapper());

        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(this.redisConnectionFactory());
        template.setKeySerializer(StringRedisSerializer.UTF_8);
        template.setValueSerializer(jackson2JsonRedisSerializer);
        template.setHashKeySerializer(StringRedisSerializer.UTF_8);
        template.setHashValueSerializer(jackson2JsonRedisSerializer);
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    @ConditionalOnMissingBean(StringRedisTemplate.class)
    public StringRedisTemplate stringRedisTemplate() {
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(this.redisConnectionFactory());
        return template;
    }
}
