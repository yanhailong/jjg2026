package com.jjg.game.season.pb.struct;

import com.jjg.game.common.pb.ItemInfo;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;

import java.util.List;

@ProtobufMessage
@ProtoDesc("当前赛季小额抽奖状态")
public class SeasonSmallDrawInfo {
    public int drawId;
    public String seasonKey;
    public long endTime;
    @ProtoDesc("剩余可购买资格，仍需付费")
    public int purchaseChances;
    public int drawCount;
    public String price;
    public String channelProductId;
    public boolean dailyRewardClaimed;
    public List<ItemInfo> dailyReward;
    public List<SeasonSmallDrawRewardInfo> rewards;
}
