package com.jjg.game.sim.pb.res;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractResponse;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.sim.constant.SimConstant;
import com.jjg.game.sim.pb.struct.VisitCasinoInfo;
import com.jjg.game.sim.pb.struct.RandomEventInfo;

import java.util.List;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SIM_GAME, cmd = SimConstant.MsgBean.RES_VISIT_CASINO, resp = true)
@ProtoDesc("拜访赌场返回")
public class ResVisitCasino extends AbstractResponse {
    public VisitCasinoInfo info;
    public int remainingLikes;
    public int dailyLikeLimit;
    public int remainingComments;
    public int dailyCommentLimit;
    public int remainingTrials;
    public int dailyTrialLimit;
    public int unreadComments;
    public boolean commentUnlocked;
    public int commissionRate;
    @ProtoDesc("房主每天可接收随机事件投放的次数上限")
    public int dailyRandomEventReceiveLimit;
    @ProtoDesc("房主目前还能接收的投放次数，受每日接收上限和待补投放容量限制")
    public int remainingRandomEventReceives;
    @ProtoDesc("当前玩家每天向所有好友投放随机事件的次数上限")
    public int dailyRandomEventSendLimit;
    @ProtoDesc("当前玩家今日剩余的好友投放次数")
    public int remainingRandomEventSends;
    @ProtoDesc("当前玩家每天在所有好友场景可完成的随机事件次数上限")
    public int dailyRandomEventCompleteLimit;
    @ProtoDesc("当前玩家今日还能在好友场景完成的随机事件次数")
    public int remainingRandomEventCompletions;
    @ProtoDesc("本次拜访中供当前玩家处理的临时随机事件，离开后清除")
    public List<RandomEventInfo> randomEvents;

    public ResVisitCasino() {
        super(0);
    }

    public ResVisitCasino(int code) {
        super(code);
    }
}
