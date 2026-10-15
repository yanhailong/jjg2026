package com.jjg.game.core.listener;

import com.jjg.game.common.baselogic.IGameSysFuncInterface;

/** 通用功能开关通过此接口读取免广告权益，玩家数据仍由 SIM 管理。 */
public interface SimNoAdsListener extends IGameSysFuncInterface {
    /** 优先读取 SIM 上下文，无上下文时只查询数据库中的永久免广告字段。 */
    boolean noads(long playerId);
}
