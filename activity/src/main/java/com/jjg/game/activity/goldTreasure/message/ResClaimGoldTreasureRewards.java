package com.jjg.game.activity.goldTreasure.message;

import com.jjg.game.activity.constant.ActivityConstant;
import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractResponse;
import com.jjg.game.common.pb.ItemInfo;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;

import java.util.List;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.ACTIVITY, cmd = ActivityConstant.MsgBean.RES_CLAIM_GOLD_TREASURE_REWARDS, resp = true)
@ProtoDesc("响应领取黄金宝藏的奖励")
public class ResClaimGoldTreasureRewards extends AbstractResponse {
    @ProtoDesc("奖励")
    public List<ItemInfo> items;
    @ProtoDesc("本次领取的奖励配置ID")
    public int detailId;
    @ProtoDesc("最新活动奖励状态")
    public List<GoldTreasureDetailInfo> activityData;

    public ResClaimGoldTreasureRewards(int code) {
        super(code);
    }
}
