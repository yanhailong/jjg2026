package com.jjg.game.minigame.service;

import com.jjg.game.common.pb.ItemInfo;
import com.jjg.game.core.constant.AddType;
import com.jjg.game.core.constant.Code;
import com.jjg.game.core.data.CommonResult;
import com.jjg.game.core.data.ItemOperationResult;
import com.jjg.game.core.data.Player;
import com.jjg.game.core.data.PlayerController;
import com.jjg.game.core.service.PlayerPackService;
import com.jjg.game.minigame.constant.MiniGameConstant.RoundState;
import com.jjg.game.minigame.dao.SpeedyDeliveryDao;
import com.jjg.game.minigame.data.SpeedyDeliveryData;
import com.jjg.game.minigame.data.SpeedyDeliveryRound;
import com.jjg.game.minigame.pb.res.ResEndSpeedyDeliveryGame;
import com.jjg.game.sampledata.GameDataManager;
import com.jjg.game.sampledata.bean.GlobalConfigCfg;
import com.jjg.game.sampledata.bean.MinigameSpeedyDeliveryCfg;
import com.jjg.game.sampledata.bean.MinigameTruckCfg;
import com.jjg.game.sampledata.bean.MinigamegoodsCfg;
import com.jjg.game.sim.data.SimChapterData;
import com.jjg.game.sim.data.SimPlayerContext;
import com.jjg.game.sim.pb.res.ResChapterInfo;
import com.jjg.game.sim.service.SimAutoSaveService;
import com.jjg.game.sim.service.SimChapterService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SpeedyDeliveryServiceTest {
    private static final long PLAYER_ID = 123;
    private static final Map<Integer, Long> COST = Map.of(1980001, 1L);
    private static final Map<Integer, Long> GIFT = Map.of(1980001, 3L);
    private final SpeedyDeliveryDao dao = mock(SpeedyDeliveryDao.class);
    private final SimAutoSaveService saves = mock(SimAutoSaveService.class);
    private final PlayerPackService pack = mock(PlayerPackService.class);
    private final SimChapterService chapters = mock(SimChapterService.class);
    private final MiniGameConditionService conditions = mock(MiniGameConditionService.class);
    private final Player player = mock(Player.class);
    private final PlayerController controller = mock(PlayerController.class);
    private final MinigameSpeedyDeliveryCfg cfg = mock(MinigameSpeedyDeliveryCfg.class);
    private final MinigameTruckCfg truck = mock(MinigameTruckCfg.class);
    private final GlobalConfigCfg quickScore = mock(GlobalConfigCfg.class);
    private final SimPlayerContext ctx = new SimPlayerContext();
    private final SpeedyDeliveryData data = new SpeedyDeliveryData();
    private final SpeedyDeliveryService service = new SpeedyDeliveryService(dao, saves, pack, chapters, conditions);
    private MockedStatic<GameDataManager> gameData;

    @BeforeEach
    void setUp() {
        ctx.setPlayerId(PLAYER_ID);
        ctx.setPlayerController(controller);
        when(controller.getPlayer()).thenReturn(player);
        when(controller.isOnline()).thenReturn(true);
        data.setPlayerId(PLAYER_ID);
        data.setLastDailyGrantTime(System.currentTimeMillis());
        ctx.setSpeedyDeliveryData(data);
        SimChapterData chapter = new SimChapterData();
        chapter.setLevelId(1002);
        ctx.setSimChapterData(chapter);
        when(cfg.getDuration()).thenReturn(90);
        when(cfg.getBackpackCapacity()).thenReturn(4);
        when(cfg.getConsumeCount()).thenReturn(COST);
        when(cfg.getGrantCount()).thenReturn(GIFT);
        Map<Integer, Map<Integer, Integer>> rewards = new LinkedHashMap<>();
        rewards.put(400, Map.of(1990000, 300));
        rewards.put(0, Map.of(1990000, 100));
        rewards.put(200, Map.of(1990000, 200));
        when(cfg.getReward()).thenReturn(rewards);
        GlobalConfigCfg unlock = mock(GlobalConfigCfg.class);
        when(unlock.getIntValue()).thenReturn(1002);
        when(quickScore.getIntValue()).thenReturn(400);
        gameData = mockStatic(GameDataManager.class);
        gameData.when(GameDataManager::getMinigameSpeedyDeliveryCfgMap).thenReturn(Map.of(9999, cfg));
        gameData.when(() -> GameDataManager.getGlobalConfigCfg(361)).thenReturn(unlock);
        gameData.when(() -> GameDataManager.getGlobalConfigCfg(362)).thenReturn(quickScore);
        gameData.when(() -> GameDataManager.getMinigameTruckCfg(1)).thenReturn(truck);
        when(truck.getNormalMultiplier()).thenReturn(50);
        when(truck.getFragileMultiplier()).thenReturn(200);
        when(truck.getDangerMultiplier()).thenReturn(300);
        goods(101, 15, 1);
        goods(102, 25, 2);
        goods(103, 30, 3);
        when(pack.removeItems(player, COST, AddType.SIM_SPEEDY_DELIVERY)).thenReturn(result(Code.SUCCESS));
        when(pack.addItems(eq(PLAYER_ID), anyMap(), eq(AddType.SIM_SPEEDY_DELIVERY), eq(true)))
                .thenReturn(result(Code.SUCCESS));
        when(pack.getItemCount(PLAYER_ID, 1980001)).thenReturn(12L);
    }

    @AfterEach
    void tearDown() {
        gameData.close();
    }

    @Test
    void enterReturnsCostOwnedAndUnlockWithoutResettingRound() {
        SpeedyDeliveryRound round = start();
        var res = service.enter(ctx);
        assertEquals(Code.SUCCESS, res.code);
        assertEquals(COST, items(res.costItems));
        assertEquals(Map.of(1980001, 12L), items(res.ownedItems));
        assertTrue(res.openOneClickPass);
        assertSame(round, ctx.getSpeedyDeliveryRound());
        verifyNoInteractions(chapters, dao, saves);
    }

    @Test
    void startChargesOnceAndReturnsExactConfiguredDeadline() {
        long before = System.currentTimeMillis();
        SpeedyDeliveryRound round = start();
        assertTrue(round.getEndTime() >= before + 90000);
        assertTrue(round.getEndTime() <= System.currentTimeMillis() + 90000);
        assertEquals(Code.REPEAT_OP, service.start(ctx).code);
        assertEquals(Code.REPEAT_OP, service.quickClear(ctx).code);
        verify(pack).removeItems(player, COST, AddType.SIM_SPEEDY_DELIVERY);
        verify(conditions).onRoundStarted(ctx, 4);
        verifyNoInteractions(saves);
    }

    @Test
    void failedChargeDoesNotCreateRoundOrRecordParticipation() {
        when(pack.removeItems(player, COST, AddType.SIM_SPEEDY_DELIVERY)).thenReturn(result(Code.FAIL));
        assertEquals(Code.FAIL, service.start(ctx).code);
        assertNull(ctx.getSpeedyDeliveryRound());
        verifyNoInteractions(conditions);
    }

    @Test
    void malformedConfigIsRejectedBeforeCharge() {
        gameData.when(GameDataManager::getMinigameSpeedyDeliveryCfgMap).thenReturn(Map.of());
        assertEquals(Code.SAMPLE_ERROR, service.start(ctx).code);
        gameData.when(GameDataManager::getMinigameSpeedyDeliveryCfgMap).thenReturn(Map.of(1, cfg, 2, cfg));
        assertEquals(Code.SAMPLE_ERROR, service.start(ctx).code);
        gameData.when(GameDataManager::getMinigameSpeedyDeliveryCfgMap).thenReturn(Map.of(1, cfg));
        when(cfg.getReward()).thenReturn(Map.of(200, Map.of(1990000, 1)));
        assertEquals(Code.SAMPLE_ERROR, service.start(ctx).code);
        verifyNoInteractions(pack, conditions);
    }

    @ParameterizedTest
    @CsvSource({"101,8", "102,50", "103,90"})
    void eachGoodsTypeUsesCorrespondingTruckMultiplier(int goodsId, int score) {
        start();
        var res = service.deliver(ctx, List.of(goodsId), 1);
        assertEquals(Code.SUCCESS, res.code);
        assertEquals(score, res.deliveryScore);
        assertEquals(score, res.score);
    }

    @Test
    void deliveryRoundsUpAfterSummingAndAccumulatesAcrossTrips() {
        start();
        assertEquals(15, service.deliver(ctx, List.of(101, 101), 1).deliveryScore);
        var mixed = service.deliver(ctx, List.of(101, 102, 103), 1);
        assertEquals(148, mixed.deliveryScore);
        assertEquals(163, mixed.score);
        assertEquals(171, service.deliver(ctx, List.of(101), 1).score);
        verifyNoInteractions(saves);
    }

    @Test
    void invalidBatchNeverPartiallyAddsScore() {
        SpeedyDeliveryRound round = start();
        round.setScore(20);
        assertEquals(Code.PARAM_ERROR, service.deliver(ctx, null, 1).code);
        assertEquals(Code.PARAM_ERROR, service.deliver(ctx, List.of(), 1).code);
        assertEquals(Code.PARAM_ERROR, service.deliver(ctx, List.of(101, 101, 101, 101, 101), 1).code);
        assertEquals(Code.PARAM_ERROR, service.deliver(ctx, List.of(101), 99).code);
        assertEquals(Code.PARAM_ERROR, service.deliver(ctx, List.of(101, 99), 1).code);
        assertEquals(Code.PARAM_ERROR, service.deliver(ctx, Arrays.asList(101, null), 1).code);
        assertEquals(20, round.getScore());
    }

    @ParameterizedTest
    @CsvSource({"-1,1,50", "10,4,50", "10,1,-1"})
    void invalidGoodsOrMultiplierConfigDoesNotAddScore(int score, int type, int multiplier) {
        start();
        goods(104, score, type);
        when(truck.getNormalMultiplier()).thenReturn(multiplier);
        assertEquals(Code.SAMPLE_ERROR, service.deliver(ctx, List.of(101, 104), 1).code);
        assertEquals(0, ctx.getSpeedyDeliveryRound().getScore());
    }

    @Test
    void scoreAndProductOverflowAreRejectedWithoutMutation() {
        SpeedyDeliveryRound round = start();
        round.setScore(Integer.MAX_VALUE - 7);
        assertEquals(Code.PARAM_ERROR, service.deliver(ctx, List.of(101), 1).code);
        assertEquals(Integer.MAX_VALUE - 7, round.getScore());
        goods(104, Integer.MAX_VALUE, 1);
        when(truck.getNormalMultiplier()).thenReturn(Integer.MAX_VALUE);
        round.setScore(0);
        assertEquals(Code.PARAM_ERROR, service.deliver(ctx, List.of(104, 104, 104, 104), 1).code);
        assertEquals(0, round.getScore());
    }

    @Test
    void deductTimeOnlySubtractsPositiveSecondsUsingLongArithmetic() {
        SpeedyDeliveryRound round = start();
        long deadline = round.getEndTime();
        assertEquals(Code.PARAM_ERROR, service.deductTime(ctx, 0).code);
        assertEquals(Code.PARAM_ERROR, service.deductTime(ctx, -5).code);
        assertEquals(deadline, round.getEndTime());
        assertEquals(deadline - 5000, service.deductTime(ctx, 5).endTime);
        assertEquals(deadline - 5000 - Integer.MAX_VALUE * 1000L,
                service.deductTime(ctx, Integer.MAX_VALUE).endTime);
        assertEquals(Code.EXPIRE, service.deliver(ctx, List.of(101), 1).code);
    }

    @Test
    void deadlineToleranceOnlyAllowsSettlementAndDoesNotExtendPlay() {
        SpeedyDeliveryRound round = start();
        round.setScore(20);
        round.setEndTime(System.currentTimeMillis() - 1000);
        var delivery = service.deliver(ctx, List.of(101), 1);
        assertEquals(Code.EXPIRE, delivery.code);
        assertEquals(20, delivery.score);
        assertEquals(Code.EXPIRE, service.deductTime(ctx, 1).code);
        assertEquals(Code.REPEAT_OP, service.start(ctx).code);
        assertEquals(Code.SUCCESS, service.end(ctx).code);
    }

    @ParameterizedTest
    @CsvSource({"0,100", "199,100", "200,200", "399,200", "400,300", "10000,300"})
    void unsortedThresholdsUseLeftClosedRightOpenIntervals(int score, int reward) {
        start().setScore(score);
        var res = service.end(ctx);
        assertEquals(Code.SUCCESS, res.code);
        assertEquals(score, res.score);
        assertEquals(Map.of(1990000, (long) reward), items(res.rewards));
        assertEquals(Code.REPEAT_OP, service.end(ctx).code);
        service.onExit(ctx);
        verify(pack).addItems(PLAYER_ID, Map.of(1990000, (long) reward), AddType.SIM_SPEEDY_DELIVERY, true);
    }

    @Test
    void lateEndIsRejectedAndExistingTickSettlesExactlyOnce() {
        SpeedyDeliveryRound round = start();
        round.setScore(400);
        long deadline = System.currentTimeMillis() - 10000;
        round.setEndTime(deadline);
        service.onTick(ctx, deadline + 2000);
        verify(pack, never()).addItems(anyLong(), anyMap(), any(), anyBoolean());
        assertEquals(Code.EXPIRE, service.end(ctx).code);
        service.onTick(ctx, deadline + 2001);
        service.onTick(ctx, deadline + 4000);
        assertEquals(RoundState.CLEARED, round.getState());
        ArgumentCaptor<ResEndSpeedyDeliveryGame> response = ArgumentCaptor.forClass(ResEndSpeedyDeliveryGame.class);
        verify(controller).send(response.capture());
        assertEquals(Code.SUCCESS, response.getValue().code);
        assertEquals(400, response.getValue().score);
        assertEquals(Map.of(1990000, 300L), items(response.getValue().rewards));
        verify(pack).addItems(PLAYER_ID, Map.of(1990000, 300L), AddType.SIM_SPEEDY_DELIVERY, true);
    }

    @Test
    void expiredRoundIsSettledBeforeNewChargeRatherThanDiscarded() {
        SpeedyDeliveryRound old = start();
        old.setScore(400);
        old.setEndTime(System.currentTimeMillis() - 10000);
        clearInvocations(pack);
        assertEquals(Code.SUCCESS, service.start(ctx).code);
        assertNotSame(old, ctx.getSpeedyDeliveryRound());
        var order = inOrder(pack);
        order.verify(pack).addItems(PLAYER_ID, Map.of(1990000, 300L), AddType.SIM_SPEEDY_DELIVERY, true);
        order.verify(pack).removeItems(player, COST, AddType.SIM_SPEEDY_DELIVERY);
    }

    @Test
    void failedOldSettlementBlocksNewChargeAndKeepsPendingRound() {
        SpeedyDeliveryRound old = start();
        old.setEndTime(System.currentTimeMillis() - 10000);
        when(pack.addItems(PLAYER_ID, Map.of(1990000, 100L), AddType.SIM_SPEEDY_DELIVERY, true))
                .thenReturn(result(Code.FAIL));
        assertEquals(Code.FAIL, service.start(ctx).code);
        assertSame(old, ctx.getSpeedyDeliveryRound());
        assertEquals(RoundState.REWARD_PENDING, old.getState());
        verify(pack).removeItems(player, COST, AddType.SIM_SPEEDY_DELIVERY);
    }

    @Test
    void rewardFailureFreezesRoundAndAllowsRetryBeyondDeadline() {
        SpeedyDeliveryRound round = start();
        when(pack.addItems(PLAYER_ID, Map.of(1990000, 100L), AddType.SIM_SPEEDY_DELIVERY, true))
                .thenReturn(result(Code.FAIL), result(Code.SUCCESS));
        assertEquals(Code.FAIL, service.end(ctx).code);
        assertEquals(Code.REPEAT_OP, service.deliver(ctx, List.of(101), 1).code);
        assertEquals(Code.REPEAT_OP, service.deductTime(ctx, 1).code);
        assertEquals(Code.REPEAT_OP, service.start(ctx).code);
        round.setEndTime(System.currentTimeMillis() - 10000);
        assertEquals(Code.SUCCESS, service.end(ctx).code);
        assertEquals(Code.REPEAT_OP, service.end(ctx).code);
        verify(pack, times(2)).addItems(PLAYER_ID, Map.of(1990000, 100L), AddType.SIM_SPEEDY_DELIVERY, true);
        verify(pack).removeItems(player, COST, AddType.SIM_SPEEDY_DELIVERY);
    }

    @Test
    void quickClearUsesGlobalScoreAndRetriesWithoutSecondChargeOrUnlockCheck() {
        when(pack.addItems(PLAYER_ID, Map.of(1990000, 300L), AddType.SIM_SPEEDY_DELIVERY, true))
                .thenReturn(result(Code.FAIL), result(Code.SUCCESS));
        assertEquals(Code.FAIL, service.quickClear(ctx).code);
        gameData.when(() -> GameDataManager.getGlobalConfigCfg(362)).thenReturn(null);
        ctx.getSimChapterData().setLevelId(0);
        var res = service.quickClear(ctx);
        assertEquals(Code.SUCCESS, res.code);
        assertEquals(400, res.score);
        assertEquals(Map.of(1990000, 300L), items(res.rewards));
        verify(pack).removeItems(player, COST, AddType.SIM_SPEEDY_DELIVERY);
        verify(conditions).onRoundStarted(ctx, 4);
    }

    @Test
    void quickClearChecksActualChapterAndGlobalScoreBeforeCharge() {
        ctx.getSimChapterData().setLevelId(1001);
        assertFalse(service.enter(ctx).openOneClickPass);
        assertEquals(Code.NOT_UNLOCKED, service.quickClear(ctx).code);
        ctx.getSimChapterData().setLevelId(1002);
        gameData.when(() -> GameDataManager.getGlobalConfigCfg(362)).thenReturn(null);
        assertEquals(Code.SAMPLE_ERROR, service.quickClear(ctx).code);
        verify(pack, never()).removeItems(any(), anyMap(), any());
    }

    @Test
    void chapterLoadingStaysInChapterService() {
        ctx.setSimChapterData(null);
        when(chapters.getResChapterInfo(ctx)).thenAnswer(call -> {
            SimChapterData chapter = new SimChapterData();
            chapter.setLevelId(1002);
            ctx.setSimChapterData(chapter);
            return new ResChapterInfo(Code.SUCCESS);
        });
        assertTrue(service.enter(ctx).openOneClickPass);
        verify(chapters).getResChapterInfo(ctx);
    }

    @ParameterizedTest
    @ValueSource(longs = {-10000, 60000})
    void disconnectSettlesCurrentScoreRegardlessOfTimeAndDropsOnlyRound(long remaining) {
        SpeedyDeliveryRound round = start();
        round.setScore(400);
        round.setEndTime(System.currentTimeMillis() + remaining);
        assertEquals(Code.SUCCESS, service.onExit(ctx));
        assertNull(ctx.getSpeedyDeliveryRound());
        assertSame(data, ctx.getSpeedyDeliveryData());
        service.onExit(ctx);
        verify(pack).addItems(PLAYER_ID, Map.of(1990000, 300L), AddType.SIM_SPEEDY_DELIVERY, true);
        verifyNoInteractions(dao, saves);
    }

    @Test
    void failedDisconnectAwardCanBeRetriedOnLoginInRetainedContext() {
        SpeedyDeliveryRound round = start();
        when(pack.addItems(PLAYER_ID, Map.of(1990000, 100L), AddType.SIM_SPEEDY_DELIVERY, true))
                .thenReturn(result(Code.FAIL), result(Code.SUCCESS));
        assertEquals(Code.FAIL, service.onExit(ctx));
        assertSame(round, ctx.getSpeedyDeliveryRound());
        service.onLogin(ctx);
        assertNull(ctx.getSpeedyDeliveryRound());
        verify(pack, times(2)).addItems(PLAYER_ID, Map.of(1990000, 100L), AddType.SIM_SPEEDY_DELIVERY, true);
    }

    @Test
    void loginAndMidnightOnlyGrantOnceAndNeverBackfillOfflineDays() {
        ctx.setSpeedyDeliveryData(null);
        data.setLastDailyGrantTime(0);
        when(dao.findById(PLAYER_ID)).thenReturn(Optional.of(data));
        service.onLogin(ctx);
        service.enter(ctx);
        long tomorrow = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        service.onDaily(ctx, tomorrow);
        service.onDaily(ctx, tomorrow);
        service.onDaily(ctx, tomorrow - 86400000L);
        assertEquals(tomorrow, data.getLastDailyGrantTime());
        verify(pack, times(2)).addItems(PLAYER_ID, GIFT, AddType.SIM_SPEEDY_DELIVERY, true);
        verify(saves, times(2)).enqueueSave(data);
        verify(dao).findById(PLAYER_ID);
    }

    @Test
    void failedDailyGrantDoesNotAdvanceDateOrChargeAndCanRetry() {
        data.setLastDailyGrantTime(0);
        when(pack.addItems(PLAYER_ID, GIFT, AddType.SIM_SPEEDY_DELIVERY, true))
                .thenReturn(result(Code.FAIL), result(Code.SUCCESS));
        assertEquals(Code.FAIL, service.start(ctx).code);
        assertEquals(0, data.getLastDailyGrantTime());
        assertNull(ctx.getSpeedyDeliveryRound());
        verify(pack, never()).removeItems(any(), anyMap(), any());
        verifyNoInteractions(saves);
        assertEquals(Code.SUCCESS, service.start(ctx).code);
        assertTrue(data.getLastDailyGrantTime() > 0);
        verify(saves).enqueueSave(data);
    }

    @Test
    void reloadKeepsCurrentRoundRulesAndOnlyAffectsNextRound() {
        SpeedyDeliveryRound old = start();
        gameData.when(GameDataManager::getMinigameSpeedyDeliveryCfgMap).thenReturn(Map.of());
        assertEquals(8, service.deliver(ctx, List.of(101), 1).score);
        assertSame(cfg, old.getConfig());
        assertEquals(Code.SUCCESS, service.end(ctx).code);
        assertEquals(Code.SAMPLE_ERROR, service.start(ctx).code);
    }

    @Test
    void offlineDailyEventAndMissingRoundOperationsHaveNoSideEffects() {
        when(controller.isOnline()).thenReturn(false);
        data.setLastDailyGrantTime(0);
        service.onDaily(ctx, System.currentTimeMillis());
        assertEquals(Code.ERROR_REQ, service.deliver(ctx, List.of(101), 1).code);
        assertEquals(Code.ERROR_REQ, service.deductTime(ctx, 5).code);
        assertEquals(Code.ERROR_REQ, service.end(ctx).code);
        service.onTick(ctx, System.currentTimeMillis());
        verifyNoInteractions(pack, saves, dao);
    }

    private SpeedyDeliveryRound start() {
        var res = service.start(ctx);
        assertEquals(Code.SUCCESS, res.code);
        assertEquals(res.endTime, ctx.getSpeedyDeliveryRound().getEndTime());
        return ctx.getSpeedyDeliveryRound();
    }

    private void goods(int id, int score, int type) {
        MinigamegoodsCfg goods = mock(MinigamegoodsCfg.class);
        when(goods.getScore()).thenReturn(score);
        when(goods.getType()).thenReturn(type);
        gameData.when(() -> GameDataManager.getMinigamegoodsCfg(id)).thenReturn(goods);
    }

    private static Map<Integer, Long> items(List<ItemInfo> items) {
        return items.stream().collect(Collectors.toMap(item -> item.itemId, item -> item.count));
    }

    private static CommonResult<ItemOperationResult> result(int code) {
        return new CommonResult<>(code, new ItemOperationResult());
    }
}
