package jun.example;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jun.example.domain.Notification;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.util.UuidUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SpringBootTest
public class Tester4 {

    private static final Logger logger = LogManager.getLogger();

    public final String KEY_FORMAT = "inform:%d";
    public final int MAX_RANGE = 30;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    public void addNotifications(long receiverID, List<Notification> notifications) {
        this.listOps().rightPushAll(this.key(receiverID),
                notifications.stream().map(this::convertToString)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList()));
    }

    @SuppressWarnings("unchecked")
    public List<Notification> retrieveNotifications(long receiverID) {

        if (receiverID <= 0) {
            logger.error("id error. value:{}", receiverID);
            return new ArrayList<>();
        }

        final byte[] key = toByteArray(this.key(receiverID));

        List<Object> results = this.redis().executePipelined(
                (RedisCallback<Object>) connection -> {
                    connection.lRange(key, 0, MAX_RANGE - 1);
                    connection.lTrim(key, MAX_RANGE, -1);
                    return null;
                });

        logger.debug("result:{}", results);

        List<String> values = (List<String>) results.get(0);

        return values.stream().map(this::convertToObject)
                .filter(Objects::nonNull).collect(Collectors.toList());
    }

    private static byte[] toByteArray(String string) {
        return string.getBytes(StandardCharsets.UTF_8);
    }

    protected ListOperations<String, String> listOps() {
        return this.redis().opsForList();
    }

    protected StringRedisTemplate redis() {
        return this.stringRedisTemplate;
    }

    protected ObjectMapper mapper() {
        return this.objectMapper;
    }

    private String key(long receiverID) {
        return String.format(KEY_FORMAT, receiverID);
    }

    private String convertToString(Notification notification) {
        try {
            return this.mapper().writeValueAsString(notification);
        } catch (JsonProcessingException cause) {
            logger.error("caught exception.", cause);
        }
        return null;
    }

    private Notification convertToObject(String string) {
        try {
            return this.mapper().readValue(string, Notification.class);
        } catch (JsonProcessingException cause) {
            logger.error("caught exception.", cause);
        }
        return null;
    }

    @Test
    public void doTest() {

        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < 10; ++i) {
            sb.append(UUID.randomUUID().toString());
        }
        final String content = sb.toString();

        List<Notification> notifications = Stream.generate(
                new Supplier<Notification>() {
                    private int count;

                    @Override
                    public Notification get() {
                        int code = ++count;
//                        String content = Integer.toString(code);
                        return new Notification(code, content);
                    }
                }).limit(1000).collect(Collectors.toList());
        try {
            addNotifications(1, notifications);
        } catch (Exception cause) {
            logger.error("caught exception.", cause);
        }

        try {
            while (true) {
                Elapse elapse = new Elapse();
                List<Notification> receivedNotifications = retrieveNotifications(1);
                logger.info("count:{} elapse:{}", receivedNotifications.size(), elapse.stop());
                if (receivedNotifications.isEmpty()) {
                    break;
                }
            }
        } catch (Exception cause) {
            logger.error("caught exception.", cause);
        }
    }
}
