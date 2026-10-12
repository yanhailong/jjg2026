package com.jjg.game.season.dao;

import com.jjg.game.season.data.ContinuousGiftData;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public class ContinuousGiftDao {
    private final MongoTemplate mongo;
    public ContinuousGiftDao(MongoTemplate mongo) { this.mongo = mongo; }
    public ContinuousGiftData get(String id) { return mongo.findById(id, ContinuousGiftData.class); }
    public void save(ContinuousGiftData data) { mongo.save(data); }
    public List<ContinuousGiftData> expired(long now, String afterId) {
        Criteria criteria = Criteria.where("endTime").lte(now).and("settled").is(false);
        if (afterId != null) criteria = criteria.and("_id").gt(afterId);
        return mongo.find(Query.query(criteria).with(Sort.by("_id")).limit(100), ContinuousGiftData.class);
    }
}
