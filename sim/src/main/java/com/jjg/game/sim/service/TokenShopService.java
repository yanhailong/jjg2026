package com.jjg.game.sim.service;

import com.jjg.game.alliance.dao.AlliancePlayerDao;
import com.jjg.game.alliance.service.AllianceAssetService;
import com.jjg.game.alliance.service.AllianceCacheService;
import com.jjg.game.common.utils.TimeHelper;
import com.jjg.game.core.base.condition.numeric.ActionConditionEvent;
import com.jjg.game.core.constant.AddType;
import com.jjg.game.core.constant.Code;
import com.jjg.game.core.data.CommonResult;
import com.jjg.game.core.data.ItemOperationResult;
import com.jjg.game.core.pb.KVInfo;
import com.jjg.game.core.service.CorePlayerService;
import com.jjg.game.core.service.PlayerPackService;
import com.jjg.game.core.service.ServerOpenTimeService;
import com.jjg.game.core.service.ShopService;
import com.jjg.game.core.utils.ItemUtils;
import com.jjg.game.sampledata.bean.TokenShopCfg;
import com.jjg.game.season.model.SeasonPhase;
import com.jjg.game.season.service.SeasonLifecycleService;
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

/** 在玩家所属 SIM 逻辑线程执行；限购随玩家基础数据持久化。 */
@Service
public class TokenShopService {
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

    public TokenShopService(TokenShopConfigService configs, SeasonLifecycleService seasons,
                            PlayerPackService packs, AllianceCacheService alliances,
                            AllianceAssetService allianceAssets, SimAutoSaveService saves,
                            SimTaskService tasks, ShopService shops, ServerOpenTimeService serverOpenTimeService,
                            AlliancePlayerDao alliancePlayerDao, CorePlayerService playerService,
                            SimConfigCacheService configCache) {
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
        res.hasItemCount = itemCount(ctx, shopData.getItemId());

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

    public ResTokenShopExchange exchange(SimPlayerContext ctx, int goodsId, int count) {
        return exchange(ctx, goodsId, count, System.currentTimeMillis());
    }

    ResTokenShopExchange exchange(SimPlayerContext ctx, int goodsId, int count, long now) {
        ResTokenShopExchange res = new ResTokenShopExchange(Code.SUCCESS);
        res.goodsId = goodsId;
        if (count <= 0) {
            res.code = Code.PARAM_ERROR;
            return res;
        }
        TokenShopConfigService.Goods product = configs.goods(goodsId);
        if (product == null) {
            res.code = Code.NOT_FOUND;
            return res;
        }
        TokenShopCfg cfg = product.cfg();
        if ((cfg.getType() <= 2 && cfg.getType() != seasonType(ctx, now))
                || (cfg.getType() == 3 && !hasAlliance(ctx))) {
            res.code = Code.FORBID;
            return res;
        }
        res.code = availability(ctx, product, now);
        if (res.code != Code.SUCCESS) {
            return res;
        }
        long today = TimeHelper.getLocalDateTime(now).toLocalDate().toEpochDay();
        TokenShopPurchaseData record = ctx.getSimBaseData().getTokenShopPurchases().get(goodsId);
        long period = periodStart(cfg, today);
        int purchased = purchased(record, period);
        if (cfg.getResetDaily() > 0 && count > cfg.getResetDaily() - purchased) {
            res.code = Code.BET_TO_LIMIT;
            return res;
        }
        Map<Integer, Long> cost;
        Map<Integer, Long> goods;
        try {
            cost = multiply(cfg.getCost(), count);
            goods = multiply(cfg.getGoods(), count);
        } catch (ArithmeticException e) {
            res.code = Code.PARAM_ERROR;
            return res;
        }
        AddType addType = cfg.getType() == 3 ? AddType.ALLIANCE_SHOP_BUY : AddType.ITEM_EXCHANGE;
        String desc = "token-shop:" + goodsId;
        long contribution = cost.getOrDefault(SimConstant.Item.ID_ALLIANCE_CONTRIBUTION, 0L);
        cost.remove(SimConstant.Item.ID_ALLIANCE_CONTRIBUTION);
        if (contribution > 0 && !allianceAssets.deductContribution(ctx.playerId(), contribution)) {
            res.code = Code.NOT_ENOUGH_ITEM;
            return res;
        }
        CommonResult<ItemOperationResult> removed = packs.removeItems(ctx.getPlayer(), cost, addType, desc);
        if (!removed.success()) {
            refundContribution(ctx, contribution);
            res.code = removed.code;
            return res;
        }
        CommonResult<ItemOperationResult> added;
        try {
            added = packs.addItems(ctx.playerId(), goods, addType, desc, true);
        } catch (RuntimeException e) {
            log.error("代币商店发货异常 playerId={},goodsId={}", ctx.playerId(), goodsId, e);
            added = new CommonResult<>(Code.EXCEPTION);
        }
        if (!added.success()) {
            try {
                CommonResult<?> rollback = packs.addItems(ctx.playerId(), cost, AddType.FAIL_ROLLBACK, desc, true);
                if (!rollback.success()) {
                    log.error("代币商店退款失败，需人工修复 playerId={},goodsId={},cost={},code={}",
                            ctx.playerId(), goodsId, cost, rollback.code);
                }
            } finally {
                refundContribution(ctx, contribution);
            }
            res.code = added.code;
            return res;
        }
        if (cfg.getResetDaily() > 0) {
            if (record == null) {
                record = new TokenShopPurchaseData();
                ctx.getSimBaseData().getTokenShopPurchases().put(goodsId, record);
            }
            record.setPeriodStartDay(period);
            record.setPurchased(purchased + count);
        }
        res.goods = ItemUtils.buildItemInfo(goods);
        res.info = info(ctx, product, today);
        //发货已经成功，落库或任务通知异常不能改成可重试的兑换失败。
        try {
            saves.enqueueSave(ctx.getSimBaseData());
            if (cost.containsKey(SimConstant.Item.ID_SEASON_COIN)
                    || goods.containsKey(SimConstant.Item.ID_SEASON_COIN)) {
                saves.enqueueSave(ctx.getSeasonPlayerData());
            }
        } catch (RuntimeException e) {
            ctx.setLastSaveTime(0);
            log.error("代币商店保存失败，保留脏数据等待自动保存 playerId={},goodsId={}", ctx.playerId(), goodsId, e);
        }
        afterPurchase(ctx, cfg, cost);
        return res;
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
        info.cost = cfg.getCost() == null ? List.of() : ItemUtils.buildItemInfo(cfg.getCost());
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

    private static Map<Integer, Long> multiply(Map<Integer, Long> source, int count) {
        Map<Integer, Long> result = new HashMap<>();
        if (source != null) {
            source.forEach((id, value) -> result.put(id, Math.multiplyExact(value, count)));
        }
        return result;
    }
}
