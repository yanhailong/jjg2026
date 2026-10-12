package com.jjg.game.season.service;

import com.jjg.game.core.data.Player;
import com.jjg.game.core.listener.ActivityOpenListener;
import com.jjg.game.core.manager.ConditionManager;
import com.jjg.game.core.utils.ItemUtils;
import com.jjg.game.sampledata.GameDataManager;
import com.jjg.game.sampledata.bean.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** ActivityConfig(type=27) -> ConsecutivePack -> ShopRechargeList. */
@Service
public class ContinuousGiftConfigService implements ActivityOpenListener {
    public static final int ACTIVITY_TYPE = 27;
    private static final Logger log = LoggerFactory.getLogger(ContinuousGiftConfigService.class);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss")
            .withResolverStyle(java.time.format.ResolverStyle.STRICT);
    private final ConditionManager conditions;
    public ContinuousGiftConfigService(ConditionManager conditions) { this.conditions = conditions; }

    public List<Definition> definitions() {
        List<Definition> result = new ArrayList<>();
        var function = rows(GameFunctionCfg.class).stream().filter(f -> f.getFunctionType() == ACTIVITY_TYPE)
                .filter(GameFunctionCfg::getIsOpen).findFirst().orElse(null);
        if (function == null) return result;
        var packs = new HashMap<Integer, ConsecutivePackCfg>();
        rows(ConsecutivePackCfg.class).forEach(p -> packs.put(p.getId(), p));
        for (ActivityConfigCfg cfg : rows(ActivityConfigCfg.class)) {
            if (cfg.getType() != ACTIVITY_TYPE || !cfg.getOpen()) continue;
            try {
                var d = build(cfg, function, packs);
                d.chests().forEach(c -> validateItemReferences(c.rewardItems()));
                validateItemReferences(d.grand().rewardItems());
                result.add(d);
            } catch (RuntimeException e) {
                log.error("连续礼包配置无效 activityId={} reason={}", cfg.getId(), e.getMessage());
            }
        }
        result.sort(Comparator.comparingLong(Definition::start));
        return result;
    }
    public Definition find(int id) { return definitions().stream().filter(d -> d.config().getId() == id).findFirst().orElse(null); }
    public ShopRechargeListCfg shop(int id) { return GameDataManager.getShopRechargeListCfg(id); }
    public boolean allowed(Player player, Definition d) {
        return player != null && d != null && d.config().getOpen() && d.function().getIsOpen()
                && conditions.isAchievement(player, "", d.config().getCondition())
                && conditions.isAchievement(player, "", d.function().getCondition());
    }
    @Override public boolean isActivityOpen(int type) {
        return type == ACTIVITY_TYPE && definitions().stream().anyMatch(d -> d.active(System.currentTimeMillis()));
    }
    @Override public boolean isActivityOpen(Player player, int type) {
        return type == ACTIVITY_TYPE && definitions().stream().anyMatch(d -> d.active(System.currentTimeMillis()) && allowed(player,d));
    }
    private void validateItemReferences(Map<Integer,Long> items) {
        for (int id : items.keySet()) if (GameDataManager.getItemCfg(id) == null)
            throw new IllegalArgumentException("unknown reward item " + id);
    }
    private <T extends BaseCfgBean> List<T> rows(Class<T> type) {
        var container = GameDataManager.getInstance().getCfgContainer(type);
        return container == null ? List.of() : container.getCfgBeanList();
    }
    Definition build(ActivityConfigCfg cfg, GameFunctionCfg function, Map<Integer,ConsecutivePackCfg> packs) {
        if (cfg.getId() <= 0 || cfg.getType() != ACTIVITY_TYPE || cfg.getOpen_type() != 2)
            throw new IllegalArgumentException("continuous gifts require a limited-time activity of type 27");
        ZoneId zone = ZoneId.systemDefault();
        long start = parseTime(cfg.getTime_start(), zone), end = parseTime(cfg.getTime_end(), zone);
        if (end <= start) throw new IllegalArgumentException("invalid time window");
        var ids = cfg.getValue();
        if (ids == null || ids.isEmpty() || new HashSet<>(ids).size() != ids.size())
            throw new IllegalArgumentException("empty or duplicate pack references");
        List<Chest> chests = new ArrayList<>();
        Grand grand = null; Integer group = null;
        for (int id : ids) {
            var p = packs.get(id);
            if (p == null) throw new IllegalArgumentException("missing ConsecutivePack " + id);
            if (p.getRewardGroupCount() <= 0 || (group != null && group != p.getRewardGroupCount()))
                throw new IllegalArgumentException("mixed reward groups");
            group = p.getRewardGroupCount();
            validateRewards(p.getGetitem());
            if (p.getType() == 2) {
                if (grand != null) throw new IllegalArgumentException("multiple grand prizes");
                grand = new Grand(p.getId(), Map.copyOf(p.getGetitem()));
            } else if (p.getType() == 1) {
                var shop = shop(p.getPrice());
                if (shop == null) throw new IllegalArgumentException("missing ShopRechargeList " + p.getPrice());
                int priceType; long price;
                if (shop.getType() == 1) {
                    if (shop.getPrice() == null) throw new IllegalArgumentException("missing cash price");
                    priceType = 1; price = shop.getPrice().movePointRight(2).longValueExact();
                } else if (shop.getType() == 2 && shop.getRedeem() != null && shop.getRedeem().size() == 1) {
                    var cost = shop.getRedeem().entrySet().iterator().next();
                    priceType = cost.getKey() == ItemUtils.getDiamondItemId() ? 2 : cost.getKey() == ItemUtils.getGoldItemId() ? 3 : 0;
                    price = cost.getValue();
                } else throw new IllegalArgumentException("unsupported gift payment type");
                if (priceType == 0 || price <= 0) throw new IllegalArgumentException("invalid gift price");
                chests.add(new Chest(p.getId(),p.getPackQuantity(),priceType,price,p.getPrice(),Map.copyOf(p.getGetitem())));
            } else throw new IllegalArgumentException("invalid pack type");
        }
        chests.sort(Comparator.comparingInt(Chest::dayIndex));
        if (chests.isEmpty() || grand == null) throw new IllegalArgumentException("missing chests or grand prize");
        for (int i=0;i<chests.size();i++) if (chests.get(i).dayIndex() != i+1)
            throw new IllegalArgumentException("pack quantities must be sequential from 1");
        long days = java.time.temporal.ChronoUnit.DAYS.between(Instant.ofEpochMilli(start).atZone(zone).toLocalDate(),
                Instant.ofEpochMilli(end-1).atZone(zone).toLocalDate()) + 1;
        if (days < chests.size()) throw new IllegalArgumentException("window too short for all chests");
        return new Definition(cfg,function,start,end,zone,List.copyOf(chests),grand);
    }
    static long parseTime(String value, ZoneId zone) {
        return LocalDateTime.parse(value.trim().replace('/','-'),TIME).atZone(zone).toInstant().toEpochMilli();
    }
    static void validateRewards(Map<Integer,Long> rewards) {
        if (rewards == null || rewards.isEmpty() || rewards.entrySet().stream().anyMatch(e ->
                e.getKey() == null || e.getKey() <= 0 || e.getValue() == null || e.getValue() <= 0))
            throw new IllegalArgumentException("invalid rewards");
    }
    public record Chest(int id,int dayIndex,int priceType,long price,int channelCommodity,Map<Integer,Long> rewardItems) { }
    public record Grand(int id,Map<Integer,Long> rewardItems) { }
    public record Definition(ActivityConfigCfg config,GameFunctionCfg function,long start,long end,ZoneId zone,List<Chest> chests,Grand grand) {
        public boolean active(long now) { return now >= start && now < end; }
        public Chest chest(int day) { return day > 0 && day <= chests.size() ? chests.get(day-1) : null; }
        public String period() { return config.getId() + ":" + start; }
    }
}
