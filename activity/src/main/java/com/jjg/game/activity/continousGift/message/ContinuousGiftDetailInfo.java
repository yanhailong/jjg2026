package com.jjg.game.activity.continousGift.message;

import com.jjg.game.activity.common.message.bean.BaseActivityDetailInfo;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.core.pb.RechargeInfo;

@ProtobufMessage
@ProtoDesc("连续礼包宝箱详情；claimStatus为0未解锁，1可购买，2可免费领取，3已购买或已领取")
public class ContinuousGiftDetailInfo extends BaseActivityDetailInfo {
    @ProtoDesc("宝箱类型 1每日付费礼包 2终极免费奖励")
    public int type;
    @ProtoDesc("充值信息")
    public RechargeInfo rechargeInfo;

    //0未解锁，1可购买，2可免费领取，3已购买或已领取
    public ContinuousGiftDetailInfo() {
        claimStatus = 0;
    }
}
