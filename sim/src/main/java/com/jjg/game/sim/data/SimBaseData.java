package com.jjg.game.sim.data;

import com.jjg.game.common.proto.Pair;
import com.jjg.game.common.utils.TimeHelper;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.*;

/**
 * 玩家 sim 基础数据
 *
 * @author 11
 * @date 2026/5/15
 */
@Document
public class SimBaseData extends AbstractData {
    @Id
    private long playerId;
    //体力值
    private int stamina;
    //当前所在场景id
    private int currentCasinoId;
    //是否已完成新手引导
    private boolean guide;
    private Set<Integer> triggeredGuideGroupIds;
    // 条件已经满足，但尚未进入 Guide.xlsx.PathName 指定场景的引导组
    private Set<Integer> scenePendingGuideGroupIds;
    private Set<Integer> completedGuideGroupIds;
    // 已完成的具体引导步骤ID，用于断线后恢复组内进度
    private Set<Integer> completedGuideIds;
    //上次离线时间 (ms), 用于长/短时掉线判定
    private long lastOfflineTime;
    //能量值
    private int power;
    //每日掉落次数 (dropItemId -> 当日已掉次数)
    private Map<Integer, Integer> dailyDropCount;
    //每日掉落计数重置日 (yyyyMMdd)
    private int dropResetDay;
    //所有场景等级之和
    private int allLevel;
    //经营信息-高级游客人次 (玩家跨娱乐城累计)
    private long receptionCount;
    //经营信息-经营总收益 (玩家跨娱乐城累计金币)
    private long businessIncome;
    //经营信息-观看广告数 (玩家跨娱乐城累计)
    private int watchAdCount;
    //经营信息-完成任务数 (玩家跨娱乐城累计, 完成即计数)
    private int finishedTaskCount;
    //任务完成时解锁的功能ID，不随任务配置变化重新计算
    private Set<Integer> unlockedFunctionIds;
    //最近一次触发"登陆天数"条件的自然日 (yyyyMMdd); 每个自然日仅计一次登陆, 跨天再计
    private int lastLoginDay;
    //经营信息-SPINE游戏统计 gameType -> 玩家累计统计
    private Map<Integer, SlotGameStatsData> slotStatsMap;
    //所有的激活的勋章
    private Set<Integer> allMedalIds;
    //当前广告特殊游客配置ID
    private List<Integer> specialGuestAdCfgIds;
    //广告特殊游客冷却重置日 (yyyyMMdd)，广告展示和限制为玩家全局
    private int specialGuestAdRefreshDay;
    //广告特殊游客下次定时刷新时间 (ms)，0 表示不按时段刷新
    private long specialGuestAdNextRefreshTime;
    //广告特殊游客冷却结束时间 (ms)
    private long specialGuestAdCdEndTime;
    //技能总等级
    private int skillAllLevel;
    //在线收益每日领取次数 (玩家全局，跨场景共享)
    private int onlineRewardDay;
    private int onlineRewardAdCount;
    private int onlineRewardDiamondCount;
    //视频冷却结束时间 (ms)，跨日与领取次数一并重置
    private long onlineRewardAdCdEndTime;
    //本次在线时长已累计到的时刻 (ms)，离线时为 0
    private long loginTime;
    //当天累计在线时长 (ms)，跨场景共享
    private long dailyOnlineTime;
    //在线时长下次自然日重置时间 (ms)
    private long onlineTimeResetTime;
    //当日游客产出统计，跨场景共享，与在线时长一起跨日重置
    private int normalGuestCount;
    private int specialGuestCount;
    private Map<Integer, Long> guestOutputRewards;
    //当日是否已通知普通游客产出时长达到80%
    private boolean guestOutputLimitNotified;
    //slots赢奖的最高历史记录
    private long slotsAthWinCoin;
    //是否免广告
    private boolean noads;
    // 已发放免广告赠品的订单，权益落库重试时不重复发奖。
    private String noAdsOrderId;
    //代币商店限购记录，玩家全局共享，不随场景、赛季或联盟变化清空
    private Map<Integer, TokenShopPurchaseData> tokenShopPurchases;

    public Map<Integer, TokenShopPurchaseData> getTokenShopPurchases() {
        if (tokenShopPurchases == null) {
            tokenShopPurchases = new HashMap<>();
        }
        return tokenShopPurchases;
    }

    public void setTokenShopPurchases(Map<Integer, TokenShopPurchaseData> tokenShopPurchases) {
        this.tokenShopPurchases = tokenShopPurchases;
    }

    public String getNoAdsOrderId() {
        return noAdsOrderId;
    }

    public void setNoAdsOrderId(String noAdsOrderId) {
        this.noAdsOrderId = noAdsOrderId;
    }

    public boolean isNoads() {
        return noads;
    }

    public void setNoads(boolean noads) {
        this.noads = noads;
    }

    public void resetOnlineRewardDay(int today) {
        if (onlineRewardDay != today) {
            onlineRewardDay = today;
            onlineRewardAdCount = 0;
            onlineRewardDiamondCount = 0;
            onlineRewardAdCdEndTime = 0;
        }
    }

    public int getOnlineRewardDay() {
        return onlineRewardDay;
    }

    public void setOnlineRewardDay(int onlineRewardDay) {
        this.onlineRewardDay = onlineRewardDay;
    }

    public int getOnlineRewardAdCount() {
        return onlineRewardAdCount;
    }

    public void setOnlineRewardAdCount(int onlineRewardAdCount) {
        this.onlineRewardAdCount = onlineRewardAdCount;
    }

    public int getOnlineRewardDiamondCount() {
        return onlineRewardDiamondCount;
    }

    public void setOnlineRewardDiamondCount(int onlineRewardDiamondCount) {
        this.onlineRewardDiamondCount = onlineRewardDiamondCount;
    }

    public long getOnlineRewardAdCdEndTime() {
        return onlineRewardAdCdEndTime;
    }

    public void setOnlineRewardAdCdEndTime(long onlineRewardAdCdEndTime) {
        this.onlineRewardAdCdEndTime = onlineRewardAdCdEndTime;
    }

    public long getPlayerId() {
        return playerId;
    }

    public void setPlayerId(long playerId) {
        this.playerId = playerId;
    }

    public int getStamina() {
        return stamina;
    }

    public void setStamina(int stamina) {
        this.stamina = stamina;
    }

    public boolean isGuide() {
        return guide;
    }

    public void setGuide(boolean guide) {
        this.guide = guide;
    }

    public Set<Integer> getTriggeredGuideGroupIds() {
        if (triggeredGuideGroupIds == null) triggeredGuideGroupIds = new HashSet<>();
        return triggeredGuideGroupIds;
    }

    public void setTriggeredGuideGroupIds(Set<Integer> value) {
        this.triggeredGuideGroupIds = value;
    }

    public Set<Integer> getScenePendingGuideGroupIds() {
        if (scenePendingGuideGroupIds == null) scenePendingGuideGroupIds = new HashSet<>();
        return scenePendingGuideGroupIds;
    }

    public void setScenePendingGuideGroupIds(Set<Integer> value) {
        this.scenePendingGuideGroupIds = value;
    }

    public Set<Integer> getCompletedGuideGroupIds() {
        if (completedGuideGroupIds == null) completedGuideGroupIds = new HashSet<>();
        return completedGuideGroupIds;
    }

    public void setCompletedGuideGroupIds(Set<Integer> value) {
        this.completedGuideGroupIds = value;
    }

    public Set<Integer> getCompletedGuideIds() {
        if (completedGuideIds == null) completedGuideIds = new HashSet<>();
        return completedGuideIds;
    }

    public void setCompletedGuideIds(Set<Integer> value) {
        this.completedGuideIds = value;
    }

    public boolean completeGuideId(int guideId) {
        return guideId > 0 && getCompletedGuideIds().add(guideId);
    }

    public List<Integer> completedGuideIds() {
        List<Integer> result = new ArrayList<>(getCompletedGuideIds());
        result.sort(Integer::compareTo);
        return result;
    }

    public boolean hasCompletedGuideId(int guideId) {
        if (completedGuideIds == null || completedGuideIds.isEmpty()) {
            return false;
        }
        return completedGuideIds.contains(guideId);
    }

    public boolean triggerGuideGroup(int groupId) {
        if (groupId <= 0 || getCompletedGuideGroupIds().contains(groupId)) return false;
        getScenePendingGuideGroupIds().remove(groupId);
        return getTriggeredGuideGroupIds().add(groupId);
    }

    public boolean deferGuideGroupForScene(int groupId) {
        return groupId > 0
                && !getCompletedGuideGroupIds().contains(groupId)
                && !getTriggeredGuideGroupIds().contains(groupId)
                && getScenePendingGuideGroupIds().add(groupId);
    }

    public boolean activateScenePendingGuideGroup(int groupId) {
        if (!getScenePendingGuideGroupIds().remove(groupId)) return false;
        return triggerGuideGroup(groupId);
    }

    public void discardScenePendingGuideGroup(int groupId) {
        getScenePendingGuideGroupIds().remove(groupId);
    }

    public boolean completeGuideGroup(int groupId) {
        if (groupId <= 0) return false;
        getScenePendingGuideGroupIds().remove(groupId);
        getTriggeredGuideGroupIds().add(groupId);
        return getCompletedGuideGroupIds().add(groupId);
    }

    public boolean hasTriggeredGuideGroup(int groupId) {
        return getTriggeredGuideGroupIds().contains(groupId);
    }

    public boolean hasCompletedGuideGroup(int groupId) {
        return getCompletedGuideGroupIds().contains(groupId);
    }

    public List<Integer> pendingGuideGroupIds() {
        List<Integer> pending = new ArrayList<>();
        for (Integer groupId : getTriggeredGuideGroupIds()) {
            if (!getCompletedGuideGroupIds().contains(groupId)) pending.add(groupId);
        }
        pending.sort(Integer::compareTo);
        return pending;
    }

    public int getCurrentCasinoId() {
        return currentCasinoId;
    }

    public void setCurrentCasinoId(int currentCasinoId) {
        this.currentCasinoId = currentCasinoId;
    }

    public long getLastOfflineTime() {
        return lastOfflineTime;
    }

    public void setLastOfflineTime(long lastOfflineTime) {
        this.lastOfflineTime = lastOfflineTime;
    }

    public int getPower() {
        return power;
    }

    public void setPower(int power) {
        this.power = power;
    }

    public Map<Integer, Integer> getDailyDropCount() {
        return dailyDropCount;
    }

    public void setDailyDropCount(Map<Integer, Integer> dailyDropCount) {
        this.dailyDropCount = dailyDropCount;
    }

    public int getDropResetDay() {
        return dropResetDay;
    }

    public void setDropResetDay(int dropResetDay) {
        this.dropResetDay = dropResetDay;
    }

    public int getAllLevel() {
        return allLevel;
    }

    public void setAllLevel(int allLevel) {
        this.allLevel = allLevel;
    }

    public long getReceptionCount() {
        return receptionCount;
    }

    public void setReceptionCount(long receptionCount) {
        this.receptionCount = receptionCount;
    }

    public void addReceptionCount(long count) {
        if (count > 0) {
            this.receptionCount += count;
        }
    }

    public long getBusinessIncome() {
        return businessIncome;
    }

    public void setBusinessIncome(long businessIncome) {
        this.businessIncome = businessIncome;
    }

    public void addBusinessIncome(long gold) {
        if (gold > 0) {
            this.businessIncome += gold;
        }
    }

    public int getWatchAdCount() {
        return watchAdCount;
    }

    public void setWatchAdCount(int watchAdCount) {
        this.watchAdCount = watchAdCount;
    }

    public void incWatchAdCount() {
        this.watchAdCount++;
    }

    public int getFinishedTaskCount() {
        return finishedTaskCount;
    }

    public void setFinishedTaskCount(int finishedTaskCount) {
        this.finishedTaskCount = finishedTaskCount;
    }

    public void incFinishedTaskCount() {
        this.finishedTaskCount++;
    }

    public Set<Integer> getUnlockedFunctionIds() {
        return unlockedFunctionIds == null ? Collections.emptySet() : unlockedFunctionIds;
    }

    public void setUnlockedFunctionIds(Set<Integer> unlockedFunctionIds) {
        this.unlockedFunctionIds = unlockedFunctionIds;
    }

    public boolean unlockFunction(int functionId) {
        if (functionId <= 0) {
            return false;
        }
        if (unlockedFunctionIds == null) {
            unlockedFunctionIds = new HashSet<>();
        }
        return unlockedFunctionIds.add(functionId);
    }

    public int getLastLoginDay() {
        return lastLoginDay;
    }

    public void setLastLoginDay(int lastLoginDay) {
        this.lastLoginDay = lastLoginDay;
    }

    public Map<Integer, SlotGameStatsData> getSlotStatsMap() {
        return slotStatsMap;
    }

    public void setSlotStatsMap(Map<Integer, SlotGameStatsData> slotStatsMap) {
        this.slotStatsMap = slotStatsMap;
    }

    public SlotGameStatsData findSlotStats(int gameType) {
        return slotStatsMap == null ? null : slotStatsMap.get(gameType);
    }

    public SlotGameStatsData findOrCreateSlotStats(int gameType) {
        if (slotStatsMap == null) {
            slotStatsMap = new HashMap<>();
        }
        return slotStatsMap.computeIfAbsent(gameType, ignored -> new SlotGameStatsData());
    }

    /**
     * 跨天则重置每日掉落计数
     *
     * @param today yyyyMMdd
     */
    public void checkResetDropCount(int today) {
        if (this.dropResetDay != today) {
            this.dropResetDay = today;
            if (this.dailyDropCount != null) {
                this.dailyDropCount.clear();
            }
        }
    }

    /**
     * 查询某 dropItem 当日已掉落次数
     */
    public int getDropCount(int dropItemId) {
        if (this.dailyDropCount == null || this.dailyDropCount.isEmpty()) {
            return 0;
        }
        return this.dailyDropCount.getOrDefault(dropItemId, 0);
    }

    /**
     * 某 dropItem 当日掉落次数 +1
     */
    public void addDropCount(int dropItemId) {
        if (this.dailyDropCount == null) {
            this.dailyDropCount = new HashMap<>();
        }
        this.dailyDropCount.merge(dropItemId, 1, Integer::sum);
    }

    public void addAllLevel(int level) {
        this.allLevel += level;
    }

    public Set<Integer> getAllMedalIds() {
        return allMedalIds;
    }

    public void setAllMedalIds(Set<Integer> allMedalIds) {
        this.allMedalIds = allMedalIds == null ? null : new HashSet<>(allMedalIds);
    }

    public void activeMedalId(int id) {
        if (this.allMedalIds == null) {
            this.allMedalIds = new HashSet<>();
        }
        this.allMedalIds.add(id);
    }

    public List<Integer> getSpecialGuestAdCfgIds() {
        return specialGuestAdCfgIds;
    }

    public void setSpecialGuestAdCfgIds(List<Integer> specialGuestAdCfgIds) {
        this.specialGuestAdCfgIds = specialGuestAdCfgIds;
    }

    public int getSpecialGuestAdRefreshDay() {
        return specialGuestAdRefreshDay;
    }

    public void setSpecialGuestAdRefreshDay(int specialGuestAdRefreshDay) {
        this.specialGuestAdRefreshDay = specialGuestAdRefreshDay;
    }

    public long getSpecialGuestAdCdEndTime() {
        return specialGuestAdCdEndTime;
    }

    public long getSpecialGuestAdNextRefreshTime() {
        return specialGuestAdNextRefreshTime;
    }

    public void setSpecialGuestAdNextRefreshTime(long specialGuestAdNextRefreshTime) {
        this.specialGuestAdNextRefreshTime = specialGuestAdNextRefreshTime;
    }

    public void setSpecialGuestAdCdEndTime(long specialGuestAdCdEndTime) {
        this.specialGuestAdCdEndTime = specialGuestAdCdEndTime;
    }

    public int getSkillAllLevel() {
        return skillAllLevel;
    }

    public void setSkillAllLevel(int skillAllLevel) {
        this.skillAllLevel = skillAllLevel;
    }

    public long getLoginTime() {
        return loginTime;
    }

    public void setLoginTime(long loginTime) {
        this.loginTime = loginTime;
    }

    public long getDailyOnlineTime() {
        return dailyOnlineTime;
    }

    public void setDailyOnlineTime(long dailyOnlineTime) {
        this.dailyOnlineTime = dailyOnlineTime;
    }

    public long getOnlineTimeResetTime() {
        return onlineTimeResetTime;
    }

    public void setOnlineTimeResetTime(long onlineTimeResetTime) {
        this.onlineTimeResetTime = onlineTimeResetTime;
    }

    public Pair<Long,Boolean> accumulateOnlineTime(long now) {
        boolean reset = false;
        if (now >= onlineTimeResetTime) {
            dailyOnlineTime = 0;
            normalGuestCount = 0;
            specialGuestCount = 0;
            guestOutputLimitNotified = false;
            if (guestOutputRewards != null) {
                guestOutputRewards = null;
            }
            onlineTimeResetTime = TimeHelper.getDaysTime(now, 1);
            if (loginTime > 0) {
                loginTime = Math.max(loginTime, TimeHelper.getDaysTime(now, 0));
            }
            reset = true;
        }
        if (loginTime > 0 && now > loginTime) {
            dailyOnlineTime += now - loginTime;
            loginTime = now;
        }
        return new Pair<>(dailyOnlineTime, reset);
    }

    public int getNormalGuestCount() {
        return normalGuestCount;
    }

    public boolean isGuestOutputLimitNotified() {
        return guestOutputLimitNotified;
    }

    public void setGuestOutputLimitNotified(boolean guestOutputLimitNotified) {
        this.guestOutputLimitNotified = guestOutputLimitNotified;
    }

    public int getSpecialGuestCount() {
        return specialGuestCount;
    }

    public Map<Integer, Long> getGuestOutputRewards() {
        return guestOutputRewards;
    }

    public void recordGuestGenerated(boolean special, long now) {
        accumulateOnlineTime(now);
        if (special) {
            specialGuestCount++;
        } else {
            normalGuestCount++;
        }
    }

    public void recordGuestOutputRewards(Map<Integer, Long> rewards, long now) {
        accumulateOnlineTime(now);
        if (guestOutputRewards == null) {
            guestOutputRewards = new HashMap<>();
        }
        rewards.forEach((itemId, count) -> guestOutputRewards.merge(itemId, count, Long::sum));
    }

    public long getSlotsAthWinCoin() {
        return slotsAthWinCoin;
    }

    public void setSlotsAthWinCoin(long slotsAthWinCoin) {
        this.slotsAthWinCoin = slotsAthWinCoin;
    }
}
