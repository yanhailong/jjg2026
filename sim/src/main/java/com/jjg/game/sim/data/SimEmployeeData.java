package com.jjg.game.sim.data;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.HashMap;
import java.util.Map;

/**
 * 雇员数据
 *
 * @author 11
 * @date 2026/5/28
 */
@Document
public class SimEmployeeData extends AbstractData {
    //联合主键 playerId:employeeId
    @Id
    private String id;
    @Indexed
    private long playerId;
    //雇员id (对应 EmployeeProfileCfg.id)
    private int employeeId;
    //当前等级 (>=1 表示已招募)
    private int level;
    //本轮升级已扣除的道具，按道具 ID 累计，随雇员等级一并落库
    private Map<Integer, Long> levelUpCost;
    //当前星级
    private int star;
    //技能组 -> 当前等级；各雇员独立升级
    private Map<Integer, Integer> skillLevels = new HashMap<>();


    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public long getPlayerId() {
        return playerId;
    }

    public void setPlayerId(long playerId) {
        this.playerId = playerId;
    }

    public int getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(int employeeId) {
        this.employeeId = employeeId;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public Map<Integer, Long> getLevelUpCost() {
        return levelUpCost == null ? Map.of() : levelUpCost;
    }

    public void addLevelUpCost(Map<Integer, Long> cost) {
        if (levelUpCost == null) {
            levelUpCost = new HashMap<>(cost);
        } else {
            cost.forEach((itemId, count) -> levelUpCost.merge(itemId, count, Long::sum));
        }
    }

    public void clearLevelUpCost() {
        levelUpCost = null;
    }

    public int getStar() {
        return star;
    }

    public void setStar(int star) {
        this.star = star;
    }

    public Map<Integer, Integer> getSkillLevels() {
        return skillLevels;
    }

    public void setSkillLevels(Map<Integer, Integer> skillLevels) {
        this.skillLevels = skillLevels;
    }

    /**
     * 根据 playerId 和 employeeId 构建联合主键
     */
    public void buildKey() {
        this.id = buildKey(this.playerId, this.employeeId);
    }

    public static String buildKey(long playerId, int employeeId) {
        return playerId + ":" + employeeId;
    }
}
