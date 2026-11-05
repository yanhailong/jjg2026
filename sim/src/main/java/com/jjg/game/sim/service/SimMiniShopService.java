package com.jjg.game.sim.service;

import com.jjg.game.common.cluster.ClusterSystem;
import com.jjg.game.core.constant.AddType;
import com.jjg.game.core.constant.Code;
import com.jjg.game.core.data.*;
import com.jjg.game.core.listener.OrderGenerate;
import com.jjg.game.core.pb.RechargeCfgType;
import com.jjg.game.core.pb.RechargeType;
import com.jjg.game.core.pb.ReqGenerateOrder;
import com.jjg.game.core.service.PlayerPackService;
import com.jjg.game.core.utils.ItemUtils;
import com.jjg.game.sampledata.GameDataManager;
import com.jjg.game.sampledata.bean.ShopMiniCfg;
import com.jjg.game.sampledata.bean.ShopRechargeListCfg;
import com.jjg.game.sim.condition.SimAllLevelCondition;
import com.jjg.game.sim.data.SimPlayerContext;
import com.jjg.game.sim.manager.SimPlayerContextRegistry;
import com.jjg.game.sim.pb.res.ResBuyMiniShop;
import com.jjg.game.sim.pb.res.ResMiniShop;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SimMiniShopService implements OrderGenerate {
    private Logger log = LoggerFactory.getLogger(getClass());

    private final PlayerPackService playerPackService;
    private final SimConfigCacheService configCacheService;
    private final SimPlayerContextRegistry simPlayerContextRegistry;
    private final SimAllLevelCondition simAllLevelCondition;
    private final ClusterSystem clusterSystem;

    public SimMiniShopService(PlayerPackService playerPackService, SimConfigCacheService configCacheService, SimPlayerContextRegistry simPlayerContextRegistry, SimAllLevelCondition simAllLevelCondition, ClusterSystem clusterSystem) {
        this.playerPackService = playerPackService;
        this.configCacheService = configCacheService;
        this.simPlayerContextRegistry = simPlayerContextRegistry;
        this.simAllLevelCondition = simAllLevelCondition;
        this.clusterSystem = clusterSystem;
    }

    public ResBuyMiniShop buy(SimPlayerContext ctx, String productId) {
        ResBuyMiniShop res = new ResBuyMiniShop(Code.SUCCESS);
        res.productId = productId;
        try {
            ShopMiniCfg cfg = GameDataManager.getShopMiniCfg(Integer.parseInt(productId));
            if (cfg == null || cfg.getShopRechargeListId() > 0) {
                res.code = Code.SAMPLE_ERROR;
                return res;
            }

            if (ctx.getSimBaseData().getAllLevel() < cfg.getPurchaseLevelMin() || ctx.getSimBaseData().getAllLevel() > cfg.getPurchaseLevelMax()) {
                res.code = Code.NOT_FOUND;
                log.warn("下单失败，等级范围不合适 playerId={},producetId={},allLevel={},min={},max={}", ctx.getPlayer().getId(), productId, ctx.getSimBaseData().getAllLevel(), cfg.getPurchaseLevelMin(), cfg.getPurchaseLevelMax());
                return res;
            }

            if (cfg.getCost() == null || cfg.getCost().isEmpty()) {
                res.code = Code.SAMPLE_ERROR;
                return res;
            }

            CommonResult<ItemOperationResult> removeResult = playerPackService.removeItem(ctx.getPlayer(), ItemUtils.buildItems(cfg.getCost()), AddType.SIM_MINI_SHOP);
            if (!removeResult.success()) {
                res.code = removeResult.code;
                log.warn("迷你商店购买失败,扣除道具失败 playerId={},productId={}", ctx.getPlayer().getId(), productId);
                return res;
            }

            playerPackService.addItems(ctx.playerId(), cfg.getGoods(), AddType.SIM_MINI_SHOP);
        } catch (Exception e) {
            log.error("购买迷你商店商品异常 playerId={},productId={}", ctx.playerId(), productId, e);
            res.code = Code.EXCEPTION;
        }
        return res;
    }


    public ResMiniShop getShop(PlayerController playerController, int itemId) {
        ResMiniShop res = new ResMiniShop(Code.SUCCESS);
        if (itemId <= 0) {
            res.code = Code.PARAM_ERROR;
            return res;
        }

        SimPlayerContext ctx = simPlayerContextRegistry.getContext(playerController.playerId());
        int allLevel = 0;
        if (ctx != null) {
            allLevel = ctx.getSimBaseData().getAllLevel();
        } else {
            allLevel = simAllLevelCondition.getAllLevel(playerController.playerId(), playerController.ipAddress());
        }

        if (allLevel <= 0) {
            res.code = Code.PARAM_ERROR;
            log.warn("获取玩家等级失败,无法获取迷你商店数据 playerId={},itemId={}", playerController.playerId(), itemId);
            return res;
        }

        ShopMiniCfg cfg = configCacheService.getShopMiniCfgMap(itemId, allLevel);
        if (cfg == null) {
            res.code = Code.NOT_FOUND;
            return res;
        }

        res.productId = cfg.getId() + "";
        res.items = ItemUtils.buildItemInfo(cfg.getGoods());

        if (cfg.getShopRechargeListId() > 0) {
            ShopRechargeListCfg shopRechargeListCfg = GameDataManager.getShopRechargeListCfg(cfg.getShopRechargeListId());
            res.price = shopRechargeListCfg.getPrice().toPlainString();
        } else {
            res.costItems = ItemUtils.buildItemInfo(cfg.getCost());
        }
        return res;
    }

    @Override
    public CommonResult<PreGenerateOrderData> generateOrderDetailInfo(Player player, ReqGenerateOrder req) {
        CommonResult<PreGenerateOrderData> result = new CommonResult<>(Code.SUCCESS);
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
        if (recharge != null && recharge.getType() != RechargeCfgType.MONEY.getType()) {
            return recharge.getType() == RechargeCfgType.ITEM_EXCHANGE.getType()
                    ? exchangeOrderDetailInfo(playerPackService, player, recharge, req.buyCount, AddType.SIM_MINI_SHOP)
                    : nonMoneyOrderDetailInfo(recharge);
        }
        if (recharge == null || recharge.getPrice() == null) {
            log.warn("迷你商店下单失败，充值商品配置或价格为空 playerId={},productId={},shopCfgId={},price={}",
                    player.getId(), req.productId, shopMiniCfg.getShopRechargeListId(),
                    recharge == null ? null : recharge.getPrice());
            return new CommonResult<>(Code.SAMPLE_ERROR);
        }
        PreGenerateOrderData preGenerateOrderData = new PreGenerateOrderData();
        preGenerateOrderData.setType(RechargeCfgType.MONEY);
        preGenerateOrderData.setPrice(recharge.getPrice());
        return new CommonResult<>(Code.SUCCESS, preGenerateOrderData);
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
        return receivePurchase(player, order.getProductId(), order.getId(), 1);
    }

    private boolean receivePurchase(Player player, String productId, String orderId, int buyCount) {
        ShopMiniCfg shopMiniCfg = GameDataManager.getShopMiniCfg(Integer.parseInt(productId));
        if (shopMiniCfg == null) {
            log.warn("获取商品配置失败 playerId={},productId={}", player.getId(), productId);
            return false;
        }
        // 等级只在下单时校验，已付款订单不因玩家升级而拒绝发货。
        var rewards = ItemUtils.multiplyItems(shopMiniCfg.getGoods(), buyCount);
        if (!playerPackService.addItems(player.getId(), rewards, AddType.SHOP_RECHARGE, orderId).success()) {
            return false;
        }
        ResBuyMiniShop res = new ResBuyMiniShop(Code.SUCCESS);
        res.productId = productId;
        res.items = ItemUtils.buildItemInfo(rewards);

        try {
            clusterSystem.sendToPlayer(res, player.getId());
        } catch (Exception e) {
            log.error("迷你商店购买成功推送失败 playerId={},productId={},buyCount={}", player.getId(), productId, buyCount, e);
        }
        return true;
    }

    @Override
    public boolean onReceivedRecharge(Player player, PreGenerateOrderData preGenerateOrderData) {
        ShopMiniCfg cfg = GameDataManager.getShopMiniCfg(Integer.parseInt(preGenerateOrderData.getProductId()));
        return cfg != null && completeNonMoneyPurchase(preGenerateOrderData,
                GameDataManager.getShopRechargeListCfg(cfg.getShopRechargeListId()),
                () -> receivePurchase(player, preGenerateOrderData.getProductId(), null, preGenerateOrderData.getBuyCount()));
    }
}
