package com.jjg.game.minigame.pb.struct;

import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;

import java.util.List;

@ProtobufMessage
@ProtoDesc("找老千关卡进度及当前单局")
public class CheaterHuntInfo {
    @ProtoDesc("当前已解锁关卡ID，对应CheaterHuntLevel.id；仅解锁下一关后推进")
    public int levelId;
    @ProtoDesc("单局标识，无单局为0；找点及退出时原样传回")
    public long roundId;
    @ProtoDesc("当前单局关卡ID，无单局为0")
    public int roundLevelId;
    @ProtoDesc("单局场景ID，对应CheaterHuntMap.id；无单局或一键通关为0")
    public int mapId;
    @ProtoDesc("本局随机作弊点位列表，点位从0开始；一键通关时为空")
    public List<Integer> cheaterSpots;
    @ProtoDesc("本局已找到的作弊点位列表；一键通关时为空")
    public List<Integer> foundSpots;
    @ProtoDesc("本局截止Unix毫秒时间戳；-1不限时，0无单局或一键通关，错误点击会减少")
    public long endTime;
    @ProtoDesc("单局状态：0无局、1进行中、2已通关、3失败、4通关待发奖；4表示发奖失败，重试不扣费")
    public int state;
    @ProtoDesc("服务器当前Unix时间，单位毫秒")
    public long serverTime;
}
