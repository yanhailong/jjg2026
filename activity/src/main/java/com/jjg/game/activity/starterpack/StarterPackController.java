package com.jjg.game.activity.starterpack;

import cn.hutool.core.collection.CollectionUtil;
import com.jjg.game.activity.common.controller.BaseActivityController;
import com.jjg.game.activity.common.data.ActivityData;
import com.jjg.game.activity.common.data.ActivityTargetType;
import com.jjg.game.activity.common.data.ActivityType;
import com.jjg.game.activity.common.data.PlayerActivityData;
import com.jjg.game.activity.common.message.bean.BaseActivityDetailInfo;
import com.jjg.game.activity.constant.ActivityConstant;
import com.jjg.game.activity.starterpack.data.StarterPackPlayerData;
import com.jjg.game.activity.starterpack.message.ResClaimStarterPackRewards;
import com.jjg.game.activity.starterpack.message.ResStarterPack;
import com.jjg.game.activity.starterpack.message.StarterPackDetailInfo;
import com.jjg.game.activity.starterpack.message.StarterPackRewardInfo;
import com.jjg.game.common.pb.AbstractResponse;
import com.jjg.game.core.constant.AddType;
import com.jjg.game.core.constant.Code;
import com.jjg.game.core.data.ChannelType;
import com.jjg.game.core.data.CommonResult;
import com.jjg.game.core.data.ItemOperationResult;
import com.jjg.game.core.data.Order;
import com.jjg.game.core.data.OrderStatus;
import com.jjg.game.core.data.Player;
import com.jjg.game.core.data.PreGenerateOrderData;
import com.jjg.game.core.listener.OrderGenerate;
import com.jjg.game.core.pb.RechargeCfgType;
import com.jjg.game.core.pb.RechargeType;
import com.jjg.game.core.pb.ReqGenerateOrder;
import com.jjg.game.core.service.PlayerStatService;
import com.jjg.game.core.utils.ItemUtils;
import com.jjg.game.core.utils.SampleDataUtils;
import com.jjg.game.sampledata.GameDataManager;
import com.jjg.game.sampledata.bean.BaseCfgBean;
import com.jjg.game.sampledata.bean.GameFunctionCfg;
import com.jjg.game.sampledata.bean.ShopRechargeListCfg;
import com.jjg.game.sampledata.bean.StarterPackCfg;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class StarterPackController extends BaseActivityController implements OrderGenerate {
    private static final int FUNCTION_ID = 5020;
    private static final int REWARD_DAYS = 3;
    private final PlayerStatService playerStatService;

    public StarterPackController(PlayerStatService playerStatService) {
        this.playerStatService = playerStatService;
    }

    @Override
    public boolean checkPlayerCanJoinActivity(Player player, ActivityData activityData) {
        GameFunctionCfg function = GameDataManager.getGameFunctionCfg(FUNCTION_ID);
        // 直接检查配置，避免GameFunctionService回调ActivityManager形成递归。
        return player != null && activityData != null && activityData.getType() == ActivityType.STARTER_PACK
                && activityData.canRun() && function != null && function.getIsOpen()
                && super.checkPlayerCanJoinActivity(player, activityData)
                && conditionManager.isAchievement(player, "", function.getCondition());
    }

    @Override
    public boolean addPlayerProgress(Player player, ActivityData activityData, long progress,
                                     long activityTargetKey, Object additionalParameters) {
        if ((activityTargetKey & ActivityTargetType.LOGIN.getTargetKey()) == 0
                || !checkPlayerCanJoinActivity(player, activityData)) {
            return false;
        }
        // Hall登录已记录；在线跨天复用同一按自然日去重的统计入口。
        playerStatService.recordLoginDay(player.getId());
        return hasRedDot(player.getId(), activityData);
    }

    @Override
    public Map<Integer, StarterPackCfg> getDetailCfgBean(ActivityData activityData) {
        if (activityData == null || activityData.getValue() == null || activityData.getValue().size() != 1) {
            return Map.of();
        }
        StarterPackCfg cfg = GameDataManager.getStarterPackCfg(activityData.getValue().getFirst());
        return cfg == null ? Map.of() : Map.of(cfg.getId(), cfg);
    }

    @Override
    public AbstractResponse joinActivity(Player player, ActivityData activityData, int detailId, int times) {
        // 购买资格只由已付款订单写入。
        return new ResClaimStarterPackRewards(Code.ERROR_REQ);
    }

    @Override
    public RechargeType getRechargeType() {
        return RechargeType.NEW_PLAYER_GIFT;
    }

    @Override
    public CommonResult<PreGenerateOrderData> generateOrderDetailInfo(Player player, ReqGenerateOrder req) {
        CommonResult<PreGenerateOrderData> result = new CommonResult<>(Code.PARAM_ERROR);
        try {
            BaseCfgBean bean = getOrderGenerateBean(player, req.productId);
            if (!(bean instanceof StarterPackCfg cfg)) {
                return result;
            }
            long activityId = Long.parseLong(req.productId.split("_")[0]);
            ActivityData activityData = activityManager.getActivityData().get(activityId);
            if (!validConfig(activityData, cfg)) {
                result.code = Code.SAMPLE_ERROR;
                return result;
            }
            if (!readPlayerData(player.getId(), activityData).isEmpty()) {
                result.code = Code.REPEAT_OP;
                return result;
            }
            ShopRechargeListCfg shop = getShopCfg(activityData);
            if (shop == null || shop.getType() != RechargeCfgType.MONEY.getType()
                    || shop.getPrice() == null || shop.getPrice().signum() <= 0
                    || StringUtils.isBlank(player.getChannel() == ChannelType.APPLE ? shop.getIosShopId() : shop.getGoogleShopId())) {
                result.code = Code.SAMPLE_ERROR;
                return result;
            }
            PreGenerateOrderData data = new PreGenerateOrderData();
            data.setType(RechargeCfgType.MONEY);
            data.setPrice(shop.getPrice());
            data.setOrderProductId(activityId + "_" + cfg.getId());
            result.data = data;
            result.code = Code.SUCCESS;
        } catch (NumberFormatException e) {
            result.code = Code.PARAM_ERROR;
        } catch (Exception e) {
            log.error("新手礼包下单失败 playerId:{} productId:{}", player.getId(), req.productId, e);
            result.code = Code.FAIL;
        }
        return result;
    }

    @Override
    public boolean onReceivedRecharge(Player player, Order order) {
        if (player == null || order.getPlayerId() != player.getId() || order.getRechargeType() != getRechargeType()
                || StringUtils.isBlank(order.getId()) || order.getOrderStatus() != OrderStatus.PROCESSING) {
            return false;
        }
        try {
            String[] parts = StringUtils.split(order.getProductId(), "_");
            if (parts == null || parts.length != 2) {
                return false;
            }
            long activityId = Long.parseLong(parts[0]);
            int detailId = Integer.parseInt(parts[1]);
            ActivityData activityData = activityManager.getActivityData().get(activityId);
            if (activityData == null || activityData.getType() != ActivityType.STARTER_PACK) {
                return false;
            }
            Map<Integer, StarterPackPlayerData> playerData = readPlayerData(player.getId(), activityData);
            StarterPackPlayerData previous = playerData.get(detailId);
            if (previous != null) {
                return order.getId().equals(previous.getOrderId());
            }
            StarterPackCfg cfg = getDetailCfgBean(activityData).get(detailId);
            if (!playerData.isEmpty() || !validConfig(activityData, cfg)) {
                return false;
            }
            // 已付款订单不重新检查当前功能开关、时间和参与条件。
            playerData.put(detailId, new StarterPackPlayerData(activityId, activityData.getRound(), order.getId()));
            if (!playerActivityDao.savePlayerActivityData(player.getId(), ActivityType.STARTER_PACK, activityId, playerData)) {
                return false;
            }
            try {
                activityManager.sendToPlayer(player.getId(), buildResponse(player, activityData));
                updateRodDot(player.getId(), activityData, false);
            } catch (Exception e) {
                log.error("新手礼包支付成功后通知失败 playerId:{} orderId:{}", player.getId(), order.getId(), e);
            }
            return true;
        } catch (Exception e) {
            log.error("新手礼包支付处理失败 playerId:{} orderId:{}", player.getId(), order.getId(), e);
            return false;
        }
    }

    @Override
    public ResClaimStarterPackRewards claimActivityRewards(Player player, ActivityData activityData, int detailId) {
        ResClaimStarterPackRewards res = new ResClaimStarterPackRewards(Code.ERROR_REQ);
        res.activityId = activityData.getId();
        res.detailId = detailId;
        if (!checkPlayerCanJoinActivity(player, activityData)) {
            return res;
        }
        StarterPackCfg cfg = getDetailCfgBean(activityData).get(detailId);
        if (!validConfig(activityData, cfg)) {
            res.code = Code.SAMPLE_ERROR;
            return res;
        }
        try {
            return claimRewards(player, activityData, cfg, res);
        } catch (Exception e) {
            log.error("新手礼包领奖失败 playerId:{} activityId:{} detailId:{}", player.getId(), activityData.getId(), detailId, e);
            res.code = Code.FAIL;
            return res;
        }
    }

    private ResClaimStarterPackRewards claimRewards(Player player, ActivityData activityData, StarterPackCfg cfg,
                                                     ResClaimStarterPackRewards res) {
        Map<Integer, StarterPackPlayerData> playerData = readPlayerData(player.getId(), activityData);
        StarterPackPlayerData data = playerData.get(cfg.getId());
        if (data == null) {
            return res;
        }
        int unlockedDays = unlockedDays(player.getId());
        int claimedDays = data.getClaimedDays();
        if (claimedDays >= unlockedDays) {
            res.code = Code.REPEAT_OP;
            return res;
        }
        Map<Integer, Long> rewards = new HashMap<>();
        List<Map<Integer, Long>> rewardMaps = rewardMaps(cfg);
        for (int i = claimedDays; i < unlockedDays; i++) {
            ItemUtils.mergeItems(rewards, rewardMaps.get(i));
        }
        CommonResult<ItemOperationResult> added = playerPackService.addItems(
                player.getId(), rewards, AddType.ACTIVITY_STARTER_PACK_REWARDS);
        if (!added.success()) {
            res.code = added.code;
            return res;
        }
        data.setClaimedDays(unlockedDays);
        if (!playerActivityDao.savePlayerActivityData(player.getId(), ActivityType.STARTER_PACK, activityData.getId(), playerData)) {
            log.error("新手礼包奖励已发放但领取状态保存失败 playerId:{} activityId:{} orderId:{} claimedDays:{} unlockedDays:{}",
                    player.getId(), activityData.getId(), data.getOrderId(), claimedDays, unlockedDays);
            res.code = Code.FAIL;
            return res;
        }
        res.code = Code.SUCCESS;
        res.items = ItemUtils.buildItemInfo(rewards);
        try {
            res.detailInfo = buildDetail(player, activityData, cfg, data, unlockedDays);
            activityLogger.sendActivityGift(player, activityData, added.data, rewards, null, cfg.getId());
        } catch (Exception e) {
            log.error("新手礼包领奖后通知或日志失败 playerId:{} activityId:{}", player.getId(), activityData.getId(), e);
        }
        return res;
    }

    @Override
    public ResStarterPack getPlayerActivityInfoByType(Player player, ActivityType activityType) {
        return activityType == ActivityType.STARTER_PACK
                ? buildResponse(player, activityManager.getOpenActivityData(player, activityType))
                : new ResStarterPack(Code.PARAM_ERROR);
    }

    @Override
    public ResStarterPack getPlayerActivityInfoByTypeRes(Player player, Map<Long, List<BaseActivityDetailInfo>> allDetailInfo) {
        return getPlayerActivityInfoByType(player, ActivityType.STARTER_PACK);
    }

    @Override
    public ResStarterPack getPlayerActivityDetail(Player player, ActivityData activityData, int detailId) {
        if (!getDetailCfgBean(activityData).containsKey(detailId)) {
            return new ResStarterPack(Code.PARAM_ERROR);
        }
        return buildResponse(player, activityData);
    }

    @Override
    public StarterPackDetailInfo buildPlayerActivityDetail(Player player, ActivityData activityData,
                                                           BaseCfgBean cfg, PlayerActivityData data) {
        return cfg instanceof StarterPackCfg starterCfg && validConfig(activityData, starterCfg)
                ? buildDetail(player, activityData, starterCfg, (StarterPackPlayerData) data, unlockedDays(player.getId())) : null;
    }

    private ResStarterPack buildResponse(Player player, ActivityData activityData) {
        ResStarterPack res = new ResStarterPack(Code.SUCCESS);
        if (!checkPlayerCanJoinActivity(player, activityData)) {
            return res;
        }
        try {
            Map<Integer, StarterPackCfg> configs = getDetailCfgBean(activityData);
            if (configs.isEmpty()) {
                res.code = Code.SAMPLE_ERROR;
                return res;
            }
            StarterPackCfg cfg = configs.values().iterator().next();
            if (!validConfig(activityData, cfg)) {
                res.code = Code.SAMPLE_ERROR;
                return res;
            }
            StarterPackPlayerData data = readPlayerData(player.getId(), activityData).get(cfg.getId());
            res.activityData = List.of(buildDetail(player, activityData, cfg, data, unlockedDays(player.getId())));
        } catch (Exception e) {
            log.error("新手礼包查询失败 playerId:{} activityId:{}", player.getId(), activityData.getId(), e);
            res.code = Code.FAIL;
        }
        return res;
    }

    private StarterPackDetailInfo buildDetail(Player player, ActivityData activityData, StarterPackCfg cfg,
                                              StarterPackPlayerData data, int unlockedDays) {
        StarterPackDetailInfo info = new StarterPackDetailInfo();
        info.activityId = activityData.getId();
        info.detailId = cfg.getId();
        info.loginDays = unlockedDays;
        info.originalPrice = activityData.getBigDecimalParam().get(0).toPlainString();
        info.bestValuePrice = activityData.getBigDecimalParam().get(1).toPlainString();
        ShopRechargeListCfg shop = getShopCfg(activityData);
        if (shop != null) {
            info.rechargeInfo = SampleDataUtils.transRechargeCfgToRechargeInfo(shop.getId(), player.getChannel());
        }
        int claimedDays = data == null ? 0 : data.getClaimedDays();
        info.claimStatus = data == null ? ActivityConstant.ClaimStatus.NOT_CLAIM : ActivityConstant.ClaimStatus.PURCHASED;
        info.rewards = new ArrayList<>(REWARD_DAYS);
        List<Map<Integer, Long>> rewardMaps = rewardMaps(cfg);
        for (int day = 1; day <= REWARD_DAYS; day++) {
            StarterPackRewardInfo reward = new StarterPackRewardInfo();
            reward.day = day;
            reward.rewardItems = ItemUtils.buildItemInfo(rewardMaps.get(day - 1));
            reward.claimStatus = day <= claimedDays ? ActivityConstant.ClaimStatus.CLAIMED
                    : data != null && day <= unlockedDays ? ActivityConstant.ClaimStatus.CAN_CLAIM : ActivityConstant.ClaimStatus.NOT_CLAIM;
            info.rewards.add(reward);
        }
        return info;
    }

    @Override
    public boolean hasRedDot(long playerId, ActivityData activityData) {
        Map<Integer, StarterPackPlayerData> playerData = readPlayerData(playerId, activityData);
        if (playerData.isEmpty()) {
            return false;
        }
        int unlockedDays = unlockedDays(playerId);
        return playerData.values().stream().anyMatch(data -> data.getClaimedDays() < unlockedDays);
    }

    private int unlockedDays(long playerId) {
        return (int) Math.min(REWARD_DAYS, playerStatService.getLoginDays(playerId));
    }

    private Map<Integer, StarterPackPlayerData> readPlayerData(long playerId, ActivityData activityData) {
        return playerActivityDao.getPlayerActivityDataOrThrow(playerId, ActivityType.STARTER_PACK, activityData.getId());
    }

    private List<Map<Integer, Long>> rewardMaps(StarterPackCfg cfg) {
        return List.of(cfg.getReward1(), cfg.getReward2(), cfg.getReward3());
    }

    private boolean validConfig(ActivityData activityData, StarterPackCfg cfg) {
        if (cfg == null || cfg.getReward1() == null || cfg.getReward2() == null || cfg.getReward3() == null
                || activityData.getBigDecimalParam() == null || activityData.getBigDecimalParam().size() < 2
                || activityData.getBigDecimalParam().get(0) == null || activityData.getBigDecimalParam().get(1) == null) {
            return false;
        }
        return rewardMaps(cfg).stream().allMatch(rewards -> CollectionUtil.isNotEmpty(rewards)
                && rewards.entrySet().stream().allMatch(item -> item.getKey() > 0 && item.getValue() > 0));
    }
}
