package com.jjg.game.activity.goldTreasure;

import cn.hutool.core.collection.CollectionUtil;
import com.jjg.game.activity.common.controller.BaseActivityController;
import com.jjg.game.activity.common.data.ActivityData;
import com.jjg.game.activity.common.data.ActivityType;
import com.jjg.game.activity.common.data.PlayerActivityData;
import com.jjg.game.activity.common.message.bean.BaseActivityDetailInfo;
import com.jjg.game.activity.constant.ActivityConstant;
import com.jjg.game.activity.goldTreasure.message.GoldTreasureDetailInfo;
import com.jjg.game.activity.goldTreasure.message.ResClaimGoldTreasureRewards;
import com.jjg.game.activity.goldTreasure.message.ResGoldTreasure;
import com.jjg.game.common.pb.AbstractResponse;
import com.jjg.game.core.base.player.IPlayerLoginSuccess;
import com.jjg.game.core.base.reddot.IRedDotService;
import com.jjg.game.core.constant.AddType;
import com.jjg.game.core.constant.Code;
import com.jjg.game.core.data.Account;
import com.jjg.game.core.data.ChannelType;
import com.jjg.game.core.data.CommonResult;
import com.jjg.game.core.data.ItemOperationResult;
import com.jjg.game.core.data.Order;
import com.jjg.game.core.data.Player;
import com.jjg.game.core.data.PlayerController;
import com.jjg.game.core.data.PlayerPack;
import com.jjg.game.core.data.PreGenerateOrderData;
import com.jjg.game.core.listener.OrderGenerate;
import com.jjg.game.core.pb.RechargeCfgType;
import com.jjg.game.core.pb.RechargeType;
import com.jjg.game.core.pb.ReqGenerateOrder;
import com.jjg.game.core.pb.reddot.RedDotDetails;
import com.jjg.game.core.service.MailService;
import com.jjg.game.core.utils.ItemUtils;
import com.jjg.game.core.utils.SampleDataUtils;
import com.jjg.game.sampledata.GameDataManager;
import com.jjg.game.sampledata.bean.BaseCfgBean;
import com.jjg.game.sampledata.bean.GameFunctionCfg;
import com.jjg.game.sampledata.bean.GoldTreasureCfg;
import com.jjg.game.sampledata.bean.ShopRechargeListCfg;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class GoldTreasureController extends BaseActivityController implements OrderGenerate, IPlayerLoginSuccess, IRedDotService {
    private static final int FUNCTION_ID = 5018;
    private static final int SEASON_FUNCTION_ID = 5004;
    private static final int RED_DOT_SUBMODULE = 1;
    private static final int UNCLAIMED_REWARD_MAIL_ID = 50;
    private static final int ORDINARY_DISPLAY_COUNT = 4;
    private final MailService mailService;

    public GoldTreasureController(MailService mailService) {
        this.mailService = mailService;
    }

    @Override
    public boolean checkPlayerCanJoinActivity(Player player, ActivityData activityData) {
        GameFunctionCfg cfg = GameDataManager.getGameFunctionCfg(FUNCTION_ID);
        return player != null && isRunning(activityData) && cfg != null && cfg.getIsOpen()
                && super.checkPlayerCanJoinActivity(player, activityData)
                && conditionManager.isAchievement(player, "", cfg.getCondition());
    }

    @Override
    public AbstractResponse joinActivity(Player player, ActivityData activityData, int detailId, int times) {
        return new ResGoldTreasure(Code.ERROR_REQ);
    }

    @Override
    public ResClaimGoldTreasureRewards claimActivityRewards(Player player, ActivityData activityData, int detailId) {
        if (player == null || activityData == null) {
            return new ResClaimGoldTreasureRewards(Code.ERROR_REQ);
        }
        ResClaimGoldTreasureRewards res = new ResClaimGoldTreasureRewards(Code.ERROR_REQ);
        res.detailId = detailId;
        if (!checkPlayerCanJoinActivity(player, activityData)) {
            return res;
        }
        Map<Integer, GoldTreasureCfg> cfgMap = getDetailCfgBean(activityData);
        GoldTreasureCfg cfg = cfgMap.get(detailId);
        if (cfg == null) {
            res.code = Code.PARAM_ERROR;
            return res;
        }
        ShopRechargeListCfg shop = GameDataManager.getShopRechargeListCfg(cfg.getPayId());
        if (CollectionUtil.isEmpty(cfg.getGetItem()) || shop == null) {
            res.code = Code.SAMPLE_ERROR;
            return res;
        }
        long playerId = player.getId();
        try {
            Map<Integer, PlayerActivityData> playerData = loadCurrentData(player, activityData);
            PlayerActivityData previous = playerData.get(detailId);
            if (shop.getType() == 2 && !isPaid(previous) && (shop.getRedeem() == null || shop.getRedeem().size() != 1)) {
                res.code = Code.SAMPLE_ERROR;
                return res;
            }
            if (isClaimed(previous)) {
                res.code = Code.REPEAT_OP;
                log.warn("该奖励已被领取 playerId={},detailId={}", playerId, detailId);
                return res;
            }
            GoldTreasureCfg next = nextUnclaimedOrdinaryReward(cfgMap, playerData);
            if (!isUnlocked(cfg, next == null ? 0 : next.getId()) || !canClaim(player, shop, previous)) {
                return res;
            }

            PlayerActivityData claimed = new PlayerActivityData(activityData.getId(), activityData.getRound());
            claimed.setClaimStatus(ActivityConstant.ClaimStatus.CLAIMED);
            playerData.put(detailId, claimed);
            savePlayerData(playerId, activityData, playerData);

            CommonResult<ItemOperationResult> added;
            try {
                if (shop.getType() == 2 && !isPaid(previous)) {
                    Map.Entry<Integer, Long> cost = shop.getRedeem().entrySet().iterator().next();
                    added = playerPackService.useItem(player, cost.getKey(), cost.getValue(), cfg.getGetItem(),
                            AddType.ACTIVITY_GOLD_TREASURE_REWARDS);
                } else {
                    added = playerPackService.addItems(playerId, cfg.getGetItem(), AddType.ACTIVITY_GOLD_TREASURE_REWARDS);
                }
            } catch (Exception e) {
                restoreClaim(playerId, activityData, playerData, detailId, previous);
                throw e;
            }
            if (!added.success()) {
                restoreClaim(playerId, activityData, playerData, detailId, previous);
                res.code = added.code;
                return res;
            }
            res.code = Code.SUCCESS;
            res.items = ItemUtils.buildItemInfo(cfg.getGetItem());
            try {
                activityLogger.sendActivityGift(player, activityData, added.data, cfg.getGetItem(), null, detailId);
                res.activityData = buildDetails(corePlayerService.get(playerId), activityData, playerData);
            } catch (Exception e) {
                log.error("黄金宝藏领奖后刷新数据失败 playerId:{} detailId:{}", playerId, detailId, e);
            }
        } catch (Exception e) {
            log.error("黄金宝藏领奖失败 playerId:{} detailId:{}", playerId, detailId, e);
            res.code = Code.FAIL;
        }
        return res;
    }

    @Override
    public GoldTreasureDetailInfo buildPlayerActivityDetail(Player player, ActivityData activityData,
                                                            BaseCfgBean baseCfgBean, PlayerActivityData data) {
        if (!(baseCfgBean instanceof GoldTreasureCfg cfg)) {
            return null;
        }
        Map<Integer, PlayerActivityData> playerData = readCurrentData(player.getId(), activityData);
        Map<Integer, GoldTreasureCfg> cfgMap = getDetailCfgBean(activityData);
        GoldTreasureCfg next = nextUnclaimedOrdinaryReward(cfgMap, playerData);
        int lastOrdinaryId = 0;
        for (GoldTreasureCfg detailCfg : cfgMap.values()) {
            if (detailCfg.getRewardType() == 1) {
                lastOrdinaryId = detailCfg.getId();
            }
        }
        return buildDetailInfo(player, activityData, cfg, playerData,
                next == null ? 0 : next.getId(), lastOrdinaryId);
    }

    @Override
    public ResGoldTreasure getPlayerActivityDetail(Player player, ActivityData activityData, int detailId) {
        ResGoldTreasure res = buildResponse(player, activityData);
        if (res.code == Code.SUCCESS) {
            res.activityData.removeIf(info -> info.detailId != detailId);
            if (res.activityData.isEmpty()) {
                res.code = Code.PARAM_ERROR;
            }
        }
        return res;
    }

    @Override
    public ResGoldTreasure getPlayerActivityInfoByType(Player player, ActivityType activityType) {
        if (activityType != ActivityType.GOLD_TREASURE) {
            return new ResGoldTreasure(Code.PARAM_ERROR);
        }
        return buildResponse(player, activityManager.getOpenActivityData(player, activityType));
    }

    @Override
    public ResGoldTreasure getPlayerActivityInfoByTypeRes(Player player, Map<Long, List<BaseActivityDetailInfo>> allDetailInfo) {
        return getPlayerActivityInfoByType(player, ActivityType.GOLD_TREASURE);
    }

    @Override
    public Map<Integer, GoldTreasureCfg> getDetailCfgBean(ActivityData activityData) {
        if (activityData == null || CollectionUtil.isEmpty(activityData.getValue())) {
            return Map.of();
        }
        Set<Integer> detailIds = new HashSet<>(activityData.getValue());
        Map<Integer, GoldTreasureCfg> result = new LinkedHashMap<>(detailIds.size());
        for (GoldTreasureCfg cfg : GameDataManager.getGoldTreasureCfgList()) {
            if (detailIds.contains(cfg.getId())) {
                result.put(cfg.getId(), cfg);
            }
        }
        return result;
    }

    @Override
    public CommonResult<PreGenerateOrderData> generateOrderDetailInfo(Player player, ReqGenerateOrder req) {
        CommonResult<PreGenerateOrderData> result = new CommonResult<>(Code.ERROR_REQ);
        ActivityData activityData = activityManager.getOpenActivityData(player, ActivityType.GOLD_TREASURE);
        if (!checkPlayerCanJoinActivity(player, activityData)) {
            log.warn("黄金宝藏下单失败，活动不存在、未开放或玩家无法参与 playerId={},productId={},activityId={}",
                    player.getId(), req.productId, activityData == null ? null : activityData.getId());
            return result;
        }
        int detailId = Integer.parseInt(req.productId);
        Map<Integer, GoldTreasureCfg> cfgMap = getDetailCfgBean(activityData);
        GoldTreasureCfg cfg = cfgMap.get(detailId);
        if (cfg == null) {
            result.code = Code.PARAM_ERROR;
            log.warn("预下单失败，获取GoldTreasureCfg配置失败 playerId:{} detailId:{}", player.getId(), detailId);
            return result;
        }
        ShopRechargeListCfg shop = GameDataManager.getShopRechargeListCfg(cfg.getPayId());
        if (shop == null) {
            result.code = Code.PARAM_ERROR;
            log.warn("预下单失败，获取ShopRechargeListCfg配置失败 playerId:{} detailId:{},cfgId:{}", player.getId(), detailId, cfg.getPayId());
            return result;
        }

        Map<Integer, PlayerActivityData> playerData = loadCurrentData(player, activityData);
        PlayerActivityData data = playerData.get(detailId);
        if (data != null && (data.getClaimStatus() == ActivityConstant.ClaimStatus.CAN_CLAIM || isClaimed(data))) {
            result.code = Code.REPEAT_OP;
            log.warn("黄金宝藏下单失败，该档位已有购买或领取记录 playerId={},productId={},activityId={},detailId={}",
                    player.getId(), req.productId, activityData.getId(), detailId);
            return result;
        }
        GoldTreasureCfg next = nextUnclaimedOrdinaryReward(cfgMap, playerData);
        if (!isUnlocked(cfg, next == null ? 0 : next.getId())) {
            log.warn("黄金宝藏下单失败，未按档位顺序购买 playerId={},productId={},activityId={},detailId={},nextDetailId={}",
                    player.getId(), req.productId, activityData.getId(), detailId, next == null ? null : next.getId());
            return result;
        }

        RechargeCfgType rechargeCfgType = RechargeCfgType.valueOf(shop.getType());
        if (rechargeCfgType != RechargeCfgType.MONEY) {
            return rechargeCfgType == RechargeCfgType.ITEM_EXCHANGE
                    ? singleExchangeOrderDetailInfo(playerPackService, player, shop, req.buyCount, AddType.ACTIVITY_GOLD_TREASURE_REWARDS)
                    : nonMoneyOrderDetailInfo(shop);
        }

        if (shop.getPrice() == null || shop.getPrice().signum() <= 0
                || StringUtils.isBlank(channelProductId(player, shop)) || CollectionUtil.isEmpty(cfg.getGetItem())) {
            result.code = Code.SAMPLE_ERROR;
            log.warn("黄金宝藏下单失败，充值商品配置、价格、渠道商品ID或奖励无效 playerId={},productId={},detailId={},shopCfgId={},price={},channelProductId={},rewardsEmpty={}",
                    player.getId(), req.productId, detailId, cfg.getPayId(),
                    shop.getPrice(),
                    channelProductId(player, shop), CollectionUtil.isEmpty(cfg.getGetItem()));
            return result;
        }
        try {
            if (data == null) {
                // 通过下单校验即记录参与，未付款时仍保持 NOT_CLAIM。
                playerData.put(detailId, new PlayerActivityData(activityData.getId(), activityData.getRound()));
                if (!savePlayerData(player.getId(), activityData, playerData)) {
                    result.code = Code.FAIL;
                    return result;
                }
            }
            result.code = Code.SUCCESS;
            PreGenerateOrderData preGenerateOrderData = new PreGenerateOrderData();
            preGenerateOrderData.setType(RechargeCfgType.MONEY);
            preGenerateOrderData.setPrice(shop.getPrice());
            result.data = preGenerateOrderData;
        } catch (Exception e) {
            log.error("黄金宝藏下单校验失败 playerId:{} detailId:{}", player.getId(), detailId, e);
            result.code = Code.FAIL;
        }
        return result;
    }

    @Override
    public RechargeType getRechargeType() {
        return RechargeType.GOLD_TREASURE;
    }

    @Override
    public boolean onReceivedRecharge(Player player, Order order) {
        if (order.getRechargeType() != getRechargeType()) {
            return true;
        }
        try {
            if (order.getPlayerId() != player.getId()) {
                return false;
            }
            int detailId = Integer.parseInt(order.getProductId());
            ActivityData activityData = activityManager.getOpenActivityData(player, ActivityType.GOLD_TREASURE);
            GoldTreasureCfg cfg = getDetailCfgBean(activityData).get(detailId);
            ShopRechargeListCfg shop = cfg == null ? null : GameDataManager.getShopRechargeListCfg(cfg.getPayId());
            if (shop == null || shop.getType() != 1) {
                return false;
            }
            return receivePayment(player, activityData, cfg, order.getId(), order.getProductId());
        } catch (Exception e) {
            log.error("黄金宝藏支付处理失败 playerId:{} orderId:{}", player.getId(), order.getId(), e);
            return false;
        }
    }

    @Override
    public boolean onReceivedRecharge(Player player, PreGenerateOrderData preGenerateOrderData) {
        RechargeCfgType type = preGenerateOrderData.getType();
        if (type == null || type == RechargeCfgType.MONEY) {
            return false;
        }
        int detailId = Integer.parseInt(preGenerateOrderData.getProductId());
        ActivityData activityData = activityManager.getOpenActivityData(player, ActivityType.GOLD_TREASURE);
        if (!checkPlayerCanJoinActivity(player, activityData)) {
            return false;
        }
        GoldTreasureCfg cfg = getDetailCfgBean(activityData).get(detailId);
        if (cfg == null) {
            log.warn("充值后处理失败，未找到GoldTreasureCfg配置 playerId:{} detailId:{}", player.getId(), detailId);
            return false;
        }
        ShopRechargeListCfg shop = GameDataManager.getShopRechargeListCfg(cfg.getPayId());
        if (shop == null || shop.getType() != type.getType()) {
            log.warn("非付费购买失败，商品配置不存在或购买类型不匹配 playerId:{} detailId:{} type:{}", player.getId(), detailId, type);
            return false;
        }
        return completeNonMoneyPurchase(preGenerateOrderData, shop,
                () -> receivePayment(player, activityData, cfg, null, preGenerateOrderData.getProductId()));
    }


    private boolean receivePayment(Player player, ActivityData activityData, GoldTreasureCfg cfg, String orderId, String productId) {
        long activityId = activityData.getId();
        int detailId = cfg.getId();
        Map<Integer, PlayerActivityData> playerData = loadCurrentData(player, activityData);
        PlayerActivityData data = playerData.get(detailId);
        if (data != null && (data.getClaimStatus() == ActivityConstant.ClaimStatus.CAN_CLAIM || isClaimed(data))) {
            return true;
        }
        data = new PlayerActivityData(activityId, activityData.getRound());
        data.setClaimStatus(ActivityConstant.ClaimStatus.CAN_CLAIM);
        playerData.put(detailId, data);
        if (!savePlayerData(player.getId(), activityData, playerData)) {
            return false;
        }
        try {
            activityManager.sendToPlayer(player.getId(), buildResponse(player, activityData));
            updateRodDot(player.getId(), activityData, false);
        } catch (Exception e) {
            log.error("黄金宝藏支付后通知失败 playerId:{} orderId:{},productId:{}", player.getId(), orderId, productId, e);
        }
        return true;
    }

    @Override
    public RedDotDetails.RedDotModule getModule() {
        return RedDotDetails.RedDotModule.SEASON;
    }

    @Override
    public int getSubmodule() {
        return RED_DOT_SUBMODULE;
    }

    @Override
    public List<RedDotDetails> initialize(long playerId, int submodule) {
        if (submodule != 0 && submodule != RED_DOT_SUBMODULE) {
            return List.of();
        }
        Player player = corePlayerService.get(playerId);
        ActivityData activityData = player == null ? null
                : activityManager.getOpenActivityData(player, ActivityType.GOLD_TREASURE);
        boolean available = activityData != null && hasRedDot(playerId, activityData)
                && gameFunctionService.getOpenedFuncIdList(player).contains(SEASON_FUNCTION_ID);
        return List.of(redDotManager.buildRedDotDetails(getModule(), RED_DOT_SUBMODULE, available ? 1 : 0));
    }

    @Override
    public boolean hasRedDot(long playerId, ActivityData activityData) {
        Player player = corePlayerService.get(playerId);
        if (!checkPlayerCanJoinActivity(player, activityData)) {
            return false;
        }
        return claimableRewardId(player, getDetailCfgBean(activityData), readCurrentData(playerId, activityData)) != 0;
    }

    @Override
    public void onActivityEnd(ActivityData activityData) {
        super.onActivityEnd(activityData);
        playerActivityDao.clearActivityData(ActivityType.GOLD_TREASURE, activityData.getId(),
                (playerId, playerData) -> playerData.values().stream().allMatch(data -> data.getRound() == activityData.getRound())
                        && settleRewards(corePlayerService.get(playerId), activityData, playerData, activityData.getRound()));
    }

    @Override
    public void onPlayerLoginSuccess(PlayerController playerController, Player player, Account account, boolean firstLogin) {
        for (ActivityData activityData : activityManager.getActivityTypeData().getOrDefault(ActivityType.GOLD_TREASURE, Map.of()).values()) {
            try {
                Map<Integer, PlayerActivityData> playerData = playerActivityDao.getPlayerActivityDataOrThrow(
                        player.getId(), ActivityType.GOLD_TREASURE, activityData.getId());
                if (!playerData.isEmpty() && (!isRunning(activityData)
                        || playerData.values().iterator().next().getRound() != activityData.getRound())) {
                    long round = playerData.values().iterator().next().getRound();
                    if (settleRewards(player, activityData, playerData, round)) {
                        playerActivityDao.deletePlayerActivityData(player.getId(), ActivityType.GOLD_TREASURE, activityData.getId());
                    }
                }
            } catch (Exception e) {
                log.error("黄金宝藏登录补发失败 playerId:{} activityId:{}", player.getId(), activityData.getId(), e);
            }
        }
    }

    private ResGoldTreasure buildResponse(Player player, ActivityData activityData) {
        ResGoldTreasure res = new ResGoldTreasure(Code.SUCCESS);
        res.activityData = new ArrayList<>();
        if (!checkPlayerCanJoinActivity(player, activityData)) {
            return res;
        }
        try {
            res.endTime = activityData.getTimeEnd();
            res.activityData = buildDetails(corePlayerService.get(player.getId()), activityData, readCurrentData(player.getId(), activityData));
        } catch (Exception e) {
            log.error("黄金宝藏活动信息读取失败 playerId:{}", player.getId(), e);
            res.code = Code.FAIL;
        }
        return res;
    }

    private List<GoldTreasureDetailInfo> buildDetails(Player player, ActivityData activityData,
                                                      Map<Integer, PlayerActivityData> playerData) {
        Map<Integer, GoldTreasureCfg> cfgMap = getDetailCfgBean(activityData);
        GoldTreasureCfg next = nextUnclaimedOrdinaryReward(cfgMap, playerData);
        int unlockedId = next == null ? 0 : next.getId();
        int ordinaryRemaining = 0;
        int lastOrdinaryId = 0;
        for (GoldTreasureCfg cfg : cfgMap.values()) {
            if (cfg.getRewardType() == 1) {
                ordinaryRemaining++;
                lastOrdinaryId = cfg.getId();
            }
        }
        List<GoldTreasureDetailInfo> result = new ArrayList<>(
                cfgMap.size() - ordinaryRemaining + Math.min(ORDINARY_DISPLAY_COUNT, ordinaryRemaining));
        int ordinaryDisplayed = 0;
        for (GoldTreasureCfg cfg : cfgMap.values()) {
            if (cfg.getRewardType() == 1) {
                if (ordinaryDisplayed == ORDINARY_DISPLAY_COUNT
                        || ordinaryRemaining-- > ORDINARY_DISPLAY_COUNT && isClaimed(playerData.get(cfg.getId()))) {
                    continue;
                }
                ordinaryDisplayed++;
            }
            result.add(buildDetailInfo(player, activityData, cfg, playerData, unlockedId, lastOrdinaryId));
        }
        return result;
    }

    private int claimableRewardId(Player player, Map<Integer, GoldTreasureCfg> cfgMap,
                                  Map<Integer, PlayerActivityData> playerData) {
        GoldTreasureCfg next = nextUnclaimedOrdinaryReward(cfgMap, playerData);
        int unlockedId = next == null ? 0 : next.getId();
        for (GoldTreasureCfg cfg : cfgMap.values()) {
            PlayerActivityData data = playerData.get(cfg.getId());
            if (!isClaimed(data) && isUnlocked(cfg, unlockedId) && CollectionUtil.isNotEmpty(cfg.getGetItem())
                    && canClaim(player, GameDataManager.getShopRechargeListCfg(cfg.getPayId()), data)) {
                return cfg.getId();
            }
        }
        return 0;
    }

    private GoldTreasureDetailInfo buildDetailInfo(Player player, ActivityData activityData, GoldTreasureCfg cfg,
                                                   Map<Integer, PlayerActivityData> playerData, int unlockedId, int lastOrdinaryId) {
        GoldTreasureDetailInfo info = new GoldTreasureDetailInfo();
        info.activityId = activityData.getId();
        info.detailId = cfg.getId();
        info.rewardType = cfg.getRewardType();
        info.rewardItems = ItemUtils.buildItemInfo(cfg.getGetItem());
        info.rechargeInfo = SampleDataUtils.transRechargeCfgToRechargeInfo(cfg.getPayId(), player.getChannel());
        info.best = cfg.getValue();
        info.last = cfg.getRewardType() == 1 && cfg.getId() == lastOrdinaryId;
        if (isClaimed(playerData.get(cfg.getId()))) {
            info.claimStatus = ActivityConstant.ClaimStatus.CLAIMED;
        } else if (isUnlocked(cfg, unlockedId)) {
            info.claimStatus = CollectionUtil.isNotEmpty(cfg.getGetItem())
                    && canClaim(player, GameDataManager.getShopRechargeListCfg(cfg.getPayId()), playerData.get(cfg.getId()))
                    ? ActivityConstant.ClaimStatus.CAN_CLAIM : ActivityConstant.ClaimStatus.NOT_CLAIM;
        }
        return info;
    }

    private boolean isUnlocked(GoldTreasureCfg cfg, int ordinaryUnlockedId) {
        return cfg.getRewardType() == 2 || cfg.getId() == ordinaryUnlockedId;
    }

    private GoldTreasureCfg nextUnclaimedOrdinaryReward(Map<Integer, GoldTreasureCfg> cfgMap,
                                                        Map<Integer, PlayerActivityData> playerData) {
        for (GoldTreasureCfg cfg : cfgMap.values()) {
            if (cfg.getRewardType() == 1 && !isClaimed(playerData.get(cfg.getId()))) {
                return cfg;
            }
        }
        return null;
    }

    private boolean canClaim(Player player, ShopRechargeListCfg shop, PlayerActivityData data) {
        if (shop == null) {
            return false;
        }
        return switch (shop.getType()) {
            case 1, 3 -> data != null && data.getClaimStatus() == ActivityConstant.ClaimStatus.CAN_CLAIM;
            case 2 -> isPaid(data) || shop.getRedeem() != null && shop.getRedeem().size() == 1
                    && playerPackService.checkHasItems(player, shop.getRedeem());
            case 4 -> true;
            default -> false;
        };
    }

    private boolean isClaimed(PlayerActivityData data) {
        return data != null && data.getClaimStatus() == ActivityConstant.ClaimStatus.CLAIMED;
    }

    private boolean isPaid(PlayerActivityData data) {
        return data != null && data.getClaimStatus() == ActivityConstant.ClaimStatus.CAN_CLAIM;
    }

    private boolean isRunning(ActivityData activityData) {
        long now = System.currentTimeMillis();
        return activityData != null && activityData.getType() == ActivityType.GOLD_TREASURE && activityData.canRun()
                && now >= activityData.getTimeStart() && now < activityData.getTimeEnd();
    }

    private String channelProductId(Player player, ShopRechargeListCfg shop) {
        return player.getChannel() == ChannelType.APPLE ? shop.getIosShopId() : shop.getGoogleShopId();
    }

    private Map<Integer, PlayerActivityData> loadCurrentData(Player player, ActivityData activityData) {
        Map<Integer, PlayerActivityData> playerData = playerActivityDao.getPlayerActivityDataOrThrow(
                player.getId(), ActivityType.GOLD_TREASURE, activityData.getId());
        if (!playerData.isEmpty() && playerData.values().iterator().next().getRound() != activityData.getRound()) {
            long oldRound = playerData.values().iterator().next().getRound();
            if (!settleRewards(player, activityData, playerData, oldRound)
                    || !savePlayerData(player.getId(), activityData, new HashMap<>())) {
                throw new IllegalStateException("黄金宝藏上期奖励未结算");
            }
            return new HashMap<>();
        }
        return playerData;
    }

    private Map<Integer, PlayerActivityData> readCurrentData(long playerId, ActivityData activityData) {
        Map<Integer, PlayerActivityData> playerData = playerActivityDao.getPlayerActivityDataOrThrow(
                playerId, ActivityType.GOLD_TREASURE, activityData.getId());
        playerData.values().removeIf(data -> data.getRound() != activityData.getRound());
        return playerData;
    }

    private boolean savePlayerData(long playerId, ActivityData activityData, Map<Integer, PlayerActivityData> playerData) {
        return playerActivityDao.savePlayerActivityData(playerId, ActivityType.GOLD_TREASURE, activityData.getId(), playerData);
    }

    private void restoreClaim(long playerId, ActivityData activityData, Map<Integer, PlayerActivityData> playerData,
                              int detailId, PlayerActivityData previous) {
        if (previous == null) {
            playerData.remove(detailId);
        } else {
            playerData.put(detailId, previous);
        }
        if (!savePlayerData(playerId, activityData, playerData)) {
            log.error("黄金宝藏领奖状态回滚失败 playerId:{} activityId:{} detailId:{}", playerId, activityData.getId(), detailId);
        }
    }

    private boolean settleRewards(Player player, ActivityData activityData, Map<Integer, PlayerActivityData> playerData, long round) {
        if (playerData.isEmpty()) {
            return true;
        }
        if (player == null) {
            return false;
        }
        Map<Integer, GoldTreasureCfg> cfgMap = getDetailCfgBean(activityData);
        Set<Integer> starItems = starItemIds(cfgMap);
        PlayerPack pack = playerPackService.getFromAllDB(player.getId());
        GoldTreasureCfg next = nextUnclaimedOrdinaryReward(cfgMap, playerData);
        int unlockedId = next == null ? 0 : next.getId();
        Map<Integer, Long> exchanged = new HashMap<>();
        for (GoldTreasureCfg cfg : cfgMap.values()) {
            PlayerActivityData data = playerData.get(cfg.getId());
            if (isClaimed(data) || !isUnlocked(cfg, unlockedId) || CollectionUtil.isEmpty(cfg.getGetItem())) {
                continue;
            }
            ShopRechargeListCfg shop = GameDataManager.getShopRechargeListCfg(cfg.getPayId());
            if (shop == null) {
                continue;
            }
            Map.Entry<Integer, Long> cost = null;
            if (shop.getType() == 2 && !isPaid(data)) {
                if (shop.getRedeem() == null || shop.getRedeem().size() != 1 || pack == null) {
                    continue;
                }
                cost = shop.getRedeem().entrySet().iterator().next();
                if (pack.getItemCount(cost.getKey()) - exchanged.getOrDefault(cost.getKey(), 0L) < cost.getValue()) {
                    continue;
                }
            } else if (!canClaim(player, shop, data)) {
                continue;
            }
            if (!sendRewardMail(player.getId(), cfg, starItems)) {
                return false;
            }
            PlayerActivityData claimed = new PlayerActivityData(activityData.getId(), round);
            claimed.setClaimStatus(ActivityConstant.ClaimStatus.CLAIMED);
            playerData.put(cfg.getId(), claimed);
            if (!savePlayerData(player.getId(), activityData, playerData)) {
                return false;
            }
            if (cost != null) {
                exchanged.merge(cost.getKey(), cost.getValue(), Math::addExact);
            }
        }
        Map<Integer, Long> removes = new HashMap<>();
        if (pack != null) {
            for (int itemId : starItems) {
                long count = pack.getItemCount(itemId);
                if (count > 0) {
                    removes.put(itemId, count);
                }
            }
        }
        return removes.isEmpty() || playerPackService.removeItems(player, removes, AddType.ACTIVITY_GOLD_TREASURE_REWARDS).success();
    }

    private Set<Integer> starItemIds(Map<Integer, GoldTreasureCfg> cfgMap) {
        Set<Integer> result = new HashSet<>();
        for (GoldTreasureCfg cfg : cfgMap.values()) {
            if (cfg.getRewardType() == 2) {
                ShopRechargeListCfg shop = GameDataManager.getShopRechargeListCfg(cfg.getPayId());
                if (shop != null && shop.getType() == 2 && CollectionUtil.isNotEmpty(shop.getRedeem())) {
                    result.addAll(shop.getRedeem().keySet());
                }
            }
        }
        return result;
    }

    private boolean sendRewardMail(long playerId, GoldTreasureCfg cfg, Set<Integer> starItems) {
        Map<Integer, Long> rewards = new HashMap<>(cfg.getGetItem());
        starItems.forEach(rewards::remove);
        if (rewards.isEmpty()) {
            return true;
        }
        return mailService.addCfgMail(playerId, UNCLAIMED_REWARD_MAIL_ID, ItemUtils.buildItems(rewards),
                AddType.ACTIVITY_GOLD_TREASURE_REWARDS, null) != null;
    }
}
