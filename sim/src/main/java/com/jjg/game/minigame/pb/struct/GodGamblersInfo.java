package com.jjg.game.minigame.pb.struct;

import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;

@ProtobufMessage
@ProtoDesc("赌神搓牌单局信息")
public class GodGamblersInfo {
    @ProtoDesc("单局标识，取牌及比拼时原样传回")
    public long roundId;
    @ProtoDesc("对手牌ID：黑桃0..12、红桃13..25、梅花26..38、方块39..51；各花色按2..A排列")
    public int opponentPoker;
    @ProtoDesc("自己的牌ID，编码同对手牌；未获取时为-1")
    public int selfPoker;
    @ProtoDesc("已获取自己牌次数，含首次")
    public int rubCount;
    @ProtoDesc("单局状态：1进行中、2已结算、4待发奖")
    public int state;
}
