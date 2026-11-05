package com.jjg.game.sim.service;

import com.jjg.game.alliance.dao.AlliancePlayerDao;
import com.jjg.game.alliance.service.AllianceAssetService;
import com.jjg.game.alliance.service.AllianceCacheService;
import com.jjg.game.common.cluster.ClusterSystem;
import com.jjg.game.common.rpc.ClusterRpcReference;
import com.jjg.game.common.rpc.GameRpcContext;
import com.jjg.game.common.rpc.RpcReqParameterBuilder;
import com.jjg.game.common.utils.TimeHelper;
import com.jjg.game.core.base.condition.numeric.ActionConditionEvent;
import com.jjg.game.core.constant.AddType;
import com.jjg.game.core.constant.Code;
import com.jjg.game.core.data.CommonResult;
import com.jjg.game.core.data.ItemOperationResult;
import com.jjg.game.core.data.Order;
import com.jjg.game.core.data.OrderStatus;
import com.jjg.game.core.data.Player;
import com.jjg.game.core.data.PreGenerateOrderData;
import com.jjg.game.core.listener.OrderGenerate;
import com.jjg.game.core.pb.KVInfo;
import com.jjg.game.core.pb.RechargeCfgType;
import com.jjg.game.core.pb.RechargeType;
import com.jjg.game.core.pb.ReqGenerateOrder;
import com.jjg.game.core.service.CorePlayerService;
import com.jjg.game.core.service.OrderService;
import com.jjg.game.core.service.PlayerPackService;
import com.jjg.game.core.service.ServerOpenTimeService;
import com.jjg.game.core.service.ShopService;
import com.jjg.game.core.utils.ItemUtils;
import com.jjg.game.core.utils.SampleDataUtils;
import com.jjg.game.sampledata.GameDataManager;
import com.jjg.game.sampledata.bean.ShopRechargeListCfg;
import com.jjg.game.sampledata.bean.TokenShopCfg;
import com.jjg.game.season.model.SeasonPhase;
import com.jjg.game.season.service.SeasonLifecycleService;
import com.jjg.game.sim.bridge.ToSimBridge;
import com.jjg.game.sim.manager.SimPlayerContextRegistry;
import com.jjg.game.sim.constant.SimConstant;
import com.jjg.game.sim.data.SimPlayerContext;
import com.jjg.game.sim.data.TokenShopData;
import com.jjg.game.sim.data.TokenShopPurchaseData;
import com.jjg.game.sim.pb.res.ResTokenShop;
import com.jjg.game.sim.pb.res.ResTokenShopExchange;
import com.jjg.game.sim.pb.struct.TokenShopGoodsInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 在玩家所属 SIM 逻辑线程执行；限购随玩家基础数据持久化。
 */
@Service
public class TokenShopService implements OrderGenerate {
    private static final Logger log = LoggerFactory.getLogger(TokenShopService.class);
    private final TokenShopConfigService configs;
    private final SeasonLifecycleService seasons;
    private final PlayerPackService packs;
    private final AllianceCacheService alliances;
    private final AllianceAssetService allianceAssets;
    private final SimAutoSaveService saves;
    private final SimTaskService tasks;
    private final ShopService shops;
    private final ServerOpenTimeService serverOpenTimeService;
    private final AlliancePlayerDao alliancePlayerDao;
    private final CorePlayerService playerService;
    private final SimConfigCacheService configCache;
    private final SimPlayerContextRegistry contexts;
    private final SimNodeService nodes;
    private final OrderService orders;
    private final ClusterSystem cluster;
    @ClusterRpcReference
    private ToSimBridge bridge;

    public TokenShopService(TokenShopConfigService configs, SeasonLifecycleService seasons,
                            PlayerPackService packs, AllianceCacheService alliances,
                            AllianceAssetService allianceAssets, SimAutoSaveService saves,
                            SimTaskService tasks, ShopService shops, ServerOpenTimeService serverOpenTimeService,
                            AlliancePlayerDao alliancePlayerDao, CorePlayerService playerService,
                            SimConfigCacheService configCache, SimPlayerContextRegistry contexts,
                            SimNodeService nodes, OrderService orders, ClusterSystem cluster) {
        this.configs = configs;
        this.seasons = seasons;
        this.packs = packs;
        this.alliances = alliances;
        this.allianceAssets = allianceAssets;
        this.saves = saves;
        this.tasks = tasks;
        this.shops = shops;
        this.serverOpenTimeService = serverOpenTimeService;
        this.alliancePlayerDao = alliancePlayerDao;
        this.playerService = playerService;
        this.configCache = configCache;
        this.contexts = contexts;
        this.nodes = nodes;
        this.orders = orders;
        this.cluster = cluster;
    }

    public ResTokenShop list(SimPlayerContext ctx, int type) {
        return list(ctx, type, System.currentTimeMillis());
    }

    ResTokenShop list(SimPlayerContext ctx, int type, long now) {
        ResTokenShop res = new ResTokenShop(Code.SUCCESS);
        res.type = type < 3 ? 1 : type;
        res.goods = List.of();
        Map<Integer, TokenShopData> shopDataMap = configCache.getTokenShopDataMap();
        int currentSeasonType = seasonType(ctx, now);
        int seasonShopType = currentSeasonType == 0 ? 1 : currentSeasonType;
        res.typeList = shopDataMap.values().stream()
                .filter(data -> data.getType() >= 3 || data.getType() == seasonShopType)
                .map(data -> new KVInfo(data.getType() < 3 ? 1 : data.getType(), data.getLangId())).toList();
        int configType = type < 3 ? currentSeasonType : type;
        //新手赛季没有商品，商店信息沿用进阶赛季。
        TokenShopData shopData = shopDataMap.get(configType == 0 ? 1 : configType);
        if (shopData == null) {
            log.warn("代币商店类型未配置 playerId={},type={},configType={},configuredTypes={}",
                    ctx.playerId(), type, configType, shopDataMap.keySet());
            res.code = Code.PARAM_ERROR;
            return res;
        }
        res.costItemId = shopData.getItemId();
        res.hasItemCount = shopData.getItemId() > 0 ? itemCount(ctx, shopData.getItemId()) : 0;

        long today = TimeHelper.getLocalDateTime(now).toLocalDate().toEpochDay();
        List<TokenShopGoodsInfo> goods = new ArrayList<>();
        for (TokenShopConfigService.Goods product : configs.goodsOfType(configType)) {
            if (availability(ctx, product, now) == Code.SUCCESS) {
                goods.add(info(ctx, product, today));
            }
        }
        res.goods = goods;
        return res;
    }

    private long itemCount(SimPlayerContext ctx, int itemId) {
        if (itemId == SimConstant.Item.ID_SEASON_COIN) {
            return ctx.getSeasonPlayerData().getSeasonCoin();
        }
        if (itemId == SimConstant.Item.ID_ALLIANCE_CONTRIBUTION) {
            return alliancePlayerDao.getOrEmpty(ctx.playerId()).getContribution();
        }
        if (itemId == ItemUtils.getDiamondItemId()) {
            return playerService.get(ctx.playerId()).getDiamond();
        }
        if (itemId == ItemUtils.getGoldItemId()) {
            return playerService.get(ctx.playerId()).getGold();
        }
        if (itemId == ItemUtils.getShellItemId()) {
            return playerService.get(ctx.playerId()).getShell();
        }
        return packs.getItemCount(ctx.playerId(), itemId);
    }

    @Override
    public RechargeType getRechargeType() {
        return RechargeType.TOKEN_SHOP;
    }

    @Override
    public CommonResult<PreGenerateOrderData> generateOrderDetailInfo(Player player, ReqGenerateOrder req) {
        return route(player.getId(), () -> prepare(player.getId(), req),
                rpc -> rpc.prepareTokenShopOrder(player.getId(), req), new CommonResult<>(Code.NOT_FOUND));
    }

    public CommonResult<PreGenerateOrderData> prepare(long playerId, ReqGenerateOrder req) {
        SimPlayerContext ctx = contexts.getContext(playerId);
        TokenShopConfigService.Goods product = product(req.productId);
        int code = checkPurchase(ctx, product, System.currentTimeMillis());
        if (code != Code.SUCCESS) {
            log.warn("代币商店下单被拒绝 playerId={},productId={},code={}", playerId, req.productId, code);
            return new CommonResult<>(code);
        }
        TokenShopCfg cfg = product.cfg();
        ShopRechargeListCfg shop = GameDataManager.getShopRechargeListCfg(cfg.getRechargeid());
        if (shop == null) {
            log.warn("代币商店支付配置缺失 playerId={},goodsId={},rechargeId={}", playerId, cfg.getId(), cfg.getRechargeid());
            return new CommonResult<>(Code.SAMPLE_ERROR);
        }
        if (shop.getType() != RechargeCfgType.MONEY.getType()) {
            return nonMoneyOrderDetailInfo(shop, TokenShopConfigService.exchangeCost(cfg, shop));
        }
        if (shop.getPrice() == null || shop.getPrice().signum() <= 0) {
            log.warn("代币商店现金价格无效 playerId={},goodsId={},rechargeId={},price={}",
                    playerId, cfg.getId(), cfg.getRechargeid(), shop.getPrice());
            return new CommonResult<>(Code.SAMPLE_ERROR);
        }
        PreGenerateOrderData data = new PreGenerateOrderData();
        data.setType(RechargeCfgType.MONEY);
        data.setPrice(shop.getPrice());
        return new CommonResult<>(Code.SUCCESS, data);
    }

    @Override
    public boolean onReceivedRecharge(Player player, Order order) {
        if (order.getRechargeType() != getRechargeType() || order.getPlayerId() != player.getId()
                || order.getOrderStatus() != OrderStatus.PROCESSING) {
            log.warn("代币商店到账订单无效 playerId={},orderId={},orderPlayerId={},rechargeType={},status={}",
                    player.getId(), order.getId(), order.getPlayerId(), order.getRechargeType(), order.getOrderStatus());
            return false;
        }
        return route(player.getId(), () -> receiveOrder(player.getId(), order.getId()),
                rpc -> rpc.receiveTokenShopOrder(player.getId(), order.getId()), false);
    }

    /** 所属节点读取可信订单；已支付订单不再因商品下架、等级或联盟变化拒绝发货。 */
    public boolean receiveOrder(long playerId, String orderId) {
        SimPlayerContext ctx = contexts.getContext(playerId);
        Order order = orders.getOrder(orderId);
        if (ctx == null || order == null || order.getPlayerId() != playerId
                || order.getRechargeType() != getRechargeType() || order.getOrderStatus() != OrderStatus.PROCESSING) {
            log.warn("代币商店到账失败，上下文或可信订单无效 playerId={},orderId={},contextMissing={}",
                    playerId, orderId, ctx == null);
            return false;
        }
        TokenShopConfigService.Goods product = product(order.getProductId());
        if (product == null) {
            log.warn("代币商店到账失败，商品不存在 playerId={},orderId={},productId={}", playerId, orderId, order.getProductId());
            return false;
        }
        return deliver(ctx, product, Map.of(), orderId, System.currentTimeMillis());
    }

    @Override
    public boolean onReceivedRecharge(Player player, PreGenerateOrderData data) {
        return route(player.getId(), () -> completePurchase(player.getId(), data),
                rpc -> rpc.completeTokenShopPurchase(player.getId(), data), false);
    }

    public boolean completePurchase(long playerId, PreGenerateOrderData data) {
        return completePurchase(playerId, data, System.currentTimeMillis());
    }

    boolean completePurchase(long playerId, PreGenerateOrderData data, long now) {
        SimPlayerContext ctx = contexts.getContext(playerId);
        TokenShopConfigService.Goods product = product(data.getProductId());
        int code = checkPurchase(ctx, product, now);
        if (code != Code.SUCCESS) {
            log.warn("代币商店购买被拒绝 playerId={},productId={},payType={},code={}",
                    playerId, data.getProductId(), data.getType(), code);
            return false;
        }
        TokenShopCfg cfg = product.cfg();
        ShopRechargeListCfg shop = GameDataManager.getShopRechargeListCfg(cfg.getRechargeid());
        if (shop == null || data.getType() == null || data.getType() == RechargeCfgType.MONEY
                || shop.getType() != data.getType().getType()) {
            log.warn("代币商店支付类型不匹配 playerId={},goodsId={},rechargeId={},payType={},configuredType={}",
                    playerId, cfg.getId(), cfg.getRechargeid(), data.getType(), shop == null ? null : shop.getType());
            return false;
        }
        Map<Integer, Long> cost = TokenShopConfigService.exchangeCost(cfg, shop);
        if (!nonMoneyOrderDetailInfo(shop, cost).success()) {
            log.warn("代币商店兑换消耗无效 playerId={},goodsId={},rechargeId={},cost={}",
                    playerId, cfg.getId(), cfg.getRechargeid(), cost);
            return false;
        }
        long contribution = cost.getOrDefault(SimConstant.Item.ID_ALLIANCE_CONTRIBUTION, 0L);
        Map<Integer, Long> packCost = cost;
        if (contribution > 0) {
            packCost = new HashMap<>(cost);
            packCost.remove(SimConstant.Item.ID_ALLIANCE_CONTRIBUTION);
            if (!allianceAssets.deductContribution(playerId, contribution)) {
                log.warn("代币商店联盟贡献不足 playerId={},goodsId={},required={}", playerId, cfg.getId(), contribution);
                return false;
            }
        }
        boolean success = false;
        try {
            success = completeNonMoneyPurchase(packs, ctx.getPlayer(), data, shop, packCost, addType(cfg),
                    () -> deliver(ctx, product, cost, null, now));
            if (!success) {
                log.warn("代币商店扣费或交付失败 playerId={},goodsId={},cost={}", playerId, cfg.getId(), cost);
            }
            return success;
        } finally {
            if (!success) {
                refundContribution(ctx, contribution);
            }
        }
    }

    private TokenShopConfigService.Goods product(String productId) {
        try {
            return configs.goods(Integer.parseInt(productId));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private int checkPurchase(SimPlayerContext ctx, TokenShopConfigService.Goods product, long now) {
        if (ctx == null || product == null) {
            return Code.NOT_FOUND;
        }
        TokenShopCfg cfg = product.cfg();
        int currentSeason = cfg.getType() <= 2 ? seasonType(ctx, now) : 0;
        if (cfg.getType() <= 2 && cfg.getType() != currentSeason || cfg.getType() == 3 && !hasAlliance(ctx)) {
            log.warn("代币商店购买条件不符 playerId={},goodsId={},shopType={},seasonType={}",
                    ctx.playerId(), cfg.getId(), cfg.getType(), currentSeason);
            return Code.FORBID;
        }
        int code = availability(ctx, product, now);
        if (code != Code.SUCCESS) {
            log.warn("代币商店商品未开放 playerId={},goodsId={},enabled={},start={},end={},now={},level={},requiredLevel={}",
                    ctx.playerId(), cfg.getId(), cfg.getIsEnabled(), product.startTime(), product.endTime(), now,
                    ctx.getCurrentCasino() == null ? null : ctx.getCurrentCasino().getCasinoLevel(), cfg.getPurchaseLevel());
            return code;
        }
        long today = TimeHelper.getLocalDateTime(now).toLocalDate().toEpochDay();
        int purchased = purchased(ctx.getSimBaseData().getTokenShopPurchases().get(cfg.getId()), periodStart(cfg, today));
        if (cfg.getResetDaily() > 0 && purchased >= cfg.getResetDaily()) {
            log.warn("代币商店已达限购 playerId={},goodsId={},purchased={},limit={}",
                    ctx.playerId(), cfg.getId(), purchased, cfg.getResetDaily());
            return Code.BET_TO_LIMIT;
        }
        return Code.SUCCESS;
    }

    private boolean deliver(SimPlayerContext ctx, TokenShopConfigService.Goods product,
                            Map<Integer, Long> cost, String orderId, long now) {
        TokenShopCfg cfg = product.cfg();
        long today = TimeHelper.getLocalDateTime(now).toLocalDate().toEpochDay();
        long period = cfg.getResetDaily() > 0 ? periodStart(cfg, today) : 0;
        String desc = orderId == null ? "token-shop:" + cfg.getId() : orderId;
        CommonResult<ItemOperationResult> added = packs.addItems(ctx.playerId(), cfg.getGoods(), addType(cfg), desc, true);
        if (!added.success()) {
            log.warn("代币商店发货失败 playerId={},goodsId={},orderId={},goods={},code={}",
                    ctx.playerId(), cfg.getId(), orderId, cfg.getGoods(), added.code);
            return false;
        }
        if (cfg.getResetDaily() > 0) {
            TokenShopPurchaseData record = ctx.getSimBaseData().getTokenShopPurchases()
                    .computeIfAbsent(cfg.getId(), key -> new TokenShopPurchaseData());
            record.setPurchased(purchased(record, period) + 1);
            record.setPeriodStartDay(period);
        }
        // 发货已成功，保存、任务及推送异常不能造成退款或重复发货。
        try {
            saves.enqueueSave(ctx.getSimBaseData());
            if (cost.containsKey(SimConstant.Item.ID_SEASON_COIN)
                    || cfg.getGoods().containsKey(SimConstant.Item.ID_SEASON_COIN)) {
                saves.enqueueSave(ctx.getSeasonPlayerData());
            }
        } catch (RuntimeException e) {
            ctx.setLastSaveTime(0);
            log.error("代币商店保存失败，保留脏数据等待自动保存 playerId={},goodsId={}", ctx.playerId(), cfg.getId(), e);
        }
        afterPurchase(ctx, cfg, cost);
        try {
            ResTokenShopExchange res = new ResTokenShopExchange(Code.SUCCESS);
            res.goodsId = cfg.getId();
            res.goods = ItemUtils.buildItemInfo(cfg.getGoods());
            res.info = info(ctx, product, today);
            cluster.sendToPlayer(res, ctx.playerId());
        } catch (RuntimeException e) {
            log.error("代币商店购买成功推送失败 playerId={},goodsId={},orderId={}", ctx.playerId(), cfg.getId(), orderId, e);
        }
        return true;
    }

    private static AddType addType(TokenShopCfg cfg) {
        return cfg.getType() == 3 ? AddType.ALLIANCE_SHOP_BUY : AddType.ITEM_EXCHANGE;
    }

    private <T> T route(long playerId, Supplier<T> local, Function<ToSimBridge, T> remote, T unavailable) {
        if (contexts.getContext(playerId) != null) {
            return local.get();
        }
        var client = nodes.getSimClusterClient(playerId, null);
        if (client == null) {
            log.warn("代币商店所属节点不可用 playerId={}", playerId);
            return unavailable;
        }
        GameRpcContext rpc = GameRpcContext.getContext();
        RpcReqParameterBuilder previous = rpc.getReqParameterBuilder();
        try {
            rpc.withReqParameterBuilder(RpcReqParameterBuilder.create()
                    .addClusterClient(client).setTryMillisPerClient(1000));
            T result = remote.apply(bridge);
            return result == null ? unavailable : result;
        } finally {
            rpc.setReqParameterBuilder(previous);
        }
    }

    private void refundContribution(SimPlayerContext ctx, long amount) {
        if (amount > 0) {
            allianceAssets.grantContribution(ctx.playerId(), amount, 0, 0);
        }
    }

    private void afterPurchase(SimPlayerContext ctx, TokenShopCfg cfg, Map<Integer, Long> cost) {
        if (cfg.getType() <= 2) {
            try {
                tasks.onConditionEvent(ctx, new ActionConditionEvent(
                        ActionConditionEvent.Type.SEASON_SHOP_BUY, 0, 0, 0, 1, 0, false));
            } catch (RuntimeException e) {
                log.error("代币商店赛季购买任务触发失败 playerId={},goodsId={}", ctx.playerId(), cfg.getId(), e);
            }
        }
        long diamonds = cost.getOrDefault(ItemUtils.getDiamondItemId(), 0L);
        if (diamonds > 0) {
            try {
                shops.onDiamondConsume(ctx.getPlayer(), diamonds);
            } catch (RuntimeException e) {
                log.error("代币商店钻石消费事件触发失败 playerId={},goodsId={}", ctx.playerId(), cfg.getId(), e);
            }
        }
    }

    private int seasonType(SimPlayerContext ctx, long now) {
        seasons.ensureCurrent(ctx, now);
        SeasonPhase phase = ctx.getSeasonPlayerData().seasonPhase();
        return phase == SeasonPhase.ADVANCED ? 1 : phase == SeasonPhase.LOOP ? 2 : 0;
    }

    private boolean hasAlliance(SimPlayerContext ctx) {
        return alliances.getAlliance(alliances.getAllianceId(ctx.playerId())) != null;
    }

    private int availability(SimPlayerContext ctx, TokenShopConfigService.Goods goods, long now) {
        if (!goods.isOpen(now)) {
            return Code.FORBID;
        }
        return ctx.getCurrentCasino() != null
                && ctx.getCurrentCasino().getCasinoLevel() >= goods.cfg().getPurchaseLevel()
                ? Code.SUCCESS : Code.NOT_UNLOCKED;
    }

    private TokenShopGoodsInfo info(SimPlayerContext ctx, TokenShopConfigService.Goods product, long today) {
        TokenShopCfg cfg = product.cfg();
        TokenShopGoodsInfo info = new TokenShopGoodsInfo();
        info.goodsId = cfg.getId();
        info.goods = ItemUtils.buildItemInfo(cfg.getGoods());
        info.language = cfg.getLanguage();
        info.icon = cfg.getIcon();
        info.discountRate = cfg.getDiscountRate();
        info.startTime = product.startTime();
        info.endTime = product.endTime();
        info.purchaseLimit = cfg.getResetDaily();
        if (cfg.getResetDaily() > 0) {
            TokenShopPurchaseData record = ctx.getSimBaseData().getTokenShopPurchases().get(cfg.getId());
            long period = periodStart(cfg, today);
            info.purchased = purchased(record, period);
            if (cfg.getResetTime() > 0) {
                info.resetTime = TimeHelper.getTimestamp(LocalDate.ofEpochDay(period)
                        .plusDays(cfg.getResetTime()).atStartOfDay());
            }
        }

        info.rechargeInfo = SampleDataUtils.transRechargeCfgToRechargeInfo(cfg.getRechargeid(), ctx.getPlayer().getChannel());
        if (info.rechargeInfo != null && info.rechargeInfo.type == RechargeCfgType.ITEM_EXCHANGE.getType()
                && (info.rechargeInfo.redeem == null || info.rechargeInfo.redeem.isEmpty())) {
            info.rechargeInfo.redeem = ItemUtils.buildItemInfo(cfg.getCost());
        }
        return info;
    }

    private long periodStart(TokenShopCfg cfg, long today) {
        int days = cfg.getResetTime();
        if (days == 0) {
            return 0;
        }
        long openDay = TimeHelper.getLocalDateTime(serverOpenTimeService.getServerOpenTimeMillis())
                .toLocalDate().toEpochDay();
        return openDay + Math.floorDiv(today - openDay, days) * days;
    }

    private static int purchased(TokenShopPurchaseData record, long period) {
        return record == null || record.getPeriodStartDay() != period ? 0 : record.getPurchased();
    }
}
