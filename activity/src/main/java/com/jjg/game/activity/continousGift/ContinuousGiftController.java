package com.jjg.game.activity.continousGift;

import cn.hutool.core.collection.CollectionUtil;
import com.jjg.game.activity.common.controller.BaseActivityController;
import com.jjg.game.activity.common.data.ActivityData;
import com.jjg.game.activity.common.data.ActivityType;
import com.jjg.game.activity.common.data.PlayerActivityData;
import com.jjg.game.activity.common.message.bean.BaseActivityDetailInfo;
import com.jjg.game.activity.constant.ActivityConstant;
import com.jjg.game.activity.continousGift.data.ContinuousGiftPlayerData;
import com.jjg.game.activity.continousGift.message.ContinuousGiftDetailInfo;
import com.jjg.game.activity.continousGift.message.ResClaimContinuousGiftRewards;
import com.jjg.game.activity.continousGift.message.ResContinuousGift;
import com.jjg.game.common.pb.AbstractResponse;
import com.jjg.game.common.utils.TimeHelper;
import com.jjg.game.core.base.player.IPlayerLoginSuccess;
import com.jjg.game.core.base.reddot.IRedDotService;
import com.jjg.game.core.constant.AddType;
import com.jjg.game.core.constant.Code;
import com.jjg.game.core.data.*;
import com.jjg.game.core.listener.GmListener;
import com.jjg.game.core.listener.OrderGenerate;
import com.jjg.game.core.pb.RechargeCfgType;
import com.jjg.game.core.pb.RechargeInfo;
import com.jjg.game.core.pb.RechargeType;
import com.jjg.game.core.pb.ReqGenerateOrder;
import com.jjg.game.core.pb.reddot.RedDotDetails;
import com.jjg.game.core.service.MailService;
import com.jjg.game.core.utils.ItemUtils;
import com.jjg.game.core.utils.SampleDataUtils;
import com.jjg.game.sampledata.GameDataManager;
import com.jjg.game.sampledata.bean.BaseCfgBean;
import com.jjg.game.sampledata.bean.ConsecutivePackCfg;
import com.jjg.game.sampledata.bean.GameFunctionCfg;
import com.jjg.game.sampledata.bean.ShopRechargeListCfg;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.time.DateTimeException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ContinuousGiftController extends BaseActivityController implements OrderGenerate, IRedDotService, IPlayerLoginSuccess, GmListener {
    private static final int FUNCTION_ID = 6001;
    private static final int PAID_PACK = 1;
    private static final int FINAL_REWARD = 2;
    private static final int RED_DOT_SUBMODULE = 2;
    private static final int UNCLAIMED_REWARD_MAIL_ID = 51;
    private final MailService mailService;
    // 仅覆盖本实例连续礼包时间，不修改系统时间或活动配置。
    private volatile long gmTimeOffset;

    public ContinuousGiftController(MailService mailService) {
        this.mailService = mailService;
    }

    private long currentTimeMillis() {
        return System.currentTimeMillis() + gmTimeOffset;
    }

    @Override
    public CommonResult<String> gm(PlayerController playerController, String[] gmOrders) {
        if (gmOrders == null || gmOrders.length == 0 || !"ContinuousGiftTime".equalsIgnoreCase(gmOrders[0])) {
            return new CommonResult<>(Code.NOT_FOUND);
        }
        if (gmOrders.length != 2) {
            return new CommonResult<>(Code.PARAM_ERROR);
        }
        long now = System.currentTimeMillis();
        try {
            long offset = TimeHelper.getDateOffset(gmOrders[1], now);
            gmTimeOffset = offset;
            return new CommonResult<>(Code.SUCCESS, "ContinuousGiftTime=" + TimeHelper.getLocalDateTime(now + offset));
        } catch (IllegalArgumentException | DateTimeException e) {
            return new CommonResult<>(Code.PARAM_ERROR);
        }
    }

    @Override
    public boolean checkPlayerCanJoinActivity(Player player, ActivityData activityData) {
        GameFunctionCfg cfg = GameDataManager.getGameFunctionCfg(FUNCTION_ID);
        // 不调用含活动检查的GameFunctionService方法，避免回调ActivityManager形成递归。
        return player != null && isRunning(activityData) && cfg != null && cfg.getIsOpen()
                && super.checkPlayerCanJoinActivity(player, activityData)
                && conditionManager.isAchievement(player, "", cfg.getCondition());
    }

    @Override
    public AbstractResponse joinActivity(Player player, ActivityData activityData, int detailId, int times) {
        return new ResContinuousGift(Code.ERROR_REQ);
    }

    @Override
    public Map<Integer, ConsecutivePackCfg> getDetailCfgBean(ActivityData activityData) {
        if (activityData == null || CollectionUtil.isEmpty(activityData.getValue())) {
            return Map.of();
        }
        Map<Integer, ConsecutivePackCfg> result = new LinkedHashMap<>(activityData.getValue().size());
        for (int id : activityData.getValue()) {
            ConsecutivePackCfg cfg = GameDataManager.getConsecutivePackCfg(id);
            if (cfg != null) {
                result.put(id, cfg);
            }
        }
        return result;
    }

    @Override
    public CommonResult<PreGenerateOrderData> generateOrderDetailInfo(Player player, ReqGenerateOrder req) {
        CommonResult<PreGenerateOrderData> result = new CommonResult<>(Code.ERROR_REQ);
        try {
            ActivityData activityData = activityManager.getOpenActivityData(player, ActivityType.CONTINUOUS_GIFT);
            if (!checkPlayerCanJoinActivity(player, activityData)) {
                log.warn("连续礼包下单失败，活动未开放或玩家无法参与 playerId:{} productId:{} activityId:{}",
                        player == null ? null : player.getId(), req.productId, activityData == null ? null : activityData.getId());
                return result;
            }
            int detailId;
            try {
                detailId = Integer.parseInt(req.productId);
            } catch (NumberFormatException e) {
                log.warn("连续礼包下单失败，宝箱ID格式错误 playerId:{} productId:{}", player.getId(), req.productId);
                result.code = Code.PARAM_ERROR;
                return result;
            }
            Map<Integer, ConsecutivePackCfg> cfgMap = getDetailCfgBean(activityData);
            if (!validConfig(activityData, cfgMap)) {
                log.error("连续礼包下单失败，宝箱配置不完整或奖励无效 playerId:{} activityId:{} detailIds:{} configuredIds:{}",
                        player.getId(), activityData.getId(), activityData.getValue(), cfgMap.keySet());
                result.code = Code.SAMPLE_ERROR;
                return result;
            }
            ConsecutivePackCfg cfg = cfgMap.get(detailId);
            if (cfg == null || cfg.getType() != PAID_PACK) {
                log.warn("连续礼包下单失败，宝箱不存在或不是付费礼包 playerId:{} activityId:{} detailId:{} type:{}",
                        player.getId(), activityData.getId(), detailId, cfg == null ? null : cfg.getType());
                result.code = Code.PARAM_ERROR;
                return result;
            }
            ShopRechargeListCfg shop = GameDataManager.getShopRechargeListCfg(cfg.getShopRechargeListID());
            if (shop == null || shop.getType() == RechargeCfgType.MONEY.getType()
                    && (shop.getPrice() == null || shop.getPrice().signum() <= 0
                    || StringUtils.isBlank(player.getChannel() == ChannelType.APPLE ? shop.getIosShopId() : shop.getGoogleShopId()))) {
                log.error("连续礼包下单失败，充值配置、价格或渠道商品ID无效 playerId:{} detailId:{} shopId:{} type:{} price:{} channel:{}",
                        player.getId(), detailId, cfg.getShopRechargeListID(), shop == null ? null : shop.getType(),
                        shop == null ? null : shop.getPrice(), player.getChannel());
                result.code = Code.SAMPLE_ERROR;
                return result;
            }
            Map<Integer, ContinuousGiftPlayerData> playerData = readCurrentData(player.getId(), activityData);
            int nextId = nextPaidPack(cfgMap, playerData);
            if (detailId != nextId) {
                log.warn("连续礼包下单失败，未按顺序购买或礼包已购买 playerId:{} activityId:{} detailId:{} nextId:{}",
                        player.getId(), activityData.getId(), detailId, nextId);
                result.code = Code.REPEAT_OP;
                return result;
            }
            if (isDailyLimited(playerData, currentTimeMillis())) {
                log.warn("连续礼包下单失败，未到下次购买日期 playerId:{} activityId:{} detailId:{}", player.getId(), activityData.getId(), detailId);
                result.code = Code.DAILY_LIMIT;
                return result;
            }
            String productId = activityData.getId() + "_" + activityData.getRound() + "_" + detailId;
            if (shop.getType() != RechargeCfgType.MONEY.getType()) {
                result = shop.getType() == RechargeCfgType.ITEM_EXCHANGE.getType()
                    ? singleExchangeOrderDetailInfo(playerPackService, player, shop, req.buyCount, AddType.ACTIVITY_CONTINUOUS_GIFT_REWARDS)
                    : nonMoneyOrderDetailInfo(shop);
                if (result.success()) {
                    result.data.setOrderProductId(productId);
                }
                return result;
            }
            PreGenerateOrderData data = new PreGenerateOrderData();
            data.setType(RechargeCfgType.MONEY);
            data.setPrice(shop.getPrice());
            data.setOrderProductId(productId);
            result.data = data;
            result.code = Code.SUCCESS;
        } catch (Exception e) {
            log.error("连续礼包下单异常 playerId:{} productId:{}", player == null ? null : player.getId(), req.productId, e);
            result.code = Code.FAIL;
        }
        return result;
    }

    @Override
    public RechargeType getRechargeType() {
        return RechargeType.CONTINUOUS_GIFT;
    }

    @Override
    public boolean onReceivedRecharge(Player player, Order order) {
        if (order.getRechargeType() != getRechargeType()) {
            log.debug("连续礼包忽略其他类型的充值 playerId:{} orderId:{} rechargeType:{}",
                    order.getPlayerId(), order.getId(), order.getRechargeType());
            return true;
        }
        if (player == null || order.getPlayerId() != player.getId() || StringUtils.isBlank(order.getId())) {
            log.warn("连续礼包支付处理失败，玩家与订单不匹配或订单ID为空 playerId:{} orderPlayerId:{} orderId:{}",
                    player == null ? null : player.getId(), order.getPlayerId(), order.getId());
            return false;
        }
        if (order.getOrderStatus() == OrderStatus.SUCCESS) {
            return true;
        }
        if (order.getOrderStatus() != OrderStatus.PROCESSING) {
            log.warn("连续礼包支付处理失败，订单状态无效 playerId:{} orderId:{} status:{}", player.getId(), order.getId(), order.getOrderStatus());
            return false;
        }
        try {
            String[] parts = order.getProductId().split("_");
            if (parts.length != 3) {
                log.warn("连续礼包支付处理失败，订单商品ID格式错误 playerId:{} orderId:{} productId:{}", player.getId(), order.getId(), order.getProductId());
                return false;
            }
            long activityId = Long.parseLong(parts[0]);
            long round = Long.parseLong(parts[1]);
            int detailId = Integer.parseInt(parts[2]);

            ConsecutivePackCfg consecutivePackCfg = GameDataManager.getConsecutivePackCfg(detailId);
            if (consecutivePackCfg == null) {
                log.warn("连续礼包支付处理失败，获取consecutivePackCfg配置失败 playerId:{} orderId:{} productId:{}", player.getId(), order.getId(), order.getProductId());
                return false;
            }

            ActivityData activityData = activityManager.getActivityData().get(activityId);
            // 本期支付只记录待领取资格；过期或换期的订单补发，不写入当前活动进度。
            boolean current = isRunning(activityData) && activityData.getRound() == round;
            if (current) {
                return receiveCurrentPurchase(player, activityData, detailId, order.getId());
            }
            CommonResult<ItemOperationResult> added = playerPackService.addItems(player.getId(), consecutivePackCfg.getGetitem(),
                    AddType.ACTIVITY_CONTINUOUS_GIFT_REWARDS, "order:" + order.getId());
            if (!added.success()) {
                log.error("连续礼包支付处理失败，奖励发放失败 playerId:{} orderId:{} activityId:{} detailId:{} code:{}",
                        player.getId(), order.getId(), activityId, detailId, added.code);
                return false;
            }
            ActivityData logData = new ActivityData();
            logData.setId(activityId);
            logData.setRound(round);
            logData.setType(ActivityType.CONTINUOUS_GIFT);
            logReward(player, logData, added.data, consecutivePackCfg.getGetitem(), order.getPrice(), detailId);
            return true;
        } catch (Exception e) {
            log.error("连续礼包支付处理失败 playerId:{} orderId:{}", player.getId(), order.getId(), e);
            return false;
        }
    }

    @Override
    public ResClaimContinuousGiftRewards claimActivityRewards(Player player, ActivityData activityData, int detailId) {
        ResClaimContinuousGiftRewards res = new ResClaimContinuousGiftRewards(Code.ERROR_REQ);
        res.detailId = detailId;
        if (!checkPlayerCanJoinActivity(player, activityData)) {
            return res;
        }
        try {
            Map<Integer, ConsecutivePackCfg> cfgMap = getDetailCfgBean(activityData);
            if (!validConfig(activityData, cfgMap)) {
                res.code = Code.SAMPLE_ERROR;
                return res;
            }
            ConsecutivePackCfg cfg = cfgMap.get(detailId);
            if (cfg == null) {
                res.code = Code.PARAM_ERROR;
                return res;
            }
            Map<Integer, ContinuousGiftPlayerData> playerData = readCurrentData(player.getId(), activityData);
            ContinuousGiftPlayerData previous = playerData.get(detailId);
            if (isClaimed(previous)) {
                res.code = Code.REPEAT_OP;
                return res;
            }
            if (cfg.getType() == PAID_PACK ? !isPurchased(previous) : nextPaidPack(cfgMap, playerData) != 0) {
                return res;
            }
            ContinuousGiftPlayerData claimed = new ContinuousGiftPlayerData(activityData.getId(), activityData.getRound());
            if (previous != null) {
                claimed.setPurchaseTime(previous.getPurchaseTime());
                claimed.setOrderId(previous.getOrderId());
            }
            claimed.setClaimStatus(ActivityConstant.ClaimStatus.CLAIMED);
            playerData.put(detailId, claimed);
            if (!savePlayerData(player.getId(), activityData, playerData)) {
                res.code = Code.FAIL;
                return res;
            }
            CommonResult<ItemOperationResult> added;
            try {
                added = playerPackService.addItems(player.getId(), cfg.getGetitem(), AddType.ACTIVITY_CONTINUOUS_GIFT_REWARDS);
            } catch (Exception e) {
                rollbackReward(player.getId(), activityData, playerData, detailId, previous);
                throw e;
            }
            if (!added.success()) {
                rollbackReward(player.getId(), activityData, playerData, detailId, previous);
                res.code = added.code;
                return res;
            }
            res.code = Code.SUCCESS;
            res.items = ItemUtils.buildItemInfo(cfg.getGetitem());
            try {
                res.activityData = buildDetails(player, activityData, cfgMap, playerData);
            } catch (Exception e) {
                log.error("连续礼包领奖后刷新失败 playerId:{} detailId:{}", player.getId(), detailId, e);
            }
            logReward(player, activityData, added.data, cfg.getGetitem(), null, detailId);
        } catch (Exception e) {
            log.error("连续礼包领奖失败 playerId:{} detailId:{}", player.getId(), detailId, e);
            res.code = Code.FAIL;
        }
        return res;
    }

    @Override
    public ResContinuousGift getPlayerActivityInfoByType(Player player, ActivityType activityType) {
        return activityType == ActivityType.CONTINUOUS_GIFT
                ? buildResponse(player, activityManager.getOpenActivityData(player, activityType))
                : new ResContinuousGift(Code.PARAM_ERROR);
    }

    @Override
    public ResContinuousGift getPlayerActivityInfoByTypeRes(Player player, Map<Long, List<BaseActivityDetailInfo>> allDetailInfo) {
        return getPlayerActivityInfoByType(player, ActivityType.CONTINUOUS_GIFT);
    }

    @Override
    public ResContinuousGift getPlayerActivityDetail(Player player, ActivityData activityData, int detailId) {
        ResContinuousGift res = buildResponse(player, activityData);
        if (res.code == Code.SUCCESS) {
            res.activityData.removeIf(info -> info.detailId != detailId);
            if (res.activityData.isEmpty()) {
                res.code = Code.PARAM_ERROR;
            }
        }
        return res;
    }

    @Override
    public ContinuousGiftDetailInfo buildPlayerActivityDetail(Player player, ActivityData activityData, BaseCfgBean cfg, PlayerActivityData data) {
        ResContinuousGift res = getPlayerActivityDetail(player, activityData, cfg.getId());
        return res.code == Code.SUCCESS ? res.activityData.getFirst() : null;
    }

    private ResContinuousGift buildResponse(Player player, ActivityData activityData) {
        ResContinuousGift res = new ResContinuousGift(Code.SUCCESS);
        res.activityData = new ArrayList<>();
        if (!checkPlayerCanJoinActivity(player, activityData)) {
            return res;
        }
        try {
            Map<Integer, ConsecutivePackCfg> cfgMap = getDetailCfgBean(activityData);
            if (!validConfig(activityData, cfgMap)) {
                res.code = Code.SAMPLE_ERROR;
                return res;
            }
            res.endTime = activityData.getTimeEnd();
            res.activityData = buildDetails(player, activityData, cfgMap, readCurrentData(player.getId(), activityData));
        } catch (Exception e) {
            log.error("连续礼包活动查询失败 playerId:{}", player.getId(), e);
            res.code = Code.FAIL;
        }
        return res;
    }

    private List<ContinuousGiftDetailInfo> buildDetails(Player player, ActivityData activityData,
                                                        Map<Integer, ConsecutivePackCfg> cfgMap,
                                                        Map<Integer, ContinuousGiftPlayerData> playerData) {
        int nextId = nextPaidPack(cfgMap, playerData);
        long unlockTime = activityData.getTimeStart();
        for (ContinuousGiftPlayerData data : playerData.values()) {
            if (data.getPurchaseTime() > 0) {
                unlockTime = Math.max(unlockTime, TimeHelper.getTimestampByDay(TimeHelper.getDateZeroMilliTime(data.getPurchaseTime()), 1));
            }
        }
        List<ContinuousGiftDetailInfo> details = new ArrayList<>(cfgMap.size());
        long now = currentTimeMillis();
        for (ConsecutivePackCfg cfg : cfgMap.values()) {
            ContinuousGiftDetailInfo info = new ContinuousGiftDetailInfo();
            info.activityId = activityData.getId();
            info.detailId = cfg.getId();
            info.type = cfg.getType();
            info.rewardItems = ItemUtils.buildItemInfo(cfg.getGetitem());
            if (cfg.getType() == PAID_PACK) {
                info.rechargeInfo = SampleDataUtils.transRechargeCfgToRechargeInfo(cfg.getShopRechargeListID(), player.getChannel());
            } else {
                info.rechargeInfo = new RechargeInfo();
                info.rechargeInfo.type = RechargeCfgType.FREE.getType();
            }
            ContinuousGiftPlayerData data = playerData.get(cfg.getId());
            if (isClaimed(data)) {
                info.claimStatus = ActivityConstant.ClaimStatus.CLAIMED;
            } else if (cfg.getType() == PAID_PACK && isPurchased(data)) {
                info.claimStatus = ActivityConstant.ClaimStatus.CAN_CLAIM;
            } else if (cfg.getType() == PAID_PACK && cfg.getId() == nextId) {
                if (now >= unlockTime && unlockTime < activityData.getTimeEnd()) {
                    info.claimStatus = ActivityConstant.ClaimStatus.NOT_CLAIM;
                }
            } else if (cfg.getType() == FINAL_REWARD && nextId == 0) {
                info.claimStatus = ActivityConstant.ClaimStatus.CAN_CLAIM;
            }
            details.add(info);
        }
        return details;
    }

    private int nextPaidPack(Map<Integer, ConsecutivePackCfg> cfgMap, Map<Integer, ContinuousGiftPlayerData> playerData) {
        for (ConsecutivePackCfg cfg : cfgMap.values()) {
            if (cfg.getType() == PAID_PACK && !isPurchased(playerData.get(cfg.getId()))) {
                return cfg.getId();
            }
        }
        return 0;
    }

    private boolean isDailyLimited(Map<Integer, ContinuousGiftPlayerData> playerData, long now) {
        long dayStart = TimeHelper.getDateZeroMilliTime(now);
        return playerData.values().stream().anyMatch(data -> data.getPurchaseTime() > 0 && data.getPurchaseTime() >= dayStart);
    }

    private boolean isClaimed(ContinuousGiftPlayerData data) {
        return data != null && data.getClaimStatus() == ActivityConstant.ClaimStatus.CLAIMED;
    }

    private boolean isPurchased(ContinuousGiftPlayerData data) {
        return data != null && (data.getClaimStatus() == ActivityConstant.ClaimStatus.CAN_CLAIM || isClaimed(data));
    }

    private boolean validConfig(ActivityData activityData, Map<Integer, ConsecutivePackCfg> cfgMap) {
        if (cfgMap.isEmpty() || cfgMap.size() != activityData.getValue().size()) {
            return false;
        }
        boolean hasPaidPack = false;
        for (ConsecutivePackCfg cfg : cfgMap.values()) {
            if ((cfg.getType() != PAID_PACK && cfg.getType() != FINAL_REWARD) || CollectionUtil.isEmpty(cfg.getGetitem())
                    || cfg.getGetitem().entrySet().stream().anyMatch(item -> item.getKey() <= 0 || item.getValue() <= 0)) {
                return false;
            }
            hasPaidPack |= cfg.getType() == PAID_PACK;
        }
        return hasPaidPack;
    }

    private boolean isRunning(ActivityData activityData) {
        long now = currentTimeMillis();
        return activityData != null && activityData.getType() == ActivityType.CONTINUOUS_GIFT && activityData.canRun()
                && now >= activityData.getTimeStart() && now < activityData.getTimeEnd();
    }

    private Map<Integer, ContinuousGiftPlayerData> readCurrentData(long playerId, ActivityData activityData) {
        Map<Integer, ContinuousGiftPlayerData> data = playerActivityDao.getPlayerActivityDataOrThrow(
                playerId, ActivityType.CONTINUOUS_GIFT, activityData.getId());
        if (!data.isEmpty() && data.values().iterator().next().getRound() != activityData.getRound()) {
            if (!settlePaidRewards(playerId, activityData, data)) {
                throw new IllegalStateException("连续礼包上期已付款奖励未补发");
            }
            data.clear();
            if (!savePlayerData(playerId, activityData, data)) {
                throw new IllegalStateException("连续礼包上期数据清理失败");
            }
        }
        return data;
    }

    private boolean savePlayerData(long playerId, ActivityData activityData, Map<Integer, ContinuousGiftPlayerData> playerData) {
        return playerActivityDao.savePlayerActivityData(playerId, ActivityType.CONTINUOUS_GIFT, activityData.getId(), playerData);
    }

    private void rollbackReward(long playerId, ActivityData activityData, Map<Integer, ContinuousGiftPlayerData> playerData,
                                int detailId, ContinuousGiftPlayerData previous) {
        if (previous == null) {
            playerData.remove(detailId);
        } else {
            playerData.put(detailId, previous);
        }
        if (!savePlayerData(playerId, activityData, playerData)) {
            log.error("连续礼包发奖状态回滚失败 playerId:{} activityId:{} detailId:{}", playerId, activityData.getId(), detailId);
        }
    }

    private void notifyPurchase(Player player, ActivityData activityData) {
        try {
            activityManager.sendToPlayer(player.getId(), buildResponse(player, activityData));
            updateRodDot(player.getId(), activityData, false);
        } catch (Exception e) {
            log.error("连续礼包购买通知失败 playerId:{} activityId:{}", player.getId(), activityData.getId(), e);
        }
    }

    private void logReward(Player player, ActivityData activityData, ItemOperationResult result,
                           Map<Integer, Long> rewards, Object cost, int detailId) {
        try {
            activityLogger.sendActivityGift(player, activityData, result, rewards, cost, detailId);
        } catch (Exception e) {
            log.error("连续礼包发奖日志失败 playerId:{} detailId:{}", player.getId(), detailId, e);
        }
    }

    @Override
    public boolean hasRedDot(long playerId, ActivityData activityData) {
        Player player = corePlayerService.get(playerId);
        if (!checkPlayerCanJoinActivity(player, activityData)) {
            return false;
        }
        Map<Integer, ConsecutivePackCfg> cfgMap = getDetailCfgBean(activityData);
        Map<Integer, ContinuousGiftPlayerData> playerData = readCurrentData(playerId, activityData);
        if (!validConfig(activityData, cfgMap)) {
            return false;
        }
        boolean allPurchased = nextPaidPack(cfgMap, playerData) == 0;
        return cfgMap.values().stream().anyMatch(cfg -> !isClaimed(playerData.get(cfg.getId()))
                && (cfg.getType() == PAID_PACK ? isPurchased(playerData.get(cfg.getId())) : allPurchased));
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
        ActivityData activityData = player == null ? null : activityManager.getOpenActivityData(player, ActivityType.CONTINUOUS_GIFT);
        boolean available = activityData != null && hasRedDot(playerId, activityData);
        return List.of(redDotManager.buildRedDotDetails(getModule(), RED_DOT_SUBMODULE, available ? 1 : 0));
    }

    @Override
    public void onActivityEnd(ActivityData activityData) {
        super.onActivityEnd(activityData);
        long round = activityData.getRound();
        playerActivityDao.<ContinuousGiftPlayerData>clearActivityData(ActivityType.CONTINUOUS_GIFT, activityData.getId(),
                (playerId, playerData) -> playerData.values().stream().allMatch(data -> data.getRound() == round)
                        && settlePaidRewards(playerId, activityData, playerData));
    }

    @Override
    public void onPlayerLoginSuccess(PlayerController playerController, Player player, Account account, boolean firstLogin) {
        for (ActivityData activityData : activityManager.getActivityTypeData().getOrDefault(ActivityType.CONTINUOUS_GIFT, Map.of()).values()) {
            try {
                if (isRunning(activityData)) {
                    readCurrentData(player.getId(), activityData);
                } else {
                    Map<Integer, ContinuousGiftPlayerData> playerData = playerActivityDao.getPlayerActivityDataOrThrow(
                            player.getId(), ActivityType.CONTINUOUS_GIFT, activityData.getId());
                    if (!playerData.isEmpty() && settlePaidRewards(player.getId(), activityData, playerData)) {
                        playerActivityDao.deletePlayerActivityData(player.getId(), ActivityType.CONTINUOUS_GIFT, activityData.getId());
                    }
                }
            } catch (Exception e) {
                log.error("连续礼包登录补发失败 playerId:{} activityId:{}", player.getId(), activityData.getId(), e);
            }
        }
    }

    private boolean settlePaidRewards(long playerId, ActivityData activityData, Map<Integer, ContinuousGiftPlayerData> playerData) {
        long activityId = activityData.getId();
        for (Map.Entry<Integer, ContinuousGiftPlayerData> entry : playerData.entrySet()) {
            ContinuousGiftPlayerData data = entry.getValue();
            if (data.getClaimStatus() != ActivityConstant.ClaimStatus.CAN_CLAIM) {
                continue;
            }
            ConsecutivePackCfg cfg = GameDataManager.getConsecutivePackCfg(entry.getKey());
            if (cfg == null || cfg.getType() != PAID_PACK || CollectionUtil.isEmpty(cfg.getGetitem())) {
                log.error("连续礼包补发配置无效 playerId:{} activityId:{} round:{} detailId:{}",
                        playerId, activityId, data.getRound(), entry.getKey());
                return false;
            }
            if (mailService.addCfgMail(playerId, UNCLAIMED_REWARD_MAIL_ID, ItemUtils.buildItems(cfg.getGetitem()),
                    AddType.ACTIVITY_CONTINUOUS_GIFT_REWARDS, "order:" + data.getOrderId()) == null) {
                log.error("连续礼包补发邮件失败 playerId:{} activityId:{} detailId:{}", playerId, activityId, entry.getKey());
                return false;
            }
            data.setClaimStatus(ActivityConstant.ClaimStatus.CLAIMED);
            if (!savePlayerData(playerId, activityData, playerData)) {
                log.error("连续礼包补发状态保存失败 playerId:{} activityId:{} detailId:{}", playerId, activityId, entry.getKey());
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean onReceivedRecharge(Player player, PreGenerateOrderData preGenerateOrderData) {
        String[] parts = preGenerateOrderData.getProductId().split("_");
        if (parts.length != 3) {
            return false;
        }
        ActivityData activityData = activityManager.getActivityData().get(Long.parseLong(parts[0]));
        if (!checkPlayerCanJoinActivity(player, activityData) || activityData.getRound() != Long.parseLong(parts[1])) {
            return false;
        }
        int detailId = Integer.parseInt(parts[2]);
        ConsecutivePackCfg cfg = getDetailCfgBean(activityData).get(detailId);
        return cfg != null && completeNonMoneyPurchase(preGenerateOrderData,
                GameDataManager.getShopRechargeListCfg(cfg.getShopRechargeListID()),
                () -> receiveCurrentPurchase(player, activityData, detailId, null));
    }

    private boolean receiveCurrentPurchase(Player player, ActivityData activityData, int detailId, String orderId) {
        Map<Integer, ContinuousGiftPlayerData> playerData = readCurrentData(player.getId(), activityData);
        ContinuousGiftPlayerData previous = playerData.get(detailId);
        if (isPurchased(previous)) {
            return orderId != null && orderId.equals(previous.getOrderId());
        }
        Map<Integer, ConsecutivePackCfg> cfgMap = getDetailCfgBean(activityData);
        if (!validConfig(activityData, cfgMap) || detailId != nextPaidPack(cfgMap, playerData)) {
            return false;
        }
        long now = currentTimeMillis();
        if (isDailyLimited(playerData, now)) {
            return false;
        }
        ContinuousGiftPlayerData purchased = new ContinuousGiftPlayerData(activityData.getId(), activityData.getRound());
        purchased.setPurchaseTime(now);
        purchased.setOrderId(orderId);
        purchased.setClaimStatus(ActivityConstant.ClaimStatus.CAN_CLAIM);
        playerData.put(detailId, purchased);
        if (!savePlayerData(player.getId(), activityData, playerData)) {
            return false;
        }
        notifyPurchase(player, activityData);
        return true;
    }
}
