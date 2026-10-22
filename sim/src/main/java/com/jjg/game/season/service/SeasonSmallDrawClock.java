package com.jjg.game.season.service;

import com.jjg.game.common.utils.TimeHelper;
import com.jjg.game.season.data.SeasonPlayerData;
import org.springframework.stereotype.Component;

/** 小额抽奖专用测试时间，仅在当前进程生效。 */
@Component
public class SeasonSmallDrawClock {
    // null 沿用原赛季日期；显式设置后使用独立偏移，0 恢复真实日期。
    private volatile Long gmTimeOffset;

    public void setDate(String date) {
        gmTimeOffset = TimeHelper.getDateOffset(date, System.currentTimeMillis());
    }

    public long currentTime() {
        Long offset = gmTimeOffset;
        return System.currentTimeMillis() + (offset == null ? 0L : offset);
    }

    public int dayKey(SeasonPlayerData season, long systemTime) {
        Long offset = gmTimeOffset;
        return offset == null ? season.getDailyKey() : SeasonFreeGameService.dailyKey(systemTime + offset);
    }
}
