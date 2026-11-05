package com.jjg.game.sim.service;

import com.jjg.game.common.utils.TimeHelper;
import com.jjg.game.core.listener.ConfigExcelChangeListener;
import com.jjg.game.sampledata.GameDataManager;
import com.jjg.game.sampledata.bean.TokenShopCfg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 商品排序、时间解析只在配置加载/热更新时执行，快照整体替换。 */
@Service
public class TokenShopConfigService implements ConfigExcelChangeListener {
    private static final Logger log = LoggerFactory.getLogger(TokenShopConfigService.class);
    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("[yyyy-MM-dd HH:mm:ss][yyyy/MM/dd HH:mm:ss]");
    private volatile Snapshot snapshot = new Snapshot(Map.of(), Map.of());

    public record Goods(TokenShopCfg cfg, long startTime, long endTime) {
        public boolean isOpen(long now) {
            return cfg.getIsEnabled() && (startTime == 0 || now >= startTime)
                    && (endTime == 0 || now < endTime);
        }
    }

    private record Snapshot(Map<Integer, Goods> byId, Map<Integer, List<Goods>> byType) {
    }

    public Goods goods(int id) {
        return snapshot.byId().get(id);
    }

    public List<Goods> goodsOfType(int type) {
        return snapshot.byType().getOrDefault(type, List.of());
    }

    private void load() {
        Map<Integer, Goods> byId = new HashMap<>();
        Map<Integer, List<Goods>> byType = new HashMap<>();
        for (TokenShopCfg cfg : GameDataManager.getTokenShopCfgList()) {
            try {
                if (cfg.getType() < 1 || cfg.getResetTime() < 0
                        || cfg.getResetDaily() < 0 || cfg.getGoods() == null || cfg.getGoods().isEmpty()) {
                    throw new IllegalArgumentException("商品类型、重置或限购配置错误，或奖励为空");
                }
                validateItems(cfg.getGoods());
                validateItems(cfg.getCost());
                long start = timestamp(cfg.getStartTime());
                long end = timestamp(cfg.getEndTime());
                if (start != 0 && end != 0 && start >= end) {
                    throw new IllegalArgumentException("结束时间必须晚于开始时间");
                }
                Goods goods = new Goods(cfg, start, end);
                byId.put(cfg.getId(), goods);
                byType.computeIfAbsent(cfg.getType(), key -> new ArrayList<>()).add(goods);
            } catch (RuntimeException e) {
                log.error("代币商店商品配置无效，跳过商品 goodsId={}", cfg.getId(), e);
            }
        }
        byType.replaceAll((type, goods) -> goods.stream()
                .sorted(Comparator.comparingInt((Goods g) -> g.cfg().getOrder())
                        .thenComparingInt(g -> g.cfg().getId())).toList());
        snapshot = new Snapshot(Map.copyOf(byId), Map.copyOf(byType));
    }

    private static void validateItems(Map<Integer, Long> items) {
        if (items == null) {
            return;
        }
        items.forEach((id, count) -> {
            if (id == null || count == null || count <= 0 || GameDataManager.getItemCfg(id) == null) {
                throw new IllegalArgumentException("道具不存在或数量不是正数: " + id);
            }
        });
    }

    private static long timestamp(String value) {
        return value == null || value.isBlank() ? 0
                : TimeHelper.getTimestamp(LocalDateTime.parse(value.trim(), DATE_TIME));
    }

    @Override
    public void initSampleCallbackCollector() {
        addInitSampleFileObserveWithCallBack(TokenShopCfg.EXCEL_NAME, this::load);
    }

    @Override
    public void changeSampleCallbackCollector() {
        addChangeSampleFileObserveWithCallBack(TokenShopCfg.EXCEL_NAME, this::load);
    }
}
