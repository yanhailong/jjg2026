package com.jjg.game.activity.continousGift.message;

import com.jjg.game.activity.constant.ActivityConstant;
import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractResponse;
import com.jjg.game.common.pb.ItemInfo;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;

import java.util.List;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.ACTIVITY, cmd = ActivityConstant.MsgBean.RES_CLAIM_CONTINUOUS_GIFT_REWARDS, resp = true)
@ProtoDesc("连续礼包终极奖励领取结果")
public class ResClaimContinuousGiftRewards extends AbstractResponse {
    public int detailId;
    public List<ItemInfo> items;
    public List<ContinuousGiftDetailInfo> activityData;

    public ResClaimContinuousGiftRewards(int code) {
        super(code);
    }
}
