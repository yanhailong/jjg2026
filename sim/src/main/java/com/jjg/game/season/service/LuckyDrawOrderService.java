package com.jjg.game.season.service;

import com.jjg.game.core.constant.Code;
import com.jjg.game.core.dao.OrderDao;
import com.jjg.game.core.data.*;
import com.jjg.game.core.listener.OrderGenerate;
import com.jjg.game.core.pb.*;
import com.jjg.game.core.service.OrderService;
import com.jjg.game.sim.manager.SimPlayerContextRegistry;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;

@Service
public class LuckyDrawOrderService implements OrderGenerate {
    private final LuckyDrawService draws;
    private final SimPlayerContextRegistry contexts;
    private final SeasonLifecycleService lifecycle;
    private final LuckyDrawPresence presence;
    private final OrderDao orders;
    public LuckyDrawOrderService(LuckyDrawService draws, SimPlayerContextRegistry contexts,
                                 SeasonLifecycleService lifecycle, LuckyDrawPresence presence, OrderDao orders) {
        this.draws = draws; this.contexts = contexts; this.lifecycle = lifecycle; this.presence = presence; this.orders = orders;
    }
    @Override public RechargeType getRechargeType() { return RechargeType.SEASON_LUCKY_DRAW; }
    @Override public String channelProductId(Player player, ReqGenerateOrder req) {
        var d = draws.tokenData(player.getId(), req.productId);
        return d != null && d.reservation != null && d.reservation.token.equals(req.productId)
                ? d.reservation.channelProductId : null;
    }
    @Override public CommonResult<PreGenerateOrderData> generateOrderDetailInfo(Player player, ReqGenerateOrder req) {
        CommonResult<PreGenerateOrderData> result = draws.locked(player.getId(), () -> {
            var target = parse(req.productId);
            var ctx = contexts.getContext(player.getId());
            if (target == null || ctx == null) return new CommonResult<>(Code.PARAM_ERROR);
            long now = System.currentTimeMillis(); lifecycle.ensureCurrent(ctx, now); presence.authenticatedRequest(ctx, now);
            var r = draws.reserve(ctx, target.activityId(), target.drawType(), target.requestId(), now);
            if (r == null) return new CommonResult<>(Code.FORBID);
            var d = draws.tokenData(player.getId(), r.token);
            req.productId = r.token;
            var detail = new PreGenerateOrderData();
            detail.setType(com.jjg.game.core.pb.RechargeCfgType.MONEY);
            detail.setPrice(BigDecimal.valueOf(d.definition.price(), 2));
            return new CommonResult<>(Code.SUCCESS, detail);
        });
        return result == null ? new CommonResult<>(Code.FAIL) : result;
    }
    @Override public void onOrderCreationFailed(Player player, ReqGenerateOrder req) {
        draws.locked(player.getId(), () -> {
            if (orders.getOrderById(OrderService.luckyDrawOrderId(player.getId(), req.productId)) == null)
                draws.release(player.getId(), req.productId);
            return true;
        });
    }
    @Override public boolean onReceivedRecharge(Player player, Order order) {
        if (order.getRechargeType() != getRechargeType()) return true;
        return Boolean.TRUE.equals(draws.locked(player.getId(), () -> draws.paid(player, order, System.currentTimeMillis())));
    }
    static Target parse(String productId) {
        if (productId == null) return null;
        String[] parts = productId.split("_", -1);
        if (parts.length != 3 || !LuckyDrawService.validRequestId(parts[2])) return null;
        try {
            int id = Integer.parseInt(parts[0]); int type = Integer.parseInt(parts[1]);
            return id > 0 && type == 1 ? new Target(id, type, parts[2]) : null;
        } catch (NumberFormatException e) { return null; }
    }
    record Target(int activityId, int drawType, String requestId) { }
}
