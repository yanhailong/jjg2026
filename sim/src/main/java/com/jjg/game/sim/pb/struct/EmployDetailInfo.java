package com.jjg.game.sim.pb.struct;

import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.core.pb.KVLongInfo;

import java.util.List;

/**
 * @author 11
 * @date 2026/6/15
 */
@ProtobufMessage
@ProtoDesc("雇员详细信息")
public class EmployDetailInfo {
    @ProtoDesc("雇员id")
    public int id;
    @ProtoDesc("等级")
    public int level;
    @ProtoDesc("星级")
    public int star;
    @ProtoDesc("是否为主管")
    public boolean manager;
    @ProtoDesc("战斗力")
    public long combatPower;
    @ProtoDesc("最终属性，key为BuildingOutputType")
    public List<KVLongInfo> attributes;
    @ProtoDesc("技能当前等级及配置")
    public List<EmployeeSkillInfo> skills;
    @ProtoDesc("职业   10.对战  20.下注  30.电子  40.酒水")
    public int professionID;
    @ProtoDesc("最高等级")
    public int maxLevel;
    @ProtoDesc("最高星级")
    public int maxStar;
}
