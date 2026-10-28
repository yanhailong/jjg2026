package com.jjg.game.minigame.pb;

import com.jjg.game.common.proto.Context;
import com.jjg.game.common.proto.Java2Pb2;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.common.protostuff.Command;
import com.jjg.game.common.protostuff.ProtostuffUtil;
import com.jjg.game.core.constant.Code;
import com.jjg.game.core.data.PlayerController;
import com.jjg.game.minigame.constant.MiniGameConstant;
import com.jjg.game.minigame.handler.SimMiniGameHandler;
import com.jjg.game.minigame.pb.req.*;
import com.jjg.game.minigame.pb.res.*;
import com.jjg.game.minigame.service.SpeedyDeliveryService;
import com.jjg.game.sim.manager.SimPlayerContextRegistry;
import io.protostuff.runtime.RuntimeSchema;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SpeedyDeliveryProtocolTest {
    private static final List<Class<?>> TYPES = List.of(ReqEnterSpeedyDeliveryGame.class, ResEnterSpeedyDeliveryGame.class,
            ReqStartSpeedyDeliveryGame.class, ResStartSpeedyDeliveryGame.class,
            ReqDeliverSpeedyDeliveryGoods.class, ResDeliverSpeedyDeliveryGoods.class,
            ReqDeductSpeedyDeliveryTime.class, ResDeductSpeedyDeliveryTime.class,
            ReqEndSpeedyDeliveryGame.class, ResEndSpeedyDeliveryGame.class,
            ReqQuickClearSpeedyDeliveryGame.class, ResQuickClearSpeedyDeliveryGame.class);

    @Test
    void allMessagesGenerateProtoAndRequestCommandsMatchHandlersWithoutCollisions() throws Exception {
        Map<Integer, String> original = new HashMap<>(Context.instance.getReqMap());
        Set<Integer> commands = new HashSet<>();
        try {
            for (Class<?> type : TYPES) {
                ProtobufMessage message = type.getAnnotation(ProtobufMessage.class);
                assertTrue(commands.add(message.cmd()));
                String proto = new Java2Pb2(RuntimeSchema.getSchema(type), message.cmd()).gen().toMesage();
                assertTrue(proto.contains("message " + type.getSimpleName() + " {"));
                if (type.getSimpleName().startsWith("Req")) {
                    var handler = Arrays.stream(SimMiniGameHandler.class.getDeclaredMethods())
                            .filter(m -> Arrays.asList(m.getParameterTypes()).contains(type)).findFirst().orElseThrow();
                    assertEquals(message.cmd(), handler.getAnnotation(Command.class).value());
                }
            }
            Map<Integer, String> allCommands = new HashMap<>();
            for (var field : MiniGameConstant.MsgBean.class.getFields()) {
                if (!field.getName().equals("BASE_MSG_PREFIX")) {
                    assertNull(allCommands.put(field.getInt(null), field.getName()), field.getName());
                }
            }
        } finally {
            Context.instance.getReqMap().clear();
            Context.instance.getReqMap().putAll(original);
        }
    }

    @Test
    void deliveryPreservesDuplicateGoodsAndDeductionOnlyContainsSeconds() {
        var delivery = new ReqDeliverSpeedyDeliveryGoods();
        delivery.goodsIds = List.of(101, 101, 103);
        delivery.truckId = 2;
        var decoded = ProtostuffUtil.deserialize(ProtostuffUtil.serialize(delivery), ReqDeliverSpeedyDeliveryGoods.class);
        assertEquals(delivery.goodsIds, decoded.goodsIds);
        assertEquals(2, decoded.truckId);
        assertEquals(List.of("seconds"), Arrays.stream(ReqDeductSpeedyDeliveryTime.class.getDeclaredFields())
                .filter(f -> !Modifier.isStatic(f.getModifiers())).map(f -> f.getName()).toList());
        var deduction = new ReqDeductSpeedyDeliveryTime();
        deduction.seconds = Integer.MAX_VALUE;
        assertEquals(Integer.MAX_VALUE, ProtostuffUtil.deserialize(ProtostuffUtil.serialize(deduction),
                ReqDeductSpeedyDeliveryTime.class).seconds);
        var start = new ResStartSpeedyDeliveryGame(Code.SUCCESS);
        start.endTime = 1793000000123L;
        assertEquals(start.endTime, ProtostuffUtil.deserialize(ProtostuffUtil.serialize(start),
                ResStartSpeedyDeliveryGame.class).endTime);
    }

    @Test
    void everyRequestRejectsMissingContextBeforeCallingService() throws Exception {
        SimMiniGameHandler handler = new SimMiniGameHandler();
        SpeedyDeliveryService service = mock(SpeedyDeliveryService.class);
        ReflectionTestUtils.setField(handler, "simPlayerContextRegistry", new SimPlayerContextRegistry());
        ReflectionTestUtils.setField(handler, "speedyDeliveryService", service);
        PlayerController player = mock(PlayerController.class);
        for (int i = 0; i < TYPES.size(); i += 2) {
            Class<?> request = TYPES.get(i);
            var method = Arrays.stream(SimMiniGameHandler.class.getDeclaredMethods())
                    .filter(m -> Arrays.asList(m.getParameterTypes()).contains(request)).findFirst().orElseThrow();
            method.invoke(handler, player, request.getConstructor().newInstance());
        }
        verify(player, times(6)).send(argThat(message -> message instanceof com.jjg.game.common.pb.AbstractResponse res
                && res.code == Code.NOT_FOUND));
        verifyNoInteractions(service);
    }
}
