package com.jjg.game.activity.goldTreasure.message;

import com.jjg.game.activity.common.message.bean.BaseActivityDetailInfo;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.core.pb.RechargeInfo;

/**
 * claimStatus：0未解锁，1已解锁不可领取，2可领取，3已领取。
 */
@ProtobufMessage
@ProtoDesc("黄金宝藏活动数据详情")
public class GoldTreasureDetailInfo extends BaseActivityDetailInfo {
    @ProtoDesc("奖励类型  1.普通奖励  2.累计奖励")
    public int rewardType;
    @ProtoDesc("充值信息")
    public RechargeInfo rechargeInfo;
    public int best;

    public GoldTreasureDetailInfo() {
        claimStatus = 0;
    }
}
