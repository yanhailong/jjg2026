package com.jjg.game.sim.pb.req;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractMessage;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.sim.constant.SimConstant;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SIM_GAME, cmd = SimConstant.MsgBean.REQ_RANDOM_EVENT_ACTION)
@ProtoDesc("操作当前场景的随机事件")
public class ReqSimRandomEventAction extends AbstractMessage {
    @ProtoDesc("当前场景中的事件配置ID，对应RandomEventDetails.id")
    public int configId;
    @ProtoDesc("1查看，2选择A（含抓间谍领取A奖励）或放弃房间游戏，3选择B，4开始抓捕或掷骰，5开箱、抓捕或重转，7领取（投资倒计时内为双倍）或见好就收")
    public int action;
    @ProtoDesc("动作5的目标位置。宝箱和骰子的位置为0到2，间谍编号从0开始")
    public int target;
}
