package com.jjg.game.activity.starterpack.message;

import com.jjg.game.activity.common.message.bean.BaseActivityDetailInfo;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.core.pb.RechargeInfo;

import java.util.List;

@ProtobufMessage
@ProtoDesc("新手礼包详情；claimStatus为1未购买、4已购买；通用领奖请求使用StarterPack配置ID，一次领取全部已解锁未领取奖励")
public class StarterPackDetailInfo extends BaseActivityDetailInfo {
    @ProtoDesc("已解锁的登录档数，最多3")
    public int loginDays;
    @ProtoDesc("原价，仅用于展示")
    public String originalPrice;
    @ProtoDesc("超值价，仅用于展示")
    public String bestValuePrice;
    @ProtoDesc("实际付费价格及渠道商品；下单productId为activityId_detailId，rechargeType为21")
    public RechargeInfo rechargeInfo;
    public List<StarterPackRewardInfo> rewards;
}
