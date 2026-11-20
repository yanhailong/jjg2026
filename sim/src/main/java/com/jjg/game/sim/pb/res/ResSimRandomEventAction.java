package com.jjg.game.sim.pb.res;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractResponse;
import com.jjg.game.common.pb.ItemInfo;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.sim.constant.SimConstant;
import com.jjg.game.sim.pb.struct.RandomEventInfo;

import java.util.List;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SIM_GAME, cmd = SimConstant.MsgBean.RES_RANDOM_EVENT_ACTION, resp = true)
@ProtoDesc("随机事件操作及结算结果")
public class ResSimRandomEventAction extends AbstractResponse {
    @ProtoDesc("操作成功后的事件状态，完成时返回最后一次状态")
    public RandomEventInfo event;
    @ProtoDesc("事件是否已完成并从场景移除")
    public boolean completed;
    @ProtoDesc("本次实际发放的奖励，未发奖时为空")
    public List<ItemInfo> rewards;
    @ProtoDesc("结算文案的多语言ID列表，奖励A取RandomEventDetails.result_AB[0]，奖励B取result_AB[1]，无对应文案时为空")
    public List<Integer> resultLanguageId;

    public ResSimRandomEventAction(int code) {
        super(code);
    }
}
