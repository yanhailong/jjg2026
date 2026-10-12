package com.jjg.game.season.service;

import com.jjg.game.core.constant.Code;
import com.jjg.game.core.data.*;
import com.jjg.game.core.listener.OrderGenerate;
import com.jjg.game.core.pb.RechargeType;
import com.jjg.game.core.pb.ReqGenerateOrder;
import com.jjg.game.sim.manager.SimPlayerContextRegistry;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;

/** productId 输入 activityId_dayIndex，订单内改写为服务器预占 token。 */
@Service
public class ContinuousGiftOrderService implements OrderGenerate {
    private final ContinuousGiftService gifts;
    private final ContinuousGiftConfigService config;
    private final SimPlayerContextRegistry contexts;
    private final com.jjg.game.core.dao.OrderDao orders;

    public ContinuousGiftOrderService(ContinuousGiftService gifts, ContinuousGiftConfigService config,
                                      SimPlayerContextRegistry contexts,
                                      com.jjg.game.core.dao.OrderDao orders) {
        this.gifts = gifts; this.config = config; this.contexts = contexts;
        this.orders = orders;
    }

    @Override public RechargeType getRechargeType() { return RechargeType.CONTINUOUS_GIFT; }

    @Override public String channelProductId(Player player, ReqGenerateOrder req) {
        var d = gifts.tokenData(player.getId(), req.productId);
        return d != null && d.reservation != null && d.reservation.token.equals(req.productId)
                ? d.reservation.channelProductId : null;
    }

    @Override
    public CommonResult<PreGenerateOrderData> generateOrderDetailInfo(Player player, ReqGenerateOrder req) {
        CommonResult<PreGenerateOrderData> result = gifts.locked(player.getId(), () -> {
            var ctx = contexts.getContext(player.getId());
            int[] target = parse(req.productId);
            if (ctx == null || target == null) return new CommonResult<>(Code.PARAM_ERROR);
            long now = System.currentTimeMillis();
            var d = config.find(target[0]);
            var c = d == null ? null : d.chest(target[1]);
            if (c == null || c.priceType() != 1) return new CommonResult<>(Code.PARAM_ERROR);
            var shop = config.shop(c.channelCommodity());
            String channelId = shop == null ? null : SeasonPassService.channelProductId(player, shop);
            if (channelId == null || channelId.isBlank()) return new CommonResult<>(Code.SAMPLE_ERROR);
            var reservation = gifts.reserve(ctx, d, target[1], now);
            if (reservation == null) return new CommonResult<>(Code.FORBID);
            req.productId = reservation.token;
            var detail = new PreGenerateOrderData();
            detail.setType(com.jjg.game.core.pb.RechargeCfgType.MONEY);
            detail.setPrice(BigDecimal.valueOf(reservation.price, 2));
            return new CommonResult<>(Code.SUCCESS, detail);
        });
        return result == null ? new CommonResult<>(Code.FAIL) : result;
    }

    @Override
    public void onOrderCreationFailed(Player player, ReqGenerateOrder req) {
        gifts.locked(player.getId(), () -> {
            String id = com.jjg.game.core.service.OrderService.continuousGiftOrderId(player.getId(), req.productId);
            // 插单超时可能已经落库；已存在的订单不能因客户端重试失败而取消预占。
            if (orders.getOrderById(id) == null) gifts.release(player.getId(), req.productId);
            return true;
        });
    }

    @Override
    public boolean onReceivedRecharge(Player player, Order order) {
        if (order.getRechargeType() != getRechargeType()) return true;
        Boolean result = gifts.locked(player.getId(), () -> gifts.paid(player, order, System.currentTimeMillis()));
        return Boolean.TRUE.equals(result);
    }

    static int[] parse(String value) {
        if (value == null) return null;
        String[] parts = value.split("_", -1);
        if (parts.length != 2) return null;
        try {
            int id = Integer.parseInt(parts[0]); int day = Integer.parseInt(parts[1]);
            return id > 0 && day > 0 ? new int[]{id, day} : null;
        } catch (NumberFormatException e) { return null; }
    }
}
