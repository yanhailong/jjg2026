package com.jjg.game.core.service;

import com.jjg.game.common.utils.TimeHelper;
import com.jjg.game.core.dao.CountDao;
import org.springframework.stereotype.Service;

/**
 * 提供全服统一的开服时间（毫秒）。启动时初始化，之后直接使用内存值。
 */
@Service
public class ServerOpenTimeService {
    private static final String OPEN_SERVER_TIME = "openServerTime";

    private final CountDao countDao;
    private volatile long serverOpenTimeMillis;

    public ServerOpenTimeService(CountDao countDao) {
        this.countDao = countDao;
    }

    public void init() {
        long today = TimeHelper.getCurrentDateZeroMilliTime();
        boolean initialized = countDao.setIfAbsentLong(CountDao.CountType.SYSTEM.getParam(),
                OPEN_SERVER_TIME, today);
        serverOpenTimeMillis = initialized ? today : countDao.getCountLong(
                CountDao.CountType.SYSTEM.getParam(), OPEN_SERVER_TIME);
    }

    public long getServerOpenTimeMillis() {
        return serverOpenTimeMillis;
    }
}
