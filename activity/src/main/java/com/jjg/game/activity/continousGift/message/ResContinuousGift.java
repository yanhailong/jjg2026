package com.jjg.game.activity.continousGift.message;

import com.jjg.game.activity.constant.ActivityConstant;
import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractResponse;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;

import java.util.List;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.ACTIVITY, cmd = ActivityConstant.MsgBean.RES_CONTINUOUS_GIFT, resp = true)
@ProtoDesc("连续礼包活动信息")
public class ResContinuousGift extends AbstractResponse {
    public List<ContinuousGiftDetailInfo> activityData;
    @ProtoDesc("活动结束时间，毫秒")
    public long endTime;

    public ResContinuousGift(int code) {
        super(code);
    }
}
