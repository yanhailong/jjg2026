package com.jjg.game.sim.service;

import com.jjg.game.core.constant.AddType;
import com.jjg.game.core.constant.Code;
import com.jjg.game.core.data.*;
import com.jjg.game.core.listener.OrderGenerate;
import com.jjg.game.core.pb.RechargeType;
import com.jjg.game.core.pb.ReqGenerateOrder;
import com.jjg.game.core.service.PlayerPackService;
import com.jjg.game.core.utils.ItemUtils;
import com.jjg.game.sampledata.GameDataManager;
import com.jjg.game.sampledata.bean.ShopMiniCfg;
import com.jjg.game.sampledata.bean.ShopRechargeListCfg;
import com.jjg.game.sim.data.SimPlayerContext;
import com.jjg.game.sim.manager.SimPlayerContextRegistry;
import com.jjg.game.sim.pb.res.ResBuyMiniShop;
import com.jjg.game.sim.pb.res.ResMiniShop;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class SimMiniShopService implements OrderGenerate {
    private Logger log = LoggerFactory.getLogger(getClass());

    private final PlayerPackService playerPackService;
    private final SimConfigCacheService configCacheService;
    private final SimPlayerContextRegistry simPlayerContextRegistry;

    public SimMiniShopService(PlayerPackService playerPackService, SimConfigCacheService configCacheService, SimPlayerContextRegistry simPlayerContextRegistry) {
        this.playerPackService = playerPackService;
        this.configCacheService = configCacheService;
        this.simPlayerContextRegistry = simPlayerContextRegistry;
    }

    public void buy(SimPlayerContext ctx, String productId) {
        ResBuyMiniShop res = new ResBuyMiniShop(Code.SUCCESS);
        res.productId = productId;
        try {
            ShopMiniCfg cfg = GameDataManager.getShopMiniCfg(Integer.parseInt(productId));
            if (cfg == null || cfg.getShopRechargeListId() > 0) {
                res.code = Code.SAMPLE_ERROR;
                ctx.send(res);
                return;
            }

            if (ctx.getSimBaseData().getAllLevel() < cfg.getPurchaseLevelMin() || ctx.getSimBaseData().getAllLevel() > cfg.getPurchaseLevelMax()) {
                res.code = Code.NOT_FOUND;
                log.warn("下单失败，等级范围不合适 playerId={},producetId={},allLevel={},min={},max={}", ctx.getPlayer().getId(), productId, ctx.getSimBaseData().getAllLevel(), cfg.getPurchaseLevelMin(), cfg.getPurchaseLevelMax());
                ctx.send(res);
                return;
            }

            if (cfg.getCost() == null || cfg.getCost().isEmpty()) {
                res.code = Code.SAMPLE_ERROR;
                ctx.send(res);
                return;
            }

            CommonResult<ItemOperationResult> commonResult = playerPackService.exchangePackItems(ctx.getPlayer(), null, cfg.getCost(), cfg.getGoods(), AddType.SIM_MINI_SHOP, null);
            if (!commonResult.success()) {
                res.code = commonResult.code;
                ctx.send(res);
                log.warn("迷你商店购买失败 playerId={},productId={}", ctx.getPlayer().getId(), productId);
                return;
            }
        } catch (Exception e) {
            log.error("购买迷你商店商品异常 playerId={},productId={}", ctx.playerId(), productId, e);
            res.code = Code.EXCEPTION;
        }
        ctx.send(res);
    }


    public void getShop(SimPlayerContext ctx, int itemId) {
        ResMiniShop res = new ResMiniShop(Code.SUCCESS);
        if (itemId <= 0) {
            res.code = Code.PARAM_ERROR;
            ctx.send(res);
            return;
        }

        ShopMiniCfg cfg = configCacheService.getShopMiniCfgMap(itemId, ctx.getSimBaseData().getAllLevel());
        if (cfg == null) {
            res.code = Code.NOT_FOUND;
            ctx.send(res);
            return;
        }

        res.productId = cfg.getId() + "";
        res.items = ItemUtils.buildItemInfo(cfg.getGoods());

        if (cfg.getShopRechargeListId() > 0) {
            ShopRechargeListCfg shopRechargeListCfg = GameDataManager.getShopRechargeListCfg(cfg.getShopRechargeListId());
            if (ctx.getPlayer().getLoginType() == LoginType.APPLE) {
                res.price = shopRechargeListCfg.getIosShopId();
            } else {
                res.price = shopRechargeListCfg.getGoogleShopId();
            }
        } else {
            res.costItems = ItemUtils.buildItemInfo(cfg.getCost());
        }
        ctx.send(res);
    }

    @Override
    public CommonResult<BigDecimal> generateOrderDetailInfo(Player player, ReqGenerateOrder req) {
        CommonResult<BigDecimal> result = new CommonResult<>(Code.SUCCESS);
        ShopMiniCfg shopMiniCfg = GameDataManager.getShopMiniCfg(Integer.parseInt(req.productId));
        if (shopMiniCfg == null) {
            result.code = Code.SAMPLE_ERROR;
            log.warn("下单失败，获取商品配置失败 playerId={},producetId={}", player.getId(), req.productId);
            return result;
        }

        SimPlayerContext context = simPlayerContextRegistry.getContext(player.getId());
        if (context == null) {
            result.code = Code.NOT_FOUND;
            log.warn("下单失败，获取context失败 playerId={},producetId={}", player.getId(), req.productId);
            return result;
        }

        if (context.getSimBaseData().getAllLevel() < shopMiniCfg.getPurchaseLevelMin() || context.getSimBaseData().getAllLevel() > shopMiniCfg.getPurchaseLevelMax()) {
            result.code = Code.NOT_FOUND;
            log.warn("下单失败，等级范围不合适 playerId={},producetId={},allLevel={},min={},max={}", player.getId(), req.productId, context.getSimBaseData().getAllLevel(), shopMiniCfg.getPurchaseLevelMin(), shopMiniCfg.getPurchaseLevelMax());
            return result;
        }

        ShopRechargeListCfg recharge = GameDataManager.getShopRechargeListCfg(shopMiniCfg.getShopRechargeListId());
        if (recharge == null || recharge.getPrice() == null) {
            return new CommonResult<>(Code.SAMPLE_ERROR);
        }
        return new CommonResult<>(Code.SUCCESS, recharge.getPrice());
    }

    @Override
    public RechargeType getRechargeType() {
        return RechargeType.SHOP_MINI;
    }

    @Override
    public boolean onReceivedRecharge(Player player, Order order) {
        if (order.getRechargeType() != getRechargeType() || order.getPlayerId() != player.getId()) {
            return false;
        }
        ShopMiniCfg shopMiniCfg = GameDataManager.getShopMiniCfg(Integer.parseInt(order.getProductId()));
        if (shopMiniCfg == null) {
            log.warn("获取商品配置失败 playerId={},orderId={},producetId={}", player.getId(), order.getProductId(), order.getProductId());
            return false;
        }
        // 等级只在下单时校验，已付款订单不因玩家升级而拒绝发货。
        return playerPackService.addItems(player.getId(), shopMiniCfg.getGoods(), AddType.SHOP_RECHARGE, order.getId()).success();
    }
}
