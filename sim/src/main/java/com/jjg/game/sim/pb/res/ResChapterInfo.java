package com.jjg.game.sim.pb.res;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractResponse;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.sim.constant.SimConstant;
import com.jjg.game.sim.pb.struct.ChapterPKInfo;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SIM_GAME, cmd = SimConstant.MsgBean.RES_CHAPTER_INFO, resp = true)
@ProtoDesc("获取章节信息返回")
public class ResChapterInfo extends AbstractResponse {
    @ProtoDesc("章节id")
    public int chapterId;
    @ProtoDesc("已通关关卡id，0表示尚未通关")
    public int levelId;
    @ProtoDesc("(点亮格子关卡)格子下标")
    public int girdIndex;
    @ProtoDesc("该格子已经点击次数")
    public int girdLightCount;
    @ProtoDesc("pk信息，没有pk环节的时候该值为空")
    public ChapterPKInfo chapterPKInfo;
    @ProtoDesc("是否完成拼图")
    public boolean finishPuzzle;

    public ResChapterInfo(int code) {
        super(code);
    }
}
