package com.jjg.game.core.listener;

/** 活动模块通过此接口访问免广告权益，SIM模块负责数据归属及持久化。 */
public interface SimNoAdsListener {
    /** 优先读取SIM上下文，无上下文时只查询数据库中的永久免广告字段。 */
    boolean noads(long playerId);

    /** 在玩家所属SIM节点开启权益，确认落库成功后返回true；节点或上下文不可用返回false。 */
    boolean enableNoads(long playerId);
}
