package com.jjg.game.season.pb.res;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractResponse;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.season.constant.SeasonConstant;
import com.jjg.game.season.pb.struct.SeasonSmallDrawInfo;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SEASON, cmd = SeasonConstant.MsgBean.RES_SEASON_SMALL_DRAW_INFO, resp = true)
@ProtoDesc("小额抽奖状态返回，也在付款到账后主动推送")
public class ResSeasonSmallDrawInfo extends AbstractResponse {
    @ProtoDesc("当前功能状态；仅code=200时返回，功能未开启时为空")
    public SeasonSmallDrawInfo info;
    public ResSeasonSmallDrawInfo(int code) { super(code); }
}
