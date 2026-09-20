package com.jjg.game.sim.service;

import com.alibaba.fastjson.JSON;
import com.jjg.game.core.constant.TaskConstant;
import com.jjg.game.core.manager.RedDotManager;
import com.jjg.game.core.pb.reddot.RedDotDetails;
import com.jjg.game.core.task.db.TaskDetail;
import com.jjg.game.sim.dao.SimTaskDao;
import com.jjg.game.sim.data.SimPlayerContext;
import com.jjg.game.sim.data.SimTaskData;
import com.jjg.game.sim.manager.SimPlayerContextRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SimAchievementRedDotTest {
    private final SimTaskService service = new SimTaskService();
    private final SimTaskConfigService config = mock(SimTaskConfigService.class);
    private final SimTaskDao dao = mock(SimTaskDao.class);
    private final SimPlayerContextRegistry registry = mock(SimPlayerContextRegistry.class);
    private final SimPlayerContext context = mock(SimPlayerContext.class);
    private final RedDotManager dots = spy(new RedDotManager(null, null, null));
    private final SimTaskData data = new SimTaskData();

    @BeforeEach void setUp() {
        ReflectionTestUtils.setField(service, "taskConfig", config);
        ReflectionTestUtils.setField(service, "simTaskDao", dao);
        ReflectionTestUtils.setField(service, "contextRegistry", registry);
        ReflectionTestUtils.setField(service, "redDotManager", dots);
        when(context.playerId()).thenReturn(7L);
        when(context.getSimTaskData()).thenReturn(data);
        when(registry.getContext(7L)).thenReturn(context);
        doNothing().when(dots).updateRedDot(anyList(), anyLong());
        group(11, 100, 1001);
        group(12, 100, 1001);
        group(13, 200, 1002);
        group(14, 300, 0);
    }

    private void group(int group, int badge, int building) {
        when(config.achievementGroup(group)).thenReturn(
                new SimTaskConfigService.AchievementGroupDef(group, badge, 0, List.of(group)));
        when(config.badge(badge)).thenReturn(
                new SimTaskConfigService.AchievementBadgeDef(badge, building, 0, List.of(), List.of()));
    }

    private TaskDetail task(int status) {
        TaskDetail task = new TaskDetail();
        task.setStatus(status);
        return task;
    }

    private RedDotDetails achievement() { return service.initialize(7, TaskConstant.TaskType.ACHIEVEMENT).getFirst(); }

    @Test void countsRewardsByBadgeAndBuildingWithoutIncludingMainOrUnfinishedTasks() {
        data.setMainTask(task(TaskConstant.TaskStatus.STATUS_COMPLETED));
        data.getAchievementTasks().putAll(Map.of(11, task(1), 12, task(1), 13, task(0), 14, task(1)));
        var result = service.initialize(7, 0);
        assertEquals(2, result.size());
        assertEquals(1, result.getFirst().getCount());
        var dot = result.getLast();
        assertEquals(3, dot.getCount());
        assertEquals(RedDotDetails.RedDotType.COUNT, dot.getRedDotType());
        var detail = JSON.parseObject(dot.getExtra());
        assertEquals(Map.of("100", 2, "300", 1), detail.getJSONObject("badgeCounts"));
        assertEquals(Map.of("1001", 2), detail.getJSONObject("buildingCounts"));
        verifyNoInteractions(dao);
    }

    @Test void completionAndClaimPushFullSnapshotEvenWhenTotalDoesNotChange() {
        data.getAchievementTasks().putAll(Map.of(11, task(1), 13, task(0)));
        String before = achievement().getExtra();
        data.getAchievementTasks().get(11).setStatus(2);
        data.getAchievementTasks().get(13).setStatus(1);
        var after = achievement();
        assertEquals(1, after.getCount());
        assertNotEquals(before, after.getExtra());
        assertEquals(Map.of("200", 1), JSON.parseObject(after.getExtra()).getJSONObject("badgeCounts"));
        ReflectionTestUtils.invokeMethod(service, "updateTaskRedDot", context);
        verify(dots).updateRedDot(argThat((List<RedDotDetails> list) -> list.stream().anyMatch(dot ->
                dot.getRedDotSubmodule() == TaskConstant.TaskType.ACHIEVEMENT
                        && dot.getExtra().equals(after.getExtra()))), eq(7L));
        data.getAchievementTasks().get(13).setStatus(2);
        assertEquals(0, achievement().getCount());
        assertTrue(JSON.parseObject(achievement().getExtra()).getJSONObject("badgeCounts").isEmpty());
        assertTrue(JSON.parseObject(achievement().getExtra()).getJSONObject("buildingCounts").isEmpty());
    }

    @Test void offlineAndReconnectUseSameSnapshotAndMissingPlayerClearsBadges() {
        data.getAchievementTasks().put(11, task(1));
        String online = achievement().getExtra();
        when(registry.getContext(7L)).thenReturn(null);
        when(dao.findRedDotData(7L)).thenReturn(data);
        assertEquals(online, achievement().getExtra());
        assertEquals(1, achievement().getCount());
        when(dao.findRedDotData(7L)).thenReturn(null);
        assertEquals(0, achievement().getCount());
        assertTrue(service.initialize(7, 99).isEmpty());
        assertNull(service.initialize(7, 2).getFirst().getExtra());
    }
}
