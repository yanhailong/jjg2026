package com.jjg.game.sim.pb.struct;

import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.core.pb.KVInfo;

import java.util.List;

@ProtobufMessage
@ProtoDesc("本轮slots统计信息")
public class SlotsStatisticsInfo {
    @ProtoDesc("spin次数")
    public int spinCount;
    @ProtoDesc("大奖统计，key为大奖类型，value为次数")
    public List<KVInfo> bigWinCounts;
    @ProtoDesc("触发免费次数")
    public int freeCount;
    @ProtoDesc("特殊模式次数")
    public int specialCount;
    @ProtoDesc("奖池次数")
    public int jackpoolCount;
    @ProtoDesc("累计获得金币")
    public long allWinCoin;
    @ProtoDesc("累计下注金币")
    public long allBetCoin;
    @ProtoDesc("累计消耗体力")
    public int allSpendPower;
    @ProtoDesc("累计因体力额外获得金币")
    public long allSpendPowerGetCoin;
    @ProtoDesc("是否历史最高")
    public boolean ath;
}
