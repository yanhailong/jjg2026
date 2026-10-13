package com.jjg.game.activity.starterpack.message;

import com.jjg.game.common.pb.ItemInfo;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;

import java.util.List;

@ProtobufMessage
@ProtoDesc("新手礼包分档奖励")
public class StarterPackRewardInfo {
    @ProtoDesc("累计登录天数，1至3对应Reward1至Reward3")
    public int day;
    @ProtoDesc("领取状态 1不可领取 2可领取 3已领取")
    public int claimStatus;
    public List<ItemInfo> rewardItems;
}
