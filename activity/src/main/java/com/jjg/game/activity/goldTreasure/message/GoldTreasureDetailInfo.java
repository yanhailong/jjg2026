package com.jjg.game.activity.goldTreasure.message;

import com.jjg.game.activity.common.message.bean.BaseActivityDetailInfo;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.core.pb.RechargeInfo;

@ProtobufMessage
@ProtoDesc("黄金宝藏活动数据详情")
public class GoldTreasureDetailInfo extends BaseActivityDetailInfo {
    @ProtoDesc("奖励类型  1.普通奖励  2.累计奖励")
    public int rewardType;
    @ProtoDesc("充值信息")
    public RechargeInfo rechargeInfo;
    public int best;
}
