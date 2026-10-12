package com.jjg.game.season.pb.req;
import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractMessage;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.season.constant.SeasonConstant;
@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SEASON, cmd = SeasonConstant.MsgBean.REQ_LUCKY_DRAW_INFO)
public class ReqLuckyDrawInfo extends AbstractMessage {
    /** 0查当前活动，其他值必须匹配本期活动 */
    public int activityId;
    public int drawType;
    /** 真正进入页面时为 true；入口轮询为 false */
    public boolean enter;
}
