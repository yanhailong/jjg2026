package com.jjg.game.season.pb.struct;

import com.jjg.game.common.pb.ItemInfo;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import java.util.List;

@ProtobufMessage
@ProtoDesc("连续礼包活动状态")
public class ContinuousGiftInfo {
    public int activityId;
    public String name;
    public long startTime;
    public long endTime;
    public long serverTime;
    public long nextPurchaseTime;
    public String iconPath;
    public String iconPosition;
    public boolean redDot;
    public List<ContinuousGiftChestInfo> chests;
    public String grandTitle;
    public String grandIcon;
    public String unlockText;
    public List<ItemInfo> grandRewards;
    @ProtoDesc("0未解锁 1可领取 2已领取") public int grandStatus;
    public int nameId;
    public int functionId;
}
