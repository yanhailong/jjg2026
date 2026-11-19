package com.jjg.game.sim.data;

import com.alibaba.fastjson.JSONObject;
import com.jjg.game.core.data.CommonResult;
import com.jjg.game.core.data.Player;
import com.jjg.game.core.data.PlayerController;
import com.jjg.game.minigame.data.CheaterHuntData;
import com.jjg.game.minigame.data.CheaterHuntRound;
import com.jjg.game.minigame.data.GodGamblersData;
import com.jjg.game.minigame.data.GodGamblersRound;
import com.jjg.game.minigame.data.HappyHappyFishData;
import com.jjg.game.minigame.data.HappyHappyFishRound;
import com.jjg.game.minigame.data.SpeedyDeliveryData;
import com.jjg.game.minigame.data.SpeedyDeliveryRound;
import com.jjg.game.season.data.SeasonPlayerData;
import com.jjg.game.sim.constant.BuildingOutputType;
import com.jjg.game.sim.randomevent.VisitRandomEvents;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 单玩家模拟经营会话上下文
 *
 * @author 11
 * @date 2026/5/26
 */
public class SimPlayerContext {
    private long playerId;
    public VisitRandomEvents visitRandomEvents;
    private PlayerController playerController;
    private SimBaseData simBaseData;
    //技能 gameType -> data
    private Map<Integer, SimSkillsData> skillsDataMap = new HashMap<>();
    //雇员 employeeId -> data (玩家级, 跨场景共享)
    private Map<Integer, SimEmployeeData> employeeMap = new HashMap<>();
    //当前所在场景 (内存中仅保留当前场景, 切换时落库旧场景并加载新场景)
    private SimCasinoData currentCasino;
    //场景及其建筑解锁的游戏快照; 登录缓存, 仅供本玩家高频读取, 解锁场景/游戏时同步刷新
    private SimCasinoUnlock casinoUnlock;

    //待领取的离线收益 (上线计算, 领取后清空)
    private SimOfflineReward pendingOffline;

    //主线/成就任务数据
    private SimTaskData simTaskData;
    //多人协作任务数据
    private SimCoopTaskData simCoopTaskData;
    //赛季玩法玩家聚合数据
    private SeasonPlayerData seasonPlayerData;
    private ActivePassData activePassData;

    public ActivePassData getActivePassData() {
        return activePassData;
    }

    public void setActivePassData(ActivePassData data) {
        activePassData = data;
    }

    //成就徽章固定值加成缓存 (BuildingOutputType -> 固定值; 登录/成就领奖后刷新; 内存态不落库)
    private Map<BuildingOutputType, Integer> medalBuffMap = new EnumMap<>(BuildingOutputType.class);

    // Slots RPC 单次等待 8s、重试一次；按约定保留 2 分钟，最多 2048 局（窗口内平均约 17 局/秒）。
    // RPC 异步池和 Hall 都可能排队超过 2 分钟；超时或容量淘汰后，不再保证旧请求去重。
    private static final int RECENT_SPIN_RESULT_MAX = 2048;
    private static final long RECENT_SPIN_RESULT_TTL_MILLIS = TimeUnit.MINUTES.toMillis(2);
    // 同玩家 RPC/tick 串行；按首次结算顺序淘汰，命中不续期，随上下文释放，不落库。
    private final LinkedHashMap<Long, SpinResultEntry> recentSpinResults = new LinkedHashMap<>();

    private record SpinResultEntry(CommonResult<SlotsSpinResult> result, long recordedAtMillis) {
    }

    //赛季币结算幂等账本 (txnId -> 结算后余额; 内存态; 防扣/发赛季币的超时重试重复应用, 同玩家 RPC 串行无需加锁)
    private final LinkedHashMap<Long, Long> recentSeasonTxns = new LinkedHashMap<>();
    private static final int RECENT_SEASON_TXN_MAX = 64;

    //上次落库检查时间 (ms; 业务置 0 可强制下个 tick 立即检查落库)
    private long lastSaveTime;
    //上次活跃时间
    private long lastActiveTime;
    //上次随机拜访时间 (ms, 内存态, 服务端兜底限频用)
    private long lastRandomVisitTime;
    //当前正在拜访的房主id (内存态; 0 表示在自己场景, 留言板据此判断查谁的)
    private long visitTargetId;
    //赛季宝石在线收益计时游标 (ms, 内存态; 重登从 0 开始以排除离线时段)
    private long lastGemEarningTime;
    //雇员卡池红点已检查的有效卡池版本 (内存态; 仅在开放卡池变化时重算)
    private long employeePoolRedDotVersion = -1;
    private boolean employeeRedDotDirty;

    public boolean isEmployeeRedDotDirty() {
        return employeeRedDotDirty;
    }

    public void setEmployeeRedDotDirty(boolean value) {
        employeeRedDotDirty = value;
    }

    // 建筑红点会话缓存，退出即释放；道具变更置脏，tick在业务变更结束后刷新。
    private boolean buildingRedDotDirty = true;
    private long buildingRedDotCheckTime;
    private String buildingRedDotInput;
    private String buildingRedDotSnapshot;

    //数据看板入口感叹号会话缓存；首次tick立即检查，之后由看板服务按较长间隔刷新。
    private long dashboardWarningCheckTime;
    private Boolean dashboardWarningSnapshot;

    // 获取资源广告入口红点会话快照；冷却和每日次数变化时由 tick 更新。
    private Boolean onlineRewardAdRedDotSnapshot;
    //赛季小额抽奖入口红点快照；仅内存保存，用于抑制重复推送。
    private Boolean seasonSmallDrawRedDotSnapshot;

    //是否在模拟游戏场景中
    private AtomicBoolean inCasino = new AtomicBoolean(true);
    //slots统计信息
    private SlotsStatisticsData slotsStatisticsData;
    //关卡信息
    private SimChapterData simChapterData;
    //找老千小游戏玩家数据，首次访问时加载，随会话释放
    private CheaterHuntData cheaterHuntData;
    //找老千本局内存状态，退出小游戏或释放会话时丢弃
    private CheaterHuntRound cheaterHuntRound;
    //赌神搓牌每日赠送记录，首次访问时加载，随会话释放
    private GodGamblersData godGamblersData;
    //赌神搓牌本局内存状态，重新进入小游戏或退出会话时丢弃
    private GodGamblersRound godGamblersRound;
    //欢乐捉鱼每日赠送日期；单局只留在会话中，掉线按当前积分结算
    private HappyHappyFishData happyHappyFishData;
    private HappyHappyFishRound happyHappyFishRound;
    private SpeedyDeliveryData speedyDeliveryData;
    private SpeedyDeliveryRound speedyDeliveryRound;
    private int pendingRoomEventConfigId;

    public void setPendingRoomEventConfigId(int configId) {
        pendingRoomEventConfigId = configId;
    }

    public int pendingRoomEventConfigId(int requiredConfigId) {
        return pendingRoomEventConfigId == requiredConfigId ? pendingRoomEventConfigId : 0;
    }

    public void clearPendingRoomEvent() {
        pendingRoomEventConfigId = 0;
    }

    public void clearRoomEvent(int configId) {
        if (pendingRoomEventConfigId == configId) {
            clearPendingRoomEvent();
        }
        if (cheaterHuntRound != null && cheaterHuntRound.randomEventConfigId == configId) {
            cheaterHuntRound.randomEventConfigId = 0;
        }
        if (godGamblersRound != null && godGamblersRound.randomEventConfigId == configId) {
            godGamblersRound.randomEventConfigId = 0;
        }
        if (happyHappyFishRound != null && happyHappyFishRound.randomEventConfigId == configId) {
            happyHappyFishRound.randomEventConfigId = 0;
        }
        if (speedyDeliveryRound != null && speedyDeliveryRound.randomEventConfigId == configId) {
            speedyDeliveryRound.randomEventConfigId = 0;
        }
    }

    //标记客户端是不是空白页面
    private boolean blankPage;

    public SpeedyDeliveryData getSpeedyDeliveryData() {
        return speedyDeliveryData;
    }

    public void setSpeedyDeliveryData(SpeedyDeliveryData speedyDeliveryData) {
        this.speedyDeliveryData = speedyDeliveryData;
    }

    public SpeedyDeliveryRound getSpeedyDeliveryRound() {
        return speedyDeliveryRound;
    }

    public void setSpeedyDeliveryRound(SpeedyDeliveryRound speedyDeliveryRound) {
        this.speedyDeliveryRound = speedyDeliveryRound;
    }

    public HappyHappyFishData getHappyHappyFishData() {
        return happyHappyFishData;
    }

    public void setHappyHappyFishData(HappyHappyFishData happyHappyFishData) {
        this.happyHappyFishData = happyHappyFishData;
    }

    public HappyHappyFishRound getHappyHappyFishRound() {
        return happyHappyFishRound;
    }

    public void setHappyHappyFishRound(HappyHappyFishRound happyHappyFishRound) {
        this.happyHappyFishRound = happyHappyFishRound;
    }

    public GodGamblersData getGodGamblersData() {
        return godGamblersData;
    }

    public void setGodGamblersData(GodGamblersData godGamblersData) {
        this.godGamblersData = godGamblersData;
    }

    public GodGamblersRound getGodGamblersRound() {
        return godGamblersRound;
    }

    public void setGodGamblersRound(GodGamblersRound godGamblersRound) {
        this.godGamblersRound = godGamblersRound;
    }

    public CheaterHuntData getCheaterHuntData() {
        return cheaterHuntData;
    }

    public void setCheaterHuntData(CheaterHuntData cheaterHuntData) {
        this.cheaterHuntData = cheaterHuntData;
    }

    public CheaterHuntRound getCheaterHuntRound() {
        return cheaterHuntRound;
    }

    public void setCheaterHuntRound(CheaterHuntRound cheaterHuntRound) {
        this.cheaterHuntRound = cheaterHuntRound;
    }

    public boolean isBuildingRedDotDirty() {
        return buildingRedDotDirty;
    }

    public void setBuildingRedDotDirty(boolean value) {
        buildingRedDotDirty = value;
    }

    public long getBuildingRedDotCheckTime() {
        return buildingRedDotCheckTime;
    }

    public void setBuildingRedDotCheckTime(long value) {
        buildingRedDotCheckTime = value;
    }

    public String getBuildingRedDotInput() {
        return buildingRedDotInput;
    }

    public void setBuildingRedDotInput(String value) {
        buildingRedDotInput = value;
    }

    public String getBuildingRedDotSnapshot() {
        return buildingRedDotSnapshot;
    }

    public void setBuildingRedDotSnapshot(String value) {
        buildingRedDotSnapshot = value;
    }

    public long getDashboardWarningCheckTime() {
        return dashboardWarningCheckTime;
    }

    public void setDashboardWarningCheckTime(long dashboardWarningCheckTime) {
        this.dashboardWarningCheckTime = dashboardWarningCheckTime;
    }

    public Boolean getDashboardWarningSnapshot() {
        return dashboardWarningSnapshot;
    }

    public void setDashboardWarningSnapshot(Boolean dashboardWarningSnapshot) {
        this.dashboardWarningSnapshot = dashboardWarningSnapshot;
    }

    public Boolean getOnlineRewardAdRedDotSnapshot() {
        return onlineRewardAdRedDotSnapshot;
    }

    public void setOnlineRewardAdRedDotSnapshot(Boolean value) {
        onlineRewardAdRedDotSnapshot = value;
    }

    public Boolean getSeasonSmallDrawRedDotSnapshot() {
        return seasonSmallDrawRedDotSnapshot;
    }

    public void setSeasonSmallDrawRedDotSnapshot(Boolean value) {
        seasonSmallDrawRedDotSnapshot = value;
    }

    //联盟免费捐献红点已检查的自然日 (内存态)
    private int allianceDonateRedDotDay;
    //下一条有效入盟申请的过期时间 (ms; -1 表示尚未初始化)
    private long allianceApplicationRedDotNextExpireTime = -1;
    //联盟任务每日首次查看红点已检查的自然日 (内存态)
    private int allianceTaskRedDotDay;

    public PlayerController getPlayerController() {
        return playerController;
    }

    public long getEmployeePoolRedDotVersion() {
        return employeePoolRedDotVersion;
    }

    public void setEmployeePoolRedDotVersion(long employeePoolRedDotVersion) {
        this.employeePoolRedDotVersion = employeePoolRedDotVersion;
    }

    public int getAllianceDonateRedDotDay() {
        return allianceDonateRedDotDay;
    }

    public void setAllianceDonateRedDotDay(int allianceDonateRedDotDay) {
        this.allianceDonateRedDotDay = allianceDonateRedDotDay;
    }

    public long getAllianceApplicationRedDotNextExpireTime() {
        return allianceApplicationRedDotNextExpireTime;
    }

    public void setAllianceApplicationRedDotNextExpireTime(long allianceApplicationRedDotNextExpireTime) {
        this.allianceApplicationRedDotNextExpireTime = allianceApplicationRedDotNextExpireTime;
    }

    public int getAllianceTaskRedDotDay() {
        return allianceTaskRedDotDay;
    }

    public void setAllianceTaskRedDotDay(int allianceTaskRedDotDay) {
        this.allianceTaskRedDotDay = allianceTaskRedDotDay;
    }

    public void setPlayerController(PlayerController playerController) {
        this.playerController = playerController;
    }

    /**
     * 扣除道具需要传 Player, 统一从会话上取
     */
    public Player getPlayer() {
        return playerController == null ? null : playerController.getPlayer();
    }

    public SimBaseData getSimBaseData() {
        return simBaseData;
    }

    public void setSimBaseData(SimBaseData simBaseData) {
        this.simBaseData = simBaseData;
    }

    public Map<Integer, SimSkillsData> getSkillsDataMap() {
        return skillsDataMap;
    }

    public void setSkillsDataMap(Map<Integer, SimSkillsData> skillsDataMap) {
        this.skillsDataMap = skillsDataMap;
    }

    public Map<Integer, SimEmployeeData> getEmployeeMap() {
        return employeeMap;
    }

    public void setEmployeeMap(Map<Integer, SimEmployeeData> employeeMap) {
        this.employeeMap = employeeMap == null ? new HashMap<>() : employeeMap;
    }

    public SimEmployeeData getEmployee(int employeeId) {
        return this.employeeMap.get(employeeId);
    }

    public void putEmployee(SimEmployeeData data) {
        this.employeeMap.put(data.getEmployeeId(), data);
    }

    public void setPlayerId(long playerId) {
        this.playerId = playerId;
    }

    public long playerId() {
        return this.playerId;
    }

    public void send(Object msg) {
        if (playerController != null) {
            playerController.send(msg);
        }
    }

    // ---------------------------------------------------------------------
    // 场景数据访问 (内存仅保留当前场景)
    // ---------------------------------------------------------------------

    /**
     * 获取当前所在场景
     */
    public SimCasinoData getCurrentCasino() {
        return currentCasino;
    }

    /**
     * 设置当前场景实体 (由 SimCasinoService 在加载/切换时维护)
     */
    public void setCurrentCasino(SimCasinoData currentCasino) {
        this.currentCasino = currentCasino;
    }

    /**
     * 切换当前场景 id (内存场景实体替换由 SimCasinoService 完成)
     */
    public void switchCasino(int casinoId) {
        this.simBaseData.setCurrentCasinoId(casinoId);
    }

    public SimCasinoUnlock getCasinoUnlock() {
        return casinoUnlock;
    }

    public void setCasinoUnlock(SimCasinoUnlock casinoUnlock) {
        this.casinoUnlock = casinoUnlock;
    }

    public SimOfflineReward getPendingOffline() {
        return pendingOffline;
    }

    public void setPendingOffline(SimOfflineReward pendingOffline) {
        this.pendingOffline = pendingOffline;
    }

    public SimTaskData getSimTaskData() {
        return simTaskData;
    }

    public void setSimTaskData(SimTaskData simTaskData) {
        this.simTaskData = simTaskData;
    }

    public SimCoopTaskData getSimCoopTaskData() {
        return simCoopTaskData;
    }

    public void setSimCoopTaskData(SimCoopTaskData simCoopTaskData) {
        this.simCoopTaskData = simCoopTaskData;
    }

    public SeasonPlayerData getSeasonPlayerData() {
        return seasonPlayerData;
    }

    public void setSeasonPlayerData(SeasonPlayerData seasonPlayerData) {
        this.seasonPlayerData = seasonPlayerData;
    }

    public Map<BuildingOutputType, Integer> getMedalBuffMap() {
        return medalBuffMap;
    }

    public void setMedalBuffMap(Map<BuildingOutputType, Integer> medalBuffMap) {
        this.medalBuffMap = new EnumMap<>(BuildingOutputType.class);
        if (medalBuffMap != null) {
            this.medalBuffMap.putAll(medalBuffMap);
        }
    }

    /**
     * 赛季币结算幂等: 命中则返回该 txnId 已提交后的余额, 未命中(含 txnId=0)返回 null。
     * 供 slots 超时重试同一 txnId 时避免重复扣/发。
     */
    public Long seasonTxnResult(long txnId) {
        return txnId == 0 ? null : recentSeasonTxns.get(txnId);
    }

    /**
     * 记录一次赛季币结算结果 (txnId -> 结算后余额), 超出容量按插入顺序淘汰最旧。
     */
    public void recordSeasonTxn(long txnId, long balance) {
        if (txnId == 0) {
            return;
        }
        recentSeasonTxns.put(txnId, balance);
        if (recentSeasonTxns.size() > RECENT_SEASON_TXN_MAX) {
            Iterator<Long> it = recentSeasonTxns.keySet().iterator();
            it.next();
            it.remove();
        }
    }

    public long getLastSaveTime() {
        return lastSaveTime;
    }

    public void setLastSaveTime(long lastSaveTime) {
        this.lastSaveTime = lastSaveTime;
    }

    public long getLastActiveTime() {
        return lastActiveTime;
    }

    public void setLastActiveTime(long lastActiveTime) {
        this.lastActiveTime = lastActiveTime;
    }

    public long getLastRandomVisitTime() {
        return lastRandomVisitTime;
    }

    public void setLastRandomVisitTime(long lastRandomVisitTime) {
        this.lastRandomVisitTime = lastRandomVisitTime;
    }

    public long getVisitTargetId() {
        return visitTargetId;
    }

    public void setVisitTargetId(long visitTargetId) {
        visitRandomEvents = null;
        clearPendingRoomEvent();
        this.visitTargetId = visitTargetId;
    }

    public long getLastGemEarningTime() {
        return lastGemEarningTime;
    }

    public void setLastGemEarningTime(long lastGemEarningTime) {
        this.lastGemEarningTime = lastGemEarningTime;
    }

    // ---------------------------------------------------------------------
    // 调试
    // ---------------------------------------------------------------------

    public void printGuest() {
        SimCasinoData casino = getCurrentCasino();
        if (casino == null) {
            return;
        }
        Map<Integer, GuestData> guestMap = casino.getGuestMap();
        if (guestMap == null || guestMap.isEmpty()) {
            return;
        }
        for (Map.Entry<Integer, GuestData> en : guestMap.entrySet()) {
            System.out.println(JSONObject.toJSONString(en.getValue()));
        }
    }

    public void printEmployee() {
        for (Map.Entry<Integer, SimEmployeeData> en : employeeMap.entrySet()) {
            System.out.println(JSONObject.toJSONString(en.getValue()));
        }
    }

    public void printBuilding() {
        SimCasinoData casino = getCurrentCasino();
        if (casino == null) {
            return;
        }
        Map<Integer, BuildingData> buildingMap = casino.getBuildingData();
        if (buildingMap == null || buildingMap.isEmpty()) {
            return;
        }
        for (Map.Entry<Integer, BuildingData> en : buildingMap.entrySet()) {
            System.out.println(JSONObject.toJSONString(en.getValue()));
        }
    }

    public SimSkillsData getSkillData(int gameType) {
        if (this.skillsDataMap == null || this.skillsDataMap.isEmpty()) {
            return null;
        }
        return this.skillsDataMap.get(gameType);
    }

    public void addSkillData(SimSkillsData data) {
        if (this.skillsDataMap == null || this.skillsDataMap.isEmpty()) {
            this.skillsDataMap = new HashMap<>();
        }
        this.skillsDataMap.put(data.getGameType(), data);
    }

    public AtomicBoolean getInCasino() {
        return inCasino;
    }

    public void setInCasino(AtomicBoolean inCasino) {
        this.inCasino = inCasino;
    }

    public SlotsStatisticsData getSlotsStatisticsData() {
        return slotsStatisticsData;
    }

    public void setSlotsStatisticsData(SlotsStatisticsData slotsStatisticsData) {
        this.slotsStatisticsData = slotsStatisticsData;
    }

    /**
     * slots的统计信息
     *
     * @param statInfo
     * @param slotsSpinResult
     */
    public void slotsStatistics(SpinStatInfo statInfo, SlotsSpinResult slotsSpinResult) {
        if (this.slotsStatisticsData == null) {
            this.slotsStatisticsData = new SlotsStatisticsData();
        }

        this.slotsStatisticsData.setSpinCount(this.slotsStatisticsData.getSpinCount() + 1);

        this.slotsStatisticsData.addBigWinCount(statInfo.getBigShowId());
        if (statInfo.getTypeNameId() == 1) {

        } else if (statInfo.getTypeNameId() == 2) {
            this.slotsStatisticsData.setFreeCount(this.slotsStatisticsData.getFreeCount() + 1);
        } else if (statInfo.getTypeNameId() == 3) {
            this.slotsStatisticsData.setSpecialCount(this.slotsStatisticsData.getSpecialCount() + 1);
        } else if (statInfo.getTypeNameId() == 4) {
            this.slotsStatisticsData.setJackpoolCount(this.slotsStatisticsData.getJackpoolCount() + 1);
        }

        this.slotsStatisticsData.setAllWinCoin(this.slotsStatisticsData.getAllWinCoin() + statInfo.getWin());
        this.slotsStatisticsData.setAllBetCoin(this.slotsStatisticsData.getAllBetCoin() + statInfo.getBet());

        if (slotsSpinResult != null) {
            this.slotsStatisticsData.setAllSpendPower(this.slotsStatisticsData.getAllSpendPower() + slotsSpinResult.getCostPower());
            this.slotsStatisticsData.setAllSpendPowerGetCoin(this.slotsStatisticsData.getAllSpendPowerGetCoin() + slotsSpinResult.getSpendPowerGetCoin());
        }
    }

    public CommonResult<SlotsSpinResult> spinResult(long spinId) {
        cleanUpSpinResults();
        SpinResultEntry entry = spinId == 0 ? null : recentSpinResults.get(spinId);
        return entry == null ? null : entry.result();
    }

    public void recordSpinResult(long spinId, CommonResult<SlotsSpinResult> result) {
        cleanUpSpinResults();
        if (spinId == 0 || recentSpinResults.containsKey(spinId)) {
            return;
        }
        if (recentSpinResults.size() >= RECENT_SPIN_RESULT_MAX) {
            recentSpinResults.pollFirstEntry();
        }
        recentSpinResults.put(spinId, new SpinResultEntry(result, System.currentTimeMillis()));
    }

    /**
     * 读写及既有玩家 tick 清理；使用系统毫秒时间，不叠加玩家 GM 时间偏移。
     */
    public void cleanUpSpinResults() {
        long now = System.currentTimeMillis();
        Iterator<SpinResultEntry> it = recentSpinResults.values().iterator();
        while (it.hasNext()) {
            if (now - it.next().recordedAtMillis() < RECENT_SPIN_RESULT_TTL_MILLIS) {
                break;
            }
            it.remove();
        }
    }

    public SimChapterData getSimChapterData() {
        return simChapterData;
    }

    public void setSimChapterData(SimChapterData simChapterData) {
        this.simChapterData = simChapterData;
    }

    public boolean isBlankPage() {
        return blankPage;
    }

    public void setBlankPage(boolean blankPage) {
        this.blankPage = blankPage;
    }
}
