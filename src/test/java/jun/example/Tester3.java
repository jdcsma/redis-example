package jun.example;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jun.example.domain.PlayerSummary;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SpringBootTest
public class Tester3 {

    private static final Logger logger = LogManager.getLogger();

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private final Set<Long> players = new HashSet<>();

    public String summaryKey(long playerID) {
        return "summary:" + playerID;
    }

    public String summaryKey(String playerID) {
        return "summary:" + playerID;
    }

    public String friendKey(long playerID) {
        return "friend:" + playerID;
    }

    public void addPlayerSummaries(int maxCount) {

        stringRedisTemplate.opsForValue().set(
                "count:", Integer.toString(maxCount));

        final List<PlayerSummary> summaryList = Stream.generate(
                new Supplier<PlayerSummary>() {
                    long count = 1;

                    @Override
                    public PlayerSummary get() {
                        PlayerSummary summary = new PlayerSummary();
                        summary.setPlayerID(count);
                        summary.setPlayerName("tester" + count);
                        summary.setPlayerHead((int) (1000L + count));
                        summary.setPlayerPortrait((int) (2000L + count));
                        players.add(count++);
                        return summary;
                    }
                }).limit(maxCount).collect(Collectors.toList());

        Elapse elapse = new Elapse();
        this.stringRedisTemplate.executePipelined(
                (RedisCallback<Object>) connection -> {
                    summaryList.forEach(summary -> {
                        Map<String, String> map = createMapFromObject(summary);
                        final Map<byte[], byte[]> serialized = new HashMap<>();
                        map.forEach((k, v) -> serialized.put(
                                k.getBytes(StandardCharsets.UTF_8),
                                v.getBytes(StandardCharsets.UTF_8)));
                        connection.hMSet(summaryKey(summary.getPlayerID())
                                .getBytes(StandardCharsets.UTF_8), serialized);
                    });
                    return null;
                });
        elapse.stop();
        logger.info("add friend summaries elapse:{}. count:{}",
                elapse.stop(), summaryList.size());
    }

    public void addFriends(long playerID, Set<Long> friends) {

        Set<Long> set = new HashSet<>(friends);
        set.remove(playerID);

        String[] ids = new String[set.size()];
        set.stream().map((id) -> Long.toString(id))
                .collect(Collectors.toList()).toArray(ids);

        Elapse elapse = new Elapse();
        this.stringRedisTemplate.opsForSet().add(this.friendKey(playerID), ids);
        elapse.stop();
        logger.info("add friends elapse:{}", elapse.stop());
    }

    @SuppressWarnings("unchecked")
    public void findFriends(long playerID) {
        final Elapse elapse = new Elapse();
        Set<String> keys = this.stringRedisTemplate.opsForSet()
                .members(friendKey(playerID));
        assert keys != null;
        List<Object> results = this.stringRedisTemplate.executePipelined(
                (RedisCallback<Object>) connection -> {
                    keys.forEach(key -> connection.hGetAll(summaryKey(key)
                            .getBytes(StandardCharsets.UTF_8)));
                    return null;
                });
        List<PlayerSummary> friendSummaries =
                results.stream()
                        .filter(v -> ((Map<Object, Object>) v).size() > 0)
                        .map(v -> createObjectFromMap((Map<Object, Object>) v))
                        .collect(Collectors.toList());
        elapse.stop();
        logger.info("find all friends elapse:{} count:{}",
                elapse.stop(), friendSummaries.size());
    }

    @Test
    public void doTest() {
        Cleaner.deleteKeys(stringRedisTemplate, "summary:");
        Cleaner.deleteKeys(stringRedisTemplate, "friend:");
        this.addPlayerSummaries(10);
        this.addFriends(1, this.players);
        this.addFriends(2, this.players);
        this.addFriends(3, this.players);
        this.findFriends(1);
        this.findFriends(2);
        this.findFriends(3);
    }

    private Map<String, String> createMapFromObject(PlayerSummary summary) {
        return this.objectMapper.convertValue(summary,
                new TypeReference<Map<String, String>>() {
                });
    }

    private PlayerSummary createObjectFromMap(Map<Object, Object> map) {
        return this.objectMapper.convertValue(map, PlayerSummary.class);
    }
}
