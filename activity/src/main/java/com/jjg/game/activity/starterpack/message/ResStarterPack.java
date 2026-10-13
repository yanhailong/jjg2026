package com.jjg.game.activity.starterpack.message;

import com.jjg.game.activity.constant.ActivityConstant;
import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractResponse;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;

import java.util.List;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.ACTIVITY, cmd = ActivityConstant.MsgBean.RES_STARTER_PACK, resp = true)
@ProtoDesc("新手礼包活动信息")
public class ResStarterPack extends AbstractResponse {
    public List<StarterPackDetailInfo> activityData = List.of();

    public ResStarterPack(int code) {
        super(code);
    }
}
