package com.jjg.game.season.pb.struct;

import com.jjg.game.common.pb.ItemInfo;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;

import java.util.List;

@ProtobufMessage
@ProtoDesc("当前赛季小额抽奖完整状态，用于展示价格、购买资格、已付款抽奖机会、每日福利及全部奖池格子")
public class SeasonSmallDrawInfo {
    @ProtoDesc("小额抽奖配置ID，对应当前赛季已开启的SmallDraw.id；用于大奖选择及每日福利领取，通用预下单的productId传此ID的字符串")
    public int drawId;
    @ProtoDesc("当前赛季实例标识，用于区分不同赛季轮次；相同赛季配置再次开启时标识不同")
    public String seasonKey;
    @ProtoDesc("当前赛季及本功能的结束时间，Unix时间戳，单位毫秒")
    public long endTime;
    @ProtoDesc("剩余可购买资格，每日登录或在线跨日增加一次，累计上限为SmallDraw.MaxCumulativeDays；付款到账扣减一次，且不超过剩余正权重格子数减去已付款未抽次数；此资格不能直接抽奖")
    public int purchaseChances;
    @ProtoDesc("本赛季该奖池累计已抽取的格子数，即已获得奖励数量；换季后归零")
    public int drawCount;
    @ProtoDesc("单次购买价格，十进制字符串；来自SmallDraw.PricePerUse对应的ShopRechargeList.Price，价格配置缺失时为空")
    public String price;
    @ProtoDesc("当前玩家渠道的充值商品ID，来自ShopRechargeList.googleShopId或iosShopId；无对应渠道或配置时为空")
    public String channelProductId;
    @ProtoDesc("今日每日福利是否已领取；true表示今天不可再次领取，新的服务器自然日恢复为false")
    public boolean dailyRewardClaimed;
    @ProtoDesc("配置的每日免费福利道具及数量，来自SmallDraw.DailyReward；每日奖励配置无效时为空列表")
    public List<ItemInfo> dailyReward;
    @ProtoDesc("奖池全部格子，按SmallDrawReward.id升序返回，包含已获得和未获得的奖励；空列表表示无有效奖池")
    public List<SeasonSmallDrawRewardInfo> rewards;
    @ProtoDesc("当前奖池核心大奖的全部候选道具及数量，按SmallDrawReward.itemid候选顺序返回；选择请求的optionIndex对应候选下标，无核心大奖时为空列表")
    public List<ItemInfo> coreItems;
    @ProtoDesc("已付款未抽奖次数；付款到账增加一次，客户端每次成功请求抽奖消耗一次，发奖失败不消耗；跨日保留，随赛季重置")
    public int drawChances;
}
