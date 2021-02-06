package jun.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import jun.example.domain.PlayerSummary;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SpringBootTest
public class Tester1 {

    private static final Logger logger = LogManager.getLogger();

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RedisTemplate<String, Object> objectRedisTemplate;

    private final Set<Long> players = new HashSet<>();

    public String summaryKey(long playerID) {
        return "summary:" + playerID;
    }

    public String friendKey(long playerID) {
        return "friend:" + playerID;
    }

    public void addPlayerSummaries(int maxCount) {

        objectRedisTemplate.opsForValue().set("count:", maxCount);

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
                (summary) -> objectRedisTemplate.opsForValue().set(
                        this.summaryKey(summary.getPlayerID()), summary)
        );
        long et = System.currentTimeMillis();
        logger.info("set string elapse:{}. count:{}",
                (et - st), summaryList.size());
    }

    public void addFriends(long playerID, Set<Long> friends) {

        Set<Long> set = new HashSet<>(friends);
        set.remove(playerID);

        long st = System.currentTimeMillis();
        this.objectRedisTemplate.opsForSet().add(
                this.friendKey(playerID), set.toArray());
        long et = System.currentTimeMillis();
        logger.info("add friends elapse:{}", (et - st));
    }

    @SuppressWarnings("unchecked")
    public void findFriends(long playerID) {
        long st = System.currentTimeMillis();
        Set<Object> members = this.objectRedisTemplate.opsForSet()
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
            Map<String, Object> map = (Map<String, Object>)
                    objectRedisTemplate.opsForValue()
                            .get(summaryKey((Integer) friendID));
            getElapse.stop();
            deserializeElapse.start();
            PlayerSummary friend = objectMapper.convertValue(
                    map, PlayerSummary.class);
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
}
