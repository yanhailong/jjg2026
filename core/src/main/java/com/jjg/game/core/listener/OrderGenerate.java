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
import com.jjg.game.core.utils.ItemUtils;
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
                || cost.entrySet().stream().anyMatch(item -> item.getKey() == null || item.getKey() <= 0
                || item.getValue() == null || item.getValue() <= 0))) {
            return new CommonResult<>(Code.SAMPLE_ERROR);
        }
        PreGenerateOrderData data = new PreGenerateOrderData();
        data.setType(type);
        return new CommonResult<>(Code.SUCCESS, data);
    }

    /** 一次性权益只允许兑换一份。 */
    default CommonResult<PreGenerateOrderData> singleExchangeOrderDetailInfo(PlayerPackService packs, Player player,
                                                                           ShopRechargeListCfg shop, int buyCount, AddType addType) {
        if (buyCount > 1) {
            LoggerFactory.getLogger(getClass()).warn("一次性权益不允许多份兑换 playerId={},shopId={},buyCount={}",
                    player.getId(), shop.getId(), buyCount);
            return new CommonResult<>(Code.PARAM_ERROR);
        }
        return exchangeOrderDetailInfo(packs, player, shop, buyCount, addType);
    }

    /** 预下单时扣除兑换消耗，扣费错误码直接返回客户端。 */
    default CommonResult<PreGenerateOrderData> exchangeOrderDetailInfo(PlayerPackService packs, Player player,
                                                                     ShopRechargeListCfg shop, int buyCount, AddType addType) {
        CommonResult<PreGenerateOrderData> result = exchangeOrderDetailInfo(shop, shop.getRedeem(), buyCount);
        if (!result.success()) {
            LoggerFactory.getLogger(getClass()).warn("道具兑换消耗配置无效或数量溢出 playerId={},shopId={},buyCount={},cost={},code={}",
                    player.getId(), shop.getId(), buyCount, shop.getRedeem(), result.code);
            return result;
        }
        int code = packs.removeItems(player, result.data.getExchangeCost(), addType).code;
        if (code != Code.SUCCESS) {
            LoggerFactory.getLogger(getClass()).warn("道具兑换扣费失败 playerId={},shopId={},buyCount={},cost={},code={}",
                    player.getId(), shop.getId(), result.data.getBuyCount(), result.data.getExchangeCost(), code);
            return new CommonResult<>(code);
        }
        return result;
    }

    /** 特殊货币由所属业务扣除；这里只计算本次兑换份数和消耗快照。 */
    default CommonResult<PreGenerateOrderData> exchangeOrderDetailInfo(ShopRechargeListCfg shop, Map<Integer, Long> cost, int buyCount) {
        CommonResult<PreGenerateOrderData> result = nonMoneyOrderDetailInfo(shop, cost);
        if (!result.success() || shop.getType() != RechargeCfgType.ITEM_EXCHANGE.getType()) {
            return new CommonResult<>(Code.SAMPLE_ERROR);
        }
        try {
            result.data.setBuyCount(Math.max(1, buyCount));
            result.data.setExchangeCost(Map.copyOf(ItemUtils.multiplyItems(cost, result.data.getBuyCount())));
            return result;
        } catch (ArithmeticException e) {
            LoggerFactory.getLogger(getClass()).warn("道具兑换消耗数量溢出 shopId={},buyCount={},cost={}", shop.getId(), buyCount, cost);
            return new CommonResult<>(Code.PARAM_ERROR);
        }
    }

    /** 交付只校验支付类型和预扣结果，不再次扣费；失败返还由通用下单入口收口。 */
    default boolean completeNonMoneyPurchase(PreGenerateOrderData data, ShopRechargeListCfg shop, BooleanSupplier receive) {
        RechargeCfgType type = data.getType();
        if (shop == null || type == null || type == RechargeCfgType.MONEY || shop.getType() != type.getType()
                || type == RechargeCfgType.ITEM_EXCHANGE && (data.getExchangeCost() == null || data.getExchangeCost().isEmpty())) {
            LoggerFactory.getLogger(getClass()).warn("非充值交付校验失败，支付类型不匹配或缺少预扣消耗 productId={},type={},shopId={},shopType={},buyCount={},cost={}",
                    data.getProductId(), type, shop == null ? null : shop.getId(), shop == null ? null : shop.getType(),
                    data.getBuyCount(), data.getExchangeCost());
            return false;
        }
        return receive.getAsBoolean();
    }

    default void refundNonMoneyPurchase(PlayerPackService packs, Player player, PreGenerateOrderData data) {
        refundNonMoneyPurchase(packs, player, data, data.getExchangeCost());
    }

    /** 允许特殊业务只返还实际背包消耗，其他货币由所属领域返还。 */
    default void refundNonMoneyPurchase(PlayerPackService packs, Player player, PreGenerateOrderData data, Map<Integer, Long> cost) {
        if (cost != null && !cost.isEmpty() && !packs.addItems(player.getId(), cost, AddType.FAIL_ROLLBACK).success()) {
            LoggerFactory.getLogger(getClass()).error("非充值购买失败后退还道具失败 playerId={},productId={},buyCount={},cost={}",
                    player.getId(), data.getProductId(), data.getBuyCount(), cost);
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
