package com.jjg.game.sim.pb.struct;

import com.jjg.game.common.pb.ItemInfo;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;

import java.util.List;

@ProtobufMessage
@ProtoDesc("场景随机事件")
public class RandomEventInfo {
    @ProtoDesc("对应RandomEventDetails.id，在当前场景内唯一")
    public int configId;
    @ProtoDesc("对应RandomEventDetails.areaId，1问答，2房间游戏，3宝箱，4抓间谍，5投资，6摇骰子")
    public int eventType;
    @ProtoDesc("事件人物在场景中的点位编号，范围1到20")
    public int position;
    @ProtoDesc("投放此事件的好友玩家ID，0表示没有投放好友")
    public long donorId;
    @ProtoDesc("今日已完成的随机事件数。自己的场景统计系统事件及好友投放，拜访时统计玩家在所有好友场景的完成数")
    public int dailyCompletedCount;
    @ProtoDesc("当日事件处理上限。自己的场景为系统事件上限加今日已接收的好友投放数，拜访场景为访客每日处理上限")
    public int dailyCompletionLimit;
    @ProtoDesc("问答事件数据，仅问答事件设置")
    public RandomEventQuizInfo quizInfo;
    @ProtoDesc("房间游戏事件数据，仅房间游戏事件设置")
    public RandomEventRoomInfo roomInfo;
    @ProtoDesc("宝箱事件数据，仅宝箱事件设置")
    public RandomEventBoxInfo boxInfo;
    @ProtoDesc("抓间谍事件数据，仅抓间谍事件设置")
    public RandomEventSpyInfo spyInfo;
    @ProtoDesc("投资事件数据，仅投资事件设置")
    public RandomEventInvestmentInfo investmentInfo;
    @ProtoDesc("摇骰子事件数据，仅摇骰子事件设置")
    public RandomEventDiceInfo diceInfo;

    @ProtobufMessage
    @ProtoDesc("问答随机事件")
    public static class RandomEventQuizInfo {
    }

    @ProtobufMessage
    @ProtoDesc("房间游戏随机事件")
    public static class RandomEventRoomInfo {
    }

    @ProtobufMessage
    @ProtoDesc("宝箱随机事件")
    public static class RandomEventBoxInfo {
        @ProtoDesc("除首次免费开箱外，剩余可开的宝箱数量")
        public int remainingAdOpens;
        @ProtoDesc("三个宝箱，列表索引0到2对应开箱操作的目标位置")
        public List<RandomEventBoxSlotInfo> boxes;
        @ProtoDesc("本次开箱的奖励类别，0空箱，1奖励A，2奖励B。仅开箱成功时有效")
        public Integer openedReward;
    }

    @ProtobufMessage
    @ProtoDesc("抓间谍随机事件")
    public static class RandomEventSpyInfo {
        @ProtoDesc("是否已开始捕获")
        public boolean started;
        @ProtoDesc("已抓捕的间谍数量")
        public int capturedCount;
        @ProtoDesc("已抓捕的间谍编号列表，编号从0开始")
        public List<Integer> capturedTargets;
        @ProtoDesc("完成事件所需抓捕的间谍总数，对应RandomEventDetails.parameter1[1]，配置无效时为0")
        public int requiredCount;
    }

    @ProtobufMessage
    @ProtoDesc("投资随机事件")
    public static class RandomEventInvestmentInfo {
        @ProtoDesc("是否已开始投资倒计时")
        public boolean started;
        @ProtoDesc("投资奖励可领取时刻，毫秒时间戳，0表示未开始倒计时")
        public long readyAt;
    }

    @ProtobufMessage
    @ProtoDesc("摇骰子随机事件")
    public static class RandomEventDiceInfo {
        @ProtoDesc("当前连胜次数")
        public int consecutiveWins;
        @ProtoDesc("剩余重转次数")
        public int remainingRerolls;
        @ProtoDesc("当前三颗骰子的点数，列表索引对应骰子位置，掷骰前为空")
        public List<Integer> dice;
        @ProtoDesc("基础奖金的道具ID和数量，对应全局配置421")
        public ItemInfo baseReward;
        @ProtoDesc("当前可领取的奖金数量，使用基础奖金的道具ID，无奖金时为0")
        public long currentReward;
    }
}
