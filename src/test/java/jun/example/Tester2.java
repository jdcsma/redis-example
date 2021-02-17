package jun.example;

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

        Elapse elapse = new Elapse();
        summaryList.forEach(
                (summary) -> {
                    Map<String, String> map = this.objectMapper.convertValue(
                            summary, new TypeReference<Map<String, String>>() {
                            });
                    stringRedisTemplate.opsForHash().putAll(
                            this.summaryKey(summary.getPlayerID()), map);
                }
        );
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

        final List<PlayerSummary> friendSummaries = new ArrayList<>();
        final Elapse elapse = new Elapse();
        members.forEach((friendID) -> {
            Map<Object, Object> map = stringRedisTemplate.opsForHash()
                    .entries(summaryKey(friendID));
            PlayerSummary friend = objectMapper.convertValue(map, PlayerSummary.class);
            assert friend != null;
            friendSummaries.add(friend);
        });
        elapse.stop();
        logger.info("player:{} - find all friend summary elapse:{} count:{}",
                playerID, elapse.stop(), friendSummaries.size());
    }

    @Test
    public void doTest() {
        Cleaner.deleteKeys(stringRedisTemplate, "summary:");
        Cleaner.deleteKeys(stringRedisTemplate, "friend:");
        this.addPlayerSummaries(1000);
        this.addFriends(1, this.players);
        this.addFriends(2, this.players);
        this.addFriends(3, this.players);
        this.findFriends(1);
        this.findFriends(2);
        this.findFriends(3);
    }
}