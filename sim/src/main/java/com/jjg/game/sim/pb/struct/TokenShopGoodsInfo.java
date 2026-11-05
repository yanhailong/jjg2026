package com.jjg.game.sim.pb.struct;

import com.jjg.game.common.pb.ItemInfo;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.core.pb.RechargeInfo;

import java.util.List;

@ProtobufMessage
@ProtoDesc("代币商店商品")
public class TokenShopGoodsInfo {
    @ProtoDesc("TokenShop商品ID")
    public int goodsId;
    @ProtoDesc("每份获得的道具")
    public List<ItemInfo> goods;
    @ProtoDesc("多语言ID")
    public int language;
    @ProtoDesc("客户端资源")
    public String icon;
    @ProtoDesc("展示折扣")
    public int discountRate;
    @ProtoDesc("开放时间戳，毫秒；0表示不限开始时间")
    public long startTime;
    @ProtoDesc("结束时间戳，毫秒；0表示不限结束时间")
    public long endTime;
    @ProtoDesc("每周期限购份数，0表示不限购")
    public int purchaseLimit;
    @ProtoDesc("本周期已购份数，不限购商品不记录")
    public int purchased;
    @ProtoDesc("下次限购重置时间戳，毫秒；按开服日0点起算周期；0表示不重置或不限购")
    public long resetTime;
    @ProtoDesc("付费信息")
    public RechargeInfo rechargeInfo;
}
