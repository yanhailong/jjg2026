package com.jjg.game.minigame.dao;

import com.jjg.game.core.dao.MongoBaseDao;
import com.jjg.game.minigame.data.HappyHappyFishData;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class HappyHappyFishDao extends MongoBaseDao<HappyHappyFishData, Long> {
    public HappyHappyFishDao(MongoTemplate mongoTemplate) {
        super(HappyHappyFishData.class, mongoTemplate);
    }
}
