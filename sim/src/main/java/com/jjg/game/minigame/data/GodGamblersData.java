package com.jjg.game.minigame.data;

import com.jjg.game.sim.data.AbstractData;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** 赌神搓牌每日补足处理记录；单局牌面与搓牌次数仅保存在玩家会话中。 */
@Document
public class GodGamblersData extends AbstractData {
    //玩家ID，同时作为文档主键
    @Id
    private long playerId;
    //上次每日道具补足处理完成时间，库存已充足也记录；0表示尚未处理
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
