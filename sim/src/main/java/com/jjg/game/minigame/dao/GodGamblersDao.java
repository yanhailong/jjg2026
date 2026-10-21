package com.jjg.game.minigame.dao;

import com.jjg.game.core.dao.MongoBaseDao;
import com.jjg.game.minigame.data.GodGamblersData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;

/** 赌神搓牌每日赠送记录，复用现有Mongo主键访问。 */
@Repository
public class GodGamblersDao extends MongoBaseDao<GodGamblersData, Long> {
    public GodGamblersDao(@Autowired MongoTemplate mongoTemplate) {
        super(GodGamblersData.class, mongoTemplate);
    }
}
