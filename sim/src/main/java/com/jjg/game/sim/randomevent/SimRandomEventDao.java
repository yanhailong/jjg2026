package com.jjg.game.sim.randomevent;

import com.jjg.game.core.dao.MongoBaseDao;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;

@Repository
class SimRandomEventDao extends MongoBaseDao<SimRandomEventState, Long> {
    SimRandomEventDao(MongoTemplate mongoTemplate) {
        super(SimRandomEventState.class, mongoTemplate);
    }

    SimRandomEventState load(long playerId) {
        return findById(playerId).orElseGet(() -> new SimRandomEventState(playerId));
    }
}
