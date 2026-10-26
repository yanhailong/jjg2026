package com.jjg.game.sim.data;

import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 建筑数据
 *
 * @author 11
 * @date 2026/5/22
 */
public class BuildingData {
    //建筑id (对应 BuildingAreaTableCfg.id)
    private int id;
    //当前建筑等级，按等级段匹配 BuildingUpgradeTableCfg
    private int level;
    //旧 CD 字段保留；即时升级不再设置 CD
    private long cdEndTime;
    //旧清 CD 广告次数字段
    private int adClearCount;
    //旧升级进度字段；即时升级不再累加进度
    private int progress;
    //接待次数
    private int receptCount;
    //已解锁的 BuildingUpgradeArea 配置 id
    private Set<Integer> unlockedAreaIds = new HashSet<>();
    //已解锁皮肤；全部参与所属区域的 Buff 计算
    private Set<Integer> unlockedSkinIds = new HashSet<>();
    //BuildingUpgradeArea 配置 id -> 当前使用的皮肤 id
    private Map<Integer, Integer> usingSkinIds = new HashMap<>();

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public long getCdEndTime() {
        return cdEndTime;
    }

    public void setCdEndTime(long cdEndTime) {
        this.cdEndTime = cdEndTime;
    }

    public int getAdClearCount() {
        return adClearCount;
    }

    public void setAdClearCount(int adClearCount) {
        this.adClearCount = adClearCount;
    }

    public int getProgress() {
        return progress;
    }

    public void setProgress(int progress) {
        this.progress = progress;
    }

    public int getReceptCount() {
        return receptCount;
    }

    public void setReceptCount(int receptCount) {
        this.receptCount = receptCount;
    }

    public Set<Integer> getUnlockedAreaIds() {
        return unlockedAreaIds;
    }

    public void setUnlockedAreaIds(Set<Integer> unlockedAreaIds) {
        this.unlockedAreaIds = unlockedAreaIds;
    }

    public Set<Integer> getUnlockedSkinIds() {
        return unlockedSkinIds;
    }

    public void setUnlockedSkinIds(Set<Integer> unlockedSkinIds) {
        this.unlockedSkinIds = unlockedSkinIds;
    }

    public Map<Integer, Integer> getUsingSkinIds() {
        return usingSkinIds;
    }

    public void setUsingSkinIds(Map<Integer, Integer> usingSkinIds) {
        this.usingSkinIds = usingSkinIds;
    }

    /**
     * 是否处于升级 CD 中 (尚未到时)
     */
    public boolean isUpgrading(long now) {
        return cdEndTime > now;
    }

    public void incrementReceptCount(){
        this.receptCount++;
    }
}
