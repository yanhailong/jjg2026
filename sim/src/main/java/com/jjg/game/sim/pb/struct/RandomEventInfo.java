package com.jjg.game.sim.pb.struct;

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
    @ProtoDesc("投资0未开始、1已开始倒计时。宝箱为剩余广告开箱次数，抓间谍为已抓捕数，摇骰子为连胜数")
    public int progress;
    @ProtoDesc("抓间谍为是否开始捕获，0否、1是。摇骰子为剩余重转次数。其他玩法为0")
    public int auxiliary;
    @ProtoDesc("投资奖励可领取时刻，毫秒时间戳，0表示未开始倒计时或非投资事件")
    public long readyAt;
    @ProtoDesc("当前三颗骰子的点数，列表索引对应骰子位置，掷骰前为空")
    public List<Integer> dice;
    @ProtoDesc("三个宝箱的开启结果，列表索引对应宝箱位置。-1未开，0空箱，1奖励A，2奖励B")
    public List<Integer> openedBoxes;
    @ProtoDesc("已抓捕的间谍编号列表，编号从0开始")
    public List<Integer> capturedTargets;
    @ProtoDesc("今日已完成的随机事件数。自己的场景统计系统事件及好友投放，拜访时统计玩家在所有好友场景的完成数")
    public int dailyCompletedCount;
    @ProtoDesc("当日事件处理上限。自己的场景为系统事件上限加今日已接收的好友投放数，拜访场景为访客每日处理上限")
    public int dailyCompletionLimit;
}
