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
import com.jjg.game.core.listener.OrderGenerate;
import com.jjg.game.core.pb.RechargeType;
import com.jjg.game.core.pb.ReqGenerateOrder;
import com.jjg.game.core.service.MailService;
import com.jjg.game.core.utils.ItemUtils;
import com.jjg.game.sampledata.GameDataManager;
import com.jjg.game.sampledata.bean.BaseCfgBean;
import com.jjg.game.sampledata.bean.GameFunctionCfg;
import com.jjg.game.sampledata.bean.GoldTreasureCfg;
import com.jjg.game.sampledata.bean.ShopRechargeListCfg;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class GoldTreasureController extends BaseActivityController implements OrderGenerate, IPlayerLoginSuccess {
    private static final int FUNCTION_ID = 5018;
    private static final int UNCLAIMED_REWARD_MAIL_ID = 50;
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
        GoldTreasureCfg cfg = getDetailCfgBean(activityData).get(detailId);
        if (cfg == null) {
            res.code = Code.PARAM_ERROR;
            return res;
        }
        if (CollectionUtil.isEmpty(cfg.getGetItem())) {
            res.code = Code.SAMPLE_ERROR;
            return res;
        }
        long playerId = player.getId();
        try {
            Map<Integer, PlayerActivityData> playerData = loadCurrentData(player, activityData);
            PlayerActivityData previous = playerData.get(detailId);
            if (isClaimed(previous)) {
                res.code = Code.REPEAT_OP;
                return res;
            }
            boolean canClaim = cfg.getRewardType() == 1
                    ? ordinaryClaimable(cfg, playerData)
                    : cfg.getRewardType() == 2 && CollectionUtil.isNotEmpty(cfg.getCondition())
                    && playerPackService.checkHasItems(corePlayerService.get(playerId), cfg.getCondition());
            if (!canClaim) {
                return res;
            }

            PlayerActivityData claimed = new PlayerActivityData(activityData.getId(), activityData.getRound());
            claimed.setClaimStatus(ActivityConstant.ClaimStatus.CLAIMED);
            playerData.put(detailId, claimed);
            if (!savePlayerData(playerId, activityData, playerData)) {
                res.code = Code.FAIL;
                return res;
            }
            CommonResult<ItemOperationResult> added;
            try {
                added = playerPackService.addItems(playerId, cfg.getGetItem(), AddType.ACTIVITY_GOLD_TREASURE_REWARDS);
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
        return buildDetailInfo(player, activityData, cfg, playerData, claimableRewardIds(player, getDetailCfgBean(activityData), playerData));
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
        Map<Integer, GoldTreasureCfg> result = new LinkedHashMap<>(activityData.getValue().size());
        for (int detailId : activityData.getValue()) {
            GoldTreasureCfg cfg = GameDataManager.getGoldTreasureCfg(detailId);
            if (cfg != null) {
                result.put(detailId, cfg);
            }
        }
        return result;
    }

    @Override
    public CommonResult<BigDecimal> generateOrderDetailInfo(Player player, ReqGenerateOrder req) {
        CommonResult<BigDecimal> result = new CommonResult<>(Code.ERROR_REQ);
        ActivityData activityData = activityManager.getOpenActivityData(player, ActivityType.GOLD_TREASURE);
        if (!checkPlayerCanJoinActivity(player, activityData)) {
            return result;
        }
        int detailId;
        try {
            detailId = Integer.parseInt(req.productId);
        } catch (NumberFormatException e) {
            result.code = Code.PARAM_ERROR;
            return result;
        }
        GoldTreasureCfg cfg = getDetailCfgBean(activityData).get(detailId);
        if (cfg == null || cfg.getRewardType() != 1 || cfg.getPayId() <= 0) {
            result.code = Code.PARAM_ERROR;
            return result;
        }
        ShopRechargeListCfg shop = GameDataManager.getShopRechargeListCfg(cfg.getPayId());
        if (shop == null || shop.getPrice() == null || shop.getPrice().signum() <= 0
                || StringUtils.isBlank(channelProductId(player, shop)) || CollectionUtil.isEmpty(cfg.getGetItem())) {
            result.code = Code.SAMPLE_ERROR;
            return result;
        }
        try {
            Map<Integer, PlayerActivityData> playerData = readCurrentData(player.getId(), activityData);
            if (playerData.containsKey(detailId)) {
                result.code = Code.REPEAT_OP;
                return result;
            }
            if (!previousClaimed(cfg, playerData)) {
                return result;
            }
            result.code = Code.SUCCESS;
            result.data = shop.getPrice();
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
            if (cfg == null || cfg.getRewardType() != 1 || cfg.getPayId() <= 0) {
                return false;
            }
            return receivePayment(player, order, activityData, cfg);
        } catch (Exception e) {
            log.error("黄金宝藏支付处理失败 playerId:{} orderId:{}", player.getId(), order.getId(), e);
            return false;
        }
    }

    private boolean receivePayment(Player player, Order order, ActivityData activityData, GoldTreasureCfg cfg) {
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
            log.error("黄金宝藏支付后通知失败 playerId:{} orderId:{}", player.getId(), order.getId(), e);
        }
        return true;
    }

    @Override
    public boolean hasRedDot(long playerId, ActivityData activityData) {
        Player player = corePlayerService.get(playerId);
        if (!checkPlayerCanJoinActivity(player, activityData)) {
            return false;
        }
        return !claimableRewardIds(player, getDetailCfgBean(activityData), readCurrentData(playerId, activityData)).isEmpty();
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
        Set<Integer> claimableIds = claimableRewardIds(player, cfgMap, playerData);
        List<GoldTreasureDetailInfo> result = new ArrayList<>(cfgMap.size());
        for (GoldTreasureCfg cfg : cfgMap.values()) {
            result.add(buildDetailInfo(player, activityData, cfg, playerData, claimableIds));
        }
        return result;
    }

    private Set<Integer> claimableRewardIds(Player player, Map<Integer, GoldTreasureCfg> cfgMap,
                                          Map<Integer, PlayerActivityData> playerData) {
        Set<Integer> claimableIds = new HashSet<>();
        for (GoldTreasureCfg cfg : cfgMap.values()) {
            if (cfg.getRewardType() == 1 && ordinaryClaimable(cfg, playerData)) {
                claimableIds.add(cfg.getId());
            }
        }
        List<GoldTreasureCfg> cumulative = cfgMap.values().stream().filter(cfg -> cfg.getRewardType() == 2
                && !isClaimed(playerData.get(cfg.getId())) && CollectionUtil.isNotEmpty(cfg.getCondition())).toList();
        Set<Integer> satisfied = cumulative.isEmpty() ? Set.of() : playerPackService.findSatisfiedItemRequirements(player,
                cumulative.stream().map(GoldTreasureCfg::getCondition).toList());
        for (int index : satisfied) {
            claimableIds.add(cumulative.get(index).getId());
        }
        return claimableIds;
    }

    private GoldTreasureDetailInfo buildDetailInfo(Player player, ActivityData activityData, GoldTreasureCfg cfg,
                                                  Map<Integer, PlayerActivityData> playerData, Set<Integer> claimableIds) {
        GoldTreasureDetailInfo info = new GoldTreasureDetailInfo();
        info.activityId = activityData.getId();
        info.detailId = cfg.getId();
        info.rewardType = cfg.getRewardType();
        info.rewardItems = ItemUtils.buildItemInfo(cfg.getGetItem());
        info.condition = cfg.getCondition() == null ? List.of() : ItemUtils.buildItemInfo(cfg.getCondition());
        ShopRechargeListCfg shop = cfg.getPayId() <= 0 ? null : GameDataManager.getShopRechargeListCfg(cfg.getPayId());
        info.price = shop == null || shop.getPrice() == null ? "0" : shop.getPrice().toPlainString();
        info.channelProductId = shop == null ? "" : channelProductId(player, shop);
        if (isClaimed(playerData.get(cfg.getId()))) {
            info.claimStatus = ActivityConstant.ClaimStatus.CLAIMED;
        } else if (claimableIds.contains(cfg.getId())) {
            info.claimStatus = ActivityConstant.ClaimStatus.CAN_CLAIM;
        }
        return info;
    }

    private boolean ordinaryClaimable(GoldTreasureCfg cfg, Map<Integer, PlayerActivityData> playerData) {
        PlayerActivityData data = playerData.get(cfg.getId());
        return !isClaimed(data) && previousClaimed(cfg, playerData)
                && (cfg.getPayId() <= 0 || data != null && data.getClaimStatus() == ActivityConstant.ClaimStatus.CAN_CLAIM);
    }

    private boolean previousClaimed(GoldTreasureCfg cfg, Map<Integer, PlayerActivityData> playerData) {
        return cfg.getPreviousId() <= 0 || isClaimed(playerData.get(cfg.getPreviousId()));
    }

    private boolean isClaimed(PlayerActivityData data) {
        return data != null && data.getClaimStatus() == ActivityConstant.ClaimStatus.CLAIMED;
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
        Set<Integer> starItems = starItemIds(activityData);
        Map<Integer, GoldTreasureCfg> cfgMap = getDetailCfgBean(activityData);
        PlayerPack pack = playerPackService.getFromAllDB(player.getId());
        List<GoldTreasureCfg> claimableRewards = new ArrayList<>();
        for (GoldTreasureCfg cfg : cfgMap.values()) {
            boolean claimable = cfg.getRewardType() == 1 ? ordinaryClaimable(cfg, playerData)
                    : cfg.getRewardType() == 2 && !isClaimed(playerData.get(cfg.getId()))
                    && CollectionUtil.isNotEmpty(cfg.getCondition()) && pack != null
                    && pack.checkHasItems(ItemUtils.buildItems(cfg.getCondition()));
            if (claimable) {
                claimableRewards.add(cfg);
            }
        }
        for (GoldTreasureCfg cfg : claimableRewards) {
            if (!sendRewardMail(player.getId(), cfg, starItems)) {
                return false;
            }
            PlayerActivityData claimed = new PlayerActivityData(activityData.getId(), round);
            claimed.setClaimStatus(ActivityConstant.ClaimStatus.CLAIMED);
            playerData.put(cfg.getId(), claimed);
            if (!savePlayerData(player.getId(), activityData, playerData)) {
                return false;
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

    private Set<Integer> starItemIds(ActivityData activityData) {
        Set<Integer> result = new HashSet<>();
        for (GoldTreasureCfg cfg : getDetailCfgBean(activityData).values()) {
            if (cfg.getRewardType() == 2 && cfg.getCondition() != null) {
                result.addAll(cfg.getCondition().keySet());
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
