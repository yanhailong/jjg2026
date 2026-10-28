package com.jjg.game.sim.pb.struct;

import com.jjg.game.common.pb.ItemInfo;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;

import java.util.List;

@ProtobufMessage
@ProtoDesc("建筑升级条件")
public class BuildingUpgradeConditonInfo {
    @ProtoDesc("需要达到的场景等级")
    public int needLevel;
    @ProtoDesc("需要消耗的道具")
    public List<ItemInfo> needItems;
}
