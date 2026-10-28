package com.jjg.game.minigame.data;

import com.jjg.game.sim.data.AbstractData;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** 仅持久化每日赠送日期，单局不落库。 */
@Document
public class SpeedyDeliveryData extends AbstractData {
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
