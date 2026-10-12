package com.jjg.game.season.service;

import com.jjg.game.common.redis.RedisLock;
import com.jjg.game.core.constant.AddType;
import com.jjg.game.core.constant.Code;
import com.jjg.game.core.data.*;
import com.jjg.game.core.service.MailService;
import com.jjg.game.core.service.PlayerPackService;
import com.jjg.game.core.utils.ItemUtils;
import com.jjg.game.season.dao.ContinuousGiftDao;
import com.jjg.game.season.data.ContinuousGiftData;
import com.jjg.game.season.data.ContinuousGiftData.Delivery;
import com.jjg.game.season.data.ContinuousGiftData.Reservation;
import com.jjg.game.season.pb.res.ResContinuousGift;
import com.jjg.game.season.pb.struct.*;
import com.jjg.game.sim.data.SimPlayerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.*;
import java.util.function.Supplier;

import static com.jjg.game.season.service.ContinuousGiftConfigService.Definition;

@Service
public class ContinuousGiftService {
    private static final Logger log = LoggerFactory.getLogger(ContinuousGiftService.class);
    private final ContinuousGiftConfigService config;
    private final ContinuousGiftDao dao;
    private final RedisLock locks;
    private final PlayerPackService pack;
    private final MailService mail;

    public ContinuousGiftService(ContinuousGiftConfigService config, ContinuousGiftDao dao,
                                 RedisLock locks, PlayerPackService pack, MailService mail) {
        this.config = config;
        this.dao = dao;
        this.locks = locks;
        this.pack = pack;
        this.mail = mail;
    }

    public <T> T locked(long playerId, Supplier<T> action) {
        return locks.lockAndGet("continuous-gift:" + playerId, 5, action);
    }

    public ResContinuousGift list(SimPlayerContext ctx, int activityId, long now) {
        ResContinuousGift result = locked(ctx.playerId(), () -> {
            var response = response(1, Code.SUCCESS);
            List<ContinuousGiftInfo> infos = new ArrayList<>();
            for (Definition d : config.definitions()) {
                if (!eligible(ctx, d, now) || (activityId != 0 && d.config().getId() != activityId)) continue;
                ContinuousGiftData data = load(ctx.playerId(), d);
                if (activityId != 0 && !data.viewed) {
                    data.viewed = true;
                    dao.save(data);
                }
                infos.add(info(ctx.getPlayer(), d, data, now));
            }
            response.activities = infos;
            if (activityId != 0 && infos.isEmpty()) response.code = Code.NOT_FOUND;
            return response;
        });
        return result == null ? response(1, Code.FAIL) : result;
    }

    public ResContinuousGift buy(SimPlayerContext ctx, int activityId, int day, long now) {
        ResContinuousGift result = locked(ctx.playerId(), () -> {
            Definition d = config.find(activityId);
            if (!eligible(ctx, d, now)) return response(2, Code.FORBID);
            var chest = d.chest(day);
            if (chest == null || chest.priceType() == 1) return response(2, Code.PARAM_ERROR);
            ContinuousGiftData data = load(ctx.playerId(), d);
            if (!canBuy(data, day, now) || data.reservation != null) return response(2, Code.REPEAT_OP);
            String key = "day-" + day;
            // 先保存消费意图。进程在扣款中断时保持 DEBITING，禁止重扣，交由对账处理。
            Delivery delivery = new Delivery();
            delivery.status = "DEBITING";
            delivery.rewards = new LinkedHashMap<>(chest.rewardItems());
            data.deliveries.put(key, delivery);
            dao.save(data);
            int currency = chest.priceType() == 2 ? ItemUtils.getDiamondItemId() : ItemUtils.getGoldItemId();
            CommonResult<?> removed = pack.removeItems(ctx.getPlayer(), Map.of(currency, chest.price()),
                    AddType.ACTIVITY, data.id + ':' + key);
            if (removed == null) return response(2, Code.FAIL);
            if (!removed.success()) {
                data.deliveries.remove(key);
                dao.save(data);
                return response(2, removed.code);
            }
            purchased(data, day, now, delivery);
            deliver(data, key, now);
            return success(ctx.getPlayer(), d, data, now, 2, delivery);
        });
        return result == null ? response(2, Code.FAIL) : result;
    }

    public ResContinuousGift claim(SimPlayerContext ctx, int activityId, long now) {
        ResContinuousGift result = locked(ctx.playerId(), () -> {
            Definition d = config.find(activityId);
            if (!eligible(ctx, d, now)) return response(3, Code.FORBID);
            ContinuousGiftData data = load(ctx.playerId(), d);
            if (data.grandClaimed || data.purchasedDays.size() != data.totalDays)
                return response(3, Code.REPEAT_OP);
            Delivery delivery = data.deliveries.computeIfAbsent("grand", ignored -> {
                Delivery value = new Delivery();
                value.rewards = new LinkedHashMap<>(data.grandRewards);
                return value;
            });
            dao.save(data);
            deliver(data, "grand", now);
            if (!completed(delivery)) return response(3, Code.FAIL);
            data.grandClaimed = true;
            dao.save(data);
            return success(ctx.getPlayer(), d, data, now, 3, delivery);
        });
        return result == null ? response(3, Code.FAIL) : result;
    }

    /** 预下单只预占，不发奖；返回值持久化了价格、奖励和活动期次快照。调用方持玩家锁。 */
    Reservation reserve(SimPlayerContext ctx, Definition d, int day, long now) {
        if (!eligible(ctx, d, now) || d.chest(day) == null || d.chest(day).priceType() != 1) return null;
        ContinuousGiftData data = load(ctx.playerId(), d);
        if (data.reservation != null) {
            // 重试/取消支付后重新下单复用业务预占，不再次消耗每日名额。
            return data.reservation.day == day ? data.reservation : null;
        }
        if (!canBuy(data, day, now)) return null;
        Reservation r = new Reservation();
        r.token = data.id + '/' + UUID.randomUUID();
        r.day = day;
        r.createdAt = now;
        r.price = d.chest(day).price();
        var shop = config.shop(d.chest(day).channelCommodity());
        r.channelProductId = shop == null ? null : SeasonPassService.channelProductId(ctx.getPlayer(), shop);
        if (r.channelProductId == null || r.channelProductId.isBlank()) return null;
        r.rewards = new LinkedHashMap<>(d.chest(day).rewardItems());
        data.reservation = r;
        dao.save(data);
        return r;
    }

    void release(long playerId, String token) {
        ContinuousGiftData data = tokenData(playerId, token);
        if (data != null && data.reservation != null && token.equals(data.reservation.token)) {
            data.reservation = null;
            dao.save(data);
        }
    }

    /** 回调只认服务器落库的预占；到期后支付到账仍通过邮件交付已支付宝箱。 */
    boolean paid(Player player, Order order, long now) {
        if (order.getId() == null || order.getId().isBlank() || order.getPlayerId() != player.getId()) return false;
        ContinuousGiftData data = tokenData(player.getId(), order.getProductId());
        if (data == null) return false;
        for (Map.Entry<String, Delivery> entry : data.deliveries.entrySet()) {
            Delivery delivery = entry.getValue();
            if (order.getId().equals(delivery.orderId)) {
                deliver(data, entry.getKey(), now);
                return completed(delivery);
            }
        }
        Reservation r = data.reservation;
        if (r == null || !r.token.equals(order.getProductId()) || order.getPrice() == null
                || order.getPrice().compareTo(java.math.BigDecimal.valueOf(r.price, 2)) != 0
                || !Objects.equals(r.channelProductId, order.getChannelProductId())) return false;
        if (!canBuy(data, r.day, r.createdAt)) return false;
        Delivery delivery = new Delivery();
        delivery.orderId = order.getId();
        delivery.rewards = new LinkedHashMap<>(r.rewards);
        String key = "day-" + r.day;
        data.deliveries.put(key, delivery);
        data.reservation = null;
        purchased(data, r.day, now, delivery);
        deliver(data, key, now);
        if (now >= data.endTime) settle(data, now);
        return completed(delivery);
    }

    ContinuousGiftData tokenData(long playerId, String token) {
        if (token == null || token.lastIndexOf('/') <= 0) return null;
        ContinuousGiftData data = dao.get(token.substring(0, token.lastIndexOf('/')));
        return data != null && data.playerId == playerId ? data : null;
    }

    private void purchased(ContinuousGiftData data, int day, long time, Delivery delivery) {
        data.purchasedDays.add(day);
        data.lastPurchaseTime = time;
        data.settled = false;
        delivery.status = "PENDING";
        dao.save(data);
    }

    static boolean canBuy(ContinuousGiftData data, int day, long now) {
        if (now < data.startTime || now >= data.endTime || day <= 0 || day > data.totalDays
                || day != data.purchasedDays.size() + 1 || data.deliveries.containsKey("day-" + day)) return false;
        if (data.lastPurchaseTime == 0) return true;
        ZoneId zone = ZoneId.of(data.zoneId);
        return Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
                .isAfter(Instant.ofEpochMilli(data.lastPurchaseTime).atZone(zone).toLocalDate());
    }

    boolean eligible(SimPlayerContext ctx, Definition d, long now) {
        return d != null && d.active(now) && config.allowed(ctx.getPlayer(), d);
    }

    ContinuousGiftData load(long playerId, Definition d) {
        String id = playerId + ":" + d.period();
        ContinuousGiftData data = dao.get(id);
        if (data != null) return data;
        data = new ContinuousGiftData();
        data.id = id;
        data.playerId = playerId;
        data.activityId = d.config().getId();
        data.startTime = d.start();
        data.endTime = d.end();
        data.totalDays = d.chests().size();
        data.zoneId = d.zone().getId();
        data.grandRewards = new LinkedHashMap<>(d.grand().rewardItems());
        return data;
    }

    private void deliver(ContinuousGiftData data, String key, long now) {
        Delivery delivery = data.deliveries.get(key);
        if (delivery == null || !"PENDING".equals(delivery.status)) return;
        if (now >= data.endTime) { sendMail(data, key, delivery); return; }
        delivery.status = "DELIVERING";
        dao.save(data);
        // 背包接口不支持业务幂等。发生异常/宕机时保留 DELIVERING，避免盲重试或邮件重复发奖。
        CommonResult<?> added = pack.addItems(data.playerId, delivery.rewards, AddType.ACTIVITY,
                data.id + ':' + key, true);
        if (added != null && added.success()) {
            delivery.status = "BAG";
        } else if (added != null) {
            delivery.status = "PENDING";
        }
        dao.save(data);
    }

    private void sendMail(ContinuousGiftData data, String key, Delivery delivery) {
        boolean sent = mail.addMailIfAbsent(data.playerId, "连续礼包奖励补发",
                "活动中已获得但未领取的奖励已补发，请查收。", ItemUtils.buildItems(delivery.rewards),
                AddType.ACTIVITY, "continuous-gift:" + data.id + ':' + key);
        if (sent) {
            delivery.status = "MAIL";
            dao.save(data);
        }
    }

    public void settleExpired(long now) {
        String after = null;
        while (true) {
            List<ContinuousGiftData> page = dao.expired(now, after);
            if (page.isEmpty()) return;
            for (ContinuousGiftData candidate : page) {
                locked(candidate.playerId, () -> {
                    ContinuousGiftData data = dao.get(candidate.id);
                    if (data != null && !data.settled && now >= data.endTime) settle(data, now);
                    return true;
                });
            }
            after = page.getLast().id;
        }
    }

    void settle(ContinuousGiftData data, long now) {
        if (now < data.endTime) return;
        if (data.purchasedDays.size() == data.totalDays && !data.grandClaimed) {
            data.deliveries.computeIfAbsent("grand", ignored -> {
                Delivery delivery = new Delivery();
                delivery.rewards = new LinkedHashMap<>(data.grandRewards);
                return delivery;
            });
            dao.save(data);
        }
        for (String key : data.deliveries.keySet()) deliver(data, key, now);
        Delivery grand = data.deliveries.get("grand");
        if (grand != null && completed(grand)) data.grandClaimed = true;
        data.settled = data.deliveries.values().stream().allMatch(ContinuousGiftService::completed);
        dao.save(data);
        if (!data.settled) log.warn("连续礼包尚有待交付或待对账记录 id={}", data.id);
    }

    private static boolean completed(Delivery d) { return "BAG".equals(d.status) || "MAIL".equals(d.status); }

    private ResContinuousGift success(Player player, Definition d, ContinuousGiftData data, long now, int action, Delivery delivery) {
        var response = response(action, Code.SUCCESS);
        response.activities = List.of(info(player, d, data, now));
        response.rewardPending = !completed(delivery);
        if ("BAG".equals(delivery.status)) response.rewards = ItemUtils.buildItemInfo(delivery.rewards);
        return response;
    }

    static ResContinuousGift response(int action, int code) {
        var response = new ResContinuousGift(code);
        response.action = action;
        return response;
    }

    ContinuousGiftInfo info(Player player, Definition d, ContinuousGiftData data, long now) {
        var info = new ContinuousGiftInfo();
        info.activityId = d.config().getId(); info.name = Integer.toString(d.config().getName());
        info.nameId = d.config().getName(); info.functionId = d.function().getId();
        info.startTime = d.start(); info.endTime = data.endTime; info.serverTime = now;
        info.iconPath = ""; info.iconPosition = d.function().getPosition() == null ? ""
                : d.function().getPosition().stream().map(String::valueOf).collect(java.util.stream.Collectors.joining("_"));
        info.redDot = !data.viewed;
        info.nextPurchaseTime = data.lastPurchaseTime == 0 ? d.start()
                : Instant.ofEpochMilli(data.lastPurchaseTime).atZone(d.zone()).toLocalDate()
                .plusDays(1).atStartOfDay(d.zone()).toInstant().toEpochMilli();
        List<ContinuousGiftChestInfo> chests = new ArrayList<>();
        for (var c : d.chests()) {
            var i = new ContinuousGiftChestInfo();
            i.chestId = c.id(); i.dayIndex = c.dayIndex(); i.dayLabel = Integer.toString(c.dayIndex());
            i.price = c.price(); i.priceType = c.priceType();
            i.chestIcon = ""; i.purchasedIcon = "";
            i.rewards = ItemUtils.buildItemInfo(c.rewardItems());
            if (c.priceType() == 1) i.channelProductId = SeasonPassService.channelProductId(player, config.shop(c.channelCommodity()));
            i.status = data.purchasedDays.contains(c.dayIndex()) ? 2
                    : c.dayIndex() != data.purchasedDays.size() + 1 ? 0
                    : data.deliveries.containsKey("day-" + c.dayIndex()) ? 5
                    : data.reservation != null ? 4 : canBuy(data, c.dayIndex(), now) ? 1 : 3;
            chests.add(i);
        }
        info.chests = chests;
        info.grandTitle = ""; info.grandIcon = "";
        info.unlockText = ""; info.grandRewards = ItemUtils.buildItemInfo(data.grandRewards);
        info.grandStatus = data.grandClaimed ? 2 : data.purchasedDays.size() == data.totalDays ? 1 : 0;
        return info;
    }
}
