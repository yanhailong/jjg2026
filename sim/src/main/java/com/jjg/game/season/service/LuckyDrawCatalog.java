package com.jjg.game.season.service;

import com.jjg.game.season.config.*;
import com.jjg.game.sampledata.GameDataManager;
import com.jjg.game.sampledata.bean.ShopRechargeListCfg;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.*;

@Service
public class LuckyDrawCatalog {
    private final List<LuckyDrawConfigSource> sources;
    public LuckyDrawCatalog(List<LuckyDrawConfigSource> sources) { this.sources = List.copyOf(sources); }

    public LuckyDrawDefinition stage(int seasonOrdinal) {
        if (seasonOrdinal < 1) return null;
        SortedMap<Integer, LuckyDrawDefinition> stages = new TreeMap<>();
        Set<Integer> ids = new HashSet<>();
        for (var source : sources) for (var d : source.definitions()) {
            if (stages.putIfAbsent(d.stageIndex(), d) != null || !ids.add(d.activityId()))
                throw new IllegalArgumentException("duplicate lucky draw stage/activity");
        }
        if (stages.isEmpty()) return null;
        int expected = 1;
        for (int stage : stages.keySet()) if (stage != expected++)
            throw new IllegalArgumentException("lucky draw stages must start at 1 without gaps");
        return stages.get(Math.min(seasonOrdinal, stages.lastKey()));
    }

    public ShopRechargeListCfg shop(int id) { return GameDataManager.getShopRechargeListCfg(id); }

    /** 在首次保存本期快照前校验真实道具和现金商品，避免无效配置扣款。 */
    public void validateReferences(LuckyDrawDefinition d) {
        List<Map<Integer, Long>> groups = new ArrayList<>();
        groups.add(d.dailyRewards());
        d.rewards().stream().filter(r -> !r.grand()).forEach(r -> groups.add(r.items()));
        d.grands().forEach(g -> groups.add(g.items()));
        for (var group : groups) for (int id : group.keySet()) {
            if (GameDataManager.getItemCfg(id) == null) throw new IllegalArgumentException("unknown item " + id);
        }
        if (d.priceType() == 1) {
            var shop = shop(d.shopId());
            if (shop == null || shop.getPrice() == null || shop.getPrice().compareTo(BigDecimal.valueOf(d.price(), 2)) != 0)
                throw new IllegalArgumentException("lucky draw price differs from recharge product");
        }
    }
}
