package com.jjg.game.minigame.data;

import com.jjg.game.sim.data.AbstractData;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.HashMap;
import java.util.Map;

/** 欢乐捉鱼的每日补足记录、可用积分和技能等级。 */
@Document
public class HappyHappyFishData extends AbstractData {
    @Id
    private long playerId;
    private long lastDailyGrantTime;
    private long skillPoints;
    private Map<Integer, Integer> skillLevels = new HashMap<>();

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

    public long getSkillPoints() {
        return skillPoints;
    }

    public void setSkillPoints(long skillPoints) {
        this.skillPoints = skillPoints;
    }

    public Map<Integer, Integer> getSkillLevels() {
        return skillLevels;
    }

    public int getSkillLevel(int groupId) {
        return skillLevels.getOrDefault(groupId, 0);
    }
}
