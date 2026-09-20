package com.jjg.game.sim.service;

import com.jjg.game.common.cluster.ClusterClient;
import com.jjg.game.common.proto.Java2Pb2;
import com.jjg.game.common.rpc.GameRpcContext;
import com.jjg.game.common.rpc.RpcReqParameterBuilder;
import com.jjg.game.sim.bridge.ToSimBridge;
import com.jjg.game.sim.dao.SlotsStatisticsDao;
import com.jjg.game.sim.data.SimPlayerContext;
import com.jjg.game.sim.data.SlotsStatisticsData;
import com.jjg.game.sim.manager.SimPlayerContextRegistry;
import com.jjg.game.sim.pb.struct.SlotsStatisticsInfo;
import io.protostuff.runtime.RuntimeSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SlotsStatisticsServiceTest {
    private final SlotsStatisticsDao dao = mock(SlotsStatisticsDao.class);
    private final SimPlayerContextRegistry contexts = mock(SimPlayerContextRegistry.class);
    private final SimNodeService nodes = mock(SimNodeService.class);
    private final ToSimBridge bridge = mock(ToSimBridge.class);
    private final SlotsStatisticsService service = new SlotsStatisticsService();
    private final SimPlayerContext ctx = new SimPlayerContext();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "slotsStatisticsDao", dao);
        ReflectionTestUtils.setField(service, "contexts", contexts);
        ReflectionTestUtils.setField(service, "simNodeService", nodes);
        ReflectionTestUtils.setField(service, "toSimBridge", bridge);
        ctx.setPlayerId(123L);
    }

    @Test
    void statisticsCanBeExportedByExistingProtocolGenerator() {
        String proto = new Java2Pb2(RuntimeSchema.getSchema(SlotsStatisticsInfo.class), 0).gen().toMesage();
        assertTrue(proto.contains("repeated KVInfo bigWinCounts"));
        assertTrue(proto.contains("allSpendPowerGetCoin"));
        assertFalse(proto.contains("clientReq"));
    }

    @Test
    void unreadStatisticsSurviveReconnectUntilRequested() {
        SlotsStatisticsData data = new SlotsStatisticsData();
        data.setSpinCount(12);
        data.setBigWinCountMap(new HashMap<>(Map.of(2, 3)));
        data.setFreeCount(4);
        data.setSpecialCount(5);
        data.setJackpoolCount(6);
        data.setAllWinCoin(700L);
        data.setAllBetCoin(800L);
        data.setAllSpendPower(9);
        data.setAllSpendPowerGetCoin(100L);
        ctx.setSlotsStatisticsData(data);

        service.saveOnExit(ctx);
        verify(dao).save(data);
        assertEquals(123L, data.getPlayerId());
        when(dao.findById(123L)).thenReturn(Optional.of(data));
        SimPlayerContext reconnected = new SimPlayerContext();
        reconnected.setPlayerId(123L);
        service.load(reconnected);

        var res = service.request(reconnected);
        assertTrue(data.isClientReq());
        verify(dao).deleteById(123L);
        assertEquals(12, res.statistics.spinCount);
        assertEquals(1, res.statistics.bigWinCounts.size());
        assertEquals(2, res.statistics.bigWinCounts.getFirst().key);
        assertEquals(3, res.statistics.bigWinCounts.getFirst().value);
        assertEquals(4, res.statistics.freeCount);
        assertEquals(5, res.statistics.specialCount);
        assertEquals(6, res.statistics.jackpoolCount);
        assertEquals(700L, res.statistics.allWinCoin);
        assertEquals(800L, res.statistics.allBetCoin);
        assertEquals(9, res.statistics.allSpendPower);
        assertEquals(100L, res.statistics.allSpendPowerGetCoin);
        data.getBigWinCountMap().put(2, 99);
        assertEquals(3, res.statistics.bigWinCounts.getFirst().value);

        service.saveOnExit(reconnected);
        verify(dao, times(1)).save(any());
        assertNotNull(service.request(reconnected).statistics);
    }

    @Test
    void emptyStatisticsAreNotSavedAndReturnEmptyResponse() {
        service.saveOnExit(ctx);
        assertNull(service.request(ctx).statistics);
        verifyNoInteractions(dao);
    }

    @Test
    void failedDeleteKeepsStatisticsUnreadForRetry() {
        SlotsStatisticsData data = new SlotsStatisticsData();
        ctx.setSlotsStatisticsData(data);
        doThrow(new IllegalStateException("Mongo unavailable")).when(dao).deleteById(123L);

        assertThrows(IllegalStateException.class, () -> service.request(ctx));
        assertFalse(data.isClientReq());
        assertThrows(IllegalStateException.class, () -> service.clear(ctx));
        assertSame(data, ctx.getSlotsStatisticsData());
        service.saveOnExit(ctx);
        verify(dao).save(data);
    }

    @Test
    void chooseWareClearsMemoryAndMongoBeforeNextSession() {
        ctx.setSlotsStatisticsData(new SlotsStatisticsData());
        when(contexts.getContext(123L)).thenReturn(ctx);

        service.clear(123L, "ip");

        verify(dao).deleteById(123L);
        assertNull(ctx.getSlotsStatisticsData());
        service.saveOnExit(ctx);
        verify(dao, never()).save(any());
        verifyNoInteractions(nodes, bridge);
    }

    @Test
    void remoteClearUsesOwnerAndRestoresRpcContextEvenOnFailure() {
        when(nodes.getSimClusterClient(123L, "ip")).thenReturn(mock(ClusterClient.class));
        when(bridge.clearSlotsStatistics(123L)).thenReturn(true).thenReturn(false);
        GameRpcContext rpc = GameRpcContext.getContext();
        RpcReqParameterBuilder original = rpc.getReqParameterBuilder();
        RpcReqParameterBuilder previous = RpcReqParameterBuilder.create();
        rpc.setReqParameterBuilder(previous);
        try {
            service.clear(123L, "ip");
            assertSame(previous, rpc.getReqParameterBuilder());
            assertThrows(IllegalStateException.class, () -> service.clear(123L, "ip"));
            assertSame(previous, rpc.getReqParameterBuilder());
            verify(bridge, times(2)).clearSlotsStatistics(123L);
            verifyNoInteractions(dao);
        } finally {
            rpc.setReqParameterBuilder(original);
        }
    }
}
