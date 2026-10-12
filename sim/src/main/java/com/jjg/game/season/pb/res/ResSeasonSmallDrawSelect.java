package com.jjg.game.season.pb.res;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractResponse;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.season.constant.SeasonConstant;
import com.jjg.game.season.pb.struct.SeasonSmallDrawInfo;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SEASON, cmd = SeasonConstant.MsgBean.RES_SEASON_SMALL_DRAW_SELECT, resp = true)
@ProtoDesc("核心大奖选择返回")
public class ResSeasonSmallDrawSelect extends AbstractResponse {
    public SeasonSmallDrawInfo info;
    public ResSeasonSmallDrawSelect(int code) { super(code); }
}
