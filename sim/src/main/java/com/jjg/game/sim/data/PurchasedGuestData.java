package com.jjg.game.sim.data;

import com.jjg.game.sim.pb.struct.DestinationInfo;
import org.springframework.data.annotation.Transient;

import java.util.Map;

/**
 * 购买游客 (花钱购买的特殊游客):
 * - 由客户端"购买"请求触发立即生成, 不走定时生成逻辑
 * - 生成时预生成目的地序列和奖励 (奖励不添加到玩家身上); 分配唯一 uid
 * - 客户端凭 uid 和目的地序号确认到达，服务端将该点预生成奖励存入对应建筑
 * - 随 {@link SimCasinoData} 落库, 用于断线重连后重新下发
 *
 * @author 11
 * @date 2026/6/15
 */
public class PurchasedGuestData {
    //唯一id (生成时分配)
    private String uid;
    //游客 id (对应 VisitorQuestCfg.id)
    private int guestId;
    //生成时的星级快照 (用于领奖时结算奖励)
    private int star;
    //生成时的等级快照 (用于领奖时结算奖励)
    private int level;
    //尚未完成的目的地序列，奖励在确认到达后存入建筑
    private Map<Integer,DestinationInfo> destinations;
    //特殊游客购买订单，非商品邀请为 null；随游客落库用于领奖清理
    @Transient
    private transient String orderId;
    @Transient
    private transient int poolId;
    @Transient
    private transient int cfgId;

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public int getGuestId() {
        return guestId;
    }

    public void setGuestId(int guestId) {
        this.guestId = guestId;
    }

    public int getStar() {
        return star;
    }

    public void setStar(int star) {
        this.star = star;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public Map<Integer, DestinationInfo> getDestinations() {
        return destinations;
    }

    public void setDestinations(Map<Integer, DestinationInfo> destinations) {
        this.destinations = destinations;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public int getPoolId() {
        return poolId;
    }

    public void setPoolId(int poolId) {
        this.poolId = poolId;
    }

    public int getCfgId() {
        return cfgId;
    }

    public void setCfgId(int cfgId) {
        this.cfgId = cfgId;
    }
}
