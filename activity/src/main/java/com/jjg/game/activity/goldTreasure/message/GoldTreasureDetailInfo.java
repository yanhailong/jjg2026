package com.jjg.game.activity.goldTreasure.message;

import com.jjg.game.activity.common.message.bean.BaseActivityDetailInfo;
import com.jjg.game.common.pb.ItemInfo;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;

import java.util.List;

@ProtobufMessage
@ProtoDesc("黄金宝藏活动数据详情")
public class GoldTreasureDetailInfo extends BaseActivityDetailInfo {
    @ProtoDesc("奖励类型  1.普通奖励  2.累计奖励")
    public int rewardType;
    @ProtoDesc("累计奖励需要的条件")
    public List<ItemInfo> condition;
    @ProtoDesc("售价, 为空表示无需付费")
    public String price;
    @ProtoDesc("玩家所属渠道的商品ID")
    public String channelProductId;
}
