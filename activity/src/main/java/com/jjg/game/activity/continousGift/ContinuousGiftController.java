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
import com.jjg.game.core.base.reddot.IRedDotService;
import com.jjg.game.core.constant.AddType;
import com.jjg.game.core.constant.Code;
import com.jjg.game.core.data.*;
import com.jjg.game.core.listener.OrderGenerate;
import com.jjg.game.core.pb.RechargeCfgType;
import com.jjg.game.core.pb.RechargeInfo;
import com.jjg.game.core.pb.RechargeType;
import com.jjg.game.core.pb.ReqGenerateOrder;
import com.jjg.game.core.pb.reddot.RedDotDetails;
import com.jjg.game.core.utils.ItemUtils;
import com.jjg.game.core.utils.SampleDataUtils;
import com.jjg.game.sampledata.GameDataManager;
import com.jjg.game.sampledata.bean.BaseCfgBean;
import com.jjg.game.sampledata.bean.ConsecutivePackCfg;
import com.jjg.game.sampledata.bean.GameFunctionCfg;
import com.jjg.game.sampledata.bean.ShopRechargeListCfg;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ContinuousGiftController extends BaseActivityController implements OrderGenerate, IRedDotService {
    private static final int FUNCTION_ID = 6001;
    private static final int PAID_PACK = 1;
    private static final int FINAL_REWARD = 2;
    private static final int RED_DOT_SUBMODULE = 2;

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
            if (shop == null || shop.getType() != RechargeCfgType.MONEY.getType()
                    || shop.getPrice() == null || shop.getPrice().signum() <= 0
                    || StringUtils.isBlank(player.getChannel() == ChannelType.APPLE ? shop.getIosShopId() : shop.getGoogleShopId())) {
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
            if (boughtToday(playerData, System.currentTimeMillis())) {
                log.warn("连续礼包下单失败，今日已购买礼包 playerId:{} activityId:{} detailId:{}", player.getId(), activityData.getId(), detailId);
                result.code = Code.DAILY_LIMIT;
                return result;
            }
            String productId = activityData.getId() + "_" + activityData.getRound() + "_" + detailId;
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
            // 已付款订单按下单快照履约，过期或换期后不写入当前活动进度。
            boolean current = isRunning(activityData) && activityData.getRound() == round;
            Map<Integer, ContinuousGiftPlayerData> playerData = current ? readCurrentData(player.getId(), activityData) : null;
            if (current) {
                ContinuousGiftPlayerData previous = playerData.get(detailId);
                if (isClaimed(previous)) {
                    if (order.getId().equals(previous.getOrderId())) {
                        return true;
                    }
                    log.warn("连续礼包支付处理失败，宝箱已通过其他订单购买 playerId:{} orderId:{} detailId:{} purchasedOrderId:{}",
                            player.getId(), order.getId(), detailId, previous.getOrderId());
                    return false;
                }
                Map<Integer, ConsecutivePackCfg> cfgMap = getDetailCfgBean(activityData);
                if (!validConfig(activityData, cfgMap)) {
                    log.error("连续礼包支付处理失败，活动宝箱配置无效 playerId:{} orderId:{} activityId:{} detailIds:{}",
                            player.getId(), order.getId(), activityId, activityData.getValue());
                    return false;
                }
                int nextId = nextPaidPack(cfgMap, playerData);
                if (detailId != nextId) {
                    log.warn("连续礼包支付处理失败，未按顺序购买 playerId:{} orderId:{} activityId:{} detailId:{} nextId:{}",
                            player.getId(), order.getId(), activityId, detailId, nextId);
                    return false;
                }
                if (boughtToday(playerData, System.currentTimeMillis())) {
                    log.warn("连续礼包支付处理失败，今日已购买礼包 playerId:{} orderId:{} activityId:{} detailId:{}",
                            player.getId(), order.getId(), activityId, detailId);
                    return false;
                }
                ContinuousGiftPlayerData purchased = new ContinuousGiftPlayerData(activityId, round);
                purchased.setPurchaseTime(System.currentTimeMillis());
                purchased.setOrderId(order.getId());
                purchased.setClaimStatus(ActivityConstant.ClaimStatus.CLAIMED);
                playerData.put(detailId, purchased);
                if (!savePlayerData(player.getId(), activityData, playerData)) {
                    log.error("连续礼包支付处理失败，购买状态保存失败 playerId:{} orderId:{} activityId:{} detailId:{}",
                            player.getId(), order.getId(), activityId, detailId);
                    return false;
                }
            }
            CommonResult<ItemOperationResult> added;
            try {
                added = playerPackService.addItems(player.getId(), consecutivePackCfg.getGetitem(),
                        AddType.ACTIVITY_CONTINUOUS_GIFT_REWARDS, "order:" + order.getId());
            } catch (Exception e) {
                if (current) {
                    rollbackReward(player.getId(), activityData, playerData, detailId);
                }
                throw e;
            }
            if (!added.success()) {
                if (current) {
                    rollbackReward(player.getId(), activityData, playerData, detailId);
                }
                log.error("连续礼包支付处理失败，奖励发放失败 playerId:{} orderId:{} activityId:{} detailId:{} code:{}",
                        player.getId(), order.getId(), activityId, detailId, added.code);
                return false;
            }
            ActivityData logData = new ActivityData();
            logData.setId(activityId);
            logData.setRound(round);
            logData.setType(ActivityType.CONTINUOUS_GIFT);
            logReward(player, logData, added.data, consecutivePackCfg.getGetitem(), order.getPrice(), detailId);
            if (current) {
                notifyPurchase(player, activityData);
            }
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
            if (cfg == null || cfg.getType() != FINAL_REWARD) {
                res.code = Code.PARAM_ERROR;
                return res;
            }
            Map<Integer, ContinuousGiftPlayerData> playerData = readCurrentData(player.getId(), activityData);
            if (isClaimed(playerData.get(detailId))) {
                res.code = Code.REPEAT_OP;
                return res;
            }
            if (nextPaidPack(cfgMap, playerData) != 0) {
                return res;
            }
            ContinuousGiftPlayerData claimed = new ContinuousGiftPlayerData(activityData.getId(), activityData.getRound());
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
                rollbackReward(player.getId(), activityData, playerData, detailId);
                throw e;
            }
            if (!added.success()) {
                rollbackReward(player.getId(), activityData, playerData, detailId);
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
        long now = System.currentTimeMillis();
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
            if (isClaimed(playerData.get(cfg.getId()))) {
                info.claimStatus = ActivityConstant.ClaimStatus.CLAIMED;
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
            if (cfg.getType() == PAID_PACK && !isClaimed(playerData.get(cfg.getId()))) {
                return cfg.getId();
            }
        }
        return 0;
    }

    private boolean boughtToday(Map<Integer, ContinuousGiftPlayerData> playerData, long now) {
        return playerData.values().stream().anyMatch(data -> data.getPurchaseTime() > 0 && TimeHelper.inSameDay(data.getPurchaseTime(), now));
    }

    private boolean isClaimed(ContinuousGiftPlayerData data) {
        return data != null && data.getClaimStatus() == ActivityConstant.ClaimStatus.CLAIMED;
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
        long now = System.currentTimeMillis();
        return activityData != null && activityData.getType() == ActivityType.CONTINUOUS_GIFT && activityData.canRun()
                && now >= activityData.getTimeStart() && now < activityData.getTimeEnd();
    }

    private Map<Integer, ContinuousGiftPlayerData> readCurrentData(long playerId, ActivityData activityData) {
        Map<Integer, ContinuousGiftPlayerData> data = playerActivityDao.getPlayerActivityDataOrThrow(
                playerId, ActivityType.CONTINUOUS_GIFT, activityData.getId());
        data.values().removeIf(value -> value.getRound() != activityData.getRound());
        return data;
    }

    private boolean savePlayerData(long playerId, ActivityData activityData, Map<Integer, ContinuousGiftPlayerData> playerData) {
        return playerActivityDao.savePlayerActivityData(playerId, ActivityType.CONTINUOUS_GIFT, activityData.getId(), playerData);
    }

    private void rollbackReward(long playerId, ActivityData activityData, Map<Integer, ContinuousGiftPlayerData> playerData, int detailId) {
        playerData.remove(detailId);
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
        return validConfig(activityData, cfgMap) && nextPaidPack(cfgMap, playerData) == 0
                && cfgMap.values().stream().anyMatch(cfg -> cfg.getType() == FINAL_REWARD && !isClaimed(playerData.get(cfg.getId())));
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
                (playerId, playerData) -> playerData.values().stream().allMatch(data -> data.getRound() == round));
    }
}
