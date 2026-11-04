package com.jjg.game.minigame.data;

import com.jjg.game.sim.data.AbstractData;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** 仅持久化每日补足处理日期，库存已充足也记录，单局不落库。 */
@Document
public class HappyHappyFishData extends AbstractData {
    @Id
    private long playerId;
    private long lastDailyGrantTime;

    public long getPlayerId() {
        return playerId;
    }

    public void setPlayerId(long playerId) {
        this.playerId = playerId;
    }

    public long getLastDailyGrantTime() {
        return lastDailyGrantTime;
    }

    public void setLastDailyGrantTime(long lastDailyGrantTime) {
        this.lastDailyGrantTime = lastDailyGrantTime;
    }
}
