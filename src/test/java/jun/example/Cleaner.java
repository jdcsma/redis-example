package jun.example;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Set;

public final class Cleaner {

    public static void deleteKeys(RedisTemplate<String, Object> redisTemplate,
                                  String prefix) {
        Set<String> keys = redisTemplate.keys(prefix + "*");
        if (keys != null) {
            redisTemplate.delete(keys);
        }
    }

    public static void deleteKeys(StringRedisTemplate redisTemplate,
                                  String prefix) {
        Set<String> keys = redisTemplate.keys(prefix + "*");
        if (keys != null) {
            redisTemplate.delete(keys);
        }
    }
}
