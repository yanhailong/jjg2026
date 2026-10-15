package com.jjg.game.activity.noads;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.jjg.game.activity.common.controller.BaseActivityController;
import com.jjg.game.activity.common.data.ActivityData;
import com.jjg.game.activity.common.data.ActivityType;
import com.jjg.game.activity.common.data.PlayerActivityData;
import com.jjg.game.activity.common.message.bean.BaseActivityDetailInfo;
import com.jjg.game.activity.constant.ActivityConstant;
import com.jjg.game.activity.noads.data.NoAdsPlayerData;
import com.jjg.game.activity.noads.message.NoAdsDetailInfo;
import com.jjg.game.activity.noads.message.ResNoAds;
import com.jjg.game.common.pb.AbstractResponse;
import com.jjg.game.common.pb.ItemInfo;
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
import com.jjg.game.core.listener.SimNoAdsListener;
import com.jjg.game.core.pb.NotifyOpenFunction;
import com.jjg.game.core.pb.RechargeCfgType;
import com.jjg.game.core.pb.RechargeType;
import com.jjg.game.core.pb.ReqGenerateOrder;
import com.jjg.game.core.utils.SampleDataUtils;
import com.jjg.game.sampledata.GameDataManager;
import com.jjg.game.sampledata.bean.ActivityConfigCfg;
import com.jjg.game.sampledata.bean.BaseCfgBean;
import com.jjg.game.sampledata.bean.GameFunctionCfg;
import com.jjg.game.sampledata.bean.ShopRechargeListCfg;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class NoAdsController extends BaseActivityController implements OrderGenerate {
    private static final int FUNCTION_ID = 5019;
    // 本活动只有一个永久礼包，value[0]是折扣时长，不能作为子项ID。
    private static final int PURCHASE_KEY = 0;
    private final List<SimNoAdsListener> simNoAdsListeners;

    public NoAdsController(List<SimNoAdsListener> simNoAdsListeners) {
        this.simNoAdsListeners = simNoAdsListeners;
    }

    @Override
    public boolean checkPlayerCanJoinActivity(Player player, ActivityData activityData) {
        return canViewActivity(player, activityData) && !noads(player.getId());
    }

    private boolean canViewActivity(Player player, ActivityData activityData) {
        GameFunctionCfg function = GameDataManager.getGameFunctionCfg(FUNCTION_ID);
        // 直接检查配置及权益，避免GameFunctionService回调活动资格形成递归。
        return player != null && activityData != null && activityData.getType() == ActivityType.NO_ADS
                && activityData.canRun() && function != null && function.getIsOpen()
                && !simNoAdsListeners.isEmpty()
                && super.checkPlayerCanJoinActivity(player, activityData)
                && conditionManager.isAchievement(player, "", function.getCondition());
    }

    @Override
    public AbstractResponse joinActivity(Player player, ActivityData activityData, int detailId, int times) {
        return new ResNoAds(Code.ERROR_REQ);
    }

    @Override
    public AbstractResponse claimActivityRewards(Player player, ActivityData activityData, int detailId) {
        return new ResNoAds(Code.ERROR_REQ);
    }

    @Override
    public BaseActivityDetailInfo buildPlayerActivityDetail(Player player, ActivityData activityData, BaseCfgBean baseCfgBean, PlayerActivityData data) {
        return buildDetail(player, activityData);
    }

    @Override
    public AbstractResponse getPlayerActivityDetail(Player player, ActivityData activityData, int detailId) {
        return buildResponse(player, activityData);
    }

    @Override
    public ResNoAds getPlayerActivityInfoByType(Player player, ActivityType activityType) {
        if (activityType != ActivityType.NO_ADS) {
            return new ResNoAds(Code.PARAM_ERROR);
        }
        // 查询保留已购买状态，不能复用会排除已购玩家的下单资格筛选。
        ActivityData activityData = activityManager.getActivityTypeData().getOrDefault(activityType, Map.of())
                .values().stream().filter(data -> canViewActivity(player, data))
                .max(Comparator.comparingLong(ActivityData::getTimeStart)).orElse(null);
        return buildResponse(player, activityData);
    }

    @Override
    public AbstractResponse getPlayerActivityInfoByTypeRes(Player player, Map<Long, List<BaseActivityDetailInfo>> allDetailInfo) {
        return getPlayerActivityInfoByType(player, ActivityType.NO_ADS);
    }

    @Override
    public Map<Integer, ? extends BaseCfgBean> getDetailCfgBean(ActivityData activityData) {
        return Map.of();
    }

    @Override
    public boolean hasRedDot(long playerId, ActivityData activityData) {
        // 入口及折扣由活动协议显示，没有待领取奖励，无需查询玩家活动Redis数据。
        return false;
    }

    @Override
    public CommonResult<PreGenerateOrderData> generateOrderDetailInfo(Player player, ReqGenerateOrder req) {
        CommonResult<PreGenerateOrderData> result = new CommonResult<>(Code.ERROR_REQ);
        try {
            ActivityData activityData = activityManager.getOpenActivityData(player, ActivityType.NO_ADS);
            if (activityData == null) {
                log.warn("免广告礼包下单失败，活动或功能未开放、条件未满足或已购买 playerId={}", player.getId());
                return result;
            }
            long activityId = activityData.getId();
            if (!validConfig(player, activityData)) {
                result.code = Code.SAMPLE_ERROR;
                log.warn("免广告礼包下单失败，活动奖励或充值配置无效 playerId={},activityId={}", player.getId(), activityId);
                return result;
            }
            ShopRechargeListCfg shop = getShopCfg(activityData, System.currentTimeMillis() < discountEndTime(activityData) ? 0 : 1);
            if (!playerActivityDao.getPlayerActivityDataOrThrow(player.getId(), ActivityType.NO_ADS, activityId).isEmpty()) {
                result.code = Code.REPEAT_OP;
                return result;
            }
            PreGenerateOrderData data = new PreGenerateOrderData();
            data.setType(RechargeCfgType.MONEY);
            data.setPrice(shop.getPrice());
            // 订单内部记录服务器选择的活动与商品，充值回调不依赖客户端productId。
            data.setOrderProductId(activityId + "_" + shop.getId());
            // 通用订单链使用req.desc落库；仅保存服务器生成的赠品快照，不信任客户端备注。
            req.desc = JSON.toJSONString(Map.of("rewards", rewards(activityData)));
            result.data = data;
            result.code = Code.SUCCESS;
        } catch (Exception e) {
            log.error("免广告礼包下单异常 playerId={}", player.getId(), e);
            result.code = Code.FAIL;
        }
        return result;
    }

    @Override
    public RechargeType getRechargeType() {
        return RechargeType.NO_ADS;
    }

    @Override
    public boolean onReceivedRecharge(Player player, Order order) {
        if (player == null || order == null || order.getPlayerId() != player.getId()
                || order.getRechargeType() != getRechargeType() || StringUtils.isBlank(order.getId())
                || order.getOrderStatus() != OrderStatus.PROCESSING) {
            return false;
        }
        try {
            String[] parts = order.getProductId() == null ? new String[0] : order.getProductId().split("_", -1);
            if (parts.length != 2 || Integer.parseInt(parts[1]) <= 0) {
                return false;
            }
            long activityId = Long.parseLong(parts[0]);
            ActivityData activityData = activityManager.getActivityData().get(activityId);
            if (activityData == null || activityData.getType() != ActivityType.NO_ADS) {
                log.warn("免广告礼包充值失败，活动不存在或类型错误 playerId={},orderId={},activityId={}", player.getId(), order.getId(), activityId);
                return false;
            }
            if (noads(player.getId())) {
                return true;
            }
            Map<Integer, NoAdsPlayerData> playerData = playerActivityDao.getPlayerActivityDataOrThrow(player.getId(), ActivityType.NO_ADS, activityId);
            NoAdsPlayerData previous = playerData.get(PURCHASE_KEY);
            if (previous != null && !order.getId().equals(previous.getOrderId())) {
                log.warn("免广告礼包充值失败，已有其他订单的发货记录 playerId={},orderId={},previousOrderId={}", player.getId(), order.getId(), previous.getOrderId());
                return false;
            }
            if (previous == null) {
                JSONObject snapshot = JSON.parseObject(order.getDesc());
                Map<Integer, Long> rewardItems = snapshot == null ? null : snapshot.getObject("rewards", new TypeReference<Map<Integer, Long>>() {});
                if (rewardItems == null || rewardItems.entrySet().stream().anyMatch(item -> item.getKey() <= 0 || item.getValue() == null || item.getValue() <= 0)) {
                    log.warn("免广告礼包充值失败，订单赠品快照无效 playerId={},orderId={}", player.getId(), order.getId());
                    return false;
                }
                CommonResult<ItemOperationResult> added = playerPackService.addItems(player.getId(), rewardItems, AddType.ACTIVITY_NO_ADS_REWARDS, order.getId());
                if (!added.success()) {
                    log.warn("免广告礼包赠品发放失败 playerId={},orderId={},code={}", player.getId(), order.getId(), added.code);
                    return false;
                }
                playerData.put(PURCHASE_KEY, new NoAdsPlayerData(activityId, activityData.getRound(), order.getId()));
                if (!playerActivityDao.savePlayerActivityData(player.getId(), ActivityType.NO_ADS, activityId, playerData)) {
                    log.error("免广告礼包赠品已发放但记录保存失败 playerId={},orderId={}", player.getId(), order.getId());
                    return false;
                }
                try {
                    activityLogger.sendActivityGift(player, activityData, added.data, rewardItems, null, PURCHASE_KEY);
                } catch (Exception e) {
                    log.error("免广告礼包赠品日志失败 playerId={},orderId={}", player.getId(), order.getId(), e);
                }
            }
            // 付款后的交付不再检查当前折扣、功能开关和活动时间；SIM负责权益及落库。
            if (!simNoAdsListeners.stream().anyMatch(listener -> listener.enableNoads(player.getId()))) {
                log.warn("免广告礼包权益开启失败 playerId={},orderId={}", player.getId(), order.getId());
                return false;
            }
            try {
                ResNoAds res = new ResNoAds(Code.SUCCESS);
                res.activityData = List.of(buildDetail(player, activityData));
                activityManager.sendToPlayer(player.getId(), res);
                NotifyOpenFunction notify = new NotifyOpenFunction();
                notify.closeFunctionIdList = List.of(FUNCTION_ID);
                activityManager.sendToPlayer(player.getId(), notify);
            } catch (Exception e) {
                log.error("免广告礼包购买成功后推送失败 playerId={},orderId={}", player.getId(), order.getId(), e);
            }
            return true;
        } catch (Exception e) {
            log.error("免广告礼包充值处理异常 playerId={},orderId={}", player.getId(), order.getId(), e);
            return false;
        }
    }

    private ResNoAds buildResponse(Player player, ActivityData activityData) {
        ResNoAds res = new ResNoAds(Code.SUCCESS);
        if (!canViewActivity(player, activityData)) {
            return res;
        }
        if (!validConfig(player, activityData)) {
            res.code = Code.SAMPLE_ERROR;
            return res;
        }
        res.activityData = List.of(buildDetail(player, activityData));
        return res;
    }

    private NoAdsDetailInfo buildDetail(Player player, ActivityData activityData) {
        NoAdsDetailInfo info = new NoAdsDetailInfo();
        info.activityId = activityData.getId();
        if (noads(player.getId())) {
            info.claimStatus = ActivityConstant.ClaimStatus.PURCHASED;
        } else {
            PlayerActivityData purchased = playerActivityDao.getPlayerActivityDataOrThrow(
                    player.getId(), ActivityType.NO_ADS, activityData.getId()).get(PURCHASE_KEY);
            if (purchased != null) {
                info.claimStatus = purchased.getClaimStatus();
            }
        }
        info.discountEndTime = discountEndTime(activityData);
        info.discountPercent = activityData.getBigDecimalParam().getFirst().toPlainString();
        ShopRechargeListCfg discount = getShopCfg(activityData, 0);
        ShopRechargeListCfg original = getShopCfg(activityData, 1);
        ShopRechargeListCfg current = System.currentTimeMillis() < info.discountEndTime ? discount : original;
        info.detailId = current.getId();
        info.discountPrice = discount.getPrice().toPlainString();
        info.originalPrice = original.getPrice().toPlainString();
        info.rechargeInfo = SampleDataUtils.transRechargeCfgToRechargeInfo(current.getId(), player.getChannel());
        List<Long> values = activityData.getValueParam();
        info.items = new ArrayList<>(values.size() / 2);
        for (int i = 0; i < values.size(); i += 2) {
            ItemInfo item = new ItemInfo();
            item.itemId = values.get(i).intValue();
            item.count = values.get(i + 1);
            info.items.add(item);
        }
        return info;
    }

    private boolean noads(long playerId) {
        return simNoAdsListeners.stream().anyMatch(listener -> listener.noads(playerId));
    }

    private long discountEndTime(ActivityData activityData) {
        return activityData.getTimeStart() + activityData.getValue().getFirst() * 1000L;
    }

    private ShopRechargeListCfg getShopCfg(ActivityData activityData, int index) {
        ActivityConfigCfg cfg = GameDataManager.getActivityConfigCfg((int) activityData.getId());
        return cfg == null || cfg.getChannelCommodity() == null || cfg.getChannelCommodity().size() <= index
                ? null : GameDataManager.getShopRechargeListCfg(cfg.getChannelCommodity().get(index));
    }

    private boolean validConfig(Player player, ActivityData activityData) {
        if (activityData.getValue() == null || activityData.getValue().isEmpty() || activityData.getValue().getFirst() < 0
                || activityData.getBigDecimalParam() == null || activityData.getBigDecimalParam().isEmpty()
                || activityData.getBigDecimalParam().getFirst() == null || activityData.getValueParam() == null
                || activityData.getValueParam().isEmpty() || activityData.getValueParam().size() % 2 != 0) {
            return false;
        }
        List<Long> values = activityData.getValueParam();
        for (int i = 0; i < values.size(); i += 2) {
            if (values.get(i) <= 0 || values.get(i) > Integer.MAX_VALUE || values.get(i + 1) <= 0) {
                return false;
            }
        }
        for (int i = 0; i < 2; i++) {
            ShopRechargeListCfg shop = getShopCfg(activityData, i);
            if (shop == null || shop.getType() != RechargeCfgType.MONEY.getType()
                    || shop.getPrice() == null || shop.getPrice().signum() <= 0
                    || StringUtils.isBlank(player.getChannel() == ChannelType.APPLE ? shop.getIosShopId() : shop.getGoogleShopId())) {
                return false;
            }
        }
        return true;
    }

    private Map<Integer, Long> rewards(ActivityData activityData) {
        Map<Integer, Long> result = new HashMap<>();
        List<Long> values = activityData.getValueParam();
        for (int i = 0; i < values.size(); i += 2) {
            result.merge(values.get(i).intValue(), values.get(i + 1), Long::sum);
        }
        return result;
    }
}
