package com.jjg.game.core.pb;

import com.jjg.game.common.pb.ItemInfo;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;

import java.util.List;

@ProtobufMessage
@ProtoDesc("充值信息")
public class RechargeInfo {
    @ProtoDesc("付费类型 1.付费  2.道具兑换  3.广告  4.免费")
    public int type;
    @ProtoDesc("道具兑换需要的道具")
    public List<ItemInfo> redeem;
    @ProtoDesc("售价")
    public String price;
    @ProtoDesc("玩家所属渠道的商品ID")
    public String channelProductId;
}
