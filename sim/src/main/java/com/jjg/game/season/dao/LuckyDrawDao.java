package com.jjg.game.season.dao;

import com.jjg.game.season.data.LuckyDrawData;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public class LuckyDrawDao {
    private final MongoTemplate mongo;
    public LuckyDrawDao(MongoTemplate mongo) { this.mongo = mongo; }
    public LuckyDrawData get(String id) { return mongo.findById(id, LuckyDrawData.class); }
    public void save(LuckyDrawData d) { mongo.save(d); }

    public void recordLogin(long playerId, long dayStart) {
        mongo.upsert(Query.query(Criteria.where("_id").is(playerId + ":" + dayStart)),
                new Update().setOnInsert("playerId", playerId).setOnInsert("dayStart", dayStart), LoginDay.class);
    }
    public List<Long> loginDays(long playerId, long start, long end, long after) {
        Criteria days = Criteria.where("playerId").is(playerId).and("dayStart").gte(start).lt(end).gt(after);
        return mongo.find(Query.query(days).with(Sort.by("dayStart")), LoginDay.class).stream()
                .map(d -> d.dayStart).toList();
    }
    public List<LuckyDrawData> expired(long now, String after) {
        Criteria c = Criteria.where("endTime").lte(now).and("settled").is(false);
        if (after != null) c = c.and("_id").gt(after);
        return mongo.find(Query.query(c).with(Sort.by("_id")).limit(100), LuckyDrawData.class);
    }
    @Document("seasonLuckyDrawLogin")
    public static class LoginDay {
        @Id public String id;
        public long playerId;
        public long dayStart;
    }
}
