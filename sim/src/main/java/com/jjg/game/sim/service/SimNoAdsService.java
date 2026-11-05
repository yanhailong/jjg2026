package com.jjg.game.sim.service;

import com.jjg.game.common.cluster.ClusterSystem;
import com.jjg.game.common.constant.EFunctionType;
import com.jjg.game.core.constant.AddType;
import com.jjg.game.core.constant.Code;
import com.jjg.game.core.data.*;
import com.jjg.game.core.listener.OrderGenerate;
import com.jjg.game.core.listener.SimNoAdsListener;
import com.jjg.game.core.pb.NotifyOpenFunction;
import com.jjg.game.core.pb.RechargeCfgType;
import com.jjg.game.core.pb.RechargeType;
import com.jjg.game.core.pb.ReqGenerateOrder;
import com.jjg.game.core.service.GameFunctionService;
import com.jjg.game.core.service.PlayerPackService;
import com.jjg.game.core.utils.ItemUtils;
import com.jjg.game.core.utils.SampleDataUtils;
import com.jjg.game.sampledata.GameDataManager;
import com.jjg.game.sampledata.bean.ShopRechargeListCfg;
import com.jjg.game.sim.constant.SimConstant;
import com.jjg.game.sim.dao.SimPlayerGameDao;
import com.jjg.game.sim.data.SimBaseData;
import com.jjg.game.sim.data.SimPlayerContext;
import com.jjg.game.sim.manager.SimPlayerContextRegistry;
import com.jjg.game.sim.pb.res.ResNoAds;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/** 免广告功能：信息查询、充值下单及支付交付。 */
@Service
public class SimNoAdsService implements OrderGenerate, SimNoAdsListener {
    private static final Logger log = LoggerFactory.getLogger(SimNoAdsService.class);

    private final SimPlayerContextRegistry simPlayerContextRegistry;
    private final SimPlayerGameDao simPlayerGameDao;
    private final SimAutoSaveService autoSaveService;
    private final SimConfigCacheService simConfigCacheService;
    private final GameFunctionService gameFunctionService;
    private final PlayerPackService playerPackService;
    private final ClusterSystem clusterSystem;

    public SimNoAdsService(SimPlayerContextRegistry simPlayerContextRegistry, SimPlayerGameDao simPlayerGameDao,
                           SimAutoSaveService autoSaveService, SimConfigCacheService simConfigCacheService,
                           GameFunctionService gameFunctionService, PlayerPackService playerPackService,
                           ClusterSystem clusterSystem) {
        this.simPlayerContextRegistry = simPlayerContextRegistry;
        this.simPlayerGameDao = simPlayerGameDao;
        this.autoSaveService = autoSaveService;
        this.simConfigCacheService = simConfigCacheService;
        this.gameFunctionService = gameFunctionService;
        this.playerPackService = playerPackService;
        this.clusterSystem = clusterSystem;
    }

    public ResNoAds noAds(SimPlayerContext ctx) {
        Player player = ctx.getPlayer();
        ResNoAds res = new ResNoAds(Code.SUCCESS);
        ShopRechargeListCfg discount = noAdsShop(simConfigCacheService.getNoadsAfterPriceId(), player);
        ShopRechargeListCfg original = noAdsShop(simConfigCacheService.getNoadsOriginPriceId(), player);
        if (discount == null || original == null) {
            res.code = Code.SAMPLE_ERROR;
            return res;
        }
        res.items = ItemUtils.buildItemInfo(simConfigCacheService.getNoAdsRewardsMap());
        res.discountEndTime = noAdsDiscountEndTime(player);
        res.discountPercent = String.valueOf(SampleDataUtils.getIntGlobalData(SimConstant.Global.NO_ADS_PROP_NUM));
        res.discountPrice = discount.getPrice() == null ? null : discount.getPrice().toPlainString();
        res.originalPrice = original.getPrice() == null ? null : original.getPrice().toPlainString();
        ShopRechargeListCfg current = System.currentTimeMillis() < res.discountEndTime ? discount : original;
        res.rechargeInfo = SampleDataUtils.transRechargeCfgToRechargeInfo(current.getId(), player.getChannel());
        return res;
    }

    @Override
    public boolean noads(long playerId) {
        SimPlayerContext ctx = simPlayerContextRegistry.getContext(playerId);
        return ctx == null ? simPlayerGameDao.findNoadsById(playerId) : ctx.getSimBaseData().isNoads();
    }

    @Override
    public RechargeType getRechargeType() {
        return RechargeType.NO_ADS;
    }

    @Override
    public CommonResult<PreGenerateOrderData> generateOrderDetailInfo(Player player, ReqGenerateOrder req) {
        SimPlayerContext ctx = simPlayerContextRegistry.getContext(player.getId());
        if (ctx == null || !gameFunctionService.checkGameFunctionOpen(player, EFunctionType.NO_ADS, true, false)) {
            return new CommonResult<>(Code.ERROR_REQ);
        }
        if (ctx.getSimBaseData().isNoads() || ctx.getSimBaseData().getNoAdsOrderId() != null) {
            return new CommonResult<>(Code.REPEAT_OP);
        }
        int shopId = System.currentTimeMillis() < noAdsDiscountEndTime(player)
                ? simConfigCacheService.getNoadsAfterPriceId() : simConfigCacheService.getNoadsOriginPriceId();
        ShopRechargeListCfg shop = noAdsShop(shopId, player);
        if (shop == null) {
            return new CommonResult<>(Code.SAMPLE_ERROR);
        }
        if (shop.getType() != RechargeCfgType.MONEY.getType()) {
            CommonResult<PreGenerateOrderData> result = shop.getType() == RechargeCfgType.ITEM_EXCHANGE.getType()
                    ? singleExchangeOrderDetailInfo(playerPackService, player, shop, req.buyCount, AddType.SIM_NO_ADS_REWARDS)
                    : nonMoneyOrderDetailInfo(shop);
            if (result.success()) {
                result.data.setOrderProductId(String.valueOf(shop.getId()));
            }
            return result;
        }
        PreGenerateOrderData data = new PreGenerateOrderData();
        data.setType(RechargeCfgType.MONEY);
        data.setPrice(shop.getPrice());
        data.setOrderProductId(String.valueOf(shop.getId()));
        return new CommonResult<>(Code.SUCCESS, data);
    }

    @Override
    public boolean onReceivedRecharge(Player player, Order order) {
        if (player == null || order == null || order.getPlayerId() != player.getId()
                || order.getRechargeType() != RechargeType.NO_ADS || StringUtils.isBlank(order.getId())
                || order.getOrderStatus() != OrderStatus.PROCESSING) {
            return false;
        }
        return receivePurchase(player, order.getId());
    }

    private boolean receivePurchase(Player player, String orderId) {
        SimPlayerContext ctx = simPlayerContextRegistry.getContext(player.getId());
        if (ctx == null) {
            return false;
        }
        SimBaseData base = ctx.getSimBaseData();
        if (base.isNoads()) {
            return true;
        }
        boolean giftsAdded = false;
        try {
            String deliveredOrder = base.getNoAdsOrderId();
            if (deliveredOrder != null && !deliveredOrder.equals(orderId)) {
                return false;
            }
            if (deliveredOrder == null) {
                if (!playerPackService.addItems(player.getId(), simConfigCacheService.getNoAdsRewardsMap(),
                        AddType.SIM_NO_ADS_REWARDS, orderId).success()) {
                    return false;
                }
                giftsAdded = true;
                base.setNoAdsOrderId(orderId);
            }
            base.setNoads(true);
            // 赠品记录和权益一并落库；失败保留发货记录，重试时不重复发奖。
            autoSaveService.saveAndWait(base);
        } catch (Exception e) {
            base.setNoads(false);
            if (orderId == null && giftsAdded && !playerPackService.removeItems(player,
                    simConfigCacheService.getNoAdsRewardsMap(), AddType.FAIL_ROLLBACK).success()) {
                log.error("免广告礼包非充值购买失败后收回赠品失败 playerId={}", player.getId());
            }
            log.error("免广告礼包交付失败 playerId={},orderId={}", player.getId(), orderId, e);
            return false;
        }
        try {
            NotifyOpenFunction notify = new NotifyOpenFunction();
            notify.closeFunctionIdList = List.of(EFunctionType.NO_ADS.getFunctionId());
            clusterSystem.sendToPlayer(notify, player.getId());
            ResNoAds res = noAds(ctx);
            res.orderId = orderId;
            clusterSystem.sendToPlayer(res, player.getId());
        } catch (Exception e) {
            log.error("免广告礼包购买成功后推送失败 playerId={},orderId={}", player.getId(), orderId, e);
        }
        return true;
    }

    private long noAdsDiscountEndTime(Player player) {
        return (player.getCreateTime() + SampleDataUtils.getIntGlobalData(SimConstant.Global.NO_ADS_PROP_TIME)) * 1000L;
    }

    private ShopRechargeListCfg noAdsShop(int shopId, Player player) {
        ShopRechargeListCfg shop = GameDataManager.getShopRechargeListCfg(shopId);
        if (shop == null || shop.getType() == RechargeCfgType.MONEY.getType()
                && (shop.getPrice() == null || shop.getPrice().signum() <= 0
                || StringUtils.isBlank(player.getChannel() == ChannelType.APPLE ? shop.getIosShopId() : shop.getGoogleShopId()))) {
            return null;
        }
        return shop;
    }

    @Override
    public boolean onReceivedRecharge(Player player, PreGenerateOrderData preGenerateOrderData) {
        return completeNonMoneyPurchase(preGenerateOrderData,
                GameDataManager.getShopRechargeListCfg(Integer.parseInt(preGenerateOrderData.getProductId())),
                () -> receivePurchase(player, null));
    }
}
