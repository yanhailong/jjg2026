package com.jjg.game.season.pb.struct;

import com.jjg.game.common.pb.ItemInfo;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;

import java.util.List;

@ProtobufMessage
@ProtoDesc("小额抽奖奖池格子")
public class SeasonSmallDrawRewardInfo {
    @ProtoDesc("SmallDrawReward.id")
    public int rewardId;
    public boolean core;
    public boolean obtained;
    @ProtoDesc("核心大奖候选索引，从0开始；普通奖励固定为0")
    public int optionIndex;
    public List<ItemInfo> items;
}
