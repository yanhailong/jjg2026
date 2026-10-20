package com.jjg.game.minigame.dao;

import com.jjg.game.core.dao.MongoBaseDao;
import com.jjg.game.minigame.data.CheaterHuntData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;

/** 找老千玩家数据读写，复用现有Mongo主键访问。 */
@Repository
public class CheaterHuntDao extends MongoBaseDao<CheaterHuntData, Long> {
    public CheaterHuntDao(@Autowired MongoTemplate mongoTemplate) {
        super(CheaterHuntData.class, mongoTemplate);
    }
}
