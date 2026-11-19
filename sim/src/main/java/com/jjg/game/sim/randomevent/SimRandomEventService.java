package com.jjg.game.sim.randomevent;

import com.jjg.game.common.pb.ItemInfo;
import com.jjg.game.common.utils.RandomUtils;
import com.jjg.game.core.constant.AddType;
import com.jjg.game.core.constant.Code;
import com.jjg.game.core.service.PlayerPackService;
import com.jjg.game.core.utils.ItemUtils;
import com.jjg.game.sampledata.GameDataManager;
import com.jjg.game.sampledata.bean.GlobalConfigCfg;
import com.jjg.game.sampledata.bean.RandomEventDetailsCfg;
import com.jjg.game.sim.data.SimPlayerContext;
import com.jjg.game.sim.pb.req.ReqSimRandomEventAction;
import com.jjg.game.sim.pb.res.NotifySimRandomEvents;
import com.jjg.game.sim.pb.res.ResSimRandomEventAction;
import com.jjg.game.sim.pb.res.ResSimRandomEventInfo;
import com.jjg.game.sim.pb.res.ResSimRandomEventList;
import com.jjg.game.sim.pb.res.ResVisitCasino;
import com.jjg.game.sim.pb.struct.RandomEventBoxInfo;
import com.jjg.game.sim.pb.struct.RandomEventInfo;
import com.jjg.game.sim.randomevent.RandomEventPlay.Outcome;
import com.jjg.game.sim.randomevent.SimRandomEventState.SceneEvent;
import com.jjg.game.sim.service.SimVisitQuotaService;
import com.jjg.game.social.service.SocialSender;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.IntToLongFunction;

@Service
public class SimRandomEventService {
    private static final Logger log = LoggerFactory.getLogger(SimRandomEventService.class);
    private static final String SEND_QUOTA = "random-event-send";
    private static final String VISIT_QUOTA = "random-event-complete";
    private static final int POSITIONS = 20;

    private final SimRandomEventDao dao;
    private final RedissonClient redis;
    private final SimVisitQuotaService quotas;
    private final PlayerPackService packs;
    private final SocialSender sender;

    SimRandomEventService(SimRandomEventDao dao, RedissonClient redis, SimVisitQuotaService quotas,
                          PlayerPackService packs, SocialSender sender) {
        this.dao = dao;
        this.redis = redis;
        this.quotas = quotas;
        this.packs = packs;
        this.sender = sender;
    }

    public void onLogin(SimPlayerContext ctx) {
        refresh(ctx, false);
    }

    public void onScheduledRefresh(SimPlayerContext ctx) {
        var controller = ctx.getPlayerController();
        if (controller == null || !controller.isOnline()) {
            return;
        }
        NotifySimRandomEvents update = refresh(ctx, true);
        if (update != null && ctx.getVisitTargetId() == 0 && controller.getScene() == ctx) {
            ctx.send(update);
        }
    }

    public ResSimRandomEventList list(SimPlayerContext ctx) {
        ResSimRandomEventList response = new ResSimRandomEventList(Code.SUCCESS);
        response.visitTargetId = ctx.getVisitTargetId();
        if (response.visitTargetId == 0) {
            response.events = ownEvents(ctx);
        } else if (ctx.visitRandomEvents != null && !ctx.visitRandomEvents.events.isEmpty()) {
            response.events = infos(ctx.visitRandomEvents.events, visitCompletion(ctx.playerId()));
        } else {
            response.events = List.of();
        }
        return response;
    }

    public ResSimRandomEventInfo detail(SimPlayerContext ctx, int configId) {
        long playerId = ctx.playerId();
        long visitTargetId = ctx.getVisitTargetId();
        if (configId <= 0) {
            log.warn("查询随机事件详情失败，配置ID必须为正数 playerId={},visitTargetId={},configId={}",
                    playerId, visitTargetId, configId);
            return new ResSimRandomEventInfo(Code.PARAM_ERROR);
        }

        SimRandomEventState scene = null;
        List<SceneEvent> events;
        if (visitTargetId == 0) {
            scene = dao.load(playerId);
            events = scene.events;
        } else {
            events = ctx.visitRandomEvents == null ? List.of() : ctx.visitRandomEvents.events;
        }
        int index = indexOf(events, configId);
        if (index < 0) {
            log.warn("查询随机事件详情失败，当前场景没有此事件 playerId={},visitTargetId={},configId={},eventCount={}",
                    playerId, visitTargetId, configId, events.size());
            return new ResSimRandomEventInfo(Code.NOT_FOUND);
        }

        SceneEvent event = events.get(index);
        RandomEventDetailsCfg cfg = GameDataManager.getRandomEventDetailsCfg(configId);
        if (cfg == null) {
            log.warn("查询随机事件详情失败，事件配置缺失 playerId={},visitTargetId={},configId={}",
                    playerId, visitTargetId, configId);
            return new ResSimRandomEventInfo(Code.SAMPLE_ERROR);
        }
        DailyCompletion completion = scene == null ? visitCompletion(playerId)
                : ownCompletion(scene, LocalDate.now().toEpochDay());
        ResSimRandomEventInfo response = new ResSimRandomEventInfo(Code.SUCCESS);
        response.event = info(event, cfg, completion);
        return response;
    }

    public List<RandomEventInfo> ownEvents(SimPlayerContext ctx) {
        SimRandomEventState scene = dao.load(ctx.playerId());
        return infos(scene.events, ownCompletion(scene, LocalDate.now().toEpochDay()));
    }

    public int validateRoomEntry(SimPlayerContext ctx, int configId, int requiredConfigId) {
        if (configId == requiredConfigId && ctx.getVisitTargetId() == 0) {
            List<SceneEvent> events = dao.load(ctx.playerId()).events;
            if (indexOf(events, configId) >= 0) {
                return Code.SUCCESS;
            }
        }
        log.warn("随机事件小游戏开局被拒绝 playerId={},configId={},requiredConfigId={},visitTargetId={}",
                ctx.playerId(), configId, requiredConfigId, ctx.getVisitTargetId());
        return Code.FORBID;
    }

    public void roomFinished(SimPlayerContext ctx, int configId) {
        if (configId == 0) {
            return;
        }
        long playerId = ctx.playerId();
        try {
            ResSimRandomEventAction response = withScene(playerId, scene -> {
                int index = indexOf(scene.events, configId);
                if (index < 0) {
                    return null;
                }
                RandomEventDetailsCfg cfg = GameDataManager.getRandomEventDetailsCfg(configId);
                if (cfg == null || cfg.getAreaId() != RandomEventPlay.ROOM) {
                    log.warn("房间游戏随机事件配置缺失或类型错误 playerId={},configId={},type={}",
                            playerId, configId, cfg == null ? null : cfg.getAreaId());
                    return null;
                }
                SceneEvent event = scene.events.get(index);
                Outcome outcome = Outcome.finishB(event, cfg);
                int code = grant(playerId, event, outcome.rewards(), false);
                if (code != Code.SUCCESS) {
                    log.warn("房间游戏随机事件发奖失败，保留事件 playerId={},configId={},rewards={},code={}",
                            playerId, configId, outcome.rewards(), code);
                    return null;
                }
                long today = LocalDate.now().toEpochDay();
                scene.completeEvent(index, today);
                dao.save(scene);
                ctx.clearRoomEvent(configId);
                return actionResponse(outcome, cfg, ownCompletion(scene, today));
            });
            if (response != null) {
                ctx.send(response);
            }
        } catch (Exception e) {
            log.error("房间游戏随机事件结算异常 playerId={},configId={}", playerId, configId, e);
        }
    }

    public void onFriendVisit(SimPlayerContext ctx, long ownerId, ResVisitCasino response) {
        long visitorId = ctx.playerId();
        int receiveLimit = limit(410);
        int sendLimit = limit(412);
        response.dailyRandomEventReceiveLimit = receiveLimit;
        response.dailyRandomEventSendLimit = sendLimit;
        ReceiveResult received = withScene(ownerId,
                scene -> receive(scene, visitorId, receiveLimit, sendLimit));
        response.remainingRandomEventReceives = received.remaining();
        if (received.update() != null) {
            sender.sendTo(ownerId, received.update());
        }
        response.remainingRandomEventSends = quotas.remaining(SEND_QUOTA, visitorId, sendLimit);
        DailyCompletion completion = visitCompletion(visitorId);
        response.dailyRandomEventCompleteLimit = completion.limit();
        response.remainingRandomEventCompletions = Math.max(0, completion.limit() - completion.completed());

        VisitRandomEvents visit = new VisitRandomEvents();
        fill(visit.events, EventPool.VISIT, Math.min(limit(414), response.remainingRandomEventCompletions), ignored -> 0);
        ctx.visitRandomEvents = visit;
        response.randomEvents = infos(visit.events, completion);
    }

    private ReceiveResult receive(SimRandomEventState scene, long visitorId, int receiveLimit, int sendLimit) {
        long day = LocalDate.now().toEpochDay();
        int received = scene.receivedOn(day);
        int remaining = Math.max(0, Math.min(receiveLimit - received, receiveLimit - scene.pendingDonors.size()));
        if (remaining == 0 || sendLimit == 0) {
            return new ReceiveResult(remaining, null);
        }
        Map<Integer, Integer> weights = weights(EventPool.DONATION);
        if (weights.isEmpty() || quotas.consume(SEND_QUOTA, visitorId, 1, sendLimit) < 0) {
            return new ReceiveResult(remaining, null);
        }
        scene.receiveDay = day;
        scene.received = received + 1;
        boolean spawned = scene.pendingDonors.isEmpty() && available(scene.events, true, limit(411)) > 0
                && fill(scene.events, weights, 1, ignored -> visitorId) > 0;
        if (!spawned) {
            scene.pendingDonors.add(visitorId);
        }
        try {
            dao.save(scene);
        } catch (Exception e) {
            quotas.rollback(SEND_QUOTA, visitorId, 1);
            throw e;
        }
        return new ReceiveResult(remaining - 1,
                spawned ? notification(List.of(scene.events.getLast()), ownCompletion(scene, day)) : null);
    }

    private NotifySimRandomEvents refresh(SimPlayerContext ctx, boolean scheduled) {
        return withScene(ctx.playerId(), scene -> {
            long today = LocalDate.now().toEpochDay();
            boolean first = scene.startDay(today);
            if (!first && !scheduled) {
                return null;
            }
            int previousSize = scene.events.size();
            int missing = available(scene.events, false, limit(401));
            if (missing > 0 && (first || scene.refills < limit(400))
                    && fill(scene.events, EventPool.SYSTEM, missing, ignored -> 0) > 0) {
                if (!first) {
                    scene.refills++;
                }
            }
            if (scheduled && !scene.pendingDonors.isEmpty()) {
                int slots = Math.min(available(scene.events, true, limit(411)), scene.pendingDonors.size());
                int filled = fill(scene.events, EventPool.DONATION, slots, scene.pendingDonors::get);
                if (filled > 0) {
                    scene.pendingDonors.subList(0, filled).clear();
                }
            }
            boolean added = scene.events.size() > previousSize;
            if (first || added) {
                dao.save(scene);
            }
            return scheduled && added
                    ? notification(scene.events.subList(previousSize, scene.events.size()), ownCompletion(scene, today))
                    : null;
        });
    }

    public ResSimRandomEventAction act(SimPlayerContext ctx, ReqSimRandomEventAction request) {
        long playerId = ctx.playerId();
        if (ctx.getVisitTargetId() != 0) {
            if (ctx.visitRandomEvents == null) {
                return reject(playerId, request, Code.NOT_FOUND, "没有好友随机事件会话");
            }
            return apply(ctx, ctx.visitRandomEvents.events, request, null);
        }
        return withScene(playerId, scene -> apply(ctx, scene.events, request, scene));
    }

    private ResSimRandomEventAction apply(SimPlayerContext ctx, List<SceneEvent> events, ReqSimRandomEventAction request,
                                          SimRandomEventState scene) {
        long playerId = ctx.playerId();
        int index = indexOf(events, request.configId);
        if (index < 0) {
            return reject(playerId, request, Code.NOT_FOUND, "该场景没有此事件");
        }
        SceneEvent event = events.get(index);
        RandomEventDetailsCfg cfg = GameDataManager.getRandomEventDetailsCfg(event.configId());
        if (cfg == null) {
            return reject(playerId, request, Code.SAMPLE_ERROR, "事件配置缺失");
        }
        Outcome outcome = RandomEventPlay.play(event, cfg, request.action, request.target, System.currentTimeMillis());
        if (outcome.code() != Code.SUCCESS) {
            return reject(playerId, request, outcome.code(), "当前状态不允许该操作，progress="
                    + event.progress() + ", auxiliary=" + event.auxiliary());
        }
        long today = LocalDate.now().toEpochDay();
        if (outcome.completed()) {
            int code = grant(playerId, event, outcome.rewards(), scene == null);
            if (code != Code.SUCCESS) {
                return reject(playerId, request, code, "发奖失败或好友事件次数已用完");
            }
            if (scene == null) {
                events.remove(index);
            } else {
                scene.completeEvent(index, today);
            }
        } else if (event != outcome.event()) {
            events.set(index, outcome.event());
        }
        if (scene != null && (outcome.completed() || event != outcome.event())) {
            dao.save(scene);
        }
        if (outcome.completed() && cfg.getAreaId() == RandomEventPlay.ROOM) {
            ctx.clearRoomEvent(event.configId());
        }
        DailyCompletion completion = scene == null ? visitCompletion(playerId) : ownCompletion(scene, today);
        ResSimRandomEventAction response = actionResponse(outcome, cfg, completion);
        if (cfg.getAreaId() == RandomEventPlay.CHEST && request.action == RandomEventPlay.TARGET) {
            response.openedReward = outcome.event().values().get(request.target);
        }
        return response;
    }

    private static ResSimRandomEventAction actionResponse(Outcome outcome, RandomEventDetailsCfg cfg,
                                                          DailyCompletion completion) {
        ResSimRandomEventAction response = new ResSimRandomEventAction(Code.SUCCESS);
        response.event = info(outcome.event(), cfg, completion);
        response.completed = outcome.completed();
        response.rewards = ItemUtils.buildItemInfo(outcome.rewards());
        response.resultLanguageId = outcome.resultLanguageId();
        return response;
    }

    private int grant(long playerId, SceneEvent event, Map<Integer, Long> rewards, boolean visit) {
        if (visit && quotas.consume(VISIT_QUOTA, playerId, 1, limit(413)) < 0) {
            return Code.FORBID;
        }
        if (rewards.isEmpty()) {
            return Code.SUCCESS;
        }
        int code = packs.addItems(playerId, rewards, AddType.SIM_RANDOM_EVENT_REWARD, Integer.toString(event.configId())).code;
        if (visit && code != Code.SUCCESS) {
            quotas.rollback(VISIT_QUOTA, playerId, 1);
        }
        return code;
    }

    private static ResSimRandomEventAction reject(long playerId, ReqSimRandomEventAction request, int code, String reason) {
        log.warn("随机事件操作失败 playerId={},configId={},action={},target={},code={},reason={}",
                playerId, request.configId, request.action, request.target, code, reason);
        return new ResSimRandomEventAction(code);
    }

    private <T> T withScene(long playerId, Function<SimRandomEventState, T> action) {
        RLock lock = redis.getLock("sim:random-event:lock:" + playerId);
        lock.lock();
        try {
            return action.apply(dao.load(playerId));
        } finally {
            lock.unlock();
        }
    }

    private static int indexOf(List<SceneEvent> events, int configId) {
        for (int i = 0; i < events.size(); i++) {
            if (events.get(i).configId() == configId) {
                return i;
            }
        }
        return -1;
    }

    private static int available(List<SceneEvent> events, boolean donated, int limit) {
        int count = (int) events.stream().filter(event -> (event.donorId() != 0) == donated).count();
        return Math.max(0, Math.min(limit - count, POSITIONS - events.size()));
    }

    private static int fill(List<SceneEvent> events, EventPool pool, int count, IntToLongFunction donorAt) {
        return count <= 0 ? 0 : fill(events, weights(pool), count, donorAt);
    }

    private static int fill(List<SceneEvent> events, Map<Integer, Integer> weights, int count, IntToLongFunction donorAt) {
        List<Integer> positions = new ArrayList<>(POSITIONS);
        for (int i = 1; i <= POSITIONS; i++) {
            positions.add(i);
        }
        for (SceneEvent event : events) {
            positions.remove(Integer.valueOf(event.position()));
            weights.remove(event.configId());
        }
        int filled = 0;
        for (int configId : RandomUtils.getRandomByWeight(weights, Math.min(count, positions.size()))) {
            int position = positions.remove(RandomUtils.randomInt(positions.size()));
            events.add(new SceneEvent(configId, position, donorAt.applyAsLong(filled)));
            filled++;
        }
        return filled;
    }

    private static Map<Integer, Integer> weights(EventPool pool) {
        Map<Integer, Integer> weights = new HashMap<>();
        for (RandomEventDetailsCfg cfg : GameDataManager.getRandomEventDetailsCfgList()) {
            int type = cfg.getAreaId();
            int weight = pool == EventPool.VISIT ? cfg.getF_EventWeight() : cfg.getS_EventWeight();
            if (weight <= 0 || type < RandomEventPlay.QUIZ || type > RandomEventPlay.DICE
                    || (pool != EventPool.SYSTEM && type == RandomEventPlay.ROOM)) {
                continue;
            }
            if (type == RandomEventPlay.INVESTMENT
                    && (cfg.getParameter2() == null || cfg.getParameter2().isEmpty() || cfg.getParameter2().getFirst() <= 0)) {
                continue;
            }
            weights.put(cfg.getId(), weight);
        }
        return weights;
    }

    private enum EventPool {
        SYSTEM, DONATION, VISIT
    }

    private static int limit(int id) {
        GlobalConfigCfg cfg = GameDataManager.getGlobalConfigCfg(id);
        return cfg == null ? 0 : Math.max(0, cfg.getIntValue());
    }

    private static DailyCompletion ownCompletion(SimRandomEventState scene, long today) {
        int systemLimit = (limit(400) + 1) * limit(401);
        return new DailyCompletion(scene.completedOn(today), systemLimit + scene.receivedOn(today));
    }

    private DailyCompletion visitCompletion(long playerId) {
        return new DailyCompletion((int) quotas.used(VISIT_QUOTA, playerId), limit(413));
    }

    private static List<RandomEventInfo> infos(List<SceneEvent> events, DailyCompletion completion) {
        return events.stream()
                .map(event -> info(event, GameDataManager.getRandomEventDetailsCfg(event.configId()), completion))
                .toList();
    }

    private static NotifySimRandomEvents notification(List<SceneEvent> events, DailyCompletion completion) {
        NotifySimRandomEvents notify = new NotifySimRandomEvents(Code.SUCCESS);
        notify.events = infos(events, completion);
        return notify;
    }

    private record ReceiveResult(int remaining, NotifySimRandomEvents update) {
    }

    private record DailyCompletion(int completed, int limit) {
    }

    private static RandomEventInfo info(SceneEvent event, RandomEventDetailsCfg cfg, DailyCompletion completion) {
        RandomEventInfo info = new RandomEventInfo();
        info.configId = event.configId();
        info.position = event.position();
        info.donorId = event.donorId();
        info.progress = event.progress();
        info.dailyCompletedCount = completion.completed();
        info.dailyCompletionLimit = completion.limit();
        if (cfg != null) {
            info.eventType = cfg.getAreaId();
            if (info.eventType == RandomEventPlay.INVESTMENT) {
                info.readyAt = event.readyAt();
            } else if (info.eventType == RandomEventPlay.DICE) {
                info.auxiliary = event.auxiliary();
                info.dice = event.values();
            } else if (info.eventType == RandomEventPlay.CHEST) {
                info.openedBoxes = boxInfos(event, cfg);
            } else if (info.eventType == RandomEventPlay.SPY) {
                info.auxiliary = event.auxiliary();
                info.capturedTargets = event.values();
            }
        }
        return info;
    }

    private static List<RandomEventBoxInfo> boxInfos(SceneEvent event, RandomEventDetailsCfg cfg) {
        List<List<ItemInfo>> rewardsByCategory = List.of(List.of(),
                ItemUtils.buildItemInfo(RandomEventPlay.chestReward(cfg, 1)),
                ItemUtils.buildItemInfo(RandomEventPlay.chestReward(cfg, 2)));
        List<Integer> previewOrder = RandomEventPlay.CHEST_PREVIEW_ORDER;
        List<RandomEventBoxInfo> boxes = new ArrayList<>(previewOrder.size());
        for (int i = 0; i < previewOrder.size(); i++) {
            RandomEventBoxInfo box = new RandomEventBoxInfo();
            box.opened = (event.auxiliary() & (1 << i)) != 0;
            box.previewRewards = rewardsByCategory.get(previewOrder.get(i));
            box.actualRewards = box.opened ? rewardsByCategory.get(event.values().get(i)) : List.of();
            boxes.add(box);
        }
        return boxes;
    }
}
