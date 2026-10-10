package com.jjg.game.activity.goldTreasure.message;

import com.jjg.game.activity.constant.ActivityConstant;
import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractResponse;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;

import java.util.List;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.ACTIVITY, cmd = ActivityConstant.MsgBean.RES_GOLD_TREASURE, resp = true)
@ProtoDesc("响应黄金宝藏活动数据")
public class ResGoldTreasure extends AbstractResponse {
    @ProtoDesc("活动列表信息")
    public List<GoldTreasureDetailInfo> activityData;
    @ProtoDesc("结束时间")
    public long endTime;

    public ResGoldTreasure(int code) {
        super(code);
    }
}
