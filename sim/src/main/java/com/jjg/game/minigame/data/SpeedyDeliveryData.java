package com.jjg.game.minigame.data;

import com.jjg.game.sim.data.AbstractData;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 持久化每日补足日期及退出时的仓库、货架库存，单局不落库。 */
@Document
public class SpeedyDeliveryData extends AbstractData {
    @Id
    private long playerId;
    private long lastDailyGrantTime;
    //送到的货物，仓库的货物 Minigamegoods.id -> 数量
    private Map<Integer, Integer> houseGoods;
    //上架的货物 Minigamegoods.type -> 格子id -> [商品id, 数量]
    private Map<Integer, Map<Integer, int[]>> goods;
    //送到的货物，仓库的货物 Minigamegoods.id -> 数量
    @Transient
    private final transient Map<Integer, Integer> tmpTouseGoods = new HashMap<>();
    //上架的货物 Minigamegoods.type -> 格子id -> [商品id, 数量]
    @Transient
    private final transient Map<Integer, Map<Integer, int[]>> tmpToods = new HashMap<>();

    public long getPlayerId() {
        return playerId;
    }

    public void setPlayerId(long playerId) {
        this.playerId = playerId;
    }

    public long getLastDailyGrantTime() {
        return lastDailyGrantTime;
    }

    public void setLastDailyGrantTime(long lastDailyGrantTime) {
        this.lastDailyGrantTime = lastDailyGrantTime;
    }

    public Map<Integer, Integer> getHouseGoods() {
        return houseGoods;
    }

    public void setHouseGoods(Map<Integer, Integer> houseGoods) {
        this.houseGoods = houseGoods;
    }

    public Map<Integer, Map<Integer, int[]>> getGoods() {
        return goods;
    }

    public void setGoods(Map<Integer, Map<Integer, int[]>> goods) {
        this.goods = goods;
    }

    public void restoreGoods() {
        if (houseGoods != null) {
            tmpTouseGoods.putAll(houseGoods);
        }
        if (goods != null) {
            goods.forEach((type, slots) -> {
                Map<Integer, int[]> restored = new HashMap<>(slots.size());
                slots.forEach((slotId, stock) -> restored.put(slotId, stock.clone()));
                tmpToods.put(type, restored);
            });
        }
    }

    /** 上下文摘除且退出落库任务接管后，临时库存可以直接交给持久化字段。 */
    public void prepareGoodsForExitSave() {
        houseGoods = tmpTouseGoods;
        goods = tmpToods;
    }

    public Map<Integer, Integer> warehouseGoods() {
        return tmpTouseGoods;
    }

    public Map<Integer, int[]> shelfGoods(int type) {
        return tmpToods.getOrDefault(type, Map.of());
    }

    public void addDeliveredGoods(List<Integer> goodsIds) {
        for (int goodsId : goodsIds) {
            tmpTouseGoods.merge(goodsId, 1, Integer::sum);
        }
    }

    public boolean stockShelf(int type, int slotId, int goodsId, int capacity) {
        int available = tmpTouseGoods.getOrDefault(goodsId, 0);
        if (available == 0) {
            return false;
        }
        Map<Integer, int[]> shelf = tmpToods.computeIfAbsent(type, key -> new HashMap<>());
        int[] stock = shelf.get(slotId);
        boolean sameGoods = stock != null && stock[0] == goodsId;
        int current = sameGoods ? stock[1] : 0;
        int moved = Math.min(available, capacity - current);
        if (moved <= 0) {
            return true;
        }
        if (stock != null && !sameGoods) {
            tmpTouseGoods.merge(stock[0], stock[1], Integer::sum);
        }
        if (available == moved) {
            tmpTouseGoods.remove(goodsId);
        } else {
            tmpTouseGoods.put(goodsId, available - moved);
        }
        shelf.put(slotId, new int[]{goodsId, current + moved});
        return true;
    }

    public void consumeShelfGoods() {
        if (tmpToods.isEmpty()) {
            return;
        }
        // 只保留有货的货架和格子，每次交互扣一件。
        var shelves = tmpToods.values().iterator();
        Map<Integer, int[]> shelf = shelves.next();
        var slots = shelf.values().iterator();
        int[] stock = slots.next();
        if (--stock[1] == 0) {
            slots.remove();
            if (shelf.isEmpty()) {
                shelves.remove();
            }
        }
    }
}
