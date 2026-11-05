package com.jjg.game.sim.service;

import com.jjg.game.core.constant.AddType;
import com.jjg.game.core.constant.Code;
import com.jjg.game.core.data.*;
import com.jjg.game.core.listener.OrderGenerate;
import com.jjg.game.core.pb.RechargeCfgType;
import com.jjg.game.core.pb.RechargeType;
import com.jjg.game.core.pb.ReqGenerateOrder;
import com.jjg.game.core.service.CorePlayerService;
import com.jjg.game.core.service.OrderService;
import com.jjg.game.core.service.PlayerPackService;
import com.jjg.game.sampledata.GameDataManager;
import com.jjg.game.sim.data.ActivePassPurchase;
import com.jjg.game.sim.data.SimPlayerContext;
import com.jjg.game.sim.manager.SimPlayerContextRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import static com.jjg.game.sim.constant.SimConstant.ActivePass.BASIC;
import static com.jjg.game.sim.constant.SimConstant.ActivePass.PREMIUM;

/**
 * 复用通用预下单与充值回调；预下单productId为ActivePass.id_轨道(2初级/4高级)。
 */
@Service
public class ActivePassOrderService implements OrderGenerate {
    private static final Logger log = LoggerFactory.getLogger(ActivePassOrderService.class);

    private final ActivePassService service;
    private final ActivePassConfigService config;
    private final ActivePassRouter router;
    private final SimPlayerContextRegistry contexts;
    private final OrderService orders;
    private final CorePlayerService playerService;
    @Autowired
    private PlayerPackService playerPackService;

    public ActivePassOrderService(ActivePassService service, ActivePassConfigService config, ActivePassRouter router,
                                  SimPlayerContextRegistry contexts, OrderService orders, CorePlayerService playerService) {
        this.service = service;
        this.config = config;
        this.router = router;
        this.contexts = contexts;
        this.orders = orders;
        this.playerService = playerService;
    }

    @Override
    public RechargeType getRechargeType() {
        return RechargeType.ACTIVE_PASS;
    }

    @Override
    public CommonResult<PreGenerateOrderData> generateOrderDetailInfo(Player player, ReqGenerateOrder req) {
        int[] target = parse(req.productId);
        if (target == null) {
            log.warn("活跃通行证下单失败，商品参数无效 playerId={},productId={}", player.getId(), req.productId);
            return new CommonResult<>(Code.PARAM_ERROR);
        }
        ActivePassPurchase result = router.execute(player.getId(), null,
                () -> prepare(contexts.getContext(player.getId()), target[0], target[1]),
                rpc -> rpc.prepareActivePassOrder(player.getId(), target[0], target[1]));
        if (result == null || result.code != Code.SUCCESS) {
            log.warn("活跃通行证下单失败，预购买校验失败或所属节点未返回结果 playerId={},productId={},passId={},track={},code={}",
                    player.getId(), req.productId, target[0], target[1], result == null ? Code.EXCEPTION : result.code);
            return new CommonResult<>(result == null ? Code.EXCEPTION : result.code);
        }
        req.productId = result.productId;

        var period = config.index().period(target[0]);
        int shopId = target[1] == BASIC ? period.config().getShopRechargeListID() : period.config().getShopRechargeList1ID();
        var shop = GameDataManager.getShopRechargeListCfg(shopId);
        if (shop.getType() != RechargeCfgType.MONEY.getType()) {
            return shop.getType() == RechargeCfgType.ITEM_EXCHANGE.getType()
                    ? singleExchangeOrderDetailInfo(playerPackService, player, shop, req.buyCount, AddType.SIM_ACTIVE_PASS)
                    : nonMoneyOrderDetailInfo(shop);
        }

        int tmpCode = tmpOrderHandle(playerService, player, 1004, AddType.SIM_ACTIVE_PASS, target[1] == BASIC ? 0 : 1);
        if (tmpCode != Code.SUCCESS) {
            log.warn("活跃通行证临时钻石购买失败 playerId={},productId={},round={},code={}",
                    player.getId(), req.productId, target[1] == BASIC ? 0 : 1, tmpCode);
            return new CommonResult<>(tmpCode);
        }
        PreGenerateOrderData preGenerateOrderData = new PreGenerateOrderData();
        preGenerateOrderData.setType(RechargeCfgType.MONEY);
        preGenerateOrderData.setPrice(result.price);
        return new CommonResult<>(Code.SUCCESS, preGenerateOrderData);
    }

    public ActivePassPurchase prepare(SimPlayerContext ctx, int passId, int track) {
        if (ctx == null || (track != BASIC && track != PREMIUM)) {
            return new ActivePassPurchase(Code.PARAM_ERROR);
        }
        var data = service.ensureCurrent(ctx, System.currentTimeMillis());
        if (data == null || data.getPassId() != passId || (data.getPurchasedTracks() & track) != 0) {
            return new ActivePassPurchase(Code.FAIL);
        }
        var period = config.index().period(passId);
        int shopId = track == BASIC ? period.config().getShopRechargeListID() : period.config().getShopRechargeList1ID();
        var shop = GameDataManager.getShopRechargeListCfg(shopId);
        if (shop == null || shop.getType() == RechargeCfgType.MONEY.getType()
                && (shop.getPrice() == null || shop.getPrice().signum() <= 0)) {
            return new ActivePassPurchase(Code.FAIL);
        }
        // 创建期记录先于付款；即使跨期到账，也能在原期补发奖励。
        if (shop.getType() == RechargeCfgType.MONEY.getType()) {
            service.saveReceipt(data);
        }
        return new ActivePassPurchase(Code.SUCCESS, passId + "_" + track, shop.getPrice());
    }

    @Override
    public boolean onReceivedRecharge(Player player, Order order) {
        if (order.getRechargeType() != getRechargeType() || order.getPlayerId() != player.getId()) {
            return false;
        }
        return router.execute(player.getId(), null,
                () -> receive(contexts.getContext(player.getId()), order.getId()),
                rpc -> rpc.receiveActivePassOrder(player.getId(), order.getId()));
    }

    /**
     * 所属节点重新读取可信订单，不接受客户端直接解锁或指定付款金额。
     */
    public boolean receive(SimPlayerContext ctx, String orderId) {
        Order order = orders.getOrder(orderId);
        if (ctx == null || order == null || order.getPlayerId() != ctx.playerId()
                || order.getRechargeType() != getRechargeType() || order.getOrderStatus() != OrderStatus.PROCESSING) {
            return false;
        }
        int[] target = parse(order.getProductId());
        return target != null && service.unlock(ctx, target[0], target[1]);
    }

    private int[] parse(String value) {
        if (value == null) {
            return null;
        }
        String[] parts = value.split("_", -1);
        if (parts.length != 2) {
            return null;
        }
        try {
            int id = Integer.parseInt(parts[0]);
            int track = Integer.parseInt(parts[1]);
            return id > 0 && (track == BASIC || track == PREMIUM) ? new int[]{id, track} : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public boolean onReceivedRecharge(Player player, PreGenerateOrderData preGenerateOrderData) {
        int[] target = parse(preGenerateOrderData.getProductId());
        if (target == null) {
            return false;
        }
        return router.execute(player.getId(), null,
                () -> receiveNonMoneyPurchase(contexts.getContext(player.getId()), preGenerateOrderData),
                rpc -> rpc.completeActivePassPurchase(player.getId(), preGenerateOrderData));
    }

    public boolean receiveNonMoneyPurchase(SimPlayerContext ctx, PreGenerateOrderData data) {
        int[] target = parse(data.getProductId());
        if (ctx == null || target == null) {
            return false;
        }
        var period = config.index().period(target[0]);
        if (period == null) {
            return false;
        }
        int shopId = target[1] == BASIC ? period.config().getShopRechargeListID() : period.config().getShopRechargeList1ID();
        return completeNonMoneyPurchase(data,
                GameDataManager.getShopRechargeListCfg(shopId),
                () -> service.unlock(ctx, target[0], target[1]));
    }
}
