package com.jjg.game.season.service;

import com.jjg.game.common.redis.RedisLock;
import com.jjg.game.core.constant.*;
import com.jjg.game.core.data.*;
import com.jjg.game.core.service.MailService;
import com.jjg.game.core.service.PlayerPackService;
import com.jjg.game.core.utils.ItemUtils;
import com.jjg.game.season.config.LuckyDrawDefinition;
import com.jjg.game.season.dao.LuckyDrawDao;
import com.jjg.game.season.data.LuckyDrawData;
import com.jjg.game.season.data.LuckyDrawData.*;
import com.jjg.game.season.model.SeasonPhase;
import com.jjg.game.season.pb.res.ResLuckyDraw;
import com.jjg.game.season.pb.struct.*;
import com.jjg.game.sim.data.SimPlayerContext;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;

/** 购买资格来自真实登录账本；购买、选择、回调共用玩家锁，随机结果先保存再交付。 */
@Service
public class LuckyDrawService {
    private final LuckyDrawCatalog catalog;
    private final LuckyDrawDao dao;
    private final RedisLock locks;
    private final LuckyDrawRandom random;
    private final PlayerPackService pack;
    private final MailService mail;

    public LuckyDrawService(LuckyDrawCatalog catalog, LuckyDrawDao dao, RedisLock locks,
                            LuckyDrawRandom random, PlayerPackService pack, MailService mail) {
        this.catalog = catalog; this.dao = dao; this.locks = locks;
        this.random = random; this.pack = pack; this.mail = mail;
    }
    public <T> T locked(long playerId, Supplier<T> action) {
        return locks.lockAndGet("season-lucky-draw:" + playerId, 5, action);
    }

    public ResLuckyDraw info(SimPlayerContext ctx, int activityId, int drawType, boolean enter, long now) {
        return execute(ctx, 1, () -> {
            LuckyDrawData d = current(ctx, activityId, drawType, now);
            if (d == null) return response(1, Code.NOT_FOUND);
            if (enter) { d.seenRevision = d.signalRevision; dao.save(d); }
            return state(ctx, d, now, 1);
        });
    }
    public ResLuckyDraw select(SimPlayerContext ctx, int activityId, int drawType, int grandId, long now) {
        return execute(ctx, 3, () -> {
            LuckyDrawData d = current(ctx, activityId, drawType, now);
            if (d == null) return response(3, Code.NOT_FOUND);
            if (d.grandObtained) return response(3, Code.FORBID);
            if (d.definition.grand(grandId) == null) return response(3, Code.PARAM_ERROR);
            d.selectedGrandId = grandId;
            dao.save(d);
            return state(ctx, d, now, 3);
        });
    }
    public ResLuckyDraw buy(SimPlayerContext ctx, int activityId, int drawType, String requestId, long now) {
        if (!validRequestId(requestId)) return response(2, Code.PARAM_ERROR);
        return execute(ctx, 2, () -> {
            LuckyDrawData d = current(ctx, activityId, drawType, now);
            if (d == null) return response(2, Code.NOT_FOUND);
            if (d.definition.priceType() == 1) return response(2, Code.PARAM_ERROR);
            String key = "draw-" + requestId;
            Receipt old = d.receipts.get(key);
            if (old != null) {
                deliver(d, key, now);
                return result(ctx, d, old, requestId, now, true, 2);
            }
            if (!canDraw(d)) return response(2, Code.FORBID);
            Receipt receipt = new Receipt(); receipt.status = "DEBITING"; receipt.createdAt = now;
            d.availableDraws--;
            d.receipts.put(key, receipt);
            dao.save(d);
            int itemId = d.definition.priceType() == 2 ? ItemUtils.getDiamondItemId() : ItemUtils.getGoldItemId();
            var removed = pack.removeItems(ctx.getPlayer(), Map.of(itemId, d.definition.price()), AddType.ACTIVITY, d.id + ':' + key);
            if (removed == null) return response(2, Code.FAIL); // 不确定是否已扣款，不恢复资格重扣
            if (!removed.success()) {
                d.availableDraws++; d.receipts.remove(key); dao.save(d);
                return response(2, removed.code);
            }
            draw(d, receipt);
            dao.save(d);
            deliver(d, key, now);
            return result(ctx, d, receipt, requestId, now, false, 2);
        });
    }
    public ResLuckyDraw daily(SimPlayerContext ctx, int activityId, int drawType, long now) {
        return execute(ctx, 4, () -> {
            LuckyDrawData d = current(ctx, activityId, drawType, now);
            if (d == null) return response(4, Code.NOT_FOUND);
            String key = dailyKey(d, now);
            Receipt receipt = d.receipts.get(key);
            boolean replay = receipt != null;
            if (receipt == null) {
                receipt = new Receipt(); receipt.status = "PENDING"; receipt.createdAt = now;
                receipt.items = new LinkedHashMap<>(d.definition.dailyRewards());
                d.receipts.put(key, receipt); dao.save(d);
            }
            deliver(d, key, now);
            return result(ctx, d, receipt, key, now, replay, 4);
        });
    }

    /** 必须已经是当前赛季的真实在线请求；这里只读取登录账本，不把查询/回调当成登录事件。 */
    LuckyDrawData current(SimPlayerContext ctx, int activityId, int drawType, long now) {
        if (drawType != LuckyDrawDefinition.ITEM_DRAW || activityId < 0) return null;
        var season = ctx.getSeasonPlayerData();
        if (season == null || season.seasonPhase() != SeasonPhase.LOOP || season.getCycleIndex() < 1
                || season.getSeasonKey() == null || now < season.getStartTime() || now >= season.getEndTime()) return null;
        String id = ctx.playerId() + ":" + season.getSeasonKey() + ":" + drawType;
        LuckyDrawData d = dao.get(id);
        if (d == null) {
            LuckyDrawDefinition definition = catalog.stage(season.getCycleIndex());
            if (definition == null || (activityId != 0 && definition.activityId() != activityId)) return null;
            catalog.validateReferences(definition);
            d = new LuckyDrawData(); d.id = id; d.playerId = ctx.playerId(); d.seasonKey = season.getSeasonKey();
            d.startTime = season.getStartTime(); d.endTime = season.getEndTime(); d.zoneId = ZoneId.systemDefault().getId();
            d.definition = definition; d.selectedGrandId = definition.grands().getFirst().id();
            dao.save(d);
        }
        if ((activityId != 0 && d.definition.activityId() != activityId) || now >= d.endTime) return null;
        long today = dayStart(now, ZoneId.of(d.zoneId));
        var loginDays = dao.loginDays(ctx.playerId(), d.startTime, Math.min(d.endTime, today + 1), d.lastLoginDay);
        boolean changed = false;
        for (long day : loginDays) {
            if (day > d.lastLoginDay && day >= d.startTime && day <= today && day < d.endTime) {
                int held = (d.reservation == null ? 0 : 1) + (int) d.receipts.values().stream()
                        .filter(r -> "DEBITING".equals(r.status)).count();
                d.availableDraws = Math.min(d.availableDraws, Math.max(0, d.definition.maxCarryDays() - held)) + 1;
                d.lastLoginDay = day; d.signalRevision++; changed = true;
            }
        }
        if (changed) dao.save(d);
        return d;
    }

    Reservation reserve(SimPlayerContext ctx, int activityId, int drawType, String requestId, long now) {
        if (!validRequestId(requestId)) return null;
        LuckyDrawData d = current(ctx, activityId, drawType, now);
        if (d == null || d.definition.priceType() != 1 || d.receipts.containsKey("draw-" + requestId)) return null;
        if (d.reservation != null) return d.reservation.requestId.equals(requestId) ? d.reservation : null;
        if (!canDraw(d)) return null;
        String sku = SeasonPassService.channelProductId(ctx.getPlayer(), catalog.shop(d.definition.shopId()));
        if (sku == null || sku.isBlank()) return null;
        Reservation r = new Reservation(); r.requestId = requestId; r.token = d.id + '/' + UUID.randomUUID();
        r.createdAt = now; r.channelProductId = sku;
        d.reservation = r; d.availableDraws--; dao.save(d);
        return r;
    }
    void release(long playerId, String token) {
        LuckyDrawData d = tokenData(playerId, token);
        if (d != null && d.reservation != null && d.reservation.token.equals(token)) {
            d.reservation = null; d.availableDraws = Math.min(4, d.availableDraws + 1); dao.save(d);
        }
    }
    LuckyDrawData tokenData(long playerId, String token) {
        if (token == null || token.lastIndexOf('/') <= 0) return null;
        LuckyDrawData d = dao.get(token.substring(0, token.lastIndexOf('/')));
        return d != null && d.playerId == playerId ? d : null;
    }
    boolean paid(Player player, Order order, long now) {
        if (order.getPlayerId() != player.getId() || order.getId() == null || order.getId().isBlank()) return false;
        LuckyDrawData d = tokenData(player.getId(), order.getProductId());
        if (d == null || order.getPrice() == null
                || order.getPrice().compareTo(BigDecimal.valueOf(d.definition.price(), 2)) != 0) return false;
        for (var entry : d.receipts.entrySet()) if (order.getId().equals(entry.getValue().orderId)) {
            if (!order.getProductId().equals(entry.getValue().token)) return false;
            deliver(d, entry.getKey(), now);
            return completed(entry.getValue());
        }
        Reservation r = d.reservation;
        if (r == null || !r.token.equals(order.getProductId()) || !r.channelProductId.equals(order.getChannelProductId())) return false;
        Receipt receipt = new Receipt(); receipt.orderId = order.getId(); receipt.token = r.token; receipt.createdAt = now;
        draw(d, receipt); // 即使切季也只在订单所属的旧期快照抽奖，不修改新赛季进度
        String key = "draw-" + r.requestId;
        d.receipts.put(key, receipt); d.reservation = null; d.settled = false; dao.save(d);
        deliver(d, key, now);
        return completed(receipt);
    }

    private boolean canDraw(LuckyDrawData d) {
        return d.availableDraws > 0 && d.obtainedRewards.size() < d.definition.rewards().size() && d.reservation == null
                && d.receipts.values().stream().noneMatch(r -> "DEBITING".equals(r.status));
    }
    private void draw(LuckyDrawData d, Receipt receipt) {
        var remaining = d.definition.rewards().stream().filter(r -> !d.obtainedRewards.contains(r.id())).toList();
        if (remaining.isEmpty()) throw new IllegalStateException("reward pool is empty");
        var prize = remaining.get(random.nextIndex(remaining.size()));
        receipt.rewardId = prize.id();
        receipt.items = new LinkedHashMap<>(prize.grand() ? d.definition.grand(d.selectedGrandId).items() : prize.items());
        if (prize.grand()) { receipt.grandId = d.selectedGrandId; d.grandObtained = true; }
        d.obtainedRewards.add(prize.id()); receipt.status = "PENDING";
    }
    private void deliver(LuckyDrawData d, String key, long now) {
        Receipt r = d.receipts.get(key);
        if (!"PENDING".equals(r.status)) return;
        if (now >= d.endTime) {
            if (mail.addMailIfAbsent(d.playerId, "小额抽奖礼包奖励", "您已获得的奖励已补发，请查收。",
                    ItemUtils.buildItems(r.items), AddType.ACTIVITY, "lucky-draw:" + d.id + ':' + key)) {
                r.status = "MAIL"; dao.save(d);
            }
            return;
        }
        r.status = "DELIVERING"; dao.save(d);
        var added = pack.addItems(d.playerId, r.items, AddType.ACTIVITY, d.id + ':' + key, true);
        if (added != null) r.status = added.success() ? "BAG" : "PENDING";
        dao.save(d);
    }
    public void settleExpired(long now) {
        String after = null;
        while (true) {
            var page = dao.expired(now, after);
            if (page.isEmpty()) return;
            for (var candidate : page) locked(candidate.playerId, () -> {
                LuckyDrawData d = dao.get(candidate.id);
                if (d != null && now >= d.endTime && !d.settled) {
                    d.availableDraws = 0; // 未使用的购买资格作废，不转换奖励
                    for (String key : d.receipts.keySet()) deliver(d, key, now);
                    d.settled = d.receipts.values().stream().allMatch(LuckyDrawService::completed);
                    dao.save(d);
                }
                return true;
            });
            after = page.getLast().id;
        }
    }
    private static boolean completed(Receipt r) { return "BAG".equals(r.status) || "MAIL".equals(r.status); }
    public static boolean validRequestId(String value) { return value != null && value.matches("[a-zA-Z0-9-]{1,64}"); }
    static long dayStart(long now, ZoneId zone) { return Instant.ofEpochMilli(now).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli(); }
    private String dailyKey(LuckyDrawData d, long now) { return "daily-" + dayStart(now, ZoneId.of(d.zoneId)); }
    private ResLuckyDraw execute(SimPlayerContext ctx, int action, Supplier<ResLuckyDraw> task) {
        var result = locked(ctx.playerId(), task);
        return result == null ? response(action, Code.FAIL) : result;
    }
    private static ResLuckyDraw response(int action, int code) { var r = new ResLuckyDraw(code); r.action = action; return r; }
    private ResLuckyDraw state(SimPlayerContext ctx, LuckyDrawData d, long now, int action) {
        var r = response(action, Code.SUCCESS); r.info = assemble(ctx.getPlayer(), d, now); return r;
    }
    private ResLuckyDraw result(SimPlayerContext ctx, LuckyDrawData d, Receipt receipt, String requestId,
                                long now, boolean replay, int action) {
        var r = state(ctx, d, now, action);
        r.requestId = requestId; r.rewardId = receipt.rewardId; r.grandId = receipt.grandId;
        r.replay = replay; r.rewardPending = !completed(receipt);
        if ("DEBITING".equals(receipt.status)) r.code = Code.FAIL;
        if (completed(receipt)) r.rewards = ItemUtils.buildItemInfo(receipt.items);
        return r;
    }
    LuckyDrawInfo assemble(Player player, LuckyDrawData d, long now) {
        var i = new LuckyDrawInfo(); var def = d.definition;
        i.activityId = def.activityId(); i.drawType = 1; i.seasonKey = d.seasonKey;
        i.name = def.name(); i.iconPath = def.iconPath(); i.serverTime = now; i.startTime = d.startTime; i.endTime = d.endTime;
        i.nextDailyReset = Instant.ofEpochMilli(now).atZone(ZoneId.of(d.zoneId)).toLocalDate().plusDays(1)
                .atStartOfDay(ZoneId.of(d.zoneId)).toInstant().toEpochMilli();
        i.price = def.price(); i.priceType = def.priceType();
        if (def.priceType() == 1) i.channelProductId = SeasonPassService.channelProductId(player, catalog.shop(def.shopId()));
        i.availableDraws = Math.min(d.availableDraws, def.rewards().size() - d.obtainedRewards.size());
        i.totalDrawn = d.obtainedRewards.size(); i.selectedGrandId = d.selectedGrandId; i.grandObtained = d.grandObtained;
        i.redDot = d.seenRevision < d.signalRevision;
        Receipt daily = d.receipts.get(dailyKey(d, now));
        i.dailyClaimed = daily != null && completed(daily); i.dailyRedDot = !i.dailyClaimed;
        i.pendingPayment = d.reservation != null;
        i.drawPending = d.receipts.values().stream().anyMatch(r -> !completed(r));
        i.dailyRewards = ItemUtils.buildItemInfo(def.dailyRewards());
        i.prizes = def.rewards().stream().map(p -> {
            var v = new LuckyDrawPrizeInfo(); v.id = p.id(); v.slot = p.slot(); v.grand = p.grand();
            v.obtained = d.obtainedRewards.contains(p.id());
            v.items = ItemUtils.buildItemInfo(p.grand() ? def.grand(d.selectedGrandId).items() : p.items()); return v;
        }).toList();
        i.grandChoices = def.grands().stream().map(g -> {
            var v = new LuckyDrawPrizeInfo(); v.id = g.id(); v.grand = true;
            v.items = ItemUtils.buildItemInfo(g.items()); return v;
        }).toList();
        return i;
    }
}
