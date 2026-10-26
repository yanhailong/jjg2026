package com.jjg.game.sim.pb.struct;

import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.core.pb.KVLongInfo;

import java.util.List;

@ProtobufMessage
@ProtoDesc("建筑加成信息")
public class BonusInfo {
    @ProtoDesc("BuildingAreaTable表的typeValue值")
    public int typeValue;
    @ProtoDesc("包含全部加成的实际值；游戏区/休息区为每小时产出，管理区为属性值")
    public long totalValue;
    @ProtoDesc("固定值来源：1藏品、2雇员、3成就、4 Buff；与totalValue单位相同")
    public List<KVLongInfo> fixedValueAddList;
    @ProtoDesc("比例来源：1藏品、2雇员、3成就、4 Buff；值为万分比，200表示2%")
    public List<KVLongInfo> propValueAddList;
}
