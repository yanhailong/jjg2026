package com.jjg.game.sim.pb.res;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractResponse;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.sim.constant.SimConstant;
import com.jjg.game.sim.pb.struct.RandomEventInfo;

import java.util.List;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SIM_GAME, cmd = SimConstant.MsgBean.RES_RANDOM_EVENT_LIST, resp = true)
@ProtoDesc("当前场景的随机事件列表")
public class ResSimRandomEventList extends AbstractResponse {
    @ProtoDesc("被拜访好友的玩家ID，0表示自己的场景")
    public long visitTargetId;
    @ProtoDesc("当前玩家今日还能在好友场景完成的事件数，自己的场景为0")
    public int remainingVisitCompletions;
    @ProtoDesc("当前场景可见的随机事件列表")
    public List<RandomEventInfo> events;
    @ProtoDesc("当日事件处理上限。自己的场景为系统事件上限加今日已接收的好友投放数，拜访场景为访客每日处理上限")
    public int dailyCompletionLimit;

    public ResSimRandomEventList(int code) {
        super(code);
    }
}
