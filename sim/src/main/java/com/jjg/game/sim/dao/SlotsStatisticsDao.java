package com.jjg.game.sim.dao;

import com.jjg.game.core.dao.MongoBaseDao;
import com.jjg.game.sim.data.SlotsStatisticsData;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SlotsStatisticsDao extends MongoBaseDao<SlotsStatisticsData, Long> {
    public SlotsStatisticsDao(MongoTemplate mongoTemplate) {
        super(SlotsStatisticsData.class, mongoTemplate);
    }
}
