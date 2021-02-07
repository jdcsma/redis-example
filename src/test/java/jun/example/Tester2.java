package jun.example;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jun.example.domain.PlayerSummary;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SpringBootTest
public class Tester2 {

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

        List<PlayerSummary> summaryList = Stream.generate(
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

        long st = System.currentTimeMillis();
        summaryList.forEach(
                (summary) -> {
                    try {
                        String value = objectMapper
                                .writeValueAsString(summary);
                        stringRedisTemplate.opsForValue().set(
                                this.summaryKey(summary.getPlayerID()), value);
                    } catch (JsonProcessingException e) {
                        e.printStackTrace();
                    }
                }
        );
        long et = System.currentTimeMillis();
        logger.info("set string elapse:{}. count:{}",
                (et - st), summaryList.size());
    }

    public void addFriends(long playerID, Set<Long> friends) {

        Set<Long> set = new HashSet<>(friends);
        set.remove(playerID);

        String[] ids = new String[set.size()];
        set.stream().map((id) -> Long.toString(id))
                .collect(Collectors.toList()).toArray(ids);

        long st = System.currentTimeMillis();
        this.stringRedisTemplate.opsForSet().add(this.friendKey(playerID), ids);
        long et = System.currentTimeMillis();
        logger.info("add friends elapse:{}", (et - st));
    }

    public void findFriends(long playerID) {
        long st = System.currentTimeMillis();
        Set<String> members = this.stringRedisTemplate.opsForSet()
                .members(this.friendKey(playerID));
        long et = System.currentTimeMillis();
        logger.info("player:{} - find friends id elapse:{}.",
                playerID, (et - st));

        assert members != null;

        List<PlayerSummary> friendSummaries = new ArrayList<>();

        final Elapse getElapse = new Elapse();
        final Elapse deserializeElapse = new Elapse();
        members.forEach((friendID) -> {
            getElapse.start();
            String value = stringRedisTemplate.opsForValue()
                    .get(summaryKey(friendID));
            getElapse.stop();
            deserializeElapse.start();
            PlayerSummary friend = null;
            try {
                friend = objectMapper.readValue(value, PlayerSummary.class);
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }
            deserializeElapse.stop();
            assert friend != null;
            friendSummaries.add(friend);
        });
        logger.info("player:{} - find friend summary elapse:{}",
                playerID, (getElapse.getAmount() + deserializeElapse.getAmount()));
        logger.info("get elapse:{}", getElapse.getAmount());
        logger.info("deserialize elapse:{}", deserializeElapse.getAmount());
        logger.info("friend count:{}", friendSummaries.size());
    }

    @Test
    public void doTest() {
        this.addPlayerSummaries(1000);
        this.addFriends(1, this.players);
        this.addFriends(2, this.players);
        this.addFriends(3, this.players);
        this.findFriends(1);
        this.findFriends(2);
        this.findFriends(3);
    }

    @Test
    public void testHash() {
        PlayerSummary summary = new PlayerSummary();
        summary.setPlayerID(1981);
        summary.setPlayerName("刘俊");
        summary.setPlayerHead(1981);
        summary.setPlayerPortrait(1981);

        Map<String, String> map = this.objectMapper.convertValue(
                summary, new TypeReference<Map<String, String>>() {
                });

        this.stringRedisTemplate.opsForHash().putAll(
                this.summaryKey(summary.getPlayerID()), map);

        Map<Object, Object> om = this.stringRedisTemplate.opsForHash()
                .entries(this.summaryKey(summary.getPlayerID()));
        logger.info(om);
        summary.setPlayerHead(10);
        this.stringRedisTemplate.opsForHash().put(
                this.summaryKey(summary.getPlayerID()),
                "playerHead",
                Integer.toString(summary.getPlayerHead()));
        om = this.stringRedisTemplate.opsForHash()
                .entries(this.summaryKey(summary.getPlayerID()));
        logger.info(om);
    }
}