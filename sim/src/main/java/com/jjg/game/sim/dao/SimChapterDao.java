package com.jjg.game.sim.dao;

import com.jjg.game.core.dao.MongoBaseDao;
import com.jjg.game.sim.data.SimChapterData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SimChapterDao extends MongoBaseDao<SimChapterData, Long> {
    public SimChapterDao(@Autowired MongoTemplate mongoTemplate) {
        super(SimChapterData.class, mongoTemplate);
    }
}
