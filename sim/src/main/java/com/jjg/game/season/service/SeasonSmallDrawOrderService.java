package com.jjg.game.season.service;

import com.jjg.game.core.constant.Code;
import com.jjg.game.core.constant.AddType;
import com.jjg.game.core.data.CommonResult;
import com.jjg.game.core.data.Order;
import com.jjg.game.core.data.OrderStatus;
import com.jjg.game.core.data.Player;
import com.jjg.game.core.data.PreGenerateOrderData;
import com.jjg.game.core.listener.OrderGenerate;
import com.jjg.game.core.service.PlayerPackService;
import com.jjg.game.core.pb.RechargeCfgType;
import com.jjg.game.core.pb.RechargeType;
import com.jjg.game.core.pb.ReqGenerateOrder;
import com.jjg.game.sampledata.GameDataManager;
import com.jjg.game.sampledata.bean.ShopRechargeListCfg;
import com.jjg.game.season.data.SeasonPlayerData;
import com.jjg.game.season.data.SeasonSmallDrawData;
import com.jjg.game.sim.data.SimPlayerContext;
import com.jjg.game.sim.manager.SimPlayerContextRegistry;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

/** 小额抽奖支付适配；客户端productId为SmallDraw.id，可信订单绑定具体赛季。 */
@Service
public class SeasonSmallDrawOrderService implements OrderGenerate {
    private final SimPlayerContextRegistry contexts;
    private final SeasonSmallDrawService drawService;
    @Autowired
    private PlayerPackService playerPackService;

    public SeasonSmallDrawOrderService(SimPlayerContextRegistry contexts,
                                       SeasonSmallDrawService drawService) {
        this.contexts = contexts;
        this.drawService = drawService;
    }

    @Override
    public RechargeType getRechargeType() { return RechargeType.LUCKY_DRAW; }

    @Override
    public CommonResult<PreGenerateOrderData> generateOrderDetailInfo(Player player, ReqGenerateOrder req) {
        SimPlayerContext ctx = contexts.getContext(player.getId());
        if (ctx == null) {
            return new CommonResult<>(Code.NOT_FOUND);
        }
        var draw = drawService.current(ctx);
        if (draw == null || !Integer.toString(draw.id()).equals(req.productId)) {
            return new CommonResult<>(Code.NOT_FOUND);
        }
        SeasonPlayerData season = ctx.getSeasonPlayerData();
        SeasonSmallDrawData state = season.getSmallDraw();
        String productId = SeasonSmallDrawService.orderToken(season, draw.id());
        if (state.getPurchaseChances() <= 0
                || draw.remainingRewardCount(state.getObtainedRewards()) <= state.drawChances()) {
            return new CommonResult<>(Code.NOT_ENOUGH);
        }
        ShopRechargeListCfg shop = GameDataManager.getShopRechargeListCfg(draw.config().getPricePerUse());
        if (shop != null && shop.getType() == RechargeCfgType.ITEM_EXCHANGE.getType()) {
            int buyCount = Math.max(1, req.buyCount);
            if (buyCount > state.getPurchaseChances()
                    || buyCount > draw.remainingRewardCount(state.getObtainedRewards()) - state.drawChances()) {
                return new CommonResult<>(Code.NOT_ENOUGH);
            }
        }
        if (shop != null && shop.getType() != RechargeCfgType.MONEY.getType()) {
            CommonResult<PreGenerateOrderData> result = shop.getType() == RechargeCfgType.ITEM_EXCHANGE.getType()
                    ? exchangeOrderDetailInfo(playerPackService, player, shop, req.buyCount, AddType.SIM_SEASON_SMALL_DRAW)
                    : nonMoneyOrderDetailInfo(shop);
            if (result.success()) {
                result.data.setOrderProductId(productId);
            }
            return result;
        }
        if (shop == null || shop.getPrice() == null || shop.getPrice().signum() <= 0) {
            return new CommonResult<>(Code.SAMPLE_ERROR);
        }
        String channelProductId = SeasonPassService.channelProductId(player, shop);
        if (channelProductId == null || channelProductId.isBlank()) {
            return new CommonResult<>(Code.SAMPLE_ERROR);
        }
        PreGenerateOrderData data = new PreGenerateOrderData();
        data.setType(RechargeCfgType.MONEY);
        data.setPrice(shop.getPrice());
        data.setOrderProductId(productId);
        return new CommonResult<>(Code.SUCCESS, data);
    }

    @Override
    public boolean onReceivedRecharge(Player player, Order order) {
        if (order.getRechargeType() != getRechargeType() || order.getPlayerId() != player.getId()
                || order.getOrderStatus() != OrderStatus.PROCESSING) {
            return false;
        }
        SimPlayerContext ctx = contexts.getContext(player.getId());
        return ctx != null && drawService.receive(ctx, order);
    }

    @Override
    public boolean onReceivedRecharge(Player player, PreGenerateOrderData preGenerateOrderData) {
        SimPlayerContext ctx = contexts.getContext(player.getId());
        if (ctx == null) {
            return false;
        }
        var draw = drawService.current(ctx);
        return draw != null && completeNonMoneyPurchase(preGenerateOrderData,
                GameDataManager.getShopRechargeListCfg(draw.config().getPricePerUse()),
                () -> drawService.receivePurchase(ctx, preGenerateOrderData.getProductId(), preGenerateOrderData.getBuyCount()));
    }
}
