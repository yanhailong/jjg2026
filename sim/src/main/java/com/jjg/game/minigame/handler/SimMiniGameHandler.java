package com.jjg.game.minigame.handler;

import com.jjg.game.common.constant.MessageConst;
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
import com.jjg.game.minigame.service.SimMiniGameService;
import com.jjg.game.minigame.service.GodGamblersService;
import com.jjg.game.sim.data.SimPlayerContext;
import com.jjg.game.sim.manager.SimPlayerContextRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** 小游戏请求入口，单局与道具逻辑由对应服务处理。 */
@Component
@MessageType(MessageConst.MessageTypeDef.SIM_MINI_GAME)
public class SimMiniGameHandler {
    private Logger log = LoggerFactory.getLogger(getClass());

    @Autowired
    private SimPlayerContextRegistry simPlayerContextRegistry;
    @Autowired
    private SimMiniGameService simMiniGameService;
    @Autowired
    private GodGamblersService godGamblersService;

    /** 获取关卡列表及一键通关开放状态。 */
    @Command(MiniGameConstant.MsgBean.REQ_ENTER_CHEAT_HUNT_GAME)
    public void reqEnterCheaterHuntGame(PlayerController playerController, ReqEnterCheaterHuntGame req) {
        SimPlayerContext ctx = simPlayerContextRegistry.getContext(playerController.playerId());
        if (ctx == null) {
            log.warn("进入找老千失败，模拟经营上下文不存在，playerId={}", playerController.playerId());
            playerController.send(new ResEnterCheaterHuntGame(Code.NOT_FOUND));
            return;
        }
        ctx.send(simMiniGameService.enter(ctx));
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
        ctx.send(simMiniGameService.gameInfo(ctx));
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
            log.warn("寻找作弊点位失败，模拟经营上下文不存在，playerId={}", playerController.playerId());
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
            log.warn("退出找老千失败，模拟经营上下文不存在，playerId={}", playerController.playerId());
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
            log.warn("进入赌神搓牌失败，模拟经营上下文不存在，playerId={}", playerController.playerId());
            playerController.send(new ResEnterGodGamblersGame(Code.NOT_FOUND));
            return;
        }
        ctx.send(godGamblersService.enter(ctx));
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
        ctx.send(godGamblersService.start(ctx));
    }

    /** 获取自己的牌，由服务核对局号及次数并处理再次搓牌消耗。 */
    @Command(MiniGameConstant.MsgBean.REQ_GOD_GAMBLERS_SELF_POKER)
    public void reqGodGamblersSelfPoker(PlayerController playerController, ReqGodGamblersSelfPoker req) {
        SimPlayerContext ctx = simPlayerContextRegistry.getContext(playerController.playerId());
        if (ctx == null) {
            log.warn("获取赌神搓牌自己牌失败，模拟经营上下文不存在，playerId={}", playerController.playerId());
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
            log.warn("确认赌神搓牌比拼失败，模拟经营上下文不存在，playerId={}", playerController.playerId());
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
}
