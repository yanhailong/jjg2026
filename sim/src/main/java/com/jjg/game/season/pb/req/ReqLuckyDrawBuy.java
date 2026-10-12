package com.jjg.game.season.pb.req;
import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractMessage;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.season.constant.SeasonConstant;
@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SEASON, cmd = SeasonConstant.MsgBean.REQ_LUCKY_DRAW_BUY)
public class ReqLuckyDrawBuy extends AbstractMessage {
    public int activityId;
    public int drawType;
    /** 一次购买生成一个新标识，网络重试必须复用；字母数字或连字符，长度1至64 */
    public String requestId;
}
