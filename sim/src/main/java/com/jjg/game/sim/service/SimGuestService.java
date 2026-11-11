package com.jjg.game.sim.service;

import com.jjg.game.alliance.service.AllianceEventService;
import com.jjg.game.common.config.NodeConfig;
import com.jjg.game.common.pb.ItemInfo;
import com.jjg.game.common.proto.Pair;
import com.jjg.game.common.utils.HttpUtils;
import com.jjg.game.common.utils.RandomUtils;
import com.jjg.game.common.utils.TimeHelper;
import com.jjg.game.common.utils.WeightRandom;
import com.jjg.game.core.base.condition.numeric.ActionConditionEvent;
import com.jjg.game.core.base.condition.numeric.GuestInviteConditionEvent;
import com.jjg.game.core.base.reddot.IRedDotService;
import com.jjg.game.core.constant.AddType;
import com.jjg.game.core.constant.Code;
import com.jjg.game.core.dao.RedDotReadDao;
import com.jjg.game.core.data.*;
import com.jjg.game.core.listener.GameFunctionListener;
import com.jjg.game.core.listener.ItemListener;
import com.jjg.game.core.manager.RedDotManager;
import com.jjg.game.core.pb.KVInfo;
import com.jjg.game.core.pb.NotifyOpenFunction;
import com.jjg.game.core.pb.RechargeType;
import com.jjg.game.core.pb.reddot.RedDotDetails;
import com.jjg.game.core.service.OrderService;
import com.jjg.game.core.service.PlayerPackService;
import com.jjg.game.core.service.SpecialGuestDailyCountService;
import com.jjg.game.core.utils.ItemUtils;
import com.jjg.game.sampledata.GameDataManager;
import com.jjg.game.sampledata.bean.*;
import com.jjg.game.sim.constant.BuildingOutputType;
import com.jjg.game.sim.constant.ServerBuildingType;
import com.jjg.game.sim.constant.SimConstant;
import com.jjg.game.sim.dao.SimCasinoDao;
import com.jjg.game.sim.dao.SimPlayerGameDao;
import com.jjg.game.sim.data.*;
import com.jjg.game.sim.listener.SimPlayerTickListener;
import com.jjg.game.sim.listener.SimTaskStateReporter;
import com.jjg.game.sim.manager.SimPlayerContextRegistry;
import com.jjg.game.sim.pb.SimPbConverter;
import com.jjg.game.sim.pb.res.*;
import com.jjg.game.sim.pb.struct.*;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.IntSupplier;

/**
 * 游客生成、解锁
 *
 * @author 11
 * @date 2026/5/26
 */
@Service
public class SimGuestService implements SimPlayerTickListener, ItemListener, SimTaskStateReporter, IRedDotService, GameFunctionListener {
    private static final Logger log = LoggerFactory.getLogger(SimGuestService.class);
    private static final List<Integer> RED_DOT_SUBMODULES = List.of(
            SimConstant.SpecialGuest.RED_DOT_FREE_REFRESH,
            SimConstant.SpecialGuest.RED_DOT_AD_AVAILABLE,
            SimConstant.SpecialGuest.RED_DOT_INVITE_ITEM,
            SimConstant.SpecialGuest.RED_DOT_DAILY_ENTRY);
    private static final String RED_DOT_DAILY_ENTRY_SCOPE = "special_guest_daily_entry";

    @Autowired
    private SimConfigCacheService configCache;
    @Autowired
    private SimCasinoDao simCasinoDao;
    @Autowired
    private SimPlayerGameDao simPlayerGameDao;
    @Autowired
    private SimRewardService rewardService;
    @Autowired
    private PlayerPackService playerPackService;
    @Autowired
    private OrderService orderService;
    @Autowired
    private SpecialGuestDailyCountService specialGuestDailyCountService;
    @Autowired
    private SimPlayerContextRegistry simPlayerContextRegistry;
    @Autowired
    private AllianceEventService allianceEventService;
    @Autowired
    private SimGuideConfigService guideConfigService;
    //懒加载打破与 SimTaskService 的循环依赖 (对方持有本服务作状态补报口)
    @Autowired
    @Lazy
    private SimTaskService simTaskService;
    @Autowired
    private NodeConfig nodeConfig;
    @Autowired
    private RedDotManager redDotManager;
    @Autowired
    private RedDotReadDao redDotReadDao;
    @Autowired
    private SimEmployeeRedDotService employeeRedDotService;
    @Autowired
    private SimBuildingService buildingService;

    @Override
    public void onTick(SimPlayerContext ctx, long now) {
        generateGuestEvent(ctx, now);
        refreshTimedSpecialGuestRedDots(ctx, now);
    }

    @Override
    public int order() {
        return 10;
    }

    /**
     * 生成游客 (定时器入口)
     */
    public void generateGuestEvent(SimPlayerContext ctx, long now) {
        //新手引导未完成不生成
        if (!newGuide(ctx.getSimBaseData()) && !ctx.getSimBaseData().isGuide()) {
            return;
        }
        //当前场景
        SimCasinoData casino = ctx.getCurrentCasino();
        if (casino == null) {
            return;
        }
        //场景配置
        CasinoStatsSheetCfg casinoCfg = configCache.getCasinoStatsSheetCfg(casino.getCasinoId(), casino.getCasinoLevel());
        if (casinoCfg == null) {
            return;
        }
        //计算实际生成间隔(ms)
        long intervalMs = computeVisitIntervalMs(ctx, casinoCfg, casino, now, false);
        if (casino.getLastGenerateTime() != 0 && now - casino.getLastGenerateTime() < intervalMs) {
            return;
        }

        //检查是否有解锁的游客
        if (casino.getGuestMap() == null || casino.getGuestMap().isEmpty()) {
            casino.setLastGenerateTime(now);
            return;
        }

        int generated = batchGenerateGuest(ctx, casinoCfg.getVisitorSpawnCount(), casinoCfg, now, null);
        allianceEventService.onGuestGenerated(ctx.playerId(), false, generated);
        notifyGuestOutputLimit(ctx, casinoCfg, now);
    }

    private boolean newGuide(SimBaseData simBaseData) {
        if (configCache.getGenGuestGuideSet() == null || configCache.getGenGuestGuideSet().isEmpty()) {
            return true;
        }

        for (int guideId : configCache.getGenGuestGuideSet()) {
            if (simBaseData.hasCompletedGuideId(guideId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 指定游客id批量生成
     *
     * @param ctx
     * @param guestId
     * @param num
     */
    public int batchGenerateSpecifyIdGuest(SimPlayerContext ctx, int guestId, int num) {
        List<GuestInfo> guests = generateSpecifyIdGuests(ctx, guestId, num);
        if (!guests.isEmpty()) {
            NotifyGenerateGuest notify = new NotifyGenerateGuest(Code.SUCCESS);
            notify.guests = guests;
            ctx.send(notify);
        }
        return guests.size();
    }

    private List<GuestInfo> generateSpecifyIdGuests(SimPlayerContext ctx, int guestId, int num) {
        if (num <= 0) {
            return Collections.emptyList();
        }
        //当前场景
        SimCasinoData casino = ctx.getCurrentCasino();
        if (casino == null) {
            log.warn("生成指定游客id失败，当前场景数据不存在 playerId={},currentCasinoId={}", ctx.playerId(), ctx.getSimBaseData().getCurrentCasinoId());
            return Collections.emptyList();
        }

        VisitorQuestCfg visitorQuestCfg = GameDataManager.getVisitorQuestCfg(guestId);
        if (visitorQuestCfg == null) {
            log.warn("生成指定游客id失败，获取 visitorQuestCfg 配置未找到 playerId={},guestId={}", ctx.playerId(), guestId);
            return Collections.emptyList();
        }

        //场景配置
        CasinoStatsSheetCfg casinoCfg = configCache.getCasinoStatsSheetCfg(casino.getCasinoId(), casino.getCasinoLevel());
        if (casinoCfg == null) {
            log.warn("生成指定游客id失败，获取 CasinoStatsSheetCfg 配置未找到 playerId={},casinoId={},level={}", ctx.playerId(), casino.getCasinoId(), casino.getCasinoLevel());
            return Collections.emptyList();
        }
        GuestData guestData = new GuestData();
        guestData.setId(guestId);
        guestData.setLevel(1);
        guestData.setStar(1);

        long now = System.currentTimeMillis();
        List<GuestInfo> guests = new ArrayList<>(num);
        for (int i = 0; i < num; i++) {
            try {
                GuestInfo guestInfo = generateOneGuest(ctx, null, casinoCfg, now, guestData);
                if (guestInfo != null) {
                    guests.add(guestInfo);
                }
            } catch (Exception e) {
                log.error("生成指定游客异常 playerId={},guestId={}", ctx.playerId(), guestId, e);
            }
        }
        return guests;
    }

    /**
     * 指定游客品质批量生成
     *
     * @param ctx
     * @param quality
     * @param num
     */
    public void batchGenerateSpecifyQualityGuest(SimPlayerContext ctx, int quality, int num) {
        //当前场景
        SimCasinoData casino = ctx.getCurrentCasino();
        if (casino == null) {
            log.warn("生成指定游客quality失败，当前场景数据不存在 playerId={},currentCasinoId={}", ctx.playerId(), ctx.getSimBaseData().getCurrentCasinoId());
            return;
        }
        //场景配置
        CasinoStatsSheetCfg casinoCfg = configCache.getCasinoStatsSheetCfg(casino.getCasinoId(), casino.getCasinoLevel());
        if (casinoCfg == null) {
            log.warn("生成指定游客quality失败，获取 CasinoStatsSheetCfg 配置未找到 playerId={},casinoId={},level={}", ctx.playerId(), casino.getCasinoId(), casino.getCasinoLevel());
            return;
        }

        List<VisitorQuestCfg> visitorQuestCfgList = configCache.getVisitorQuestCfgList(quality);
        if (visitorQuestCfgList == null || visitorQuestCfgList.isEmpty()) {
            log.warn("生成指定游客quality失败，获取 VisitorQuestCfg 配置未找到 playerId={},quality={}", ctx.playerId(), quality);
            return;
        }

        long now = System.currentTimeMillis();
        NotifyGenerateGuest notify = new NotifyGenerateGuest(Code.SUCCESS);
        notify.guests = new ArrayList<>();
        for (int i = 0; i < num; i++) {
            VisitorQuestCfg visitorQuestCfg = RandomUtils.randomEle(visitorQuestCfgList);

            GuestData guestData = new GuestData();
            guestData.setId(visitorQuestCfg.getId());
            guestData.setLevel(1);
            guestData.setStar(1);

            //生成单个游客
            GuestInfo guestInfo = generateOneGuest(ctx, null, casinoCfg, now, guestData);
            if (guestInfo != null) {
                //将游客信息放入列表
                notify.guests.add(guestInfo);
            }
        }
        if (!notify.guests.isEmpty()) {
            ctx.send(notify);
        }
    }

    // ---------------------------------------------------------------------
    // 购买游客 (点击购买后立即生成, 不走定时逻辑; 预生成目的地和奖励, 奖励延后领取时才添加)
    // ---------------------------------------------------------------------

    /**
     * 生成购买游客
     * 预生成目的地序列和奖励并分配唯一 uid; 客户端确认到达目的地后将奖励存入对应建筑
     *
     * @param ctx
     * @param guestId 购买的游客id
     */
    public void generatePurchasedGuest(SimPlayerContext ctx, int guestId, int count) {
        ResGenPurchasedGuest res = new ResGenPurchasedGuest(Code.SUCCESS);
        appendPurchasedGuests(ctx, guestId, count, null, 0, 0, res);
        if (res.code == Code.SUCCESS) {
            allianceEventService.onGuestGenerated(ctx.playerId(), true, res.guests.size());
        }
        ctx.send(res);
    }

    private boolean appendPurchasedGuests(SimPlayerContext ctx, int guestId, int count, String orderId, int poolId, int cfgId,
                                          ResGenPurchasedGuest res) {
        if (count < 1) {
            count = 1;
        }

        SimCasinoData casino = ctx.getCurrentCasino();
        if (casino == null) {
            log.warn("生成购买游客失败，当前场景数据不存在 playerId={},currentCasinoId={}", ctx.playerId(), ctx.getSimBaseData().getCurrentCasinoId());
            res.code = Code.NOT_FOUND;
            return false;
        }
        VisitorQuestCfg visitorQuestCfg = GameDataManager.getVisitorQuestCfg(guestId);
        if (visitorQuestCfg == null) {
            log.warn("生成购买游客失败，VisitorQuest 配置未找到 playerId={},guestId={}", ctx.playerId(), guestId);
            res.code = Code.NOT_FOUND;
            return false;
        }
        CasinoStatsSheetCfg casinoCfg = configCache.getCasinoStatsSheetCfg(casino.getCasinoId(), casino.getCasinoLevel());
        if (casinoCfg == null) {
            log.warn("生成购买游客失败，获取 CasinoStatsSheetCfg 配置未找到 playerId={},casinoId={},level={}", ctx.playerId(), casino.getCasinoId(), casino.getCasinoLevel());
            res.code = Code.FAIL;
            return false;
        }

        if (res.guests == null) {
            res.guests = new ArrayList<>();
        }
        long receptionCapacity = receptionCapacity(ctx);
        boolean guestUnlocked = false;
        for (int i = 0; i < count; i++) {
            GuestData guest = casino.findGuestData(guestId);
            if (guest == null) {
                guest = new GuestData();
                guest.setId(guestId);
                guest.setStar(1);
                guest.setLevel(1);
                casino.addGuest(guest);
                guestUnlocked = true;
            }

            //本次交互次数 (有奖励 + 无奖励)
            VisitorStarCfg starCfg = rewardService.getStarCfg(guestId, guest.getStar());
            int prosperity = buildingService.computeProsperity(casino);
            int rewardedCount = computeInteractionCount(receptionCapacity, visitorQuestCfg.getServiceCapacity(), prosperity, starCfg);
            int unrewardedCount = computeInteractionCount(receptionCapacity, visitorQuestCfg.getBaseServiceCapacity(), prosperity, starCfg);
            if (rewardedCount + unrewardedCount <= 0) {
                log.info("生成购买游客但交互次数为 0, 跳过 playerId={},guestId={}", ctx.playerId(), guestId);
                continue;
            }

            //预生成目的地序列和奖励，到达目的地时存入对应建筑
            List<DestinationInfo> destinations = planDestinations(guest, visitorQuestCfg, casino, rewardedCount);
            if (destinations.isEmpty()) {
//            log.warn("生成购买游客失败，目的地序列为空 playerId={},guestId={}", ctx.playerId(), guestId);
                continue;
            }

            PurchasedGuestData data = new PurchasedGuestData();
            data.setUid(RandomUtils.getUUid());
            data.setGuestId(guestId);
            data.setOrderId(orderId);
            data.setCfgId(cfgId);
            data.setPoolId(poolId);
            data.setStar(guest.getStar());
            data.setLevel(guest.getLevel());

            Map<Integer, DestinationInfo> map = new HashMap<>();
            for (DestinationInfo info : destinations) {
                map.put(info.index, info);
            }
            data.setDestinations(map);
            casino.addPurchasedGuest(data);
            //经营信息: 招商生成一名高级游客即累计一次, 与该游客的目的地数量无关
            ctx.getSimBaseData().addReceptionCount(1);
            ctx.getSimBaseData().recordGuestGenerated(true, System.currentTimeMillis());
            recordDashboardJourney(casino, destinations, System.currentTimeMillis());

            res.guests.add(SimPbConverter.toGuestInfo(data, visitorQuestCfg));
            //累加经验
            guest.addExp(configCache.getVisitorLevelCfgMap());
        }
        if (guestUnlocked) {
            employeeRedDotService.updateRedDots(ctx.playerId(),
                    SimConstant.Employee.RED_DOT_GUEST_STAR_UP);
        }
        return true;
    }

    /**
     * 确认购买游客到达单个目的地，将预生成奖励存入建筑；全部目的地完成后移除该游客。
     *
     * @param ctx
     * @param uid   购买游客唯一id
     * @param index 目的地序号 (destinations 下标)
     */
    public void claimPurchasedGuestReward(SimPlayerContext ctx, String uid, int index) {
        ResPurchasedGuestReward res = new ResPurchasedGuestReward(Code.SUCCESS);
        res.uid = uid;
        res.index = index;

        if (uid == null || uid.isEmpty() || index < 0) {
            res.code = Code.PARAM_ERROR;
            ctx.send(res);
            log.warn("领取购买游客奖励失败，uid参数错误 playerId={},uid={}", ctx.playerId(), uid);
            return;
        }

        SimCasinoData casino = ctx.getCurrentCasino();
        if (casino == null) {
            res.code = Code.NOT_FOUND;
            ctx.send(res);
            log.warn("领取购买游客奖励失败，当前场景数据不存在 playerId={},uid={}", ctx.playerId(), uid);
            return;
        }
        PurchasedGuestData data = casino.findPurchasedGuest(uid);
        if (data == null) {
            res.code = Code.NOT_FOUND;
            ctx.send(res);
            log.warn("领取购买游客奖励失败，未找到对应购买游客 playerId={},uid={}", ctx.playerId(), uid);
            return;
        }

        Map<Integer, DestinationInfo> destinations = data.getDestinations();
        if (destinations == null || destinations.isEmpty()) {
            res.code = Code.NOT_FOUND;
            ctx.send(res);
            log.warn("领取购买游客奖励失败，destinations为空 playerId={},uid={},index={}", ctx.playerId(), uid, index);
            return;
        }

        DestinationInfo dest = destinations.get(index);
        if (dest == null) {
            res.code = Code.NOT_FOUND;
            ctx.send(res);
            log.warn("领取购买游客奖励失败，该目的地未找到 playerId={},uid={},index={}", ctx.playerId(), uid, index);
            return;
        }

        if (!dest.claimed) {
            if (dest.rewards != null && !dest.rewards.isEmpty()) {
                BuildingData building = casino.findBuilding(dest.buildingId);
                if (building == null) {
                    res.code = Code.NOT_FOUND;
                    log.warn("领取购买游客奖励失败，目的地建筑不存在 playerId={},casinoId={},uid={},index={},buildingId={}",
                            ctx.playerId(), casino.getCasinoId(), uid, index, dest.buildingId);
                    ctx.send(res);
                    return;
                }
                for (ItemInfo item : dest.rewards) {
                    building.getGuestRewards().merge(item.itemId, item.count, Long::sum);
                }
            }
            consumeTavernGoods(ctx, dest.buildingId);
        }
        dest.claimed = true;
        res.rewards = dest.rewards;

        destinations.remove(index);
        if (destinations.isEmpty()) {
            casino.removePurchasedGuest(uid);
        }
        log.info("购买游客目的地奖励存入建筑 playerId={},guestId={},uid={},index={},buildingId={}",
                ctx.playerId(), data.getGuestId(), uid, index, dest.buildingId);
        ctx.send(res);
    }

    /**
     * 批量生成游客
     *
     * @param ctx
     * @param num
     * @param casinoCfg
     * @param now
     * @param specifyGuest
     */
    public int batchGenerateGuest(SimPlayerContext ctx, int num, CasinoStatsSheetCfg casinoCfg, long now, GuestData specifyGuest) {
        NotifyGenerateGuest notify = new NotifyGenerateGuest(Code.SUCCESS);
        notify.guests = new ArrayList<>();

        WeightRandom<GuestData> guestDataWeightRandom = weightRandomGuest(ctx);

        int cacheSize = 0;

        for (int i = 0; i < num; i++) {
            //生成单个游客
            GuestInfo guestInfo = generateOneGuest(ctx, guestDataWeightRandom, casinoCfg, now, specifyGuest);
            if (guestInfo == null) {
                continue;
            }
            if (ctx.getInCasino().get() || ctx.getCurrentCasino().cacheGuestInfoListSize() > ctx.getCurrentCasino().getCacheGuestSize()) {
                //将游客信息放入列表
                notify.guests.add(guestInfo);
            } else {
                ctx.getCurrentCasino().addCacheGuestInfo(guestInfo);
                cacheSize++;
            }
        }

        if (!notify.guests.isEmpty()) {
            ctx.send(notify);
        }
        return notify.guests.size() + cacheSize;
    }

    /**
     * 批量生成 count 个用于展示的普通假游客，不发奖励、不加经验、不写入任何接待或生成记录，也不推送消息。
     * 无可选游客或可用目的地时跳过，返回实际生成的列表，展示奖励放入 fakeRewards。
     */
    public List<GuestInfo> batchGenerateFakeGuests(SimPlayerContext ctx, int count) {
        if (count <= 0) {
            return null;
        }
        List<GuestInfo> guests = new ArrayList<>();
        SimCasinoData casino = ctx.getCurrentCasino();
        WeightRandom<GuestData> random = weightRandomGuest(ctx);
        int prosperity = buildingService.computeProsperity(casino);
        long receptionCapacity = receptionCapacity(ctx);
        for (int i = 0; i < count; i++) {
            GuestData guest = random.next();
            if (guest == null) {
                break;
            }
            VisitorQuestCfg visitorQuestCfg = GameDataManager.getVisitorQuestCfg(guest.getId());
            if (visitorQuestCfg == null) {
                continue;
            }
            VisitorStarCfg starCfg = rewardService.getStarCfg(guest.getId(), guest.getStar());
            int rewardedCount = computeInteractionCount(receptionCapacity, visitorQuestCfg.getServiceCapacity(), prosperity, starCfg);
            List<DestinationInfo> destinations = planDestinations(guest, visitorQuestCfg, casino, rewardedCount, false,
                    canGenerateOrdinaryGuestRewards(ctx, System.currentTimeMillis()));
            if (destinations.isEmpty()) {
                continue;
            }
            for (DestinationInfo destination : destinations) {
                destination.fakeRewards = true;
            }
            guests.add(SimPbConverter.toGuestInfo(guest, destinations, visitorQuestCfg));
        }
        return guests;
    }

    /**
     * 生成单个游客，不推送消息
     *
     * @param ctx
     * @param guestDataWeightRandom
     * @param casinoCfg
     * @param now
     * @param specifyGuest
     * @return
     */
    private GuestInfo generateOneGuest(SimPlayerContext ctx, WeightRandom<GuestData> guestDataWeightRandom, CasinoStatsSheetCfg casinoCfg, long now, GuestData specifyGuest) {
        //加权随机选一个已解锁的游客
        GuestData guest = specifyGuest;
        if (guest == null) {
            if (guestDataWeightRandom == null) {
                return null;
            }
            guest = guestDataWeightRandom.next();
            if (guest == null) {
                ctx.getCurrentCasino().setLastGenerateTime(now);
                return null;
            }
        }
        VisitorQuestCfg visitorQuestCfg = GameDataManager.getVisitorQuestCfg(guest.getId());
        if (visitorQuestCfg == null) {
            ctx.getCurrentCasino().setLastGenerateTime(now);
            log.warn("生成游客失败，VisitorQuest 配置未找到 playerId={},guestId={}", ctx.playerId(), guest.getId());
            return null;
        }

        //本次交互次数 (有奖励 + 无奖励)
        VisitorStarCfg starCfg = rewardService.getStarCfg(guest.getId(), guest.getStar());
        int prosperity = buildingService.computeProsperity(ctx.getCurrentCasino());
        long receptionCapacity = receptionCapacity(ctx);
        int rewardedCount = computeInteractionCount(receptionCapacity, visitorQuestCfg.getServiceCapacity(), prosperity, starCfg);
        int unrewardedCount = computeInteractionCount(receptionCapacity, visitorQuestCfg.getBaseServiceCapacity(), prosperity, starCfg);
        if (rewardedCount + unrewardedCount <= 0) {
            ctx.getCurrentCasino().setLastGenerateTime(now);
//            log.info("生成游客但交互次数为 0, 跳过 playerId={},guestId={}", ctx.playerId(), visitorQuestCfg.getId());
            return null;
        }

        //规划本次行程的目的地序列 (生成奖励)
        List<DestinationInfo> destinations = planDestinations(guest, visitorQuestCfg, ctx.getCurrentCasino(), rewardedCount, true,
                canGenerateOrdinaryGuestRewards(ctx, now));
        if (destinations.isEmpty()) {
            ctx.getCurrentCasino().setLastGenerateTime(now);
//            log.warn("生成游客失败，目的地序列为空 playerId={},guestId={}", ctx.playerId(), visitorQuestCfg.getId());
            return null;
        }

        //奖励生成即存入建筑；场景外缓存的游客仅用于补发展示。
        for (DestinationInfo info : destinations) {
            consumeTavernGoods(ctx, info.buildingId);
            if (info.rewards == null || info.rewards.isEmpty()) {
                continue;
            }

            BuildingData building = ctx.getCurrentCasino().findBuilding(info.buildingId);
            for (ItemInfo item : info.rewards) {
                building.getGuestRewards().merge(item.itemId, item.count, Long::sum);
            }
        }

        //累加经验
        guest.addExp(configCache.getVisitorLevelCfgMap());

        ctx.getCurrentCasino().setLastGenerateTime(now);
        recordDashboardJourney(ctx.getCurrentCasino(), destinations, now);
        ctx.getSimBaseData().recordGuestGenerated(false, now);
        return SimPbConverter.toGuestInfo(guest, destinations, visitorQuestCfg);
    }

    private void consumeTavernGoods(SimPlayerContext ctx, int buildingId) {
        if (buildingId == SimConstant.TAVERN_BUILDING_ID && ctx.getSpeedyDeliveryData() != null) {
            ctx.getSpeedyDeliveryData().consumeShelfGoods();
        }
    }

    private void notifyGuestOutputLimit(SimPlayerContext ctx, CasinoStatsSheetCfg cfg, long now) {
        SimBaseData base = ctx.getSimBaseData();
        Pair<Long,Boolean> onlineTime = base.accumulateOnlineTime(now);
        if (base.isGuestOutputLimitNotified() || cfg.getVisitorDailyLimit() <= 0
                || ctx.getPlayerController() == null) {
            return;
        }
        long threshold = cfg.getVisitorDailyLimit() * 60_000L
                * SimConstant.Common.GUEST_OUTPUT_LIMIT_NOTIFY_PERCENT / 100;
        if (onlineTime.getFirst() >= threshold) {
            NotifyOpenFunction notify = new NotifyOpenFunction();
            notify.functionIdList = List.of(SimConstant.Common.GUEST_OUTPUT_LIMIT_FUNCTION_ID);
            ctx.send(notify);
            base.setGuestOutputLimitNotified(true);
        }else if(onlineTime.getSecond()){
            //重置的话要关闭
            NotifyOpenFunction notify = new NotifyOpenFunction();
            notify.closeFunctionIdList = List.of(SimConstant.Common.GUEST_OUTPUT_LIMIT_FUNCTION_ID);
            ctx.send(notify);
        }
    }

    /**
     * 记录细分运营看板使用的固定窗口游客数和各建筑交互数。
     */
    private void recordDashboardJourney(SimCasinoData casino, List<DestinationInfo> destinations, long now) {
        if (casino == null || destinations == null || destinations.isEmpty()) {
            return;
        }
        casino.recordGenerate(now, SimConstant.Common.CAPACITY_WINDOW_MS);
        List<Integer> buildingIds = new ArrayList<>(destinations.size());
        for (DestinationInfo destination : destinations) {
            if (destination != null && destination.buildingId > 0) {
                buildingIds.add(destination.buildingId);
            }
        }
        casino.recordBuildingInteractions(now, SimConstant.Common.CAPACITY_WINDOW_MS, buildingIds);
    }

    /**
     * 计算实际来访间隔 (ms)
     * 当场景满员 (10 分钟内生成人数 >= 场景容纳上限) 时降低刷新频率:
     * 实际间隔 = 基础间隔 * 当前总人数 / 上限
     *
     * @param show 表示是否为展示数据，如果为展示数据，就不用考虑限制
     */
    public long computeVisitIntervalMs(SimPlayerContext ctx, CasinoStatsSheetCfg casinoCfg, SimCasinoData casino, long now, boolean show) {
        long base = (long) casinoCfg.getBaseVisitInterval() * 1000L;
        int recent = casino.countGenerateInWindow(now, SimConstant.Common.CAPACITY_WINDOW_MS);

        //获取当前运营部的属性
        int operateBuildId = configCache.getCasinoManageBuildId(casino.getCasinoId(), ServerBuildingType.OPERATIONS);
        if (operateBuildId < 1) {
            return computeVisitIntervalMsResult(casino, recent, base, show);
        }
        BuildingData building = casino.findBuilding(operateBuildId);
        if (building == null) {
            return computeVisitIntervalMsResult(casino, recent, base, show);
        }
        Map<BuildingOutputType, Long> buildingValues = buildingService.computeDashboardBuildingValues(ctx, building);
        if (buildingValues.isEmpty()) {
            return computeVisitIntervalMsResult(casino, recent, base, show);
        }

        //获取基础繁荣度
        CasinoStatsSheetCfg casinoStatsSheetCfg = configCache.getCasinoStatsSheetCfg(casino.getCasinoId(), casino.getCasinoLevel());
        if (casinoStatsSheetCfg == null) {
            return computeVisitIntervalMsResult(casino, recent, base, show);
        }

        double tmpExposureTimes = Math.round(
                (double) buildingValues.getOrDefault(BuildingOutputType.EXPOSURE, 0L)
                        / casinoStatsSheetCfg.getExposureRequirements() * 10000
        ) / 10000.0;
        double param2 = Math.max(0, 1 - tmpExposureTimes);
        double param3 = param2 * (casinoStatsSheetCfg.getMaxMultiplier() - 1);

        long actual = (int) (base * (1 + param3));
        return computeVisitIntervalMsResult(casino, recent, actual, show);
    }

    private long computeVisitIntervalMsResult(SimCasinoData casino, int recent, long actual, boolean show) {
        int capacity = casino.getCacheGuestSize();
        //容量为零时不做满员降频，避免除零。
        if (show || capacity <= 0 || recent < capacity) {
            return actual;
        }
        return actual * recent / capacity;
    }

    /**
     * 计算单类交互次数 (有奖励 / 无奖励):
     * 1) 服务能力先按 VisitorStarCfg.Additioncoefficient (百分比) 加成
     * 2) floor(serviceCapacity / Prosperity) + 小数部分概率触发 +1
     */
    private int computeInteractionCount(long receptionCapacity, int serviceCapacity, int prosperity, VisitorStarCfg starCfg) {
        if (serviceCapacity <= 0 || prosperity <= 0) {
            return 0;
        }
        int coefficient = SimConstant.Common.SERVICE_CAPACITY_COEFFICIENT_BASE;
        if (starCfg != null && starCfg.getAdditioncoefficient() > 0) {
            coefficient = starCfg.getAdditioncoefficient();
        }
        double effective = (double) serviceCapacity * coefficient / SimConstant.Common.SERVICE_CAPACITY_COEFFICIENT_BASE;

        effective += receptionCapacity;

        double ratio = effective / (double) prosperity;
        int floor = (int) Math.floor(ratio);
        double frac = ratio - floor;
        if (frac > 0 && RandomUtils.getRandomNumDou() < frac) {
            floor += 1;
        }
        return floor;
    }

    private long receptionCapacity(SimPlayerContext ctx) {
        int welcomeBuildId = configCache.getCasinoManageBuildId(ctx.getCurrentCasino().getCasinoId(), ServerBuildingType.WELCOME);
        if (welcomeBuildId > 0) {
            BuildingData buildingData = ctx.getCurrentCasino().findBuilding(welcomeBuildId);
            if (buildingData != null) {
                return buildingService.computeDashboardBuildingValues(ctx, buildingData)
                        .getOrDefault(BuildingOutputType.SERVICE, 0L);
            }
        }
        return 0;
    }

    /**
     * 按已解锁游客的刷新权重加权随机
     *
     * @param ctx
     * @return
     */
    private WeightRandom<GuestData> weightRandomGuest(SimPlayerContext ctx) {
        WeightRandom<GuestData> random = WeightRandom.create();
        SimCasinoData casino = ctx.getCurrentCasino();
        if (casino.getGuestMap() == null || casino.getGuestMap().isEmpty()) {
            return random;
        }
        BigDecimal awarenessRatio = guestAwarenessRatio(ctx, casino);
        for (GuestData g : casino.getGuestMap().values()) {
            VisitorQuestCfg cfg = GameDataManager.getVisitorQuestCfg(g.getId());
            if (cfg == null) {
                continue;
            }
            int weight = effectiveRefreshWeight(awarenessRatio, cfg);
            if (weight > 0) {
                random.add(g, weight);
            }
        }
        return random;
    }

    /**
     * 计算游客的有效刷新权重 (考虑知名度修正)
     */
    private int effectiveRefreshWeight(BigDecimal awarenessRatio, VisitorQuestCfg cfg) {
        int base = cfg.getBaseWeight();
        if (cfg.getAwareness() <= 0 || awarenessRatio.signum() <= 0) {
            return base;
        }
        return awarenessRatio.multiply(BigDecimal.valueOf(cfg.getAwareness())).intValue() + base;
    }

    private BigDecimal guestAwarenessRatio(SimPlayerContext ctx, SimCasinoData casino) {
        int operateBuildId = configCache.getCasinoManageBuildId(casino.getCasinoId(), ServerBuildingType.OPERATIONS);
        BuildingData buildingData = casino.findBuilding(operateBuildId);
        if (buildingData == null) {
            return BigDecimal.ZERO;
        }
        //保留原刷新权重取值口径：OPERATIONS 对应曝光建筑，此处使用该建筑的实际曝光值。
        long awareness = buildingService.computeDashboardBuildingValues(ctx, buildingData)
                .getOrDefault(BuildingOutputType.EXPOSURE, 0L);
        if (awareness < 1) {
            return BigDecimal.ZERO;
        }

        GlobalConfigCfg globalConfigCfg = GameDataManager.getGlobalConfigCfg(SimConstant.Global.GUEST_AWARENESS_MAX);
        if (globalConfigCfg == null || globalConfigCfg.getIntValue() < 1) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(awareness).divide(BigDecimal.valueOf(globalConfigCfg.getIntValue()), 4, RoundingMode.HALF_EVEN);
    }

    /**
     * 规划本次行程的目的地序列:
     * - 有奖励交互: 按 InteractionWeight 加权随机一个建筑, 标记 rewarded=true
     * - 无奖励交互: 按 TargetArea 顺序循环
     * - 两类穿插; 简单实现为先有奖励, 再无奖励
     * - 目标建筑未解锁/未配置则跳过该次交互
     * - 概率交互: 优先逐个判定 SpecialInteraction, 成功生成交互点后消耗一次 rewardedCount, 耗尽即结束
     * - 有奖励交互点结算奖励到 dest.rewards (是否添加到玩家身上由调用方决定)
     */
    private List<DestinationInfo> planDestinations(GuestData guest, VisitorQuestCfg cfg, SimCasinoData casino, int rewardedCount) {
        return planDestinations(guest, cfg, casino, rewardedCount, true, true);
    }

    private boolean canGenerateOrdinaryGuestRewards(SimPlayerContext ctx, long now) {
        SimCasinoData casino = ctx.getCurrentCasino();
        CasinoStatsSheetCfg cfg = configCache.getCasinoStatsSheetCfg(casino.getCasinoId(), casino.getCasinoLevel());
        return cfg != null && ctx.getSimBaseData().accumulateOnlineTime(now).getFirst() <= cfg.getVisitorDailyLimit() * 60_000L;
    }

    private List<DestinationInfo> planDestinations(GuestData guest, VisitorQuestCfg cfg, SimCasinoData casino, int rewardedCount,
                                                   boolean recordReception, boolean generateRewards) {
        if (rewardedCount < 1) {
            return Collections.emptyList();
        }
        List<DestinationInfo> result = new ArrayList<>();

        int id = 0;
        //SpecialInteraction 每次生成游客时, 每个建筑按百分比概率判定一次
        Map<Integer, Integer> specialInteraction = cfg.getSpecialInteraction();
        if (specialInteraction != null && !specialInteraction.isEmpty()) {
            for (Map.Entry<Integer, Integer> en : specialInteraction.entrySet()) {
                if (en.getValue() == null || en.getValue() <= 0 || RandomUtils.randomInt(100) >= en.getValue()) {
                    continue;
                }
                DestinationInfo dest = pickBuildingDevice(en.getKey(), casino, recordReception);
                if (dest != null) {
                    //前台不产生奖励
//                    rewardService.grantReward(guest, dest);
                    dest.index = id;
                    id++;
                    result.add(dest);
                    rewardedCount--;
                    if (rewardedCount < 1) {
                        return result;
                    }
                }
            }
        }

        //InteractionWeight (Map<buildingId, weight>)
        Map<Integer, Integer> interactionWeight = cfg.getInteractionWeight();
        if (interactionWeight != null && !interactionWeight.isEmpty()) {
            WeightRandom<Integer> random = WeightRandom.create();
            for (Map.Entry<Integer, Integer> en : interactionWeight.entrySet()) {
                if (en.getValue() == null || en.getValue() <= 0) {
                    continue;
                }
                if (casino.findBuilding(en.getKey()) == null) {
                    continue;
                }
                random.add(en.getKey(), en.getValue());
            }
            for (int i = 0; i < rewardedCount; i++) {
                Integer buildingId = random.next();
                if (buildingId == null) {
                    continue;
                }
                DestinationInfo dest = pickBuildingDevice(buildingId, casino, recordReception);
                if (dest != null) {
                    if (generateRewards) {
                        rewardService.grantReward(guest, dest);
                    }
                    dest.index = id;
                    id++;
                    result.add(dest);
                }
            }
        }

        //TargetArea 顺序循环
        List<Integer> targetArea = cfg.getTargetArea();
        if (targetArea != null && !targetArea.isEmpty()) {
            int cursor = 0;
            int safety = rewardedCount * targetArea.size();
            int added = 0;
            while (added < rewardedCount && safety-- > 0) {
                int buildingId = targetArea.get(cursor % targetArea.size());
                cursor++;
                DestinationInfo dest = pickBuildingDevice(buildingId, casino, recordReception);
                if (dest != null) {
                    if (generateRewards) {
                        rewardService.grantReward(guest, dest);
                    }
                    dest.index = id;
                    id++;
                    result.add(dest);
                    added++;
                }
            }
        }
        return result;
    }

    /**
     * 在指定建筑随机挑一个交互设备 (建筑必须已解锁)
     */
    private DestinationInfo pickBuildingDevice(int buildingId, SimCasinoData casino, boolean recordReception) {
        BuildingData building = casino.findBuilding(buildingId);
        if (building == null) {
            return null;
        }

        BuildingUnlockEquipmentData data = configCache.getBuildingUnlockEquipmentDataByBuildId(buildingId);
        if (data == null) {
            return null;
        }

        int level = Math.min(building.getLevel(), data.getMaxLevel());
        List<Integer> list = data.getLevelUnlockEquipment(level);

        if (list == null || list.isEmpty()) {
            return null;
        }

        if (recordReception) {
            building.incrementReceptCount();
        }

        BuildingAreaTableCfg cfg = GameDataManager.getBuildingAreaTableCfg(buildingId);

        int deviceId = list.get(RandomUtils.randomInt(list.size()));

        DestinationInfo destinationInfo = new DestinationInfo();
        destinationInfo.buildingId = buildingId;
        destinationInfo.deviceId = deviceId;
        destinationInfo.interactTime = cfg.getInteractTime();
        return destinationInfo;
    }

    // ---------------------------------------------------------------------
    // 解锁
    // ---------------------------------------------------------------------

    /**
     * 添加游客道具：首次解锁，其余数量按招募配置转为碎片。
     */
    public boolean addGuestItem(SimPlayerContext ctx, int itemId, long count, AddType addType) {
        VisitorQuestCfg cfg = configCache.getVisitorQuestCfgByItemId(itemId);
        if (cfg == null || ctx.getCurrentCasino() == null) {
            log.warn("添加游客道具失败, 配置或当前场景不存在 playerId={},itemId={}", ctx.playerId(), itemId);
            return false;
        }
        boolean unlocked = ctx.getCurrentCasino().findGuestData(cfg.getId()) == null;
        long duplicateCount = count - (unlocked ? 1 : 0);
        if (duplicateCount > 0) {
            List<Integer> shards = cfg.getDuplicatetoShard();
            CommonResult<ItemOperationResult> result = playerPackService.addItems(ctx.playerId(),
                    Map.of(shards.get(1), duplicateCount * shards.get(2)), addType, "游客重复转碎片", true);
            if (!result.success()) {
                return false;
            }
        }
        if (unlocked) {
            if (unlockGuest(ctx, cfg.getId()) != Code.SUCCESS) {
                return false;
            }
            reportGuestCounts(ctx);
        }
        return true;
    }

    /**
     * 解锁游客
     */
    public int unlockGuest(SimPlayerContext ctx, int guestId) {
        SimCasinoData casino = ctx.getCurrentCasino();
        if (casino == null) {
            log.warn("解锁游客时，当前场景数据不存在 playerId={},currentCasinoId={}", ctx.playerId(), ctx.getSimBaseData().getCurrentCasinoId());
            return Code.NOT_FOUND;
        }
        boolean unlocked = casino.findGuestData(guestId) == null;
        int code = unlockGuest(casino, guestId);
        if (unlocked && code == Code.SUCCESS) {
            employeeRedDotService.updateRedDots(ctx.playerId(),
                    SimConstant.Employee.RED_DOT_GUEST_STAR_UP);
        }
        return code;
    }

    /**
     * 解锁游客
     */
    public int unlockGuest(SimCasinoData casino, int guestId) {
        GuestData guest = casino.findGuestData(guestId);
        if (guest == null) {
            VisitorQuestCfg cfg = GameDataManager.getVisitorQuestCfg(guestId);
            if (cfg == null) {
                log.warn("解锁游客时，未找到对应的配置 guestId={}", guestId);
                return Code.NOT_FOUND;
            }
            guest = new GuestData();
            guest.setId(guestId);
            guest.setLevel(1);
            guest.setStar(1);
            casino.addGuest(guest);
            unlockBonds(casino, Collections.singletonList(guestId));
        }
        log.info("解锁游客成功 guestId={}", guestId);
        return Code.SUCCESS;
    }

    /**
     * 获取玩家跨场景共享的当日游客产出统计。
     */
    public ResGuestOutputInfo guestOutputInfo(SimPlayerContext ctx) {
        SimCasinoData casino = ctx.getCurrentCasino();
        CasinoStatsSheetCfg cfg = configCache.getCasinoStatsSheetCfg(casino.getCasinoId(), casino.getCasinoLevel());
        if (cfg == null) {
            return new ResGuestOutputInfo(Code.NOT_FOUND);
        }
        SimBaseData base = ctx.getSimBaseData();
        ResGuestOutputInfo res = new ResGuestOutputInfo(Code.SUCCESS);
        res.allTime = (int) (base.accumulateOnlineTime(System.currentTimeMillis()).getFirst() / 1000);
        res.timeLimit = cfg.getVisitorDailyLimit() * 60;
        res.allRewards = base.getGuestOutputRewards() == null
                ? Collections.emptyList() : ItemUtils.buildItemInfo(base.getGuestOutputRewards());
        res.normalGuestCount = base.getNormalGuestCount();
        res.specialGuestCount = base.getSpecialGuestCount();
        return res;
    }

    /**
     * 获取所有游客
     *
     * @param ctx
     */
    public void onAllGuest(SimPlayerContext ctx) {
        ResAllGuest res = new ResAllGuest(Code.SUCCESS);
        try {
            SimCasinoData casinoData = ctx.getCurrentCasino();
            if (casinoData == null) {
                log.warn("获取所有游客失败,当前场景为空 playerId={}", ctx.playerId());
                res.code = Code.NOT_FOUND;
                ctx.send(res);
                return;
            }

            if (casinoData.getGuestMap() != null && !casinoData.getGuestMap().isEmpty()) {
                res.guests = new ArrayList<>();
                for (Map.Entry<Integer, GuestData> en : casinoData.getGuestMap().entrySet()) {
                    GuestData guestData = en.getValue();

                    GuestDetailInfo guestDetailInfo = new GuestDetailInfo();
                    guestDetailInfo.id = guestData.getId();
                    guestDetailInfo.level = guestData.getLevel();
                    guestDetailInfo.star = guestData.getStar();
                    guestDetailInfo.exp = guestData.getExp();
                    res.guests.add(guestDetailInfo);
                }
            }
        } catch (Exception e) {
            log.error("", e);
            res.code = Code.EXCEPTION;
        }
        ctx.send(res);
    }

    /**
     * 升星游客
     *
     * @param ctx
     * @param guestId
     */
    public void onStarUpGuest(SimPlayerContext ctx, int guestId) {
        ResStarUpGuest res = new ResStarUpGuest(Code.SUCCESS);
        try {
            SimCasinoData casinoData = ctx.getCurrentCasino();
            if (casinoData == null) {
                log.warn("升星游客失败,当前场景为空 playerId={}", ctx.playerId());
                res.code = Code.NOT_FOUND;
                ctx.send(res);
                return;
            }

            GuestData guestData = casinoData.findGuestData(guestId);
            if (guestData == null) {
                log.warn("升星游客失败,获取该游客数据失败 playerId={},guestId={}", ctx.playerId(), guestId);
                res.code = Code.NOT_FOUND;
                ctx.send(res);
                return;
            }

            VisitorStarCfg cfg = configCache.getVisitorStarCfgByGuest(guestId, guestData.getStar());
            if (cfg == null) {
                log.warn("升星游客失败,获取游客星级配置失败 playerId={},guestId={},star={}", ctx.playerId(), guestId, guestData.getStar());
                res.code = Code.NOT_FOUND;
                ctx.send(res);
                return;
            }

            int nextStar = guestData.getStar() + 1;
            VisitorStarCfg nextStarCfg = configCache.getVisitorStarCfgByGuest(guestId, nextStar);
            if (nextStarCfg == null) {
                log.warn("升星游客失败,星级已满 playerId={},guestId={},star={},nextStar={}", ctx.playerId(), guestId, guestData.getStar(), nextStar);
                res.code = Code.PARAM_ERROR;
                ctx.send(res);
                return;
            }

            VisitorQuestCfg visitorQuestCfg = GameDataManager.getVisitorQuestCfg(guestId);
            if (visitorQuestCfg == null) {
                log.warn("升星游客失败,获取游客配置失败 playerId={},guestId={}", ctx.playerId(), guestId);
                res.code = Code.NOT_FOUND;
                ctx.send(res);
                return;
            }

            CommonResult<ItemOperationResult> commonResult = playerPackService.removeItem(ctx.getPlayer(), visitorQuestCfg.getDuplicatetoShard().get(1), cfg.getAscend(), AddType.SIM_GUEST_STAR_UP);
            if (!commonResult.success()) {
                log.warn("升星游客失败,扣除道具失败 playerId={},guestId={},itemId={},count={},cfgId={},code={}", ctx.playerId(), guestId, visitorQuestCfg.getDuplicatetoShard().get(1), cfg.getAscend(), cfg.getId(), commonResult.code);
                res.code = commonResult.code;
                ctx.send(res);
                return;
            }

            //升星
            guestData.setStar(nextStar);

            res.guestId = guestData.getId();
            res.star = guestData.getStar();
            employeeRedDotService.updateRedDots(ctx.playerId(),
                    SimConstant.Employee.RED_DOT_GUEST_STAR_UP);
            //主线任务: 升星改变各星级持有量 -> 上报 12214 "拥有 N 个 X 星游客"
            reportGuestCounts(ctx);
            log.info("升星游客成功 playerId={},guestId={},star={}", ctx.playerId(), guestId, guestData.getStar());
        } catch (Exception e) {
            log.error("", e);
            res.code = Code.EXCEPTION;
        }
        ctx.send(res);
    }

    /**
     * 招募游客
     *
     * @param ctx
     * @param poolId 卡池id
     * @param count
     */
    public void onRecruitGuest(SimPlayerContext ctx, int poolId, int count) {
        ResRecruitGuest res = new ResRecruitGuest(Code.SUCCESS);
        try {
            if (count != 1 && count != 10) {
                log.warn("招募游客失败,次数参数错误 playerId={},count={}", ctx.playerId(), count);
                res.code = Code.PARAM_ERROR;
                ctx.send(res);
                return;
            }

            PoolListCfg poolCfg = configCache.getOpenPoolCfg(poolId, SimConstant.PoolList.TYPE_GUEST);
            if (poolCfg == null) {
                log.warn("招募游客失败,卡池不存在、未开启、不在开放时间或类型错误 playerId={},poolId={},count={}", ctx.playerId(), poolId, count);
                res.code = Code.PARAM_ERROR;
                ctx.send(res);
                return;
            }

            WeightRandom<List<Integer>> poolRand = configCache.getPoolRand(poolCfg.getDropItem());
            if (poolRand == null) {
                log.warn("招募游客失败,获取卡池权重失败 playerId={},count={},poolId={}", ctx.playerId(), count, poolCfg.getId());
                res.code = Code.PARAM_ERROR;
                ctx.send(res);
                return;
            }

            if (poolCfg.getDrawCost() != null && !poolCfg.getDrawCost().isEmpty()) {
                Map<Integer, Long> costMap = poolCfg.getDrawCost();
                if (count > 1) {
                    costMap = new HashMap<>();
                    for (Map.Entry<Integer, Long> en : poolCfg.getDrawCost().entrySet()) {
                        costMap.put(en.getKey(), en.getValue() * count);
                    }
                }
                boolean remove = playerPackService.removeItems(ctx.getPlayer(), costMap, AddType.SIM_GUEST_RECRUIT, null).success();
                if (!remove) {
                    log.warn("招募游客失败,扣除道具失败 playerId={},count={},poolId={}", ctx.playerId(), count, poolCfg.getId());
                    res.code = Code.PARAM_ERROR;
                    ctx.send(res);
                    return;
                }
            }

            VisitorPoolCfg visitorPoolCfg = GameDataManager.getVisitorPoolCfg(poolCfg.getDropItem());
            List<Integer> newbieGuideDraw = visitorPoolCfg == null ? null : visitorPoolCfg.getNewbieGuideDraw();
            int guideItemId = guideConfigService.newbieFixedDrawItemId(ctx.getSimBaseData(), newbieGuideDraw);

            Map<Integer, Long> addAllItems = new HashMap<>();
            List<RecruitItemInfo> recruitItems = new ArrayList<>();

            Map<Integer, Integer> addGuest = new HashMap<>();


            for (int i = 0; i < count; i++) {
                int drawItemId;
                int rewardCount;
                if (guideItemId > 0) {
                    drawItemId = guideItemId;
                    rewardCount = 1;
                } else {
                    List<Integer> next = poolRand.next();
                    if (next == null || next.size() < 3) {
                        log.warn("招募游客失败,获取配置失败2 playerId={},count={},i={}", ctx.playerId(), count, i);
                        return;
                    }
                    drawItemId = next.get(1);
                    rewardCount = next.get(2);
                }

                VisitorQuestCfg visitorQuestCfg = configCache.getVisitorQuestCfgByItemId(drawItemId);
                if (visitorQuestCfg == null) {
                    log.warn("招募游客失败,根据itemId获取VisitorQuestCfg失败 playerId={},count={},itemId={},i={}", ctx.playerId(), count, drawItemId, i);
                    return;
                }

                for (int j = 0; j < rewardCount; j++) {
                    RecruitItemInfo re = new RecruitItemInfo();
                    re.itemId = visitorQuestCfg.getDuplicatetoShard().get(0);
                    re.count = visitorQuestCfg.getDuplicatetoShard().get(2);

                    GuestData guestData = ctx.getCurrentCasino().findGuestData(visitorQuestCfg.getId());
                    if (guestData == null) {
                        guestData = new GuestData();
                        guestData.setId(visitorQuestCfg.getId());
                        guestData.setStar(1);
                        guestData.setLevel(1);
                        ctx.getCurrentCasino().addGuest(guestData);

                        addGuest.merge(visitorQuestCfg.getId(), 1, Integer::sum);
                    } else { //分解成碎片
                        List<Integer> tmpList = visitorQuestCfg.getDuplicatetoShard();
                        if (tmpList != null && !tmpList.isEmpty()) {
                            int itemId = tmpList.get(1);
                            long itemCount = tmpList.get(2).longValue();

                            addAllItems.merge(itemId, itemCount, Long::sum);
                            re.breakDown = true;
                        }
                    }
                    recruitItems.add(re);
                }
            }

            if (!addAllItems.isEmpty()) {
                CommonResult<ItemOperationResult> simItemOperationResultCommonResult = playerPackService.addItems(ctx.playerId(), addAllItems, AddType.SIM_GUEST_RECRUIT, count + "", false);
                if (!simItemOperationResultCommonResult.success()) {
                    log.warn("招募游客后添加碎片道具失败 playerId={},count={},code={}", ctx.playerId(), count, simItemOperationResultCommonResult.code);
                    res.code = simItemOperationResultCommonResult.code;
                    ctx.send(res);
                    return;
                }
            }

            if (!addGuest.isEmpty()) {
                List<Integer> guestIds = new ArrayList<>();
                for (Map.Entry<Integer, Integer> en : addGuest.entrySet()) {
                    guestIds.add(en.getKey());
                }
                unlockBonds(ctx.getCurrentCasino(), guestIds);
            }

            res.shardInfos = recruitItems;
            //联盟任务: 卡池抽奖次数 (param=卡池ID, 供 0=任意/指定卡池 过滤; 10 连计为 10 次)
            allianceEventService.onCardPoolDraw(ctx.playerId(), poolCfg.getId(), count);
            allianceEventService.onGuestPoolDraw(ctx.playerId(), count);
            allianceEventService.onGuestRecruit(ctx.playerId(),
                    poolCfg.getDrawCost() != null && !poolCfg.getDrawCost().isEmpty(), count);
            employeeRedDotService.updateRedDots(ctx.playerId(),
                    SimConstant.Employee.RED_DOT_RECRUIT_POOL,
                    SimConstant.Employee.RED_DOT_GUEST_STAR_UP);
            //主线任务: 招募改变各星级持有量 -> 上报 12214 "拥有 N 个 X 星游客"
            reportGuestCounts(ctx);
            log.info("招募游客成功 playerId={},count={},newEmployee={},addAllItems={}", ctx.playerId(), count, addGuest, addAllItems);
        } catch (Exception e) {
            log.error("", e);
            res.code = Code.EXCEPTION;
        }
        ctx.send(res);
    }

    /**
     * 上报当前场景各星级游客的持有量, 推进 12214 "拥有 N 个 X 星游客" (X 星按 ≥ 阈值判定)。
     * 内存仅驻留当前场景, 主线新手阶段玩家通常仅一座娱乐城, 故按当前场景统计。
     * 直接用 ctx 投递: 登录期补报时 ctx 尚未入 registry, 走 playerId 查找会被丢弃。
     */
    @Override
    public void reportTaskState(SimPlayerContext ctx, Consumer<ActionConditionEvent> sink) {
        reportGuestCounts(ctx, sink);
    }

    private void reportGuestCounts(SimPlayerContext ctx) {
        reportGuestCounts(ctx, e -> simTaskService.onConditionEvent(ctx, e));
    }

    private void reportGuestCounts(SimPlayerContext ctx, Consumer<ActionConditionEvent> sink) {
        SimCasinoData casino = ctx.getCurrentCasino();
        if (casino == null || casino.getGuestMap() == null || casino.getGuestMap().isEmpty()) {
            return;
        }
        SortedMap<Integer, Long> countByStar = new TreeMap<>();
        for (GuestData guest : casino.getGuestMap().values()) {
            countByStar.merge(guest.getStar(), 1L, Long::sum);
        }
        SimConditionEventFactory.emitOwnershipCounts(sink,
                ActionConditionEvent.Type.GUEST_COUNT, 0, countByStar);
    }

    /**
     * 获取游客羁绊
     *
     * @param ctx
     */
    public List<Integer> onBonds(SimPlayerContext ctx) {
        ResGuestBonds res = new ResGuestBonds(Code.SUCCESS);
        try {
            if (ctx.getCurrentCasino().getGuestBondsSet() != null && !ctx.getCurrentCasino().getGuestBondsSet().isEmpty()) {
                res.bonds = new ArrayList<>(ctx.getCurrentCasino().getGuestBondsSet());

                Map<Integer, Integer> map = new HashMap<>();
                for (int id : ctx.getCurrentCasino().getGuestBondsSet()) {
                    VisitorBondsCfg cfg = GameDataManager.getVisitorBondsCfg(id);
                    if (cfg == null) {
                        continue;
                    }
                    if (cfg.getStatBoost() > 0) {
                        map.merge(cfg.getLanguageID(), cfg.getStatBoost(), Integer::sum);
                    }
                }

                if (!map.isEmpty()) {
                    res.rewards = new ArrayList<>();
                    for (Map.Entry<Integer, Integer> en : map.entrySet()) {
                        res.rewards.add(new KVInfo(en.getKey(), en.getValue()));
                    }
                }
            }
        } catch (Exception e) {
            log.error("", e);
            res.code = Code.EXCEPTION;
        }
        ctx.send(res);
        // 只有成功返回给客户端的羁绊才可视为已经查看，供消息处理器清除对应未读红点。
        return res.code == Code.SUCCESS && res.bonds != null ? List.copyOf(res.bonds) : List.of();
    }

    /**
     * 将指定场景已解锁、且匹配当前 slots 游戏的游客羁绊加入进场技能快照。
     */
    public void addVisitorBondsSkillBonus(long skillOwnerId, SimCasinoData currentCasino,
                                          int casinoId, int gameType,
                                          SlotsSkillEffectData visitorBondsEffect) {
        if (visitorBondsEffect == null) {
            return;
        }
        SimCasinoData casino = casinoId <= 0 || (currentCasino != null && currentCasino.getCasinoId() == casinoId)
                ? currentCasino : simCasinoDao.findOne(skillOwnerId, casinoId);
        if (casino == null || casino.getGuestBondsSet() == null || casino.getGuestBondsSet().isEmpty()) {
            return;
        }
        for (int bondsId : casino.getGuestBondsSet()) {
            VisitorBondsCfg cfg = GameDataManager.getVisitorBondsCfg(bondsId);
            if (cfg == null || cfg.getGameType() != gameType) {
                continue;
            }
            visitorBondsEffect.addBonus(
                    cfg.getSpecialMode(), cfg.getWinRate(), cfg.getSpecialModeProbUp());
        }
    }

    /**
     * 解锁羁绊
     *
     * @param casino
     */
    private void unlockBonds(SimCasinoData casino, List<Integer> guestIds) {
        if (guestIds == null || guestIds.isEmpty()) {
            return;
        }

        Map<Integer, Long> addItems = new HashMap<>();

        for (int guestId : guestIds) {
            Set<Integer> bondsIds = configCache.getBondsByGuestId(guestId);
            if (bondsIds == null || bondsIds.isEmpty()) {
                continue;
            }

            bonds:
            for (int bondsCfgId : bondsIds) {
                //是否已解锁这个羁绊
                if (casino.containsGuestBonds(bondsCfgId)) {
                    continue;
                }

                //获取羁绊配置
                VisitorBondsCfg visitorBondsCfg = GameDataManager.getVisitorBondsCfg(bondsCfgId);
                if (visitorBondsCfg == null) {
                    continue;
                }

                if (visitorBondsCfg.getMembers() != null && !visitorBondsCfg.getMembers().isEmpty()) {
                    for (int memberGuestId : visitorBondsCfg.getMembers()) {
                        GuestData guestData = casino.findGuestData(memberGuestId);
                        if (guestData == null) {
                            continue bonds;
                        }
                    }
                }
                //添加羁绊
                casino.addGuestBonds(visitorBondsCfg.getId());
                employeeRedDotService.recordNewBond(casino, visitorBondsCfg.getId());
                log.info("成功解锁羁绊 playerId={},bondsId={}", casino.getPlayerId(), visitorBondsCfg.getId());
            }
        }

        if (!addItems.isEmpty()) {
            CommonResult<ItemOperationResult> addResult = playerPackService.addItems(casino.getPlayerId(), addItems, AddType.SIM_UNLOCK_BONDS, null, true);
            if (addResult.success()) {
                log.info("解锁羁绊添加道具成功 playerId={},addItems={}", casino.getPlayerId(), addItems);
            } else {
                log.warn("解锁羁绊添加道具失败 playerId={},addItems={},code={}", casino.getPlayerId(), addItems, addResult.code);
            }
        }
    }

    /**
     * 获取卡池
     *
     * @param ctx
     * @param poolId 卡池id
     */
    public void onPool(SimPlayerContext ctx, int poolId) {
        ResGuestPool res = new ResGuestPool(Code.SUCCESS);
        try {
            PoolListCfg poolCfg = configCache.getOpenPoolCfg(poolId, SimConstant.PoolList.TYPE_GUEST);
            if (poolCfg == null) {
                log.warn("获取游客卡池失败,卡池不存在、未开启、未到开放时间或类型错误 playerId={},poolId={}", ctx.playerId(), poolId);
                res.code = Code.PARAM_ERROR;
                ctx.send(res);
                return;
            }

            VisitorPoolCfg visitorPoolCfg = GameDataManager.getVisitorPoolCfg(poolCfg.getDropItem());
            if (visitorPoolCfg == null || visitorPoolCfg.getDetailedDropItem() == null) {
                log.warn("获取游客卡池失败,掉落配置不存在 playerId={},poolId={},dropItem={}", ctx.playerId(), poolId, poolCfg.getDropItem());
                res.code = Code.PARAM_ERROR;
                ctx.send(res);
                return;
            }

            res.guestIds = new ArrayList<>();
            for (List<Integer> list : visitorPoolCfg.getDetailedDropItem()) {
                res.guestIds.add(list.get(1));
            }
        } catch (Exception e) {
            log.error("", e);
            res.code = Code.EXCEPTION;
        }
        ctx.send(res);
    }

    /**
     * 获取玩家当前展示的广告、付费特殊游客列表；首次请求或跨时段时按两个卡池的配置分别生成。
     */
    public void specialGuests(SimPlayerContext ctx) {
        ResSpecialGuestList res = new ResSpecialGuestList(Code.SUCCESS);
        try {
            if (!ensureSpecialGuestOffers(ctx)) {
                log.warn("获取特殊游客列表失败，卡池配置缺失 playerId={}", ctx.playerId());
                res.code = Code.PARAM_ERROR;
            } else {
                fillSpecialGuestListResponse(ctx, res);
            }
        } catch (Exception e) {
            log.error("获取特殊游客列表异常 playerId={}", ctx.playerId(), e);
            res.code = Code.EXCEPTION;
        }
        ctx.send(res);
    }

    /**
     * 手动刷新付费特殊游客，广告游客不刷新。
     */
    public void refreshSpecialGuests(SimPlayerContext ctx) {
        ResSpecialGuestList res = new ResSpecialGuestList(Code.SUCCESS);
        try {
            SimCasinoData casino = ctx.getCurrentCasino();
            if (!ensureSpecialGuestOffers(ctx)) {
                log.warn("刷新特殊游客失败，卡池配置缺失 playerId={}", ctx.playerId());
                res.code = Code.PARAM_ERROR;
                ctx.send(res);
                return;
            }

            int refreshCount = casino.getSpecialGuestRefreshCount();
            if (!canManualRefreshSpecialGuests(refreshCount)) {
                log.warn("刷新特殊游客被拒绝 playerId={},refreshCount={},maxManualRefreshPerPeriod={}",
                        ctx.playerId(), refreshCount, GameDataManager.getGlobalConfigCfg(SimConstant.Global.SPECIAL_GUEST_MAX_MANUAL_REFRESH_PER_PERIOD).getIntValue());
                res.code = Code.REFRESH_MAX;
                ctx.send(res);
                return;
            }

            List<VisitorTargetListCfg> refreshCfgs = getSpecialGuestRefreshCfgs(casino);

            if (refreshCfgs.isEmpty()) {
                log.warn("特殊游客刷新没有找到可以刷新的卡池 playerId={},refreshCount={}", ctx.playerId(), refreshCount);
                res.code = Code.NOT_FOUND;
                ctx.send(res);
                return;
            }

            ItemInfo cost = getSpecialGuestRefreshCost(refreshCount);
            if (cost == null) {
                log.warn("特殊游客刷新费用配置错误 playerId={},refreshCount={}", ctx.playerId(), refreshCount);
                res.code = Code.PARAM_ERROR;
                ctx.send(res);
                return;
            }

            if (cost.count > 0) {
                CommonResult<ItemOperationResult> removeResult = playerPackService.removeItem(
                        ctx.getPlayer(), cost.itemId, cost.count, AddType.SIM_SPECIAL_GUEST_REFRESH);
                if (!removeResult.success()) {
                    log.warn("刷新特殊游客扣除费用失败 playerId={},itemId={},count={},code={}",
                            ctx.playerId(), cost.itemId, cost.count, removeResult.code);
                    res.code = removeResult.code;
                    ctx.send(res);
                    return;
                }
            }

            List<Integer> oldCfgIds = casino.getSpecialGuestPaidCfgIds() == null
                    ? Collections.emptyList() : new ArrayList<>(casino.getSpecialGuestPaidCfgIds());
            for (VisitorTargetListCfg cfg : refreshCfgs) {
                if (cfg.getPoolType() == SimConstant.SpecialGuest.POOL_PAID) {
                    casino.setSpecialGuestPaidCfgIds(selectPaidSpecialGuestCfgIds(cfg, new HashSet<>(oldCfgIds)));
                }
            }
            refreshQualitySpecialGuestOffers(casino, refreshCfgs);
            casino.getSpecialGuestPurchaseCounts().clear();
            casino.setSpecialGuestOfferVersion(casino.getSpecialGuestOfferVersion() + 1);
            casino.setSpecialGuestRefreshCount(refreshCount + 1);
            updateSpecialGuestRedDot(ctx.playerId(), SimConstant.SpecialGuest.RED_DOT_FREE_REFRESH);
            fillSpecialGuestListResponse(ctx, res);
            log.info("刷新特殊游客成功 playerId={},oldCfgIds={},newCfgIds={},refreshCount={},costItemId={},costCount={}",
                    ctx.playerId(), oldCfgIds, casino.getSpecialGuestPaidCfgIds(),
                    casino.getSpecialGuestRefreshCount(), cost.itemId, cost.count);
        } catch (Exception e) {
            log.error("刷新特殊游客列表异常 playerId={}", ctx.playerId(), e);
            res.code = Code.EXCEPTION;
        }
        ctx.send(res);
    }

    /**
     * 购买当前场景展示的特殊游客。
     *
     * <p>按当前展示池和 CostType 路由：广告和钻石购买成功后直接邀请并生成游客，
     * 现金类型创建订单并在到账后发放到下单场景。</p>
     */
    public void buySpecialGuest(SimPlayerContext ctx, int poolId, int cfgId, int costType, int payTypeValue) {
        log.warn("请求购买游客 poolId={}，cfgId={},costype={},paytype={}", poolId, cfgId, costType, payTypeValue);

        ResBuySpecialGuest res = new ResBuySpecialGuest(Code.SUCCESS);
        try {
            SimBaseData baseData = ctx.getSimBaseData();
            SimCasinoData casino = ctx.getCurrentCasino();
            if (!ensureSpecialGuestOffers(ctx)) {
                log.warn("购买特殊游客失败，卡池配置缺失 playerId={},cfgId={}", ctx.playerId(), cfgId);
                res.code = Code.PARAM_ERROR;
                ctx.send(res);
                return;
            }

            VisitorTargetListCfg poolCfg = GameDataManager.getVisitorTargetListCfg(poolId);
            if (poolCfg == null || !poolCfg.getIsEnabled() || poolCfg.getRegionID() != casino.getCasinoId()) {
                res.code = Code.PARAM_ERROR;
                ctx.send(res);
                return;
            }

            //生成一个订单id
            String orderId = costType == SimConstant.SpecialGuest.COST_CASH ? null : RandomUtils.getUUid();

            if (poolCfg.getPoolType() == SimConstant.SpecialGuest.POOL_AD
                    && costType == SimConstant.SpecialGuest.COST_AD) {
                if (baseData.getSpecialGuestAdCfgIds() != null
                        && baseData.getSpecialGuestAdCfgIds().contains(cfgId)) {
                    CommonResult<Integer> result = buySpecialGuestWithAd(ctx, baseData, orderId, poolId, cfgId);
                    res.code = result.code;
                    if (result.success()) {
                        res.orderId = orderId;
                        VisitorGenWatchVideoCfg cfg = GameDataManager.getVisitorGenWatchVideoCfg(result.data);
                        if (cfg != null) {
                            res.specialGuest = toSpecialGuestInfo(ctx, baseData, cfg,
                                    getFirstSpecialGuestPoolCfg(casino, SimConstant.SpecialGuest.POOL_AD));
                        }
                    }

                } else {
                    res.code = Code.PARAM_ERROR;
                    log.warn("购买特殊游客时该配置不支持看广告 playerId={},cfgId={},costType={},payTypeValue={}", ctx.playerId(), cfgId, costType, payTypeValue);
                }

                ctx.send(res);
                return;
            }

            if (poolCfg.getPoolType() == SimConstant.SpecialGuest.POOL_PAID) {
                VisitorGenPaidCfg cfg = GameDataManager.getVisitorGenPaidCfg(cfgId);
                if (!casino.getSpecialGuestPaidCfgIds().contains(cfgId)
                        || !validPaidSpecialGuestCfg(cfg) || cfg.getCostType() != costType) {
                    res.code = Code.PARAM_ERROR;
                } else {
                    buyPaidSpecialGuest(ctx, poolCfg, cfgId, costType, cfg.getPriceValue1(), cfg.getDailyLimitCount(),
                            payTypeValue, () -> invitePurchasedSpecialGuest(ctx, orderId, poolId, cfgId, cfg.getVisitorID(), cfg.getVisitorCount()), res);
                    res.specialGuest = toSpecialGuestInfo(ctx, cfg, poolCfg);
                }
            } else if (poolCfg.getPoolType() == SimConstant.SpecialGuest.POOL_QUALITY) {
                VisitorQualityAcquisitionCfg cfg = GameDataManager.getVisitorQualityAcquisitionCfg(cfgId);
                List<Integer> displayed = casino.getSpecialGuestQualityCfgIds().get(poolId);
                if (displayed == null || !displayed.contains(cfgId)
                        || !validQualitySpecialGuestCfg(cfg) || cfg.getCostType() != costType) {
                    res.code = Code.PARAM_ERROR;
                } else {
                    buyPaidSpecialGuest(ctx, poolCfg, cfgId, costType, cfg.getPriceValue1(), cfg.getDailyLimitCount(),
                            payTypeValue, () -> inviteQualitySpecialGuests(ctx, orderId, poolId, cfg), res);
                    res.specialGuest = toSpecialGuestInfo(ctx, cfg, poolCfg);
                }
            } else {
                res.code = Code.PARAM_ERROR;
            }
            if (res.code == Code.SUCCESS && costType == SimConstant.SpecialGuest.COST_DIAMOND) {
                res.orderId = orderId;
                res.specialGuest.orderId = orderId;
                if (poolCfg.getPoolType() == SimConstant.SpecialGuest.POOL_QUALITY) {
                    updateSpecialGuestButtonStatus(casino, res.specialGuest);
                }
            }
        } catch (ArithmeticException e) {
            log.error("购买特殊游客价格配置错误 playerId={},cfgId={}", ctx.playerId(), cfgId, e);
            res.code = Code.PARAM_ERROR;
        } catch (Exception e) {
            log.error("购买特殊游客异常 playerId={},cfgId={}", ctx.playerId(), cfgId, e);
            res.code = Code.EXCEPTION;
        }
        ctx.send(res);
    }

    private void buyPaidSpecialGuest(SimPlayerContext ctx, VisitorTargetListCfg poolCfg, int cfgId,
                                     int costType, int priceValue, int dailyLimit, int payType,
                                     IntSupplier invite, ResBuySpecialGuest res) {
        int dailyCount = specialGuestDailyCountService.getPaidCount(ctx.playerId(), poolCfg.getPoolType(), cfgId);
        if (dailyLimit > 0 && dailyCount >= dailyLimit) {
            res.code = Code.TODAY_CLIAM_LIMIT;
        } else if (costType == SimConstant.SpecialGuest.COST_DIAMOND) {
            buySpecialGuestWithDiamond(ctx, poolCfg, cfgId, priceValue, invite, res);
        } else if (costType == SimConstant.SpecialGuest.COST_CASH) {
            createSpecialGuestOrder(ctx, poolCfg, cfgId, priceValue, payType, res);
        } else {
            res.code = Code.PARAM_ERROR;
        }
    }

    private static String specialGuestPurchaseKey(int poolId, int cfgId) {
        return poolId + ":" + cfgId;
    }

    /**
     * 观看广告购买特殊游客。成功时保留当前商品，领取订单奖励后再替换。
     */
    private CommonResult<Integer> buySpecialGuestWithAd(SimPlayerContext ctx, SimBaseData baseData, String orderId, int poolId, int cfgId) {
        if (baseData.getSpecialGuestAdCfgIds() == null
                || !baseData.getSpecialGuestAdCfgIds().contains(cfgId)) {
            log.warn("观看特殊游客广告失败，配置不在当前展示列表 playerId={},cfgId={},currentCfgIds={}",
                    ctx.playerId(), cfgId, baseData.getSpecialGuestAdCfgIds());
            return new CommonResult<>(Code.PARAM_ERROR);
        }

        VisitorGenWatchVideoCfg cfg = GameDataManager.getVisitorGenWatchVideoCfg(cfgId);
        if (!validAdSpecialGuestCfg(cfg)) {
            log.warn("观看特殊游客广告失败，配置无效 playerId={},cfgId={}", ctx.playerId(), cfgId);
            return new CommonResult<>(Code.PARAM_ERROR);
        }
        if (ctx.getCurrentCasino().findBuySpecialGuestLogInfo(poolId, cfgId) != null) {
            log.warn("观看特殊游客广告失败，该商品订单奖励尚未领取 playerId={},poolId={},cfgId={}",
                    ctx.playerId(), poolId, cfgId);
            return new CommonResult<>(Code.REPEAT_OP);
        }
        long now = System.currentTimeMillis();
        if (now < baseData.getSpecialGuestAdCdEndTime()) {
            log.warn("观看特殊游客广告失败，冷却未结束 playerId={},cfgId={},now={},cdEndTime={}",
                    ctx.playerId(), cfgId, now, baseData.getSpecialGuestAdCdEndTime());
            return new CommonResult<>(Code.EXPIRE);
        }
        int dailyCount = specialGuestDailyCountService.getAdCount(ctx.playerId());
        VisitorTargetListCfg adPoolCfg = getFirstSpecialGuestPoolCfg(ctx.getCurrentCasino(), SimConstant.SpecialGuest.POOL_AD);
        if (adPoolCfg == null) {
            log.warn("观看特殊游客广告失败，未找到广告卡池配置 playerId={},cfgId={},now={},cdEndTime={}",
                    ctx.playerId(), cfgId, now, baseData.getSpecialGuestAdCdEndTime());
            return new CommonResult<>(Code.SAMPLE_ERROR);
        }
        if (adPoolCfg.getDailyViewLimit() > 0 && dailyCount >= adPoolCfg.getDailyViewLimit()) {
            log.warn("观看特殊游客广告失败，达到每日上限 playerId={},cfgId={},dailyCount={},dailyLimit={}",
                    ctx.playerId(), cfgId, dailyCount, adPoolCfg.getDailyViewLimit());
            return new CommonResult<>(Code.TODAY_CLIAM_LIMIT);
        }
        if (invitePurchasedSpecialGuest(ctx, orderId, poolId, cfgId, cfg.getVisitorID(), cfg.getVisitorCount()) != Code.SUCCESS) {
            log.warn("观看特殊游客广告邀请失败 playerId={},cfgId={},visitorItemId={},visitorCount={}",
                    ctx.playerId(), cfgId, cfg.getVisitorID(), cfg.getVisitorCount());
            return new CommonResult<>(Code.FAIL);
        }
        int newDailyCount = specialGuestDailyCountService.addAdCount(ctx.playerId());
        baseData.setSpecialGuestAdCdEndTime(now + (long) cfg.getViewCD() * TimeHelper.ONE_MINUTE_OF_MILLIS);
        updateSpecialGuestRedDot(ctx.playerId(), SimConstant.SpecialGuest.RED_DOT_AD_AVAILABLE);
        baseData.incWatchAdCount();
        allianceEventService.onAdWatch(ctx.playerId());
        log.info("观看特殊游客广告成功 playerId={},cfgId={},orderId={},visitorItemId={},visitorCount={},dailyBuyCount={},cdEndTime={}",
                ctx.playerId(), cfgId, orderId, cfg.getVisitorID(), cfg.getVisitorCount(), newDailyCount,
                baseData.getSpecialGuestAdCdEndTime());
        return new CommonResult<>(Code.SUCCESS, cfgId);
    }

    /**
     * 使用 PriceValue1 作为钻石价格；只有扣款和邀请生成均成功后才增加全局每日购买次数。
     */
    private void buySpecialGuestWithDiamond(SimPlayerContext ctx, VisitorTargetListCfg poolCfg, int cfgId,
                                            long price, IntSupplier invite, ResBuySpecialGuest res) {
        CommonResult<ItemOperationResult> removeResult = playerPackService.removeItem(
                ctx.getPlayer(), ItemUtils.getDiamondItemId(), price, AddType.SIM_SPECIAL_GUEST_BUY);
        if (!removeResult.success()) {
            log.warn("钻石购买特殊游客扣款失败 playerId={},cfgId={},price={},code={}",
                    ctx.playerId(), cfgId, price, removeResult.code);
            res.code = removeResult.code;
            return;
        }

        if (invite.getAsInt() != Code.SUCCESS) {
            log.warn("钻石购买特殊游客邀请失败 playerId={},poolId={},cfgId={}",
                    ctx.playerId(), poolCfg.getId(), cfgId);
            CommonResult<ItemOperationResult> refundResult = playerPackService.addItem(
                    ctx.playerId(), ItemUtils.getDiamondItemId(), price, AddType.FAIL_ROLLBACK);
            if (!refundResult.success()) {
                log.error("购买特殊游客邀请失败且钻石回滚失败 playerId={},cfgId={},code={}",
                        ctx.playerId(), cfgId, refundResult.code);
            }
            res.code = Code.FAIL;
            return;
        }
        int dailyBuyCount = specialGuestDailyCountService.addPaidCount(ctx.playerId(), poolCfg.getPoolType(), cfgId);
        ctx.getCurrentCasino().getSpecialGuestPurchaseCounts().merge(specialGuestPurchaseKey(poolCfg.getId(), cfgId), 1, Integer::sum);
        log.info("钻石购买特殊游客成功 playerId={},poolId={},cfgId={},price={},dailyBuyCount={}",
                ctx.playerId(), poolCfg.getId(), cfgId, price, dailyBuyCount);
    }

    /**
     * 额度只在下单时校验；订单保存场景和展示轮次，购买次数在到账成功后累计。
     */
    private void createSpecialGuestOrder(SimPlayerContext ctx, VisitorTargetListCfg poolCfg, int cfgId,
                                         int priceValue, int payTypeValue, ResBuySpecialGuest res) {
        PayType payType = PayType.valueOf(payTypeValue);
        if (payType == null) {
            log.warn("现金购买特殊游客下单失败，支付类型无效 playerId={},cfgId={},payType={}",
                    ctx.playerId(), cfgId, payTypeValue);
            res.code = Code.PARAM_ERROR;
            return;
        }
        BigDecimal price = GameDataManager.getShopRechargeListCfg(priceValue).getPrice();
        SimCasinoData casino = ctx.getCurrentCasino();
        Order order = orderService.generateOrder(ctx.getPlayer(), payType, String.valueOf(cfgId),
                price, RechargeType.BUY_GUEST,
                casino.getCasinoId() + ":" + casino.getSpecialGuestOfferVersion() + ":" + poolCfg.getId());
        if (order == null) {
            log.error("现金购买特殊游客创建订单失败 playerId={},cfgId={},payType={},price={}",
                    ctx.playerId(), cfgId, payType, price);
            res.code = Code.FAIL;
            return;
        }
        res.orderId = payType == PayType.IOS ? order.getUuid() : order.getId();
        log.info("现金购买特殊游客创建订单成功 playerId={},cfgId={},payType={},price={},orderId={}",
                ctx.playerId(), cfgId, payType, price, res.orderId);

        //如果有测试充值url直接调用
        try {
            if (StringUtils.isNotEmpty(nodeConfig.getTestRechargeUrl())) {
                HttpUtils.HttpResponse httpResponse = HttpUtils.doPostWithJSON(nodeConfig.getTestRechargeUrl() + order.getId(), "");
                if (!httpResponse.isOk()) {
                    log.debug("测试充值玩家预下单调用失败 playerId={}", ctx.playerId());
                }
            }
        } catch (Exception e) {
            log.error("", e);
        }

    }

    /**
     * 购买成功后直接执行邀请逻辑，不进入当前场景的特殊游客持有数量。
     */
    public int invitePurchasedSpecialGuest(SimPlayerContext ctx, String orderId, int poolId, int cfgId, int itemId, long count) {
        VisitorQuestCfg cfg = configCache.getVisitorQuestCfgByItemId(itemId);
        Map<Integer, Integer> invitedGuests = Map.of(cfg.getId(), (int) count);
        return generateInvitedGuests(ctx, invitedGuests, orderId, poolId, cfgId);
    }

    /**
     * 品质商品按购买数量独立抽取，合并同种游客后统一生成、统计和通知。
     */
    public int inviteQualitySpecialGuests(SimPlayerContext ctx, String orderId, int poolId, VisitorQualityAcquisitionCfg cfg) {
        WeightRandom<Integer> random = configCache.getVisitorQualityRandom(cfg.getId());
        if (cfg.getQuantityPerPurchase() <= 0 || random == null) {
            return Code.SAMPLE_ERROR;
        }
        Map<Integer, Integer> invitedGuests = new HashMap<>();
        for (int i = 0; i < cfg.getQuantityPerPurchase(); i++) {
            int itemId = random.next();
            VisitorQuestCfg visitorCfg = configCache.getVisitorQuestCfgByItemId(itemId);
            if (visitorCfg == null) {
                log.error("品质游客配置未关联游客 playerId={},cfgId={},itemId={}", ctx.playerId(), cfg.getId(), itemId);
                return Code.SAMPLE_ERROR;
            }
            invitedGuests.merge(visitorCfg.getId(), 1, Integer::sum);
        }
        return generateInvitedGuests(ctx, invitedGuests, orderId, poolId, cfg.getId());
    }

    /**
     * 现金订单到账后，将游客发放到下单时的场景。场景持久化订单 ID，保证充值重试不会重复发放。
     */
    public boolean receiveCashSpecialGuest(long playerId, int casinoId, int itemId, long count, String orderId) {
        if (casinoId <= 0 || itemId <= 0 || count <= 0 || orderId == null || orderId.isEmpty()) {
            log.warn("现金特殊游客到账参数错误 playerId={},casinoId={},itemId={},count={},orderId={}",
                    playerId, casinoId, itemId, count, orderId);
            return false;
        }
        SimPlayerContext ctx = simPlayerContextRegistry.getContext(playerId);
        SimCasinoData casino = ctx != null && ctx.getCurrentCasino() != null
                && ctx.getCurrentCasino().getCasinoId() == casinoId
                ? ctx.getCurrentCasino() : simCasinoDao.findOne(playerId, casinoId);
        if (casino == null) {
            log.warn("现金特殊游客到账失败，场景不存在 playerId={},casinoId={},orderId={}",
                    playerId, casinoId, orderId);
            return false;
        }
        try {
            if (!casino.receiveSpecialGuestOrder(orderId, itemId, count)) {
                return false;
            }
            simCasinoDao.save(casino);
            updateSpecialGuestRedDot(playerId, SimConstant.SpecialGuest.RED_DOT_INVITE_ITEM);
            log.info("现金特殊游客写入场景成功 playerId={},casinoId={},itemId={},count={},orderId={}",
                    playerId, casinoId, itemId, count, orderId);
            return true;
        } catch (Exception e) {
            log.error("现金特殊游客写入场景异常 playerId={},casinoId={},itemId={},count={},orderId={}",
                    playerId, casinoId, itemId, count, orderId, e);
            return false;
        }
    }

    /**
     * 获取当前场景持有、尚未邀请的特殊游客。
     */
    public void ownedSpecialGuests(SimPlayerContext ctx) {
        ResOwnedSpecialGuestList res = new ResOwnedSpecialGuestList(Code.SUCCESS);
        try {
            res.specialGuests = new ArrayList<>();
            SimCasinoData casino = ctx.getCurrentCasino();
            if (casino == null) {
                res.code = Code.PARAM_ERROR;
                ctx.send(res);
                return;
            }
            for (Map.Entry<Integer, Long> entry : casino.getSpecialGuestItemCounts().entrySet()) {
                int itemId = entry.getKey();
                long count = entry.getValue();
                if (count > 0) {
                    res.specialGuests.add(toItemInfo(itemId, count));
                }
            }
        } catch (Exception e) {
            log.error("获取已购买特殊游客异常 playerId={}", ctx.playerId(), e);
            res.code = Code.EXCEPTION;
        }
        ctx.send(res);
    }

    /**
     * 邀请并立即生成当前场景中所选特殊游客的全部持有数量。
     */
    public void inviteSpecialGuests(SimPlayerContext ctx, List<Integer> itemIds) {
        ResInviteSpecialGuest res = new ResInviteSpecialGuest(Code.SUCCESS);
        try {
            if (itemIds == null || itemIds.isEmpty() || ctx.getCurrentCasino() == null) {
                log.warn("邀请特殊游客失败，请求参数或当前场景无效 playerId={},itemIds={},hasCasino={}",
                        ctx.playerId(), itemIds, ctx.getCurrentCasino() != null);
                res.code = Code.PARAM_ERROR;
                ctx.send(res);
                return;
            }

            Set<Integer> itemSet = new HashSet<>(itemIds);

            SimCasinoData casino = ctx.getCurrentCasino();
            Map<Integer, Long> inviteItems = new LinkedHashMap<>();
            Map<Integer, Integer> invitedGuests = new LinkedHashMap<>();
            for (int itemId : itemSet) {
                ItemCfg itemCfg = GameDataManager.getItemCfg(itemId);
                long itemCount = casino.getSpecialGuestItemCounts().getOrDefault(itemId, 0L);
                if (itemCount < 1) {
                    continue;
                }

                if (!appendInvitedGuests(ctx.playerId(), itemId, itemCount, itemCfg, invitedGuests)) {
                    res.code = Code.PARAM_ERROR;
                    ctx.send(res);
                    return;
                }
                inviteItems.put(itemId, itemCount);
            }
            if (inviteItems.isEmpty()) {
                log.warn("邀请特殊游客失败，当前场景未持有所选游客 playerId={},casinoId={},itemIds={}",
                        ctx.playerId(), casino.getCasinoId(), itemSet);
                res.code = Code.NOT_ENOUGH_ITEM;
                ctx.send(res);
                return;
            }

            if (!casino.consumeSpecialGuests(inviteItems)) {
                log.warn("邀请特殊游客扣除持有数量失败 playerId={},casinoId={},items={}",
                        ctx.playerId(), casino.getCasinoId(), inviteItems);
                res.code = Code.NOT_ENOUGH_ITEM;
                ctx.send(res);
                return;
            }
            updateSpecialGuestRedDot(ctx.playerId(), SimConstant.SpecialGuest.RED_DOT_INVITE_ITEM);
            res.code = generateInvitedGuests(ctx, invitedGuests, null, 0, 0);
            if (res.code == Code.SUCCESS) {
                log.info("邀请特殊游客成功 playerId={},casinoId={},inviteItems={},invitedGuests={}",
                        ctx.playerId(), casino.getCasinoId(), inviteItems, invitedGuests);
            }
        } catch (Exception e) {
            log.error("邀请特殊游客异常 playerId={},itemIds={}", ctx.playerId(), itemIds, e);
            res.code = Code.EXCEPTION;
        }
        ctx.send(res);
    }

    private boolean appendInvitedGuests(long playerId, int itemId, long itemCount, ItemCfg itemCfg,
                                        Map<Integer, Integer> invitedGuests) {
        try {
            boolean appended = false;
            for (Map.Entry<Integer, Long> entry : itemCfg.getGetItem().entrySet()) {
                int guestItemId = entry.getKey();
                VisitorQuestCfg visitorCfg = configCache.getVisitorQuestCfgByItemId(guestItemId);
                if (visitorCfg == null) {
                    log.warn("邀请特殊游客失败，特殊游客未关联游客配置 playerId={},itemId={},guestItemId={}",
                            playerId, itemId, guestItemId);
                    return false;
                }
                int guestCount = Math.toIntExact(Math.multiplyExact(itemCount, entry.getValue()));
                if (guestCount > 0) {
                    invitedGuests.merge(visitorCfg.getId(), guestCount, Math::addExact);
                    appended = true;
                }
            }
            return appended;
        } catch (ArithmeticException e) {
            log.warn("邀请特殊游客失败，游客数量超限 playerId={},itemId={},itemCount={}",
                    playerId, itemId, itemCount);
            return false;
        }
    }

    private int generateInvitedGuests(SimPlayerContext ctx, Map<Integer, Integer> invitedGuests, String orderId, int poolId, int cfgId) {
        ResGenPurchasedGuest generateRes = new ResGenPurchasedGuest(Code.SUCCESS);
        for (Map.Entry<Integer, Integer> entry : invitedGuests.entrySet()) {
            if (!appendPurchasedGuests(ctx, entry.getKey(), entry.getValue(), orderId, poolId, cfgId, generateRes)) {
                break;
            }
        }
        if (generateRes.code == Code.SUCCESS) {
            simTaskService.onConditionEvent(ctx, new GuestInviteConditionEvent(invitedGuests.keySet()));
            allianceEventService.onGuestGenerated(ctx.playerId(), true, generateRes.guests.size());
        }
        ctx.send(generateRes);
        return generateRes.code;
    }

    /**
     * 到账只结算订单所属的展示轮次，已刷新的旧订单不占用新一轮购买额度。
     */
    public void recordCashSpecialGuestPurchase(SimPlayerContext ctx, Order order, int cfgId) {
        String[] orderContext = order.getDesc().split(":");
        int casinoId = Integer.parseInt(orderContext[0]);
        long offerVersion = Long.parseLong(orderContext[1]);
        int poolId = Integer.parseInt(orderContext[2]);
        SimCasinoData casino = ctx.getCurrentCasino();
        boolean currentCasino = casino != null && casino.getCasinoId() == casinoId;
        if (!currentCasino) {
            casino = simCasinoDao.findOne(ctx.playerId(), casinoId);
        }
        if (casino == null || casino.getSpecialGuestOfferVersion() != offerVersion
                || isSpecialGuestRefreshDue(casino.getSpecialGuestNextRefreshTime(), System.currentTimeMillis())) {
            return;
        }
        casino.getSpecialGuestPurchaseCounts().merge(specialGuestPurchaseKey(poolId, cfgId), 1, Integer::sum);
        if (currentCasino) {
            ctx.setLastSaveTime(0);
        } else {
            simCasinoDao.save(casino);
        }
    }

    /**
     * 付费列表、刷新和本轮购买次数按场景保存；广告列表、冷却及每日限购为玩家全局。
     */
    private boolean ensureSpecialGuestOffers(SimPlayerContext ctx) {
        return ensureSpecialGuestOffers(ctx, System.currentTimeMillis());
    }

    private boolean ensureSpecialGuestOffers(SimPlayerContext ctx, long now) {
        SimBaseData baseData = ctx.getSimBaseData();
        SimCasinoData casino = ctx.getCurrentCasino();
        if (baseData == null || casino == null) {
            return false;
        }

        VisitorTargetListCfg adPoolCfg = getFirstSpecialGuestPoolCfg(casino, SimConstant.SpecialGuest.POOL_AD);
        if (adPoolCfg != null) {
            int today = specialGuestDay(now);
            if (baseData.getSpecialGuestAdRefreshDay() != today) {
                baseData.setSpecialGuestAdRefreshDay(today);
                baseData.setSpecialGuestAdCdEndTime(0);
            }
            if (baseData.getSpecialGuestAdCfgIds() == null
                    || isSpecialGuestRefreshDue(baseData.getSpecialGuestAdNextRefreshTime(), now)) {
                Set<Integer> pendingCfgIds = new LinkedHashSet<>();
                if (casino.getSpecialGuestMap() != null) {
                    for (BuySpecialGuestLogInfo order : casino.getSpecialGuestMap().values()) {
                        if (order.getPoolId() == adPoolCfg.getId()) {
                            pendingCfgIds.add(order.getCfgId());
                        }
                    }
                }
                List<Integer> adCfgIds = new ArrayList<>(pendingCfgIds);
                List<VisitorGenWatchVideoCfg> candidates = GameDataManager.getVisitorGenWatchVideoCfgList();
                if (candidates != null && !pendingCfgIds.isEmpty()) {
                    candidates = candidates.stream().filter(cfg -> !pendingCfgIds.contains(cfg.getId())).toList();
                }
                adCfgIds.addAll(selectCfgIds(candidates, adPoolCfg.getDisplayCount() - adCfgIds.size(), Collections.emptySet()));
                baseData.setSpecialGuestAdCfgIds(adCfgIds);
                baseData.setSpecialGuestAdNextRefreshTime(nextSpecialGuestRefreshTime(adPoolCfg, now));
            }
        }

        VisitorTargetListCfg paidPoolCfg = getFirstSpecialGuestPoolCfg(casino, SimConstant.SpecialGuest.POOL_PAID);
        if (paidPoolCfg != null) {
            if (casino.getSpecialGuestPaidCfgIds() == null
                    || isSpecialGuestRefreshDue(casino.getSpecialGuestNextRefreshTime(), now)) {
                refreshPaidSpecialGuestOffers(casino, paidPoolCfg, Collections.emptySet());
                casino.setSpecialGuestRefreshCount(0);
                casino.setSpecialGuestNextRefreshTime(nextSpecialGuestRefreshTime(paidPoolCfg, now));
                log.info("特殊游客付费展示刷新 playerId={},casinoId={},nextRefreshTime={},paidCfgIds={}",
                        ctx.playerId(), casino.getCasinoId(), casino.getSpecialGuestNextRefreshTime(), casino.getSpecialGuestPaidCfgIds());
            }
        }

        VisitorTargetListCfg qualityPoolCfg = getFirstSpecialGuestPoolCfg(casino, SimConstant.SpecialGuest.POOL_QUALITY);
        if (qualityPoolCfg != null && (casino.needsSpecialGuestQualityInitialization()
                || paidPoolCfg == null && isSpecialGuestRefreshDue(casino.getSpecialGuestNextRefreshTime(), now))) {
            refreshQualitySpecialGuestOffers(casino);
            if (paidPoolCfg == null) {
                casino.getSpecialGuestPurchaseCounts().clear();
                casino.setSpecialGuestOfferVersion(casino.getSpecialGuestOfferVersion() + 1);
                casino.setSpecialGuestRefreshCount(0);
                casino.setSpecialGuestNextRefreshTime(nextSpecialGuestRefreshTime(qualityPoolCfg, now));
            }
        }

        return true;
    }

    private void refreshPaidSpecialGuestOffers(SimCasinoData casino, VisitorTargetListCfg cfg, Set<Integer> excludedIds) {
        List<Integer> paidCfgIds = selectPaidSpecialGuestCfgIds(cfg, excludedIds);
        refreshQualitySpecialGuestOffers(casino);
        casino.setSpecialGuestPaidCfgIds(paidCfgIds);
        casino.getSpecialGuestPurchaseCounts().clear();
        casino.setSpecialGuestOfferVersion(casino.getSpecialGuestOfferVersion() + 1);
    }

    private void refreshQualitySpecialGuestOffers(SimCasinoData casino) {
        List<VisitorTargetListCfg> qualityPools = getSpecialGuestPoolCfg(casino, SimConstant.SpecialGuest.POOL_QUALITY);
        refreshQualitySpecialGuestOffers(casino, qualityPools);
    }

    private void refreshQualitySpecialGuestOffers(SimCasinoData casino, List<VisitorTargetListCfg> qualityPools) {
        Map<Integer, List<Integer>> qualityOffers = new LinkedHashMap<>();
        if (qualityPools != null) {
            for (VisitorTargetListCfg qualityPool : qualityPools) {
                if (qualityPool.getPoolType() != SimConstant.SpecialGuest.POOL_QUALITY) {
                    continue;
                }
                if (RandomUtils.randomInt(100) >= qualityPool.getRate()) {
                    continue;
                }
                List<Integer> cfgIds = selectQualitySpecialGuestCfgIds(qualityPool);
                if (!cfgIds.isEmpty()) {
                    qualityOffers.put(qualityPool.getId(), cfgIds);
                }
            }
        }
        casino.setSpecialGuestQualityCfgIds(qualityOffers);
    }

    private List<Integer> selectQualitySpecialGuestCfgIds(VisitorTargetListCfg poolCfg) {
        if (poolCfg.getDisplayCount() <= 0 || poolCfg.getVisitorWeight() == null) {
            return Collections.emptyList();
        }
        Map<Integer, Integer> weights = new HashMap<>();
        for (List<Integer> entry : poolCfg.getVisitorWeight()) {
            int cfgId = entry.getFirst();
            if (entry.get(1) > 0 && validQualitySpecialGuestCfg(GameDataManager.getVisitorQualityAcquisitionCfg(cfgId))) {
                weights.merge(cfgId, entry.get(1), Math::addExact);
            }
        }
        return new ArrayList<>(RandomUtils.getRandomByWeight(weights, Math.min(poolCfg.getDisplayCount(), weights.size())));
    }

    private static int specialGuestDay(long now) {
        LocalDate date = TimeHelper.getLocalDateTime(now).toLocalDate();
        return date.getYear() * 10000 + date.getMonthValue() * 100 + date.getDayOfMonth();
    }

    private static boolean isSpecialGuestRefreshDue(long nextRefreshTime, long now) {
        return nextRefreshTime > 0 && now >= nextRefreshTime;
    }

    /**
     * 只在生成列表时计算下一个时点，离线跨过多个时段也只生成一次。
     */
    private long nextSpecialGuestRefreshTime(VisitorTargetListCfg cfg, long now) {
        if (!cfg.getIsRefreshByTimePeriod()) {
            return 0;
        }
        LocalDate today = TimeHelper.getLocalDateTime(now).toLocalDate();
        ZoneId zone = ZoneId.systemDefault();
        long next = Long.MAX_VALUE;
        for (int hour : configCache.getSpecialGuestDailyRefreshHours()) {
            long candidate = today.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli();
            if (candidate <= now) {
                candidate = today.plusDays(1).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli();
            }
            next = Math.min(next, candidate);
        }
        return next;
    }

    private boolean canManualRefreshSpecialGuests(int refreshCount) {
        int maxRefreshCount = GameDataManager.getGlobalConfigCfg(SimConstant.Global.SPECIAL_GUEST_MAX_MANUAL_REFRESH_PER_PERIOD).getIntValue();
        return maxRefreshCount <= 0 || refreshCount < maxRefreshCount;
    }

    private boolean isSpecialGuestPurchaseLimitReached(int purchaseCount) {
        int maxPurchaseCount = GameDataManager.getGlobalConfigCfg(SimConstant.Global.SPECIAL_GUEST_MAX_PURCHASE_PER_REFRESH).getIntValue();
        return maxPurchaseCount > 0 && purchaseCount >= maxPurchaseCount;
    }

    /**
     * 获取当前场景和卡池类型对应的 VisitorTargetList 配置。
     */
    private List<VisitorTargetListCfg> getSpecialGuestPoolCfg(SimCasinoData casino, int poolType) {
        if (casino == null) {
            return null;
        }
        return configCache.getVisitorTargetListCfg(casino.getCasinoId(), poolType);
    }

    /**
     * 广告、付费卡池使用配置列表中的第一条。
     */
    private VisitorTargetListCfg getFirstSpecialGuestPoolCfg(SimCasinoData casino, int poolType) {
        List<VisitorTargetListCfg> cfgs = getSpecialGuestPoolCfg(casino, poolType);
        return cfgs == null || cfgs.isEmpty() ? null : cfgs.getFirst();
    }

    /**
     * 按卡池展示数量选取付费游客，其中 VisitorGiftPackCount 指定需要预留的现金商品数量。
     */
    private List<Integer> selectPaidSpecialGuestCfgIds(VisitorTargetListCfg poolCfg, Set<Integer> excludedIds) {
        List<VisitorGenPaidCfg> diamondCostCfgs = new ArrayList<>();
        List<VisitorGenPaidCfg> cashCostCfgs = new ArrayList<>();
        for (VisitorGenPaidCfg cfg : GameDataManager.getVisitorGenPaidCfgList()) {
            if (cfg.getCostType() == SimConstant.SpecialGuest.COST_CASH) {
                cashCostCfgs.add(cfg);
            } else if (cfg.getCostType() == SimConstant.SpecialGuest.COST_DIAMOND) {
                diamondCostCfgs.add(cfg);
            }
        }

        int displayCount = Math.max(0, poolCfg.getDisplayCount());
        int moneyCount = Math.min(Math.max(0, poolCfg.getVisitorGiftPackCount()), displayCount);
        LinkedHashSet<Integer> result = new LinkedHashSet<>();
        result.addAll(selectCfgIds(diamondCostCfgs, displayCount - moneyCount, excludedIds));
        result.addAll(selectCfgIds(cashCostCfgs, moneyCount, excludedIds));

        if (result.size() < displayCount) {
            List<VisitorGenPaidCfg> allCfgs = new ArrayList<>(diamondCostCfgs.size() + cashCostCfgs.size());
            allCfgs.addAll(diamondCostCfgs);
            allCfgs.addAll(cashCostCfgs);
            Set<Integer> excluded = new HashSet<>(excludedIds);
            excluded.addAll(result);
            result.addAll(selectCfgIds(allCfgs, displayCount - result.size(), excluded));
        }
        return new ArrayList<>(result);
    }

    private <T extends BaseCfgBean> List<Integer> selectCfgIds(List<T> cfgs, int count, Set<Integer> excludedIds) {
        if (cfgs == null || cfgs.isEmpty() || count <= 0) {
            return new ArrayList<>();
        }
        List<T> preferred = new ArrayList<>();
        List<T> fallback = new ArrayList<>();
        for (T cfg : cfgs) {
            if (excludedIds.contains(cfg.getId())) {
                fallback.add(cfg);
            } else {
                preferred.add(cfg);
            }
        }
        Collections.shuffle(preferred);
        Collections.shuffle(fallback);
        preferred.addAll(fallback);

        List<Integer> result = new ArrayList<>(Math.min(count, preferred.size()));
        for (int i = 0; i < count && i < preferred.size(); i++) {
            result.add(preferred.get(i).getId());
        }
        return result;
    }

    /**
     * 合并当前展示商品与全部购买订单，刷新后仍保留每笔订单的入口。
     */
    private List<SpecialGuestInfo> buildSpecialGuestList(SimPlayerContext ctx, SimBaseData baseData) {
        List<SpecialGuestInfo> result = new ArrayList<>();
        SimCasinoData casino = ctx.getCurrentCasino();

        //广告卡池
        VisitorTargetListCfg adPoolCfg = getFirstSpecialGuestPoolCfg(casino, SimConstant.SpecialGuest.POOL_AD);
        if (adPoolCfg != null) {
            for (int cfgId : baseData.getSpecialGuestAdCfgIds()) {
                VisitorGenWatchVideoCfg cfg = GameDataManager.getVisitorGenWatchVideoCfg(cfgId);
                if (cfg != null) {
                    result.add(toSpecialGuestInfo(ctx, baseData, cfg, adPoolCfg));
                }
            }
        }

        //付费卡池
        VisitorTargetListCfg paidPoolCfg = getFirstSpecialGuestPoolCfg(casino, SimConstant.SpecialGuest.POOL_PAID);
        if (paidPoolCfg != null) {
            for (int cfgId : casino.getSpecialGuestPaidCfgIds()) {
                VisitorGenPaidCfg cfg = GameDataManager.getVisitorGenPaidCfg(cfgId);
                if (validPaidSpecialGuestCfg(cfg)) {
                    result.add(toSpecialGuestInfo(ctx, cfg, paidPoolCfg));
                }
            }
        }

        //品质卡池
        for (Map.Entry<Integer, List<Integer>> entry : casino.getSpecialGuestQualityCfgIds().entrySet()) {
            VisitorTargetListCfg poolCfg = GameDataManager.getVisitorTargetListCfg(entry.getKey());
            if (poolCfg == null || !poolCfg.getIsEnabled() || poolCfg.getRegionID() != casino.getCasinoId()
                    || poolCfg.getPoolType() != SimConstant.SpecialGuest.POOL_QUALITY) {
                continue;
            }
            for (int cfgId : entry.getValue()) {
                VisitorQualityAcquisitionCfg cfg = GameDataManager.getVisitorQualityAcquisitionCfg(cfgId);
                if (validQualitySpecialGuestCfg(cfg)) {
                    //本轮买过的商品由订单行展示；刷新后次数清零，新商品与旧订单分别展示。
                    if (casino.getSpecialGuestPurchaseCounts().getOrDefault(specialGuestPurchaseKey(poolCfg.getId(), cfgId), 0) > 0
                            && casino.findBuySpecialGuestLogInfo(poolCfg.getId(), cfgId) != null) {
                        continue;
                    }
                    result.add(toSpecialGuestInfo(ctx, cfg, poolCfg));
                }
            }
        }


        if (casino.getSpecialGuestMap() != null) {
            Set<String> displayedOrderIds = new HashSet<>();
            for (SpecialGuestInfo info : result) {
                if (info.orderId != null) {
                    displayedOrderIds.add(info.orderId);
                }
            }
            for (Map.Entry<String, BuySpecialGuestLogInfo> entry : casino.getSpecialGuestMap().entrySet()) {
                if (displayedOrderIds.contains(entry.getKey())) {
                    continue;
                }
                BuySpecialGuestLogInfo order = entry.getValue();
                VisitorTargetListCfg poolCfg = GameDataManager.getVisitorTargetListCfg(order.getPoolId());
                if (poolCfg == null || poolCfg.getRegionID() != casino.getCasinoId()) {
                    log.warn("购买游客订单展示失败，卡池配置不存在或场景不匹配 playerId={},orderId={},poolId={}",
                            ctx.playerId(), entry.getKey(), order.getPoolId());
                    continue;
                }
                SpecialGuestInfo info = null;
                if (poolCfg.getPoolType() == SimConstant.SpecialGuest.POOL_AD) {
                    VisitorGenWatchVideoCfg cfg = GameDataManager.getVisitorGenWatchVideoCfg(order.getCfgId());
                    if (cfg != null) {
                        info = toSpecialGuestInfo(ctx, baseData, cfg, poolCfg);
                    }
                } else if (poolCfg.getPoolType() == SimConstant.SpecialGuest.POOL_QUALITY) {
                    VisitorQualityAcquisitionCfg cfg = GameDataManager.getVisitorQualityAcquisitionCfg(order.getCfgId());
                    if (cfg != null) {
                        info = toSpecialGuestInfo(ctx, cfg, poolCfg);
                    }
                } else if (poolCfg.getPoolType() == SimConstant.SpecialGuest.POOL_PAID) {
                    VisitorGenPaidCfg cfg = GameDataManager.getVisitorGenPaidCfg(order.getCfgId());
                    if (cfg != null) {
                        info = toSpecialGuestInfo(ctx, cfg, poolCfg);
                    }
                }
                if (info == null) {
                    log.warn("购买游客订单展示失败，商品配置不存在或卡池类型无效 playerId={},orderId={},poolId={},cfgId={}",
                            ctx.playerId(), entry.getKey(), order.getPoolId(), order.getCfgId());
                    continue;
                }
                info.orderId = entry.getKey();
                if (poolCfg.getPoolType() != SimConstant.SpecialGuest.POOL_PAID) {
                    updateSpecialGuestButtonStatus(casino, info);
                }
                result.add(info);
            }
        }
        return result;
    }

    private SpecialGuestInfo toSpecialGuestInfo(SimPlayerContext ctx, SimBaseData baseData,
                                                VisitorGenWatchVideoCfg cfg, VisitorTargetListCfg poolCfg) {
        SpecialGuestInfo info = new SpecialGuestInfo();
        info.id = cfg.getId();
        info.itemId = cfg.getVisitorID();
        info.count = cfg.getVisitorCount();
        info.costType = SimConstant.SpecialGuest.COST_AD;
        info.price = "0";
        info.dailyBuyCount = specialGuestDailyCountService.getAdCount(ctx.playerId());
        info.dailyLimitCount = poolCfg.getDailyViewLimit();
        info.viewCd = cfg.getViewCD();
        info.viewCdEndTime = baseData.getSpecialGuestAdCdEndTime();
        info.output = ItemUtils.buildItemInfo(cfg.getRewardDisplay());
        info.visitorGiftPackCount = poolCfg.getVisitorGiftPackCount();
        info.poolId = poolCfg.getId();
        info.maxPurchasePerRefresh = GameDataManager.getGlobalConfigCfg(SimConstant.Global.SPECIAL_GUEST_MAX_PURCHASE_PER_REFRESH).getIntValue();

        updateSpecialGuestButtonStatus(ctx.getCurrentCasino(), info);
        return info;
    }

    private SpecialGuestInfo toSpecialGuestInfo(SimPlayerContext ctx, VisitorGenPaidCfg cfg, VisitorTargetListCfg poolCfg) {
        SpecialGuestInfo info = new SpecialGuestInfo();
        info.id = cfg.getId();
        info.itemId = cfg.getVisitorID();
        info.count = cfg.getVisitorCount();
        info.costType = cfg.getCostType();
        info.dailyBuyCount = specialGuestDailyCountService.getPaidCount(ctx.playerId(), poolCfg.getPoolType(), cfg.getId());
        info.dailyLimitCount = cfg.getDailyLimitCount();
        info.purchaseCount = ctx.getCurrentCasino().getSpecialGuestPurchaseCounts().getOrDefault(specialGuestPurchaseKey(poolCfg.getId(), cfg.getId()), 0);
        info.price = cfg.getCostType() == SimConstant.SpecialGuest.COST_CASH
                ? GameDataManager.getShopRechargeListCfg(cfg.getPriceValue1()).getPrice().toPlainString()
                : String.valueOf(cfg.getPriceValue1());
        info.output = getSpecialGuestOutput(ctx, info.itemId);
        info.visitorGiftPackCount = poolCfg.getVisitorGiftPackCount();
        info.poolId = poolCfg.getId();
        info.maxPurchasePerRefresh = GameDataManager.getGlobalConfigCfg(SimConstant.Global.SPECIAL_GUEST_MAX_PURCHASE_PER_REFRESH).getIntValue();

        updateSpecialGuestPurchaseButtonStatus(info);
        return info;
    }

    private SpecialGuestInfo toSpecialGuestInfo(SimPlayerContext ctx, VisitorQualityAcquisitionCfg cfg, VisitorTargetListCfg poolCfg) {
        SpecialGuestInfo info = new SpecialGuestInfo();
        info.id = cfg.getId();
        info.itemId = cfg.getId();
        info.count = cfg.getQuantityPerPurchase();
        info.costType = cfg.getCostType();
        info.dailyBuyCount = specialGuestDailyCountService.getPaidCount(ctx.playerId(), poolCfg.getPoolType(), cfg.getId());
        info.dailyLimitCount = cfg.getDailyLimitCount();
        info.purchaseCount = ctx.getCurrentCasino().getSpecialGuestPurchaseCounts().getOrDefault(specialGuestPurchaseKey(poolCfg.getId(), cfg.getId()), 0);
        info.price = cfg.getCostType() == SimConstant.SpecialGuest.COST_CASH
                ? GameDataManager.getShopRechargeListCfg(cfg.getPriceValue1()).getPrice().toPlainString()
                : String.valueOf(cfg.getPriceValue1());

        //品质奖池展示额外奖励
        info.output = ItemUtils.buildItemInfo(cfg.getRewardDisplay());
        info.visitorGiftPackCount = poolCfg.getVisitorGiftPackCount();
        info.poolId = poolCfg.getId();
        info.maxPurchasePerRefresh = GameDataManager.getGlobalConfigCfg(SimConstant.Global.SPECIAL_GUEST_MAX_PURCHASE_PER_REFRESH).getIntValue();

        updateSpecialGuestPurchaseButtonStatus(info);
        return info;
    }

    private void updateSpecialGuestPurchaseButtonStatus(SpecialGuestInfo info) {
        boolean dailyLimitReached = info.dailyLimitCount > 0 && info.dailyBuyCount >= info.dailyLimitCount;
        boolean refreshLimitReached = info.maxPurchasePerRefresh > 0 && info.purchaseCount >= info.maxPurchasePerRefresh;
        info.btnStatus = dailyLimitReached || refreshLimitReached ? 1 : 0;
    }

    /** 广告和品质卡池已购订单的等待、领奖状态优先于限购状态。 */
    private void updateSpecialGuestButtonStatus(SimCasinoData casino, SpecialGuestInfo info) {
        updateSpecialGuestPurchaseButtonStatus(info);
        BuySpecialGuestLogInfo logInfo;
        if (info.orderId == null || info.orderId.isEmpty()) {
            logInfo = casino.findBuySpecialGuestLogInfo(info.poolId, info.id);
            if (logInfo != null) {
                for (Map.Entry<String, BuySpecialGuestLogInfo> entry : casino.getSpecialGuestMap().entrySet()) {
                    if (entry.getValue() == logInfo) {
                        info.orderId = entry.getKey();
                        break;
                    }
                }
            }
        } else {
            logInfo = casino.getSpecialGuestMap() == null ? null : casino.getSpecialGuestMap().get(info.orderId);
        }
        if (logInfo == null) {
            return;
        }
        int completed = countCompletedSpecialGuests(casino, logInfo);
        info.guestCountKVInfo = new KVInfo(completed, logInfo.getGuestIds().size());
        info.btnStatus = completed == info.guestCountKVInfo.value
                || System.currentTimeMillis() - logInfo.getCreateTime() > 10 * TimeHelper.ONE_MINUTE_OF_MILLIS ? 3 : 2;
    }

    private boolean canClaimSpecialGuestRewards(SimCasinoData casino, BuySpecialGuestLogInfo logInfo, long now) {
        if (now - logInfo.getCreateTime() > 10 * TimeHelper.ONE_MINUTE_OF_MILLIS) {
            return true;
        }
        return countCompletedSpecialGuests(casino, logInfo) == logInfo.getGuestIds().size();
    }

    private int countCompletedSpecialGuests(SimCasinoData casino, BuySpecialGuestLogInfo logInfo) {
        int completed = 0;
        for (String uid : logInfo.getGuestIds()) {
            PurchasedGuestData guest = casino.findPurchasedGuest(uid);
            //游客全部目的地完成后会移除，找不到即表示该游客行程已结束。
            if (guest == null || guest.getDestinations() == null) {
                completed++;
                continue;
            }
            boolean allClaimed = true;
            for (DestinationInfo destination : guest.getDestinations().values()) {
                if (!destination.claimed) {
                    allClaimed = false;
                    break;
                }
            }
            if (allClaimed) {
                completed++;
            }
        }
        return completed;
    }

    /** 游客行程全部完成或订单超过十分钟后可领广告、品质商品奖励，入账成功后删除订单记录。 */
    public void claimSpecialGuestRewards(SimPlayerContext ctx, String orderId) {
        ResSpecialGuestRewards res = new ResSpecialGuestRewards(Code.SUCCESS);
        try {
            SimCasinoData casino = ctx.getCurrentCasino();
            if (orderId == null || orderId.isEmpty() || casino == null) {
                res.code = Code.PARAM_ERROR;
                log.warn("领取特殊游客订单奖励失败，订单参数或当前场景无效 playerId={},orderId={},hasCasino={}",
                        ctx.playerId(), orderId, casino != null);
                return;
            }
            BuySpecialGuestLogInfo logInfo = casino.getSpecialGuestMap() == null
                    ? null : casino.getSpecialGuestMap().get(orderId);
            if (logInfo == null) {
                res.code = Code.NOT_FOUND;
                log.warn("领取特殊游客订单奖励失败，订单记录不存在 playerId={},casinoId={},orderId={}",
                        ctx.playerId(), casino.getCasinoId(), orderId);
                return;
            }
            VisitorTargetListCfg poolCfg = GameDataManager.getVisitorTargetListCfg(logInfo.getPoolId());
            if (poolCfg == null || (poolCfg.getPoolType() != SimConstant.SpecialGuest.POOL_QUALITY
                    && poolCfg.getPoolType() != SimConstant.SpecialGuest.POOL_AD)
                    || poolCfg.getRegionID() != casino.getCasinoId()) {
                res.code = Code.PARAM_ERROR;
                log.warn("领取特殊游客订单奖励失败，卡池配置不存在、类型或场景不匹配 playerId={},casinoId={},orderId={},poolId={},poolType={},regionId={}",
                        ctx.playerId(), casino.getCasinoId(), orderId, logInfo.getPoolId(),
                        poolCfg == null ? null : poolCfg.getPoolType(), poolCfg == null ? null : poolCfg.getRegionID());
                return;
            }
            Map<Integer, Long> rewards;
            if (poolCfg.getPoolType() == SimConstant.SpecialGuest.POOL_AD) {
                VisitorGenWatchVideoCfg cfg = GameDataManager.getVisitorGenWatchVideoCfg(logInfo.getCfgId());
                if (cfg == null) {
                    res.code = Code.SAMPLE_ERROR;
                    log.warn("领取特殊游客订单奖励失败，广告商品配置不存在 playerId={},orderId={},poolId={},cfgId={}",
                            ctx.playerId(), orderId, logInfo.getPoolId(), logInfo.getCfgId());
                    return;
                }
                rewards = cfg.getRewardDisplay();
            } else {
                VisitorQualityAcquisitionCfg cfg = GameDataManager.getVisitorQualityAcquisitionCfg(logInfo.getCfgId());
                if (cfg == null) {
                    res.code = Code.SAMPLE_ERROR;
                    log.warn("领取特殊游客订单奖励失败，品质商品配置不存在 playerId={},orderId={},poolId={},cfgId={}",
                            ctx.playerId(), orderId, logInfo.getPoolId(), logInfo.getCfgId());
                    return;
                }
                rewards = cfg.getRewardDisplay();
            }
            if (!canClaimSpecialGuestRewards(casino, logInfo, System.currentTimeMillis())) {
                res.code = Code.FAIL;
                log.warn("领取特殊游客订单奖励失败，游客行程尚未全部完成且订单未超过十分钟 playerId={},casinoId={},orderId={},poolId={},cfgId={}",
                        ctx.playerId(), casino.getCasinoId(), orderId, logInfo.getPoolId(), logInfo.getCfgId());
                return;
            }
            if (rewards != null && !rewards.isEmpty()) {
                CommonResult<ItemOperationResult> result = playerPackService.addItems(
                        ctx.playerId(), rewards, AddType.SIM_GUEST_REWARDS, null, false);
                if (result == null || !result.success()) {
                    res.code = result == null ? Code.FAIL : result.code;
                    log.warn("领取特殊游客订单奖励失败，道具入账失败 playerId={},orderId={},poolId={},cfgId={},code={},resultNull={}",
                            ctx.playerId(), orderId, logInfo.getPoolId(), logInfo.getCfgId(), res.code, result == null);
                    return;
                }
            }
            casino.getSpecialGuestMap().remove(orderId);
            if (casino.getSpecialGuestMap().isEmpty()) {
                casino.setSpecialGuestMap(null);
            }
            ctx.setLastSaveTime(0);
            if (poolCfg.getPoolType() == SimConstant.SpecialGuest.POOL_AD) {
                replaceSpecialGuestAdOffer(ctx.getSimBaseData(), logInfo.getCfgId());
            }
            res.items = rewards == null || rewards.isEmpty() ? Collections.emptyList() : ItemUtils.buildItemInfo(rewards);
        } catch (Exception e) {
            log.error("领取特殊游客订单奖励异常 playerId={},orderId={}", ctx.playerId(), orderId, e);
            res.code = Code.EXCEPTION;
        } finally {
            ctx.send(res);
        }
    }

    /**
     * 获取游客卡对应游客的等级产出；当前场景尚未解锁该游客时按 1 级展示。
     */
    private List<ItemInfo> getSpecialGuestOutput(SimPlayerContext ctx, int visitorItemId) {
        VisitorQuestCfg visitorCfg = configCache.getVisitorQuestCfgByItemId(visitorItemId);
        if (visitorCfg == null) {
            log.warn("获取特殊游客产出失败，游客卡未关联游客配置 playerId={},visitorItemId={}",
                    ctx.playerId(), visitorItemId);
            return Collections.emptyList();
        }

        GuestData guest = ctx.getCurrentCasino() == null
                ? null : ctx.getCurrentCasino().findGuestData(visitorCfg.getId());
        int level = guest == null ? 1 : guest.getLevel();
        List<ItemInfo> output = rewardService.getOutputPreview(visitorCfg.getId(), level);
        if (output.isEmpty()) {
            log.warn("获取特殊游客产出失败，等级产出配置为空 playerId={},guestId={},level={}",
                    ctx.playerId(), visitorCfg.getId(), level);
        }
        return output;
    }

    private void fillSpecialGuestListResponse(SimPlayerContext ctx, ResSpecialGuestList res) {
        SimBaseData baseData = ctx.getSimBaseData();
        res.specialGuestList = buildSpecialGuestList(ctx, baseData);
        res.refreshCount = ctx.getCurrentCasino().getSpecialGuestRefreshCount();
        res.nextRefreshCost = nextSpecialGuestRefreshCost(ctx.getCurrentCasino());
        res.nextRefreshTime = ctx.getCurrentCasino().getSpecialGuestNextRefreshTime();
        res.adNextRefreshTime = baseData.getSpecialGuestAdNextRefreshTime();

        res.maxManualRefreshPerPeriod = GameDataManager.getGlobalConfigCfg(SimConstant.Global.SPECIAL_GUEST_MAX_MANUAL_REFRESH_PER_PERIOD).getIntValue();
    }

    private boolean validPaidSpecialGuestCfg(VisitorGenPaidCfg cfg) {
        return cfg != null && cfg.getVisitorID() > 0 && cfg.getVisitorCount() > 0
                && validSpecialGuestPrice(cfg.getCostType(), cfg.getPriceValue1());
    }

    private boolean validQualitySpecialGuestCfg(VisitorQualityAcquisitionCfg cfg) {
        if (cfg == null || cfg.getQuantityPerPurchase() <= 0 || configCache.getVisitorQualityRandom(cfg.getId()) == null
                || !validSpecialGuestPrice(cfg.getCostType(), cfg.getPriceValue1())) {
            return false;
        }
        for (Map.Entry<Integer, Long> entry : cfg.getVisitorWeight().entrySet()) {
            if (entry.getValue() > 0 && configCache.getVisitorQuestCfgByItemId(entry.getKey()) == null) {
                return false;
            }
        }
        return true;
    }

    private boolean validSpecialGuestPrice(int costType, int priceValue) {
        if (priceValue <= 0) {
            return false;
        }
        if (costType == SimConstant.SpecialGuest.COST_CASH) {
            ShopRechargeListCfg shopCfg = GameDataManager.getShopRechargeListCfg(priceValue);
            return shopCfg != null && shopCfg.getPrice() != null && shopCfg.getPrice().signum() > 0;
        }
        return costType == SimConstant.SpecialGuest.COST_DIAMOND;
    }

    /**
     * 广告领取后只替换被观看的槽位，其他广告游客保持不变。
     *
     * @return 替换后的配置 ID；没有可替换配置时返回原配置 ID
     */
    private int replaceSpecialGuestAdOffer(SimBaseData baseData, int cfgId) {
        List<Integer> cfgIds = new ArrayList<>(baseData.getSpecialGuestAdCfgIds());
        int index = cfgIds.indexOf(cfgId);
        if (index < 0) {
            return cfgId;
        }
        List<Integer> next = selectCfgIds(GameDataManager.getVisitorGenWatchVideoCfgList(),
                1, new HashSet<>(cfgIds));
        if (!next.isEmpty()) {
            cfgIds.set(index, next.getFirst());
            baseData.setSpecialGuestAdCfgIds(cfgIds);
            return next.getFirst();
        }
        return cfgId;
    }

    private ItemInfo nextSpecialGuestRefreshCost(SimCasinoData casino) {
        if (getSpecialGuestRefreshCfgs(casino).isEmpty()) {
            return null;
        }
        return getSpecialGuestRefreshCost(casino.getSpecialGuestRefreshCount());
    }

    private List<VisitorTargetListCfg> getSpecialGuestRefreshCfgs(SimCasinoData casino) {
        List<VisitorTargetListCfg> refreshCfgs = new ArrayList<>();
        VisitorTargetListCfg paidPoolCfg = getFirstSpecialGuestPoolCfg(casino, SimConstant.SpecialGuest.POOL_PAID);
        if (paidPoolCfg != null) {
            refreshCfgs.add(paidPoolCfg);
        }
        List<VisitorTargetListCfg> qualityPools = getSpecialGuestPoolCfg(casino, SimConstant.SpecialGuest.POOL_QUALITY);
        if (qualityPools != null) {
            refreshCfgs.addAll(qualityPools);
        }
        return refreshCfgs;
    }

    /**
     * 刷新次数超过费用档位数量时，使用最后一个已配置档位。
     */
    private ItemInfo getSpecialGuestRefreshCost(int refreshCount) {
        List<Item> costs = configCache.getSpecialGuestRefreshCosts();
        if (costs.isEmpty()) {
            return null;
        }
        Item cost = costs.get(Math.min(refreshCount, costs.size() - 1));
        return toItemInfo(cost.getId(), cost.getItemCount());
    }

    private ItemInfo toItemInfo(int itemId, long count) {
        ItemInfo info = new ItemInfo();
        info.itemId = itemId;
        info.count = count;
        return info;
    }

    private void refreshTimedSpecialGuestRedDots(SimPlayerContext ctx, long now) {
        SimBaseData baseData = ctx.getSimBaseData();
        SimCasinoData casino = ctx.getCurrentCasino();
        if (baseData == null || casino == null) {
            return;
        }
        int today = specialGuestDay(now);
        if (baseData.getSpecialGuestAdRefreshDay() != today
                || baseData.getSpecialGuestAdCfgIds() == null || casino.getSpecialGuestPaidCfgIds() == null
                || casino.needsSpecialGuestQualityInitialization()
                || isSpecialGuestRefreshDue(baseData.getSpecialGuestAdNextRefreshTime(), now)
                || isSpecialGuestRefreshDue(casino.getSpecialGuestNextRefreshTime(), now)) {
            if (ensureSpecialGuestOffers(ctx, now)) {
                redDotManager.updateRedDotByInitialize(getModule(), RED_DOT_SUBMODULES, ctx.playerId());
            }
            return;
        }
        if (baseData.getSpecialGuestAdCdEndTime() > 0
                && now >= baseData.getSpecialGuestAdCdEndTime()) {
            baseData.setSpecialGuestAdCdEndTime(0);
            updateSpecialGuestRedDot(ctx.playerId(), SimConstant.SpecialGuest.RED_DOT_AD_AVAILABLE);
        }
    }

    private void updateSpecialGuestRedDot(long playerId, int submodule) {
        redDotManager.updateRedDotByInitialize(getModule(), submodule, playerId);
    }

    public void updateSpecialGuestRedDots(long playerId) {
        redDotManager.updateRedDotByInitialize(getModule(), RED_DOT_SUBMODULES, playerId);
    }

    @Override
    public RedDotDetails.RedDotModule getModule() {
        return RedDotDetails.RedDotModule.SPECIAL_GUEST;
    }

    @Override
    public List<Integer> getSubmodules() {
        return RED_DOT_SUBMODULES;
    }

    @Override
    public boolean markRead(long playerId, int submodule, List<Integer> entityIds) {
        if (submodule != SimConstant.SpecialGuest.RED_DOT_DAILY_ENTRY) {
            return false;
        }
        redDotReadDao.viewToday(playerId, RED_DOT_DAILY_ENTRY_SCOPE);
        return true;
    }

    @Override
    public List<RedDotDetails> initialize(long playerId, int submodule) {
        SimPlayerContext ctx = simPlayerContextRegistry.getContext(playerId);
        SimBaseData baseData;
        SimCasinoData casino;
        if (ctx != null) {
            baseData = ctx.getSimBaseData();
            casino = ctx.getCurrentCasino();
        } else {
            baseData = simPlayerGameDao.findSpecialGuestRedDotData(playerId);
            casino = baseData == null ? null
                    : simCasinoDao.findSpecialGuestRedDotData(playerId, baseData.getCurrentCasinoId());
        }

        List<Integer> submodules = submodule == 0 ? RED_DOT_SUBMODULES : List.of(submodule);
        List<RedDotDetails> details = new ArrayList<>(submodules.size());
        long now = System.currentTimeMillis();
        int today = TimeHelper.getDayNumerical();
        for (int currentSubmodule : submodules) {
            if (!RED_DOT_SUBMODULES.contains(currentSubmodule)) {
                continue;
            }
            details.add(redDotManager.buildRedDotDetails(getModule(), currentSubmodule,
                    getSpecialGuestRedDotCount(playerId, baseData, casino, currentSubmodule, today, now),
                    currentSubmodule == SimConstant.SpecialGuest.RED_DOT_INVITE_ITEM
                            || currentSubmodule == SimConstant.SpecialGuest.RED_DOT_DAILY_ENTRY
                            ? RedDotDetails.RedDotType.COUNT : RedDotDetails.RedDotType.COMMON));
        }
        return details;
    }

    private long getSpecialGuestRedDotCount(long playerId, SimBaseData baseData, SimCasinoData casino,
                                            int submodule, int today, long now) {
        return switch (submodule) {
            case SimConstant.SpecialGuest.RED_DOT_FREE_REFRESH -> hasFreeSpecialGuestRefresh(casino, now) ? 1 : 0;
            case SimConstant.SpecialGuest.RED_DOT_AD_AVAILABLE ->
                    hasAvailableSpecialGuestAd(playerId, baseData, casino, today, now) ? 1 : 0;
            case SimConstant.SpecialGuest.RED_DOT_INVITE_ITEM -> casino == null ? 0
                    : casino.getSpecialGuestItemCounts().values().stream().filter(count -> count != null && count > 0).mapToLong(Long::longValue).sum();
            case SimConstant.SpecialGuest.RED_DOT_DAILY_ENTRY -> baseData != null && casino != null
                    && !redDotReadDao.viewedToday(playerId, RED_DOT_DAILY_ENTRY_SCOPE) ? 1 : 0;
            default -> 0;
        };
    }

    private boolean hasFreeSpecialGuestRefresh(SimCasinoData casino, long now) {
        if (getSpecialGuestRefreshCfgs(casino).isEmpty()) {
            return false;
        }
        int refreshCount = casino != null && !isSpecialGuestRefreshDue(casino.getSpecialGuestNextRefreshTime(), now)
                ? casino.getSpecialGuestRefreshCount() : 0;
        if (!canManualRefreshSpecialGuests(refreshCount)) {
            return false;
        }
        ItemInfo cost = getSpecialGuestRefreshCost(refreshCount);
        return cost != null && cost.count == 0;
    }

    private boolean hasAvailableSpecialGuestAd(long playerId, SimBaseData baseData, SimCasinoData casino,
                                               int today, long now) {
        if (baseData == null) {
            return false;
        }
        VisitorTargetListCfg poolCfg = getFirstSpecialGuestPoolCfg(casino, SimConstant.SpecialGuest.POOL_AD);
        if (poolCfg == null || poolCfg.getDisplayCount() <= 0
                || poolCfg.getDailyViewLimit() > 0
                && specialGuestDailyCountService.getAdCount(playerId) >= poolCfg.getDailyViewLimit()) {
            return false;
        }
        long cdEndTime = baseData.getSpecialGuestAdRefreshDay() == today
                ? baseData.getSpecialGuestAdCdEndTime() : 0;
        if (now < cdEndTime) {
            return false;
        }

        List<Integer> cfgIds = baseData.getSpecialGuestAdCfgIds();
        boolean refreshOffers = cfgIds == null
                || isSpecialGuestRefreshDue(baseData.getSpecialGuestAdNextRefreshTime(), now);
        if (refreshOffers) {
            return GameDataManager.getVisitorGenWatchVideoCfgList().stream()
                    .anyMatch(this::validAdSpecialGuestCfg);
        }
        return cfgIds.stream()
                .map(GameDataManager::getVisitorGenWatchVideoCfg)
                .anyMatch(this::validAdSpecialGuestCfg);
    }

    private boolean validAdSpecialGuestCfg(VisitorGenWatchVideoCfg cfg) {
        return cfg != null && cfg.getVisitorID() > 0 && cfg.getVisitorCount() > 0;
    }

    private boolean hasSpecialGuestInviteItem(SimCasinoData casino) {
        return casino != null && casino.getSpecialGuestItemCounts().values().stream().anyMatch(count -> count > 0);
    }

    @Override
    public void useItem(Player player, int itemId, long useItemCount, int selectItemId, long finalSelectItemCount, Map<Integer, Long> getItemsMap) {
        try {
            if (selectItemId > 0) {
                VisitorQuestCfg cfg = null;
                if (itemId == SimConstant.Item.ID_BATCH_GENERATE_SPECIFY_ID_GUEST) {
                    cfg = configCache.getVisitorQuestCfgByItemId(selectItemId);
                } else if (itemId == SimConstant.Item.ID_BATCH_GENERATE_SPECIFY_QUALITY_GUEST) {
                    Integer qulity = configCache.queryGuestQuality(selectItemId);
                    if (qulity != null) {
                        List<VisitorQuestCfg> tmpList = configCache.getVisitorQuestCfgList(qulity);
                        if (tmpList == null || tmpList.isEmpty()) {
                            return;
                        }
                        cfg = RandomUtils.randomEle(tmpList);
                    }
                } else {
                    return;
                }

                if (cfg == null) {
                    return;
                }

                SimPlayerContext context = this.simPlayerContextRegistry.getContext(player.getId());
                if (context == null) {
                    log.warn("使用道具后生成游客失败，获取context 失败 playerId={}", player.getId());
                    return;
                }

                if (itemId == SimConstant.Item.ID_BATCH_GENERATE_SPECIFY_ID_GUEST) {
                    batchGenerateSpecifyIdGuest(context, cfg.getId(), (int) finalSelectItemCount);
                } else {
                    batchGenerateSpecifyQualityGuest(context, cfg.getQuality(), (int) finalSelectItemCount);
                }
            } else if (getItemsMap != null && !getItemsMap.isEmpty()) {
                SimPlayerContext context = this.simPlayerContextRegistry.getContext(player.getId());
                if (context == null) {
                    log.warn("使用道具后生成游客失败，获取context 失败 playerId={}", player.getId());
                    return;
                }

                for (Map.Entry<Integer, Long> en : getItemsMap.entrySet()) {
                    int getItemId = en.getKey();
                    int count = (int) (en.getValue() * useItemCount);
                    if (count <= 0) {
                        continue;
                    }

                    //品质道具 -> 生成对应品质的随机游客
                    Integer quality = configCache.queryGuestQuality(getItemId);
                    if (quality != null) {
                        batchGenerateSpecifyQualityGuest(context, quality, count);
                        continue;
                    }

                    //游客卡道具 -> 生成指定游客
                    VisitorQuestCfg cfg = configCache.getVisitorQuestCfgByItemId(getItemId);
                    if (cfg != null) {
                        batchGenerateSpecifyIdGuest(context, cfg.getId(), count);
                    }
                }
            }
        } catch (Exception e) {
            log.error("", e);
        }
    }

    @Override
    public Set<Integer> checkOpenFunction(Player player) {
        SimPlayerContext ctx = simPlayerContextRegistry.getContext(player.getId());
        if(ctx == null){
            return null;
        }
        SimBaseData base = ctx.getSimBaseData();
        long onlineTime = base.accumulateOnlineTime(System.currentTimeMillis()).getFirst();

        //场景配置
        CasinoStatsSheetCfg casinoCfg = configCache.getCasinoStatsSheetCfg(ctx.getCurrentCasino().getCasinoId(), ctx.getCurrentCasino().getCasinoLevel());
        if (casinoCfg == null) {
            return null;
        }

        long threshold = casinoCfg.getVisitorDailyLimit() * 60_000L
                * SimConstant.Common.GUEST_OUTPUT_LIMIT_NOTIFY_PERCENT / 100;
        if (onlineTime >= threshold) {
            return Set.of(SimConstant.Common.GUEST_OUTPUT_LIMIT_FUNCTION_ID);
        }
        return null;
    }
}
