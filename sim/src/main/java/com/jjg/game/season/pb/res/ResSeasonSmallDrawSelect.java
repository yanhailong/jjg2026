package com.jjg.game.season.pb.res;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractResponse;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.season.constant.SeasonConstant;
import com.jjg.game.season.pb.struct.SeasonSmallDrawInfo;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SEASON, cmd = SeasonConstant.MsgBean.RES_SEASON_SMALL_DRAW_SELECT, resp = true)
@ProtoDesc("核心大奖选择返回；code=200成功，404功能未开启或drawId不匹配，402格子或候选索引无效，500选择保存失败")
public class ResSeasonSmallDrawSelect extends AbstractResponse {
    @ProtoDesc("选择成功后的最新功能状态，包含已更新的核心大奖候选及展示道具；仅code=200时返回")
    public SeasonSmallDrawInfo info;
    public ResSeasonSmallDrawSelect(int code) { super(code); }
}
