package com.jjg.game.minigame.handler;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractResponse;
import com.jjg.game.common.protostuff.Command;
import com.jjg.game.common.protostuff.MessageType;
import com.jjg.game.core.constant.Code;
import com.jjg.game.core.data.PlayerController;
import com.jjg.game.minigame.constant.MiniGameConstant;
import com.jjg.game.minigame.pb.req.ReqCheaterHuntGameInfo;
import com.jjg.game.minigame.pb.req.ReqEnterCheaterHuntGame;
import com.jjg.game.minigame.pb.req.ReqExitCheaterHuntGame;
import com.jjg.game.minigame.pb.req.ReqFindCheaterHuntSpot;
import com.jjg.game.minigame.pb.req.ReqEnterGodGamblersGame;
import com.jjg.game.minigame.pb.req.ReqGodGamblersConfirmBattle;
import com.jjg.game.minigame.pb.req.ReqGodGamblersQuickBattle;
import com.jjg.game.minigame.pb.req.ReqGodGamblersSelfPoker;
import com.jjg.game.minigame.pb.req.ReqQuickClearCheaterHuntGame;
import com.jjg.game.minigame.pb.req.ReqStartGodGamblersGame;
import com.jjg.game.minigame.pb.req.ReqUnlockNextLevel;
import com.jjg.game.minigame.pb.req.ReqEnterHappyHappyFishGame;
import com.jjg.game.minigame.pb.req.ReqStartHappyHappyFishGame;
import com.jjg.game.minigame.pb.req.ReqSetHappyHappyFishPaused;
import com.jjg.game.minigame.pb.req.ReqCatchHappyHappyFish;
import com.jjg.game.minigame.pb.req.ReqEndHappyHappyFishGame;
import com.jjg.game.minigame.pb.req.ReqQuickClearHappyHappyFishGame;
import com.jjg.game.minigame.pb.req.ReqHappyHappyFishSkills;
import com.jjg.game.minigame.pb.req.ReqUpgradeHappyHappyFishSkill;
import com.jjg.game.minigame.pb.req.ReqEnterSpeedyDeliveryGame;
import com.jjg.game.minigame.pb.req.ReqStartSpeedyDeliveryGame;
import com.jjg.game.minigame.pb.req.ReqSetSpeedyDeliveryPaused;
import com.jjg.game.minigame.pb.req.ReqDeliverSpeedyDeliveryGoods;
import com.jjg.game.minigame.pb.req.ReqDeductSpeedyDeliveryTime;
import com.jjg.game.minigame.pb.req.ReqEndSpeedyDeliveryGame;
import com.jjg.game.minigame.pb.req.ReqQuickClearSpeedyDeliveryGame;
import com.jjg.game.minigame.pb.req.ReqSpeedyDeliveryGoodsInfo;
import com.jjg.game.minigame.pb.req.ReqStockSpeedyDeliveryShelf;
import com.jjg.game.minigame.pb.req.ReqSpeedyDeliverySkills;
import com.jjg.game.minigame.pb.req.ReqUpgradeSpeedyDeliverySkill;
import com.jjg.game.minigame.pb.res.ResCheaterHuntGameInfo;
import com.jjg.game.minigame.pb.res.ResEnterCheaterHuntGame;
import com.jjg.game.minigame.pb.res.ResExitCheaterHuntGame;
import com.jjg.game.minigame.pb.res.ResFindCheaterHuntSpot;
import com.jjg.game.minigame.pb.res.ResEnterGodGamblersGame;
import com.jjg.game.minigame.pb.res.ResGodGamblersConfirmBattle;
import com.jjg.game.minigame.pb.res.ResGodGamblersQuickBattle;
import com.jjg.game.minigame.pb.res.ResGodGamblersSelfPoker;
import com.jjg.game.minigame.pb.res.ResQuickClearCheaterHuntGame;
import com.jjg.game.minigame.pb.res.ResStartGodGamblersGame;
import com.jjg.game.minigame.pb.res.ResUnlockNextLevel;
import com.jjg.game.minigame.pb.res.ResEnterHappyHappyFishGame;
import com.jjg.game.minigame.pb.res.ResStartHappyHappyFishGame;
import com.jjg.game.minigame.pb.res.ResSetHappyHappyFishPaused;
import com.jjg.game.minigame.pb.res.ResCatchHappyHappyFish;
import com.jjg.game.minigame.pb.res.ResEndHappyHappyFishGame;
import com.jjg.game.minigame.pb.res.ResQuickClearHappyHappyFishGame;
import com.jjg.game.minigame.pb.res.ResHappyHappyFishSkills;
import com.jjg.game.minigame.pb.res.ResUpgradeHappyHappyFishSkill;
import com.jjg.game.minigame.pb.res.ResEnterSpeedyDeliveryGame;
import com.jjg.game.minigame.pb.res.ResStartSpeedyDeliveryGame;
import com.jjg.game.minigame.pb.res.ResSetSpeedyDeliveryPaused;
import com.jjg.game.minigame.pb.res.ResDeliverSpeedyDeliveryGoods;
import com.jjg.game.minigame.pb.res.ResDeductSpeedyDeliveryTime;
import com.jjg.game.minigame.pb.res.ResEndSpeedyDeliveryGame;
import com.jjg.game.minigame.pb.res.ResQuickClearSpeedyDeliveryGame;
import com.jjg.game.minigame.pb.res.ResSpeedyDeliveryGoodsInfo;
import com.jjg.game.minigame.pb.res.ResStockSpeedyDeliveryShelf;
import com.jjg.game.minigame.pb.res.ResSpeedyDeliverySkills;
import com.jjg.game.minigame.pb.res.ResUpgradeSpeedyDeliverySkill;
import com.jjg.game.minigame.service.SimMiniGameService;
import com.jjg.game.minigame.service.GodGamblersService;
import com.jjg.game.minigame.service.HappyHappyFishService;
import com.jjg.game.minigame.service.SpeedyDeliveryService;
import com.jjg.game.sim.constant.SimConstant;
import com.jjg.game.sim.data.SimPlayerContext;
import com.jjg.game.sim.manager.SimPlayerContextRegistry;
import com.jjg.game.sim.randomevent.SimRandomEventService;
import com.jjg.game.sim.service.SimGuideService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.function.IntFunction;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/** 小游戏请求入口，单局与道具逻辑由对应服务处理。 */
@Component
@MessageType(MessageConst.MessageTypeDef.SIM_MINI_GAME)
public class SimMiniGameHandler {
    private Logger log = LoggerFactory.getLogger(getClass());

    @Autowired
    private SimPlayerContextRegistry simPlayerContextRegistry;
    @Autowired
    private SimRandomEventService randomEvents;
    @Autowired
    private SimMiniGameService simMiniGameService;
    @Autowired
    private GodGamblersService godGamblersService;
    @Autowired
    private HappyHappyFishService happyHappyFishService;
    @Autowired
    private SpeedyDeliveryService speedyDeliveryService;
    @Autowired
    private SimGuideService guideService;

    private <T extends AbstractResponse> T enterGame(SimPlayerContext ctx, int requiredConfigId, int configId,
                                                   Supplier<T> enter, IntFunction<T> error) {
        log.debug("进入模拟经营小游戏 playerId={},requiredConfigId={},randomEventConfigId={}",
                ctx.playerId(), requiredConfigId, configId);
        ctx.clearPendingRoomEvent();
        if (configId == 0) {
            return enter.get();
        }
        int code = randomEvents.validateRoomEntry(ctx, configId, requiredConfigId);
        if (code != Code.SUCCESS) {
            return error.apply(code);
        }
        T response = enter.get();
        if (response.code == Code.SUCCESS) {
            ctx.setPendingRoomEventConfigId(configId);
        }
        return response;
    }

    private <T extends AbstractResponse> T startGame(SimPlayerContext ctx, int requiredConfigId, Supplier<T> start,
                                                   IntFunction<T> error, IntConsumer bindEvent) {
        int configId = ctx.pendingRoomEventConfigId(requiredConfigId);
        if (configId == 0) {
            return start.get();
        }
        int code = randomEvents.validateRoomEntry(ctx, configId, requiredConfigId);
        if (code != Code.SUCCESS) {
            ctx.clearPendingRoomEvent();
            return error.apply(code);
        }
        T response = start.get();
        if (response.code == Code.SUCCESS) {
            bindEvent.accept(configId);
            ctx.clearPendingRoomEvent();
            log.debug("小游戏开局绑定随机事件 playerId={},randomEventConfigId={}", ctx.playerId(), configId);
        }
        return response;
    }

    /** 获取关卡列表及一键通关开放状态。 */
    @Command(MiniGameConstant.MsgBean.REQ_ENTER_CHEAT_HUNT_GAME)
    public void reqEnterCheaterHuntGame(PlayerController playerController, ReqEnterCheaterHuntGame req) {
        SimPlayerContext ctx = simPlayerContextRegistry.getContext(playerController.playerId());
        if (ctx == null) {
            log.warn("进入找老千失败，模拟经营上下文不存在 playerId={},randomEventConfigId={}",
                    playerController.playerId(), req.randomEventConfigId);
            playerController.send(new ResEnterCheaterHuntGame(Code.NOT_FOUND));
            return;
        }
        ResEnterCheaterHuntGame res = enterGame(ctx, 2003, req.randomEventConfigId,
                () -> simMiniGameService.enter(ctx), ResEnterCheaterHuntGame::new);
        ctx.send(res);
        if (res.code == Code.SUCCESS) {
            guideService.trigger(ctx, SimConstant.GuideCondition.ENTER_MINI_GAME, 1, true);
        }
    }

    /** 获取当前关卡并开始新局，由服务检查已有单局。 */
    @Command(MiniGameConstant.MsgBean.REQ_CHEAT_HUNT_GAME_INFO)
    public void reqCheaterHuntGameInfo(PlayerController playerController, ReqCheaterHuntGameInfo req) {
        SimPlayerContext ctx = simPlayerContextRegistry.getContext(playerController.playerId());
        if (ctx == null) {
            log.warn("获取找老千关卡信息失败，模拟经营上下文不存在，playerId={}", playerController.playerId());
            playerController.send(new ResCheaterHuntGameInfo(Code.NOT_FOUND));
            return;
        }
        ctx.send(startGame(ctx, 2003, () -> simMiniGameService.gameInfo(ctx), ResCheaterHuntGameInfo::new,
                configId -> ctx.getCheaterHuntRound().randomEventConfigId = configId));
    }

    /** 按顺序解锁下一关，由服务校验并扣除解锁道具。 */
    @Command(MiniGameConstant.MsgBean.REQ_UNLOCK_NEXT_LEVEL)
    public void reqUnlockNextLevel(PlayerController playerController, ReqUnlockNextLevel req) {
        SimPlayerContext ctx = simPlayerContextRegistry.getContext(playerController.playerId());
        if (ctx == null) {
            log.warn("解锁找老千下一关失败，模拟经营上下文不存在，playerId={}", playerController.playerId());
            playerController.send(new ResUnlockNextLevel(Code.NOT_FOUND));
            return;
        }
        ctx.send(simMiniGameService.unlockNextLevel(ctx));
    }

    /** 提交点位或触发超时结算，由服务校验局号、时限及发奖状态。 */
    @Command(MiniGameConstant.MsgBean.REQ_FIND_CHEATER_HUNT_SPOT)
    public void reqFindCheaterHuntSpot(PlayerController playerController, ReqFindCheaterHuntSpot req) {
        SimPlayerContext ctx = simPlayerContextRegistry.getContext(playerController.playerId());
        if (ctx == null) {
            log.warn("找老千寻找点位失败，模拟经营上下文不存在 playerId={},roundId={},spotId={}",
                    playerController.playerId(), req.roundId, req.spotId);
            playerController.send(new ResFindCheaterHuntSpot(Code.NOT_FOUND));
            return;
        }
        ctx.send(simMiniGameService.find(ctx, req.roundId, req.spotId));
    }

    /** 主动放弃指定单局。 */
    @Command(MiniGameConstant.MsgBean.REQ_EXIT_CHEATER_HUNT_GAME)
    public void reqExitCheaterHuntGame(PlayerController playerController, ReqExitCheaterHuntGame req) {
        SimPlayerContext ctx = simPlayerContextRegistry.getContext(playerController.playerId());
        if (ctx == null) {
            log.warn("退出找老千失败，模拟经营上下文不存在 playerId={},roundId={}", playerController.playerId(), req.roundId);
            playerController.send(new ResExitCheaterHuntGame(Code.NOT_FOUND));
            return;
        }
        ctx.send(simMiniGameService.exit(ctx, req.roundId));
    }

    /** 使用配置道具一键通关，由服务处理消耗及奖励。 */
    @Command(MiniGameConstant.MsgBean.REQ_QUICK_CLEAR_CHEATER_HUNT_GAME)
    public void reqQuickClearCheaterHuntGame(PlayerController playerController, ReqQuickClearCheaterHuntGame req) {
        SimPlayerContext ctx = simPlayerContextRegistry.getContext(playerController.playerId());
        if (ctx == null) {
            log.warn("一键通关找老千失败，模拟经营上下文不存在，playerId={}", playerController.playerId());
            playerController.send(new ResQuickClearCheaterHuntGame(Code.NOT_FOUND));
            return;
        }
        ctx.send(simMiniGameService.quickClear(ctx));
    }

    /** 进入赌神搓牌，返回配置消耗及对应道具持有数量。 */
    @Command(MiniGameConstant.MsgBean.REQ_ENTER_GOD_GAMBLERS_GAME)
    public void reqEnterGodGamblersGame(PlayerController playerController, ReqEnterGodGamblersGame req) {
        SimPlayerContext ctx = simPlayerContextRegistry.getContext(playerController.playerId());
        if (ctx == null) {
            log.warn("进入赌神搓牌失败，模拟经营上下文不存在 playerId={},randomEventConfigId={}",
                    playerController.playerId(), req.randomEventConfigId);
            playerController.send(new ResEnterGodGamblersGame(Code.NOT_FOUND));
            return;
        }
        ResEnterGodGamblersGame res = enterGame(ctx, 2004, req.randomEventConfigId,
                () -> godGamblersService.enter(ctx), ResEnterGodGamblersGame::new);
        ctx.send(res);
        if (res.code == Code.SUCCESS) {
            guideService.trigger(ctx, SimConstant.GuideCondition.ENTER_MINI_GAME, 2, true);
        }
    }

    /** 开始单局，由服务处理扣费及对手牌生成。 */
    @Command(MiniGameConstant.MsgBean.REQ_START_GOD_GAMBLERS_GAME)
    public void reqStartGodGamblersGame(PlayerController playerController, ReqStartGodGamblersGame req) {
        SimPlayerContext ctx = simPlayerContextRegistry.getContext(playerController.playerId());
        if (ctx == null) {
            log.warn("开始赌神搓牌失败，模拟经营上下文不存在，playerId={}", playerController.playerId());
            playerController.send(new ResStartGodGamblersGame(Code.NOT_FOUND));
            return;
        }
        ctx.send(startGame(ctx, 2004, () -> godGamblersService.start(ctx), ResStartGodGamblersGame::new,
                configId -> ctx.getGodGamblersRound().randomEventConfigId = configId));
    }

    /** 获取自己的牌，由服务核对局号及次数并处理再次搓牌消耗。 */
    @Command(MiniGameConstant.MsgBean.REQ_GOD_GAMBLERS_SELF_POKER)
    public void reqGodGamblersSelfPoker(PlayerController playerController, ReqGodGamblersSelfPoker req) {
        SimPlayerContext ctx = simPlayerContextRegistry.getContext(playerController.playerId());
        if (ctx == null) {
            log.warn("获取赌神搓牌自己牌失败，模拟经营上下文不存在 playerId={},roundId={},rubCount={}",
                    playerController.playerId(), req.roundId, req.rubCount);
            playerController.send(new ResGodGamblersSelfPoker(Code.NOT_FOUND));
            return;
        }
        ctx.send(godGamblersService.selfPoker(ctx, req.roundId, req.rubCount));
    }

    /** 确认比拼，由服务按当前牌对应配置结算奖励。 */
    @Command(MiniGameConstant.MsgBean.REQ_GOD_GAMBLERS_CONFIRM_BATTLE)
    public void reqGodGamblersConfirmBattle(PlayerController playerController, ReqGodGamblersConfirmBattle req) {
        SimPlayerContext ctx = simPlayerContextRegistry.getContext(playerController.playerId());
        if (ctx == null) {
            log.warn("确认赌神搓牌比拼失败，模拟经营上下文不存在 playerId={},roundId={}", playerController.playerId(), req.roundId);
            playerController.send(new ResGodGamblersConfirmBattle(Code.NOT_FOUND));
            return;
        }
        ctx.send(godGamblersService.confirmBattle(ctx, req.roundId));
    }

    /** 快速比拼，由服务完成扣费、双方发牌及奖励结算。 */
    @Command(MiniGameConstant.MsgBean.REQ_GOD_GAMBLERS_QUICK_BATTLE)
    public void reqGodGamblersQuickBattle(PlayerController playerController, ReqGodGamblersQuickBattle req) {
        SimPlayerContext ctx = simPlayerContextRegistry.getContext(playerController.playerId());
        if (ctx == null) {
            log.warn("赌神搓牌快速比拼失败，模拟经营上下文不存在，playerId={}", playerController.playerId());
            playerController.send(new ResGodGamblersQuickBattle(Code.NOT_FOUND));
            return;
        }
        ctx.send(godGamblersService.quickBattle(ctx));
    }

    @Command(MiniGameConstant.MsgBean.REQ_ENTER_HAPPY_HAPPY_FISH_GAME)
    public void reqEnterHappyHappyFishGame(PlayerController playerController, ReqEnterHappyHappyFishGame req) {
        SimPlayerContext ctx = simPlayerContextRegistry.getContext(playerController.playerId());
        if (ctx == null) {
            log.warn("进入欢乐捉鱼失败，模拟经营上下文不存在 playerId={},randomEventConfigId={}",
                    playerController.playerId(), req.randomEventConfigId);
            playerController.send(new ResEnterHappyHappyFishGame(Code.NOT_FOUND));
            return;
        }
        ResEnterHappyHappyFishGame res = enterGame(ctx, 2002, req.randomEventConfigId,
                () -> happyHappyFishService.enter(ctx), ResEnterHappyHappyFishGame::new);
        ctx.send(res);
        if (res.code == Code.SUCCESS) {
            guideService.trigger(ctx, SimConstant.GuideCondition.ENTER_MINI_GAME, 3, true);
        }
    }

    @Command(MiniGameConstant.MsgBean.REQ_START_HAPPY_HAPPY_FISH_GAME)
    public void reqStartHappyHappyFishGame(PlayerController playerController, ReqStartHappyHappyFishGame req) {
        SimPlayerContext ctx = simPlayerContextRegistry.getContext(playerController.playerId());
        if (ctx == null) {
            log.warn("开始欢乐捉鱼失败，模拟经营上下文不存在 playerId={}", playerController.playerId());
            playerController.send(new ResStartHappyHappyFishGame(Code.NOT_FOUND));
            return;
        }
        ctx.send(startGame(ctx, 2002, () -> happyHappyFishService.start(ctx), ResStartHappyHappyFishGame::new,
                configId -> ctx.getHappyHappyFishRound().randomEventConfigId = configId));
    }

    @Command(MiniGameConstant.MsgBean.REQ_SET_HAPPY_HAPPY_FISH_PAUSED)
    public void reqSetHappyHappyFishPaused(PlayerController playerController, ReqSetHappyHappyFishPaused req) {
        SimPlayerContext ctx = simPlayerContextRegistry.getContext(playerController.playerId());
        if (ctx == null) {
            log.warn("欢乐捉鱼切换暂停状态失败，玩家会话不存在 playerId={},paused={}",
                    playerController.playerId(), req.paused);
            playerController.send(new ResSetHappyHappyFishPaused(Code.NOT_FOUND));
            return;
        }
        ctx.send(happyHappyFishService.setPaused(ctx, req.paused));
    }

    @Command(MiniGameConstant.MsgBean.REQ_CATCH_HAPPY_HAPPY_FISH)
    public void reqCatchHappyHappyFish(PlayerController playerController, ReqCatchHappyHappyFish req) {
        SimPlayerContext ctx = simPlayerContextRegistry.getContext(playerController.playerId());
        if (ctx == null) {
            log.warn("欢乐捉鱼捕获失败，模拟经营上下文不存在 playerId={},roundId={},fishId={}",
                    playerController.playerId(), req.roundId, req.fishId);
            playerController.send(new ResCatchHappyHappyFish(Code.NOT_FOUND));
            return;
        }
        ctx.send(happyHappyFishService.catchFish(ctx, req.roundId, req.fishId));
    }

    @Command(MiniGameConstant.MsgBean.REQ_END_HAPPY_HAPPY_FISH_GAME)
    public void reqEndHappyHappyFishGame(PlayerController playerController, ReqEndHappyHappyFishGame req) {
        SimPlayerContext ctx = simPlayerContextRegistry.getContext(playerController.playerId());
        if (ctx == null) {
            log.warn("结束欢乐捉鱼失败，模拟经营上下文不存在 playerId={},roundId={}", playerController.playerId(), req.roundId);
            playerController.send(new ResEndHappyHappyFishGame(Code.NOT_FOUND));
            return;
        }
        ctx.send(happyHappyFishService.end(ctx, req.roundId));
    }

    @Command(MiniGameConstant.MsgBean.REQ_QUICK_CLEAR_HAPPY_HAPPY_FISH_GAME)
    public void reqQuickClearHappyHappyFishGame(PlayerController playerController, ReqQuickClearHappyHappyFishGame req) {
        SimPlayerContext ctx = simPlayerContextRegistry.getContext(playerController.playerId());
        if (ctx == null) {
            log.warn("欢乐捉鱼一键通关失败，模拟经营上下文不存在 playerId={}", playerController.playerId());
            playerController.send(new ResQuickClearHappyHappyFishGame(Code.NOT_FOUND));
            return;
        }
        ctx.send(happyHappyFishService.quickClear(ctx));
    }

    @Command(MiniGameConstant.MsgBean.REQ_HAPPY_HAPPY_FISH_SKILLS)
    public void reqHappyHappyFishSkills(PlayerController playerController, ReqHappyHappyFishSkills req) {
        SimPlayerContext ctx = simPlayerContextRegistry.getContext(playerController.playerId());
        if (ctx == null) {
            log.warn("查询欢乐捉鱼技能失败，模拟经营上下文不存在 playerId={}", playerController.playerId());
            playerController.send(new ResHappyHappyFishSkills(Code.NOT_FOUND));
            return;
        }
        ctx.send(happyHappyFishService.skills(ctx));
    }

    @Command(MiniGameConstant.MsgBean.REQ_UPGRADE_HAPPY_HAPPY_FISH_SKILL)
    public void reqUpgradeHappyHappyFishSkill(PlayerController playerController, ReqUpgradeHappyHappyFishSkill req) {
        SimPlayerContext ctx = simPlayerContextRegistry.getContext(playerController.playerId());
        if (ctx == null) {
            log.warn("升级欢乐捉鱼技能失败，模拟经营上下文不存在 playerId={},groupId={}",
                    playerController.playerId(), req.groupId);
            playerController.send(new ResUpgradeHappyHappyFishSkill(Code.NOT_FOUND));
            return;
        }
        ctx.send(happyHappyFishService.upgradeSkill(ctx, req.groupId));
    }

    @Command(MiniGameConstant.MsgBean.REQ_ENTER_SPEEDY_DELIVERY_GAME)
    public void reqEnterSpeedyDeliveryGame(PlayerController playerController, ReqEnterSpeedyDeliveryGame req) {
        SimPlayerContext ctx = simPlayerContextRegistry.getContext(playerController.playerId());
        if (ctx == null) {
            log.warn("进入飞驰快送失败，模拟经营上下文不存在 playerId={},randomEventConfigId={}",
                    playerController.playerId(), req.randomEventConfigId);
            playerController.send(new ResEnterSpeedyDeliveryGame(Code.NOT_FOUND));
            return;
        }
        ResEnterSpeedyDeliveryGame res = enterGame(ctx, 2001, req.randomEventConfigId,
                () -> speedyDeliveryService.enter(ctx), ResEnterSpeedyDeliveryGame::new);
        ctx.send(res);
        if (res.code == Code.SUCCESS) {
            guideService.trigger(ctx, SimConstant.GuideCondition.ENTER_MINI_GAME, 4, true);
        }
    }

    @Command(MiniGameConstant.MsgBean.REQ_START_SPEEDY_DELIVERY_GAME)
    public void reqStartSpeedyDeliveryGame(PlayerController playerController, ReqStartSpeedyDeliveryGame req) {
        SimPlayerContext ctx = simPlayerContextRegistry.getContext(playerController.playerId());
        if (ctx == null) {
            log.warn("开始飞驰快送失败，模拟经营上下文不存在 playerId={}", playerController.playerId());
            playerController.send(new ResStartSpeedyDeliveryGame(Code.NOT_FOUND));
            return;
        }
        ctx.send(startGame(ctx, 2001, () -> speedyDeliveryService.start(ctx), ResStartSpeedyDeliveryGame::new,
                configId -> ctx.getSpeedyDeliveryRound().randomEventConfigId = configId));
    }

    @Command(MiniGameConstant.MsgBean.REQ_SET_SPEEDY_DELIVERY_PAUSED)
    public void reqSetSpeedyDeliveryPaused(PlayerController playerController, ReqSetSpeedyDeliveryPaused req) {
        SimPlayerContext ctx = simPlayerContextRegistry.getContext(playerController.playerId());
        if (ctx == null) {
            log.warn("飞驰快送切换暂停状态失败，玩家会话不存在 playerId={},paused={}",
                    playerController.playerId(), req.paused);
            playerController.send(new ResSetSpeedyDeliveryPaused(Code.NOT_FOUND));
            return;
        }
        ctx.send(speedyDeliveryService.setPaused(ctx, req.paused));
    }

    @Command(MiniGameConstant.MsgBean.REQ_DELIVER_SPEEDY_DELIVERY_GOODS)
    public void reqDeliverSpeedyDeliveryGoods(PlayerController playerController, ReqDeliverSpeedyDeliveryGoods req) {
        SimPlayerContext ctx = simPlayerContextRegistry.getContext(playerController.playerId());
        if (ctx == null) {
            log.warn("飞驰快送交货失败，模拟经营上下文不存在 playerId={},truckId={},count={}",
                    playerController.playerId(), req.truckId, req.goodsIds == null ? 0 : req.goodsIds.size());
            playerController.send(new ResDeliverSpeedyDeliveryGoods(Code.NOT_FOUND));
            return;
        }
        ctx.send(speedyDeliveryService.deliver(ctx, req.goodsIds, req.truckId));
    }

    @Command(MiniGameConstant.MsgBean.REQ_DEDUCT_SPEEDY_DELIVERY_TIME)
    public void reqDeductSpeedyDeliveryTime(PlayerController playerController, ReqDeductSpeedyDeliveryTime req) {
        SimPlayerContext ctx = simPlayerContextRegistry.getContext(playerController.playerId());
        if (ctx == null) {
            log.warn("飞驰快送扣时失败，模拟经营上下文不存在 playerId={},seconds={}", playerController.playerId(), req.seconds);
            playerController.send(new ResDeductSpeedyDeliveryTime(Code.NOT_FOUND));
            return;
        }
        ctx.send(speedyDeliveryService.deductTime(ctx, req.seconds));
    }

    @Command(MiniGameConstant.MsgBean.REQ_END_SPEEDY_DELIVERY_GAME)
    public void reqEndSpeedyDeliveryGame(PlayerController playerController, ReqEndSpeedyDeliveryGame req) {
        SimPlayerContext ctx = simPlayerContextRegistry.getContext(playerController.playerId());
        if (ctx == null) {
            log.warn("结束飞驰快送失败，模拟经营上下文不存在 playerId={}", playerController.playerId());
            playerController.send(new ResEndSpeedyDeliveryGame(Code.NOT_FOUND));
            return;
        }
        ctx.send(speedyDeliveryService.end(ctx));
    }

    @Command(MiniGameConstant.MsgBean.REQ_QUICK_CLEAR_SPEEDY_DELIVERY_GAME)
    public void reqQuickClearSpeedyDeliveryGame(PlayerController playerController, ReqQuickClearSpeedyDeliveryGame req) {
        SimPlayerContext ctx = simPlayerContextRegistry.getContext(playerController.playerId());
        if (ctx == null) {
            log.warn("飞驰快送一键通关失败，模拟经营上下文不存在 playerId={}", playerController.playerId());
            playerController.send(new ResQuickClearSpeedyDeliveryGame(Code.NOT_FOUND));
            return;
        }
        ctx.send(speedyDeliveryService.quickClear(ctx));
    }

    @Command(MiniGameConstant.MsgBean.REQ_STOCK_SPEEDY_DELIVERY_SHELF)
    public void reqStockSpeedyDeliveryShelf(PlayerController playerController, ReqStockSpeedyDeliveryShelf req) {
        SimPlayerContext ctx = simPlayerContextRegistry.getContext(playerController.playerId());
        if (ctx == null) {
            log.warn("货物上架失败，玩家会话不存在 playerId={},type={},slotId={},goodsId={}",
                    playerController.playerId(), req.type, req.slotId, req.goodsId);
            playerController.send(new ResStockSpeedyDeliveryShelf(Code.NOT_FOUND));
            return;
        }
        ctx.send(speedyDeliveryService.stockShelf(ctx, req.type, req.slotId, req.goodsId));
    }

    @Command(MiniGameConstant.MsgBean.REQ_SPEEDY_DELIVERY_GOODS_INFO)
    public void reqSpeedyDeliveryGoodsInfo(PlayerController playerController, ReqSpeedyDeliveryGoodsInfo req) {
        SimPlayerContext ctx = simPlayerContextRegistry.getContext(playerController.playerId());
        if (ctx == null) {
            log.warn("查询货物失败，玩家会话不存在 playerId={},type={}", playerController.playerId(), req.type);
            playerController.send(new ResSpeedyDeliveryGoodsInfo(Code.NOT_FOUND));
            return;
        }
        ctx.send(speedyDeliveryService.goodsInfo(ctx, req.type));
    }

    @Command(MiniGameConstant.MsgBean.REQ_SPEEDY_DELIVERY_SKILLS)
    public void reqSpeedyDeliverySkills(PlayerController playerController, ReqSpeedyDeliverySkills req) {
        SimPlayerContext ctx = simPlayerContextRegistry.getContext(playerController.playerId());
        if (ctx == null) {
            log.warn("查询飞驰快送技能失败，玩家会话不存在 playerId={}", playerController.playerId());
            playerController.send(new ResSpeedyDeliverySkills(Code.NOT_FOUND));
            return;
        }
        ctx.send(speedyDeliveryService.skills(ctx));
    }

    @Command(MiniGameConstant.MsgBean.REQ_UPGRADE_SPEEDY_DELIVERY_SKILL)
    public void reqUpgradeSpeedyDeliverySkill(PlayerController playerController, ReqUpgradeSpeedyDeliverySkill req) {
        SimPlayerContext ctx = simPlayerContextRegistry.getContext(playerController.playerId());
        if (ctx == null) {
            log.warn("升级飞驰快送技能失败，玩家会话不存在 playerId={},groupId={}", playerController.playerId(), req.groupId);
            playerController.send(new ResUpgradeSpeedyDeliverySkill(Code.NOT_FOUND));
            return;
        }
        ctx.send(speedyDeliveryService.upgradeSkill(ctx, req.groupId));
    }
}
