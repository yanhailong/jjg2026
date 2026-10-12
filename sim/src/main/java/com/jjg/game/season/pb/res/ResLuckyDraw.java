package com.jjg.game.season.pb.res;
import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractResponse;
import com.jjg.game.common.pb.ItemInfo;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.season.constant.SeasonConstant;
import com.jjg.game.season.pb.struct.LuckyDrawInfo;
import java.util.List;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SEASON, cmd = SeasonConstant.MsgBean.RES_LUCKY_DRAW, resp = true)
public class ResLuckyDraw extends AbstractResponse {
    /** 1详情 2购买抽奖 3选择大奖 4每日福利 */
    public int action;
    public String requestId;
    public int rewardId;
    public int grandId;
    public boolean replay;
    public boolean rewardPending;
    public List<ItemInfo> rewards = List.of();
    public LuckyDrawInfo info;
    public ResLuckyDraw(int code) { super(code); }
}
