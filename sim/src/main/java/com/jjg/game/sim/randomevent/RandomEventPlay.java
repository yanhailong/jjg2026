package com.jjg.game.sim.randomevent;

import com.jjg.game.common.utils.RandomUtils;
import com.jjg.game.core.constant.Code;
import com.jjg.game.sampledata.GameDataManager;
import com.jjg.game.sampledata.bean.GlobalConfigCfg;
import com.jjg.game.sampledata.bean.RandomEventDetailsCfg;
import com.jjg.game.sim.randomevent.SimRandomEventState.SceneEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

final class RandomEventPlay {
    private static final Logger log = LoggerFactory.getLogger(RandomEventPlay.class);

    static final int QUIZ = 1;
    static final int ROOM = 2;
    static final int CHEST = 3;
    static final int SPY = 4;
    static final int INVESTMENT = 5;
    static final int DICE = 6;

    static final int VIEW = 1;
    static final int CHOOSE_A = 2;
    static final int CHOOSE_B = 3;
    static final int START = 4;
    static final int TARGET = 5;
    static final int CLAIM = 7;

    static final List<Integer> CHEST_PREVIEW_ORDER = List.of(1, 2, 0);

    private RandomEventPlay() {
    }

    static Outcome play(SceneEvent event, RandomEventDetailsCfg cfg, int action, int target, long now) {
        if (action == VIEW && cfg.getAreaId() != CHEST) {
            return Outcome.update(event);
        }
        return switch (cfg.getAreaId()) {
            case QUIZ -> switch (action) {
                case CHOOSE_A -> Outcome.finishA(event, cfg);
                case CHOOSE_B -> Outcome.finishB(event, cfg);
                default -> Outcome.reject(event, Code.PARAM_ERROR);
            };
            case ROOM -> action == CHOOSE_A ? Outcome.finish(event, Map.of())
                    : Outcome.reject(event, Code.FORBID);
            case CHEST -> chest(event, cfg, action, target);
            case SPY -> spy(event, cfg, action, target);
            case INVESTMENT -> investment(event, cfg, action, now);
            case DICE -> dice(event, action, target);
            default -> Outcome.reject(event, Code.SAMPLE_ERROR);
        };
    }

    private static Outcome chest(SceneEvent event, RandomEventDetailsCfg cfg, int action, int target) {
        if (action == VIEW) {
            if (!event.values().isEmpty()) {
                return Outcome.update(event);
            }
            List<Integer> boxes = new ArrayList<>(CHEST_PREVIEW_ORDER);
            Collections.shuffle(boxes);
            return Outcome.update(event.advance(0, 0, 0, boxes));
        }
        if (event.values().isEmpty()) {
            return Outcome.reject(event, Code.FORBID);
        }
        if (action == CLAIM && event.auxiliary() != 0) {
            return Outcome.finish(event, chestRewards(event, cfg));
        }
        if (action != TARGET || target < 0 || target >= 3 || (event.auxiliary() & (1 << target)) != 0) {
            return Outcome.reject(event, Code.FORBID);
        }
        int opened = event.auxiliary() | (1 << target);
        SceneEvent updated = event.advance(0, opened, 0, event.values());
        return opened == 7 ? Outcome.finish(updated, chestRewards(updated, cfg)) : Outcome.update(updated);
    }

    private static Map<Integer, Long> chestRewards(SceneEvent event, RandomEventDetailsCfg cfg) {
        Map<Integer, Long> rewards = new HashMap<>();
        for (int i = 0; i < 3; i++) {
            if ((event.auxiliary() & (1 << i)) != 0) {
                chestReward(cfg, event.values().get(i)).forEach((item, count) -> rewards.merge(item, count, Long::sum));
            }
        }
        return rewards;
    }

    static Map<Integer, Long> chestReward(RandomEventDetailsCfg cfg, int category) {
        Map<Integer, Long> reward = switch (category) {
            case 1 -> cfg.getA_reward();
            case 2 -> cfg.getB_reward();
            default -> null;
        };
        return reward == null ? Map.of() : reward;
    }

    private static Outcome spy(SceneEvent event, RandomEventDetailsCfg cfg, int action, int target) {
        if (action == CHOOSE_A) {
            return Outcome.finishA(event, cfg);
        }
        List<Integer> limits = cfg.getParameter1();
        if (limits == null || limits.size() != 2 || limits.getFirst() > 20
                || limits.getLast() <= 0 || limits.getLast() > limits.getFirst()) {
            return Outcome.reject(event, Code.SAMPLE_ERROR);
        }
        if (action == START) {
            return Outcome.update(event.advance(event.progress(), 1, 0, event.values()));
        }
        if (action != TARGET || event.auxiliary() == 0 || target < 0 || target >= limits.getFirst()
                || event.values().contains(target)) {
            return Outcome.reject(event, Code.FORBID);
        }
        List<Integer> captured = new ArrayList<>(event.values());
        captured.add(target);
        SceneEvent updated = event.advance(captured.size(), 1, 0, captured);
        return captured.size() >= limits.getLast()
                ? Outcome.finishB(updated, cfg) : Outcome.update(updated);
    }

    private static Outcome investment(SceneEvent event, RandomEventDetailsCfg cfg, int action, long now) {
        if (action == CHOOSE_A) {
            return Outcome.finishA(event, cfg);
        }
        if (action == CHOOSE_B && event.readyAt() == 0) {
            List<Integer> duration = cfg.getParameter2();
            if (duration == null || duration.isEmpty() || duration.getFirst() <= 0) {
                return Outcome.reject(event, Code.SAMPLE_ERROR);
            }
            return Outcome.update(event.advance(1, 0, now + TimeUnit.SECONDS.toMillis(duration.getFirst()), List.of()));
        }
        if (action == CLAIM && event.readyAt() > 0) {
            if (now >= event.readyAt()) {
                return Outcome.finishB(event, cfg);
            }
            Map<Integer, Long> rewards = new HashMap<>();
            if (cfg.getB_reward() != null) {
                cfg.getB_reward().forEach((item, count) -> rewards.put(item, Math.multiplyExact(count, 2)));
            }
            return Outcome.finishB(event, cfg, rewards);
        }
        return Outcome.reject(event, Code.FORBID);
    }

    private static Outcome dice(SceneEvent event, int action, int target) {
        DicePrizes prizes = dicePrizes();
        if (prizes == null) {
            return Outcome.reject(event, Code.SAMPLE_ERROR);
        }
        if (event.progress() > prizes.amounts().length) {
            log.warn("摇骰子连胜次数超出奖金配置档位 configId={},wins={},tierCount={},action={}",
                    event.configId(), event.progress(), prizes.amounts().length, action);
            return Outcome.reject(event, Code.SAMPLE_ERROR);
        }
        if (action == CLAIM) {
            return Outcome.finish(event, prizes.payout(prizes.currentAmount(event)));
        }
        boolean big = isBig(event.values());
        boolean first = event.values().isEmpty();
        List<Integer> values;
        int rerolls = event.auxiliary();
        if (action == START && (first || big)) {
            values = List.of(die(), die(), die());
            if (first) {
                GlobalConfigCfg cfg = GameDataManager.getGlobalConfigCfg(420);
                rerolls = cfg == null ? 0 : cfg.getIntValue();
            }
        } else if (action == TARGET && !first && !big && rerolls > 0 && target >= 0 && target < 3) {
            values = new ArrayList<>(event.values());
            values.set(target, die());
            rerolls--;
        } else {
            return Outcome.reject(event, Code.FORBID);
        }
        boolean won = isBig(values);
        int wins = event.progress() + (won ? 1 : 0);
        SceneEvent updated = event.advance(wins, rerolls, 0, values);
        if (won && wins == prizes.amounts().length) {
            return Outcome.finish(updated, prizes.payout(prizes.amountAt(wins)));
        }
        return !won && rerolls == 0 ? Outcome.finish(updated, Map.of()) : Outcome.update(updated);
    }

    static DicePrizes dicePrizes() {
        GlobalConfigCfg base = GameDataManager.getGlobalConfigCfg(421);
        GlobalConfigCfg multipliers = GameDataManager.getGlobalConfigCfg(422);
        String baseValue = base == null ? null : base.getValue();
        String multiplierValue = multipliers == null ? null : multipliers.getValue();
        if (baseValue == null || multiplierValue == null) {
            log.warn("摇骰子奖金配置缺失 base421={},multipliers422={}", baseValue, multiplierValue);
            return null;
        }
        try {
            String[] item = baseValue.split("_", -1);
            if (item.length != 2) {
                throw new IllegalArgumentException("基础奖金应为道具ID_数量");
            }
            int itemId = Integer.parseInt(item[0]);
            long baseAmount = Long.parseLong(item[1]);
            String[] odds = multiplierValue.split("_", -1);
            long[] amounts = new long[odds.length];
            for (int i = 0; i < odds.length; i++) {
                amounts[i] = Math.multiplyExact(baseAmount, Long.parseLong(odds[i]));
            }
            return new DicePrizes(itemId, baseAmount, amounts);
        } catch (IllegalArgumentException | ArithmeticException e) {
            log.warn("摇骰子奖金配置无效 base421={},multipliers422={},reason={}", baseValue, multiplierValue, e.getMessage());
            return null;
        }
    }

    private static boolean isBig(List<Integer> values) {
        return values.stream().mapToInt(Integer::intValue).sum() >= 11;
    }

    private static int die() {
        return RandomUtils.randomInt(6) + 1;
    }

    record DicePrizes(int itemId, long baseAmount, long[] amounts) {
        long currentAmount(SceneEvent event) {
            int wins = event.progress();
            return wins > 0 && wins <= amounts.length && isBig(event.values()) ? amountAt(wins) : 0;
        }

        private long amountAt(int wins) {
            return amounts[wins - 1];
        }

        private Map<Integer, Long> payout(long amount) {
            return amount == 0 ? Map.of() : Map.of(itemId, amount);
        }
    }

    record Outcome(int code, SceneEvent event, boolean completed, Map<Integer, Long> rewards, List<Integer> resultLanguageId) {
        static Outcome update(SceneEvent event) {
            return new Outcome(Code.SUCCESS, event, false, Map.of(), List.of());
        }

        static Outcome finish(SceneEvent event, Map<Integer, Long> rewards) {
            return finish(event, rewards, List.of());
        }

        static Outcome finishA(SceneEvent event, RandomEventDetailsCfg cfg) {
            return finish(event, cfg.getA_reward(), resultLanguageIds(cfg, 0));
        }

        static Outcome finishB(SceneEvent event, RandomEventDetailsCfg cfg) {
            return finishB(event, cfg, cfg.getB_reward());
        }

        static Outcome finishB(SceneEvent event, RandomEventDetailsCfg cfg, Map<Integer, Long> rewards) {
            return finish(event, rewards, resultLanguageIds(cfg, 1));
        }

        private static Outcome finish(SceneEvent event, Map<Integer, Long> rewards, List<Integer> resultLanguageId) {
            return new Outcome(Code.SUCCESS, event, true, rewards == null ? Map.of() : rewards, resultLanguageId);
        }

        private static List<Integer> resultLanguageIds(RandomEventDetailsCfg cfg, int rewardIndex) {
            List<List<Integer>> groups = cfg.getResult_AB();
            List<Integer> ids = groups != null && rewardIndex < groups.size() ? groups.get(rewardIndex) : null;
            return ids == null ? List.of() : ids;
        }

        static Outcome reject(SceneEvent event, int code) {
            return new Outcome(code, event, false, Map.of(), List.of());
        }
    }
}
