package com.jjg.game.activity.noads.message;

import com.jjg.game.activity.common.message.bean.BaseActivityDetailInfo;
import com.jjg.game.common.pb.ItemInfo;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.core.pb.RechargeInfo;

import java.util.List;

/**
 * activityId为ActivityConfig.id，detailId为当前价格对应的ShopRechargeList.id。
 * 下单只需rechargeType=22及payType，无需productId；服务器按下单时的折扣时间选择价格。
 * items按ActivityConfig.valueParam的配置顺序返回各组道具及数量；赠品无需单独领取。
 */
@ProtobufMessage
@ProtoDesc("免广告礼包详情；下单无需productId，使用rechargeType=22及payType，服务器按折扣时间选择价格；detailId是当前ShopRechargeList.id，赠品随支付自动发放")
public class NoAdsDetailInfo extends BaseActivityDetailInfo {
    @ProtoDesc("购买赠送的配置道具；按ActivityConfig.valueParam从下标0起每两个元素组成道具ID和数量")
    public List<ItemInfo> items;
    @ProtoDesc("折扣结束时间戳，单位毫秒；等于活动开始时间加value[0]秒，当前时间达到此值后按原价购买")
    public long discountEndTime;
    @ProtoDesc("折扣百分比，来自bigDecimalParam[0]的十进制字符串，仅用于客户端显示，不参与价格计算")
    public String discountPercent;
    @ProtoDesc("折扣价，来自channelCommodity[0]对应ShopRechargeList.price的十进制字符串")
    public String discountPrice;
    @ProtoDesc("原价，来自channelCommodity[1]对应ShopRechargeList.price的十进制字符串；倒计时结束后显示此价格")
    public String originalPrice;
    @ProtoDesc("当前展示的付费商品信息，含价格和玩家渠道商品ID；实际下单价格由服务器按下单时的折扣时间选择，无需传productId或detailId")
    public RechargeInfo rechargeInfo;
}
