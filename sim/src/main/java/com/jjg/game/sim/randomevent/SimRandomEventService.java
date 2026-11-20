package com.jjg.game.sim.randomevent;

import com.jjg.game.common.pb.ItemInfo;
import com.jjg.game.common.utils.RandomUtils;
import com.jjg.game.core.constant.AddType;
import com.jjg.game.core.constant.Code;
import com.jjg.game.core.data.CommonResult;
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
import com.jjg.game.sim.pb.struct.RandomEventBoxSlotInfo;
import com.jjg.game.sim.pb.struct.RandomEventInfo;
import com.jjg.game.sim.pb.struct.RandomEventInfo.RandomEventBoxInfo;
import com.jjg.game.sim.pb.struct.RandomEventInfo.RandomEventDiceInfo;
import com.jjg.game.sim.pb.struct.RandomEventInfo.RandomEventInvestmentInfo;
import com.jjg.game.sim.pb.struct.RandomEventInfo.RandomEventQuizInfo;
import com.jjg.game.sim.pb.struct.RandomEventInfo.RandomEventRoomInfo;
import com.jjg.game.sim.pb.struct.RandomEventInfo.RandomEventSpyInfo;
import com.jjg.game.sim.randomevent.RandomEventPlay.DicePrizes;
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
        if (update != null) {
            if (ctx.getVisitTargetId() == 0 && controller.getScene() == ctx) {
                ctx.send(update);
            } else {
                log.debug("随机事件刷新已保存，当前场景未推送 playerId={},visitTargetId={},addedCount={}",
                        ctx.playerId(), ctx.getVisitTargetId(), update.events.size());
            }
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
        log.debug("查询随机事件列表成功 playerId={},visitTargetId={},eventCount={}",
                ctx.playerId(), response.visitTargetId, response.events.size());
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
        log.debug("查询随机事件详情成功 playerId={},visitTargetId={},configId={},eventType={},position={},progress={},readyAt={}",
                playerId, visitTargetId, configId, cfg.getAreaId(), event.position(), event.progress(), event.readyAt());
        return response;
    }

    public List<RandomEventInfo> ownEvents(SimPlayerContext ctx) {
        SimRandomEventState scene = dao.load(ctx.playerId());
        return infos(scene.events, ownCompletion(scene, LocalDate.now().toEpochDay()));
    }

    public CommonResult<String> gmSpawnByType(SimPlayerContext ctx, int eventType) {
        long playerId = ctx.playerId();
        Map<Integer, Integer> candidates = new HashMap<>();
        for (RandomEventDetailsCfg cfg : GameDataManager.getRandomEventDetailsCfgList()) {
            if (cfg.getAreaId() == eventType && canSpawn(cfg)) {
                candidates.put(cfg.getId(), 1);
            }
        }
        if (candidates.isEmpty()) {
            log.warn("GM 刷随机事件失败，该类型无可用配置 playerId={},eventType={}", playerId, eventType);
            return new CommonResult<>(Code.SAMPLE_ERROR);
        }

        NotifySimRandomEvents update = withScene(playerId, scene -> {
            if (fill(scene.events, candidates, 1, ignored -> 0) == 0) {
                String reason = candidates.isEmpty() ? "该类型的候选事件都已在场景中" : "场景位置已满";
                log.warn("GM 刷随机事件失败 playerId={},eventType={},eventCount={},reason={}",
                        playerId, eventType, scene.events.size(), reason);
                return null;
            }
            dao.save(scene);
            return notification(List.of(scene.events.getLast()), ownCompletion(scene, LocalDate.now().toEpochDay()));
        });
        if (update == null) {
            return new CommonResult<>(Code.FAIL);
        }
        var controller = ctx.getPlayerController();
        if (ctx.getVisitTargetId() == 0 && controller != null && controller.getScene() == ctx) {
            ctx.send(update);
        }
        RandomEventInfo added = update.events.getFirst();
        return new CommonResult<>(Code.SUCCESS, "已生成随机事件，类型=" + eventType
                + "，配置ID=" + added.configId + "，位置=" + added.position);
    }

    public int validateRoomEntry(SimPlayerContext ctx, int configId, int requiredConfigId) {
        long visitTargetId = ctx.getVisitTargetId();
        if (configId != requiredConfigId || visitTargetId != 0) {
            log.warn("随机事件小游戏开局被拒绝 playerId={},configId={},requiredConfigId={},visitTargetId={},reason={}",
                    ctx.playerId(), configId, requiredConfigId, visitTargetId,
                    configId != requiredConfigId ? "事件与小游戏不匹配" : "当前处于拜访场景");
            return Code.FORBID;
        }
        List<SceneEvent> events = dao.load(ctx.playerId()).events;
        if (indexOf(events, configId) >= 0) {
            return Code.SUCCESS;
        }
        log.warn("随机事件小游戏开局被拒绝，自己场景没有此事件 playerId={},configId={},eventCount={}",
                ctx.playerId(), configId, events.size());
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
                    log.debug("房间游戏随机事件未结算，场景中已无此事件 playerId={},configId={},eventCount={}",
                            playerId, configId, scene.events.size());
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
                int code = grant(ctx, event, outcome.rewards(), false, null);
                if (code != Code.SUCCESS) {
                    return null;
                }
                long today = LocalDate.now().toEpochDay();
                scene.completeEvent(index, today);
                dao.save(scene);
                ctx.clearRoomEvent(configId);
                log.info("房间游戏随机事件结算成功 playerId={},configId={},position={},rewards={},dailyCompletedCount={}",
                        playerId, configId, event.position(), outcome.rewards(), scene.completedOn(today));
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
            boolean sent = sender.sendTo(ownerId, received.update());
            log.debug("随机事件赠送通知 playerId={},visitTargetId={},sent={}", visitorId, ownerId, sent);
        }
        response.remainingRandomEventSends = quotas.remaining(SEND_QUOTA, visitorId, sendLimit);
        DailyCompletion completion = visitCompletion(visitorId);
        response.dailyRandomEventCompleteLimit = completion.limit();
        response.remainingRandomEventCompletions = Math.max(0, completion.limit() - completion.completed());

        VisitRandomEvents visit = new VisitRandomEvents();
        int requested = Math.min(limit(414), response.remainingRandomEventCompletions);
        fill(visit.events, EventPool.VISIT, requested, ignored -> 0);
        ctx.visitRandomEvents = visit;
        response.randomEvents = infos(visit.events, completion);
        if (log.isDebugEnabled()) {
            log.debug("拜访随机事件已生成 playerId={},visitTargetId={},requested={},configIds={},remainingCompletions={},completionLimit={}",
                    visitorId, ownerId, requested, visit.events.stream().map(SceneEvent::configId).toList(),
                    response.remainingRandomEventCompletions, completion.limit());
        }
    }

    private ReceiveResult receive(SimRandomEventState scene, long visitorId, int receiveLimit, int sendLimit) {
        long day = LocalDate.now().toEpochDay();
        int received = scene.receivedOn(day);
        int remaining = Math.max(0, Math.min(receiveLimit - received, receiveLimit - scene.pendingDonors.size()));
        if (remaining == 0 || sendLimit == 0) {
            log.debug("随机事件赠送跳过 playerId={},visitTargetId={},receiveLimit={},received={},pendingDonorCount={},sendLimit={}",
                    visitorId, scene.playerId, receiveLimit, received, scene.pendingDonors.size(), sendLimit);
            return new ReceiveResult(remaining, null);
        }
        Map<Integer, Integer> weights = weights(EventPool.DONATION);
        if (weights.isEmpty()) {
            log.warn("随机事件赠送跳过，赠送事件池无可用配置 playerId={},visitTargetId={}", visitorId, scene.playerId);
            return new ReceiveResult(remaining, null);
        }
        if (quotas.consume(SEND_QUOTA, visitorId, 1, sendLimit) < 0) {
            log.debug("随机事件赠送跳过，发送次数已达上限 playerId={},visitTargetId={},sendLimit={}",
                    visitorId, scene.playerId, sendLimit);
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
            log.error("随机事件赠送保存失败，回退发送次数 playerId={},visitTargetId={},spawned={}",
                    visitorId, scene.playerId, spawned, e);
            quotas.rollback(SEND_QUOTA, visitorId, 1);
            throw e;
        }
        log.info("随机事件赠送已保存 playerId={},visitTargetId={},spawned={},configId={},remainingReceives={},pendingDonorCount={}",
                visitorId, scene.playerId, spawned, spawned ? scene.events.getLast().configId() : null,
                remaining - 1, scene.pendingDonors.size());
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
            int systemAdded = 0;
            if (missing > 0 && (first || scene.refills < limit(400))) {
                systemAdded = fill(scene.events, EventPool.SYSTEM, missing, ignored -> 0);
                if (systemAdded > 0 && !first) {
                    scene.refills++;
                }
                if (systemAdded < missing && (first || systemAdded > 0)) {
                    log.debug("系统随机事件补刷数量不足 playerId={},day={},requested={},added={},eventCount={}",
                            ctx.playerId(), today, missing, systemAdded, scene.events.size());
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
                if (log.isInfoEnabled()) {
                    List<SceneEvent> newEvents = scene.events.subList(previousSize, scene.events.size());
                    log.info("随机事件刷新已保存 playerId={},day={},scheduled={},firstOfDay={},systemSlots={},systemAdded={},donationAdded={},configIds={},eventCount={},refills={},pendingDonorCount={}",
                            ctx.playerId(), today, scheduled, first, missing, systemAdded, newEvents.size() - systemAdded,
                            newEvents.stream().map(SceneEvent::configId).toList(), scene.events.size(), scene.refills,
                            scene.pendingDonors.size());
                }
            }
            return scheduled && added
                    ? notification(scene.events.subList(previousSize, scene.events.size()), ownCompletion(scene, today))
                    : null;
        });
    }

    public ResSimRandomEventAction act(SimPlayerContext ctx, ReqSimRandomEventAction request) {
        if (request.action < RandomEventPlay.VIEW
                || (request.action > RandomEventPlay.TARGET && request.action != RandomEventPlay.CLAIM)) {
            return reject(ctx, request, Code.PARAM_ERROR, "不支持的随机事件操作");
        }
        try {
            if (ctx.getVisitTargetId() != 0) {
                if (ctx.visitRandomEvents == null) {
                    return reject(ctx, request, Code.NOT_FOUND, "没有好友随机事件会话");
                }
                return apply(ctx, ctx.visitRandomEvents.events, request, null);
            }
            return withScene(ctx.playerId(), scene -> apply(ctx, scene.events, request, scene));
        } catch (RuntimeException e) {
            log.error("处理随机事件异常 playerId={},visitTargetId={},configId={},action={},target={}",
                    ctx.playerId(), ctx.getVisitTargetId(), request.configId, request.action, request.target, e);
            throw e;
        }
    }

    private ResSimRandomEventAction apply(SimPlayerContext ctx, List<SceneEvent> events, ReqSimRandomEventAction request,
                                          SimRandomEventState scene) {
        long playerId = ctx.playerId();
        long visitTargetId = ctx.getVisitTargetId();
        int index = indexOf(events, request.configId);
        if (index < 0) {
            return reject(ctx, request, Code.NOT_FOUND, "该场景没有此事件，eventCount=" + events.size());
        }
        SceneEvent event = events.get(index);
        RandomEventDetailsCfg cfg = GameDataManager.getRandomEventDetailsCfg(event.configId());
        if (cfg == null) {
            return reject(ctx, request, Code.SAMPLE_ERROR, "事件配置缺失");
        }
        long now = System.currentTimeMillis();
        Outcome outcome = RandomEventPlay.play(event, cfg, request.action, request.target, now);
        if (outcome.code() != Code.SUCCESS) {
            log.warn("随机事件操作被拒绝 playerId={},visitTargetId={},configId={},eventType={},position={},action={},target={},code={},reason={},progress={},auxiliary={},readyAt={},now={}",
                    playerId, visitTargetId, event.configId(), cfg.getAreaId(), event.position(), request.action, request.target,
                    outcome.code(), outcome.rejectionReason(), event.progress(), event.auxiliary(), event.readyAt(), now);
            return new ResSimRandomEventAction(outcome.code());
        }
        long today = LocalDate.now().toEpochDay();
        if (outcome.completed()) {
            int code = grant(ctx, event, outcome.rewards(), scene == null, request);
            if (code != Code.SUCCESS) {
                return new ResSimRandomEventAction(code);
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
        SceneEvent updated = outcome.event();
        if (outcome.completed()) {
            log.info("随机事件结算成功 playerId={},visitTargetId={},configId={},eventType={},position={},donorId={},action={},target={},rewards={},dailyCompletedCount={},completionLimit={}",
                    playerId, visitTargetId, event.configId(), cfg.getAreaId(), event.position(), event.donorId(),
                    request.action, request.target, outcome.rewards(), completion.completed(), completion.limit());
        } else if (event != updated) {
            log.debug("随机事件状态已更新 playerId={},visitTargetId={},configId={},eventType={},position={},action={},target={},progress={},auxiliary={},readyAt={}",
                    playerId, visitTargetId, event.configId(), cfg.getAreaId(), event.position(),
                    request.action, request.target, updated.progress(), updated.auxiliary(), updated.readyAt());
        }
        ResSimRandomEventAction response = actionResponse(outcome, cfg, completion);
        if (cfg.getAreaId() == RandomEventPlay.CHEST && request.action == RandomEventPlay.TARGET) {
            response.event.boxInfo.openedReward = outcome.event().values().get(request.target);
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

    private int grant(SimPlayerContext ctx, SceneEvent event, Map<Integer, Long> rewards, boolean visit,
                      ReqSimRandomEventAction request) {
        long playerId = ctx.playerId();
        if (visit) {
            int completionLimit = limit(413);
            if (quotas.consume(VISIT_QUOTA, playerId, 1, completionLimit) < 0) {
                log.warn("随机事件结算被拒绝，拜访完成次数已达上限 playerId={},visitTargetId={},configId={},position={},action={},target={},completionLimit={},code={}",
                        playerId, ctx.getVisitTargetId(), event.configId(), event.position(), request.action, request.target,
                        completionLimit, Code.FORBID);
                return Code.FORBID;
            }
        }
        if (rewards.isEmpty()) {
            return Code.SUCCESS;
        }
        int code = packs.addItems(playerId, rewards, AddType.SIM_RANDOM_EVENT_REWARD, Integer.toString(event.configId())).code;
        if (code != Code.SUCCESS) {
            log.warn("随机事件发奖失败，保留事件 playerId={},visitTargetId={},configId={},position={},donorId={},action={},target={},rewards={},code={}",
                    playerId, ctx.getVisitTargetId(), event.configId(), event.position(), event.donorId(),
                    request == null ? null : request.action, request == null ? null : request.target, rewards, code);
            if (visit) {
                quotas.rollback(VISIT_QUOTA, playerId, 1);
            }
        }
        return code;
    }

    private static ResSimRandomEventAction reject(SimPlayerContext ctx, ReqSimRandomEventAction request, int code, String reason) {
        log.warn("随机事件操作失败 playerId={},visitTargetId={},configId={},action={},target={},code={},reason={}",
                ctx.playerId(), ctx.getVisitTargetId(), request.configId, request.action, request.target, code, reason);
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
            if (weight > 0 && (pool == EventPool.SYSTEM || type != RandomEventPlay.ROOM) && canSpawn(cfg)) {
                weights.put(cfg.getId(), weight);
            }
        }
        return weights;
    }

    private static boolean canSpawn(RandomEventDetailsCfg cfg) {
        int type = cfg.getAreaId();
        if (type != RandomEventPlay.INVESTMENT) {
            return type >= RandomEventPlay.QUIZ && type <= RandomEventPlay.DICE;
        }
        List<Integer> duration = cfg.getParameter2();
        return duration != null && !duration.isEmpty() && duration.getFirst() > 0;
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
        info.dailyCompletedCount = completion.completed();
        info.dailyCompletionLimit = completion.limit();
        if (cfg == null) {
            return info;
        }
        info.eventType = cfg.getAreaId();
        switch (info.eventType) {
            case RandomEventPlay.QUIZ -> info.quizInfo = new RandomEventQuizInfo();
            case RandomEventPlay.ROOM -> info.roomInfo = new RandomEventRoomInfo();
            case RandomEventPlay.CHEST -> info.boxInfo = boxInfo(event, cfg);
            case RandomEventPlay.SPY -> {
                RandomEventSpyInfo spy = new RandomEventSpyInfo();
                spy.started = event.auxiliary() != 0;
                spy.capturedCount = event.progress();
                spy.capturedTargets = event.values();
                spy.requiredCount = RandomEventPlay.requiredSpyCaptures(cfg.getParameter1());
                info.spyInfo = spy;
            }
            case RandomEventPlay.INVESTMENT -> {
                RandomEventInvestmentInfo investment = new RandomEventInvestmentInfo();
                investment.started = event.progress() != 0;
                investment.readyAt = event.readyAt();
                info.investmentInfo = investment;
            }
            case RandomEventPlay.DICE -> {
                RandomEventDiceInfo dice = new RandomEventDiceInfo();
                dice.consecutiveWins = event.progress();
                dice.remainingRerolls = event.auxiliary();
                dice.dice = event.values();
                DicePrizes prizes = RandomEventPlay.dicePrizes();
                if (prizes != null) {
                    dice.baseReward = new ItemInfo();
                    dice.baseReward.itemId = prizes.itemId();
                    dice.baseReward.count = prizes.baseAmount();
                    dice.currentReward = prizes.currentAmount(event);
                }
                info.diceInfo = dice;
            }
        }
        return info;
    }

    private static RandomEventBoxInfo boxInfo(SceneEvent event, RandomEventDetailsCfg cfg) {
        List<List<ItemInfo>> rewardsByCategory = List.of(List.of(),
                ItemUtils.buildItemInfo(RandomEventPlay.chestReward(cfg, 1)),
                ItemUtils.buildItemInfo(RandomEventPlay.chestReward(cfg, 2)));
        List<Integer> previewOrder = RandomEventPlay.CHEST_PREVIEW_ORDER;
        RandomEventBoxInfo info = new RandomEventBoxInfo();
        info.remainingAdOpens = previewOrder.size() - Math.max(1, Integer.bitCount(event.auxiliary()));
        info.boxes = new ArrayList<>(previewOrder.size());
        for (int i = 0; i < previewOrder.size(); i++) {
            RandomEventBoxSlotInfo box = new RandomEventBoxSlotInfo();
            box.opened = (event.auxiliary() & (1 << i)) != 0;
            box.previewRewards = rewardsByCategory.get(previewOrder.get(i));
            box.actualRewards = box.opened ? rewardsByCategory.get(event.values().get(i)) : List.of();
            info.boxes.add(box);
        }
        return info;
    }
}
