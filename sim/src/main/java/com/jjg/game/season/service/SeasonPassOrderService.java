package com.jjg.game.season.service;

import com.jjg.game.core.constant.AddType;
import com.jjg.game.core.constant.Code;
import com.jjg.game.core.data.CommonResult;
import com.jjg.game.core.data.Order;
import com.jjg.game.core.data.Player;
import com.jjg.game.core.data.PreGenerateOrderData;
import com.jjg.game.core.listener.OrderGenerate;
import com.jjg.game.core.pb.RechargeCfgType;
import com.jjg.game.core.pb.RechargeType;
import com.jjg.game.core.pb.ReqGenerateOrder;
import com.jjg.game.core.service.CorePlayerService;
import com.jjg.game.core.service.PlayerPackService;
import com.jjg.game.sampledata.bean.ShopRechargeListCfg;
import com.jjg.game.season.constant.SeasonConstant;
import com.jjg.game.season.data.SeasonPlayerData;
import com.jjg.game.sim.data.SimPlayerContext;
import com.jjg.game.sim.manager.SimPlayerContextRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 通行证订单适配器。预下单 productId 使用 passId_track，创建订单时转换为带赛季范围的业务标识。
 */
@Service
public class SeasonPassOrderService implements OrderGenerate {
    private static final Logger log = LoggerFactory.getLogger(SeasonPassOrderService.class);

    private final SimPlayerContextRegistry contextRegistry;
    private final SeasonLifecycleService lifecycleService;
    private final SeasonPassConfigService passConfig;
    private final SeasonPassService passService;
    private final CorePlayerService playerService;
    @Autowired
    private PlayerPackService playerPackService;

    public SeasonPassOrderService(SimPlayerContextRegistry contextRegistry,
                                  SeasonLifecycleService lifecycleService,
                                  SeasonPassConfigService passConfig,
                                  SeasonPassService passService, CorePlayerService playerService) {
        this.contextRegistry = contextRegistry;
        this.lifecycleService = lifecycleService;
        this.passConfig = passConfig;
        this.passService = passService;
        this.playerService = playerService;
    }

    @Override
    public CommonResult<PreGenerateOrderData> generateOrderDetailInfo(Player player, ReqGenerateOrder req) {
        CommonResult<PreGenerateOrderData> result = new CommonResult<>(Code.FAIL);
        SimPlayerContext ctx = contextRegistry.getContext(player.getId());
        if (ctx == null) {
            log.warn("通行证下单失败，获取玩家ctx失败 playerId={},productId={}", player.getId(), req.productId);
            return result;
        }
        lifecycleService.ensureCurrent(ctx, System.currentTimeMillis());
        SeasonPlayerData data = ctx.getSeasonPlayerData();
        PurchaseTarget target = parsePurchaseTarget(req.productId);
        if (target == null) {
            log.warn("通行证下单失败，商品参数无效 playerId={},productId={}", player.getId(), req.productId);
            return result;
        }
        SeasonPassConfigService.PassDefinition pass = passConfig.activePass(data.getSeasonId(), target.passId());
        if (pass == null) {
            log.warn("通行证下单失败，未找到当前赛季有效的通行证配置 playerId={},productId={},seasonId={},passId={}",
                    player.getId(), req.productId, data.getSeasonId(), target.passId());
            return result;
        }
        if (!passService.hasTrackRewards(pass, target.track())) {
            log.warn("通行证下单失败，轨道未配置奖励 playerId={},productId={},passId={},track={}",
                    player.getId(), req.productId, pass.id(), target.track());
            return result;
        }
        int purchased = data.getPassPurchasedTracks().getOrDefault(pass.id(), 0);
        if ((purchased & SeasonPassService.trackBit(target.track())) != 0) {
            log.warn("通行证下单失败，轨道已购买 playerId={},productId={},passId={},track={},purchased={}",
                    player.getId(), req.productId, pass.id(), target.track(), purchased);
            return result;
        }
        ShopRechargeListCfg shop = passConfig.shop(pass, target.track());
        if (shop == null) {
            log.warn("通行证下单失败，未找到充值商品配置 playerId={},productId={},passId={},track={}",
                    player.getId(), req.productId, pass.id(), target.track());
            return result;
        }
        if (shop.getType() != RechargeCfgType.MONEY.getType()) {
            result = shop.getType() == RechargeCfgType.ITEM_EXCHANGE.getType()
                    ? singleExchangeOrderDetailInfo(playerPackService, player, shop, req.buyCount, AddType.SIM_SEASON_PASS)
                    : nonMoneyOrderDetailInfo(shop);
            if (result.success()) {
                result.data.setOrderProductId(passService.orderToken(data, pass, target.track()));
            }
            return result;
        }
        String channelProductId = SeasonPassService.channelProductId(player, shop);
        if (shop.getPrice() == null) {
            log.warn("通行证下单失败，充值商品价格为空 playerId={},productId={},passId={},track={}",
                    player.getId(), req.productId, pass.id(), target.track());
            return result;
        }
        if (shop.getPrice().signum() <= 0) {
            log.warn("通行证下单失败，充值商品价格非正数 playerId={},productId={},passId={},track={},price={}",
                    player.getId(), req.productId, pass.id(), target.track(), shop.getPrice());
            return result;
        }
        if (channelProductId == null) {
            log.warn("通行证下单失败，渠道商品ID为null playerId={},productId={},passId={},track={}",
                    player.getId(), req.productId, pass.id(), target.track());
            return result;
        }
        if (channelProductId.isBlank()) {
            log.warn("通行证下单失败，渠道商品ID为空白 playerId={},productId={},passId={},track={}",
                    player.getId(), req.productId, pass.id(), target.track());
            return result;
        }

        int tmpIndex = (target.passId % 1000) - 2;
        int round = tmpIndex * 2 + (target.track() == SeasonConstant.PassTrack.BASIC ? 0 : 1);
        int tmpCode = tmpOrderHandle(playerService, player, 1007, AddType.SIM_SEASON_PASS, round);
        if (tmpCode != Code.SUCCESS) {
            log.warn("赛季通行证临时钻石购买失败 playerId={},productId={},round={},code={}",
                    player.getId(), req.productId, round, tmpCode);
            return new CommonResult<>(tmpCode);
        }

        req.productId = passService.orderToken(data, pass, target.track());
        result.code = Code.SUCCESS;
        PreGenerateOrderData preGenerateOrderData = new PreGenerateOrderData();
        preGenerateOrderData.setType(RechargeCfgType.MONEY);
        preGenerateOrderData.setPrice(shop.getPrice());
        result.data = preGenerateOrderData;
        return result;
    }

    @Override
    public RechargeType getRechargeType() {
        return RechargeType.SEASON_PASS;
    }

    @Override
    public boolean onReceivedRecharge(Player player, Order order) {
        if (order.getRechargeType() != getRechargeType()) {
            return true;
        }
        if (order.getPlayerId() != player.getId()) {
            log.error("通行证充值订单玩家不匹配 orderId={},orderPlayerId={},playerId={}",
                    order.getId(), order.getPlayerId(), player.getId());
            return false;
        }
        SimPlayerContext ctx = contextRegistry.getContext(player.getId());
        if (ctx == null) {
            return false;
        }
        lifecycleService.ensureCurrent(ctx, System.currentTimeMillis());
        SeasonPassService.PurchaseToken token = passService.parseOrderToken(order.getProductId());
        boolean success = passService.unlock(ctx, token);
        if (!success) {
            log.error("通行证充值到账失败 playerId={},orderId={},token={}",
                    player.getId(), order.getId(), order.getProductId());
        }
        return success;
    }

    private PurchaseTarget parsePurchaseTarget(String productId) {
        if (productId == null || productId.isBlank()) {
            return null;
        }
        int separator = productId.indexOf('_');
        if (separator <= 0 || separator != productId.lastIndexOf('_')
                || separator >= productId.length() - 1) {
            return null;
        }
        try {
            int passId = Integer.parseInt(productId.substring(0, separator));
            int track = Integer.parseInt(productId.substring(separator + 1));
            return passId > 0 && SeasonPassService.validTrack(track)
                    ? new PurchaseTarget(passId, track) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private record PurchaseTarget(int passId, int track) {
    }

    @Override
    public boolean onReceivedRecharge(Player player, PreGenerateOrderData preGenerateOrderData) {
        SimPlayerContext ctx = contextRegistry.getContext(player.getId());
        if (ctx == null) {
            return false;
        }
        lifecycleService.ensureCurrent(ctx, System.currentTimeMillis());
        SeasonPassService.PurchaseToken token = passService.parseOrderToken(preGenerateOrderData.getProductId());
        if (token == null || !token.seasonKey().equals(ctx.getSeasonPlayerData().getSeasonKey())) {
            return false;
        }
        SeasonPassConfigService.PassDefinition pass = passConfig.activePass(ctx.getSeasonPlayerData().getSeasonId(), token.passId());
        return pass != null && completeNonMoneyPurchase(preGenerateOrderData,
                passConfig.shop(pass, token.track()),
                () -> passService.unlock(ctx, token));
    }
}
