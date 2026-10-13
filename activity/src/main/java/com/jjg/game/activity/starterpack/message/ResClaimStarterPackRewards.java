package com.jjg.game.activity.starterpack.message;

import com.jjg.game.activity.constant.ActivityConstant;
import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractResponse;
import com.jjg.game.common.pb.ItemInfo;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;

import java.util.List;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.ACTIVITY, cmd = ActivityConstant.MsgBean.RES_CLAIM_STARTER_PACK_REWARDS, resp = true)
@ProtoDesc("新手礼包领奖结果")
public class ResClaimStarterPackRewards extends AbstractResponse {
    public long activityId;
    public int detailId;
    public List<ItemInfo> items;
    public StarterPackDetailInfo detailInfo;

    public ResClaimStarterPackRewards(int code) {
        super(code);
    }
}
