package com.jjg.game.core.listener;

import com.jjg.game.common.baselogic.IGameSysFuncInterface;
import com.jjg.game.core.constant.AddType;
import com.jjg.game.core.constant.Code;
import com.jjg.game.core.data.CommonResult;
import com.jjg.game.core.data.Order;
import com.jjg.game.core.data.Player;
import com.jjg.game.core.data.PreGenerateOrderData;
import com.jjg.game.core.pb.RechargeCfgType;
import com.jjg.game.core.pb.RechargeType;
import com.jjg.game.core.pb.ReqGenerateOrder;
import com.jjg.game.core.service.CorePlayerService;
import com.jjg.game.core.service.PlayerPackService;
import com.jjg.game.sampledata.GameDataManager;
import com.jjg.game.sampledata.bean.GlobalConfigCfg;
import com.jjg.game.sampledata.bean.ShopRechargeListCfg;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * @author lm
 * @date 2025/10/30 13:56
 */
public interface OrderGenerate extends IGameSysFuncInterface {
    /**
     * 获取对应渠道配置的商品品ID,以及配置价格
     *
     * @param player 玩家id
     * @param req    请求
     * @return 商品id,配置价格
     */
    CommonResult<PreGenerateOrderData> generateOrderDetailInfo(Player player, ReqGenerateOrder req);

    /**
     * 预下单业务校验成功、但订单最终未创建时的补偿回调。
     * 默认无需处理；存在限购预占等状态的业务可覆盖。
     */
    default void onOrderCreationFailed(Player player, ReqGenerateOrder req) {
    }

    /**
     * 获取对应充值类型
     *
     * @return 充值类型
     */
    RechargeType getRechargeType();


    /**
     * 收到充值的处理
     *
     * @param player 玩家数据
     * @param order  订单数据
     * @return `true` 表示处理成功，`false` 表示处理失败
     */
    boolean onReceivedRecharge(Player player, Order order);

    /**
     * 处理非充值购买，复用业务发奖或权益解锁逻辑，不创建充值订单。
     *
     * @param player 玩家数据
     * @param preGenerateOrderData 已通过业务校验的预下单数据
     * @return true 表示处理成功，false 表示处理失败
     */
    boolean onReceivedRecharge(Player player, PreGenerateOrderData preGenerateOrderData);

    /** 构建非充值购买数据，校验兑换消耗配置。 */
    default CommonResult<PreGenerateOrderData> nonMoneyOrderDetailInfo(ShopRechargeListCfg shop) {
        return nonMoneyOrderDetailInfo(shop, shop.getRedeem());
    }

    /** 允许业务提供实际兑换消耗，不修改共享配置。 */
    default CommonResult<PreGenerateOrderData> nonMoneyOrderDetailInfo(ShopRechargeListCfg shop, Map<Integer, Long> cost) {
        RechargeCfgType type = RechargeCfgType.valueOf(shop.getType());
        if (type == RechargeCfgType.MONEY || type == RechargeCfgType.ITEM_EXCHANGE
                && (cost == null || cost.isEmpty()
                || cost.entrySet().stream().anyMatch(item -> item.getKey() <= 0 || item.getValue() <= 0))) {
            return new CommonResult<>(Code.SAMPLE_ERROR);
        }
        PreGenerateOrderData data = new PreGenerateOrderData();
        data.setType(type);
        return new CommonResult<>(Code.SUCCESS, data);
    }

    /** 扣除兑换消耗并执行业务交付；交付失败时返还已扣道具。 */
    default boolean completeNonMoneyPurchase(PlayerPackService packs, Player player, PreGenerateOrderData data,
                                             ShopRechargeListCfg shop, AddType addType, BooleanSupplier receive) {
        if (shop != null && shop.getType() == RechargeCfgType.ITEM_EXCHANGE.getType()
                && (shop.getRedeem() == null || shop.getRedeem().isEmpty())) {
            return false;
        }
        return completeNonMoneyPurchase(packs, player, data, shop,
                shop == null ? null : shop.getRedeem(), addType, receive);
    }

    /** 实际背包消耗由业务解析，扣费及失败返还仍走公共流程。 */
    default boolean completeNonMoneyPurchase(PlayerPackService packs, Player player, PreGenerateOrderData data,
                                             ShopRechargeListCfg shop, Map<Integer, Long> exchangeCost,
                                             AddType addType, BooleanSupplier receive) {
        RechargeCfgType type = data.getType();
        if (shop == null || type == null || type == RechargeCfgType.MONEY || shop.getType() != type.getType()) {
            return false;
        }
        Map<Integer, Long> cost = type == RechargeCfgType.ITEM_EXCHANGE ? exchangeCost : Map.of();
        if (type == RechargeCfgType.ITEM_EXCHANGE && (cost == null
                || cost.entrySet().stream().anyMatch(item -> item.getKey() <= 0 || item.getValue() <= 0))) {
            return false;
        }
        if (!cost.isEmpty() && !packs.removeItems(player, cost, addType).success()) {
            return false;
        }
        boolean success = false;
        try {
            success = receive.getAsBoolean();
            return success;
        } finally {
            if (!success && !cost.isEmpty() && !packs.addItems(player.getId(), cost, AddType.FAIL_ROLLBACK).success()) {
                LoggerFactory.getLogger(getClass()).error("非充值购买失败后退还道具失败 playerId={},productId={},cost={}",
                        player.getId(), data.getProductId(), cost);
            }
        }
    }


    /**
     * 处理临时购买逻辑
     *
     * @param corePlayerService
     * @param player
     * @param globalCfgId
     * @param addType
     * @param round
     * @return
     */
    default int tmpOrderHandle(CorePlayerService corePlayerService, Player player, int globalCfgId, AddType addType, int round) {
        if (globalCfgId < 1) {
            return Code.SUCCESS;
        }

        GlobalConfigCfg globalCfg = GameDataManager.getGlobalConfigCfg(globalCfgId);
        if (globalCfg == null) {
            return Code.SUCCESS;
        }

        if (globalCfg.getLongValue() > 0) {
            return corePlayerService.deductDiamond(player.getId(), globalCfg.getLongValue(), addType, "tmp order buy", true).code;
        }

        if (StringUtils.isNotEmpty(globalCfg.getValue())) {
            String[] s = globalCfg.getValue().split("_");
            if (s.length < round) {
                return Code.SAMPLE_ERROR;
            }
            long num = Long.parseLong(s[round]);
            return corePlayerService.deductDiamond(player.getId(), num, addType, "tmp order buy " + round, true).code;
        }
        return Code.SUCCESS;
    }

}
