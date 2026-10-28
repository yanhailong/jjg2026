package com.jjg.game.minigame.dao;

import com.jjg.game.core.dao.MongoBaseDao;
import com.jjg.game.minigame.data.SpeedyDeliveryData;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SpeedyDeliveryDao extends MongoBaseDao<SpeedyDeliveryData, Long> {
    public SpeedyDeliveryDao(MongoTemplate mongoTemplate) {
        super(SpeedyDeliveryData.class, mongoTemplate);
    }
}
