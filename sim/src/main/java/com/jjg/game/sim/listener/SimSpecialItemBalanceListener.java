package com.jjg.game.sim.listener;

/**
 * 调用节点上的 sim 特殊资源余额同步。
 * <p>
 * 特殊资源可能实际落在远端 Hall；操作成功后以 owner 节点的权威余额刷新调用节点缓存。
 */
public interface SimSpecialItemBalanceListener {

    default boolean support(int itemId){return false;}

    default void onBalanceChanged(long playerId, int itemId, long balance){}

    /** 当前场景每分钟产出，type 与 OnlineRewardInfo.hourlyRewards 的 itemId 一致。 */
    default long output(long playerId, int type){return 0;}
}
