package com.jjg.game.sampledata.bean;

import java.util.*;


import javax.annotation.processing.Generated;
/**
 * 配置bean
 *
 * @excelName MinigameFishSkil.xlsx
 * @sheetName MinigameFishSkil
 * @author Auto.Generator
 */
@Generated("com.eouna.configtool.generator.template.java.JavaTemplateGenerator")
public class MinigameFishSkilCfg extends BaseCfgBean {

  /** 配置表名 */
  public static final String EXCEL_NAME = "MinigameFishSkil.xlsx";
  /** 配置表工作薄名 */
  public static final String SHEET_NAME = "MinigameFishSkil";

  /** 等级提升至下一级需要消耗 */
  protected int Cost;
  /** 增益固定值 */
  protected int FlatValue;
  /** 等级 */
  protected int Level;
  /** 关联技能id */
  protected List<Integer> LinkedSkillID;
  /** 增益万分比 */
  protected int Permille;
  /** 位置坐标 */
  protected String Position;
  /** 技能描述多语言 */
  protected int SkillDesc;
  /** 技能图标 */
  protected String SkillIcon;
  /** 技能名称多语言 */
  protected int SkillName;
  /** 技能影响对象 */
  protected int Target;
  /** 技能影响类型 */
  protected List<List<Integer>> Type;
  /** 技能组ID */
  protected int groupId;

  /** 返回等级提升至下一级需要消耗 */
  public int getCost() {
    return Cost;
  }

  /** 返回增益固定值 */
  public int getFlatValue() {
    return FlatValue;
  }

  /** 返回等级 */
  public int getLevel() {
    return Level;
  }

  /** 返回关联技能id */
  public List<Integer> getLinkedSkillID() {
    return LinkedSkillID;
  }

  /** 返回增益万分比 */
  public int getPermille() {
    return Permille;
  }

  /** 返回位置坐标 */
  public String getPosition() {
    return Position;
  }

  /** 返回技能描述多语言 */
  public int getSkillDesc() {
    return SkillDesc;
  }

  /** 返回技能图标 */
  public String getSkillIcon() {
    return SkillIcon;
  }

  /** 返回技能名称多语言 */
  public int getSkillName() {
    return SkillName;
  }

  /** 返回技能影响对象 */
  public int getTarget() {
    return Target;
  }

  /** 返回技能影响类型 */
  public List<List<Integer>> getType() {
    return Type;
  }

  /** 返回技能组ID */
  public int getGroupId() {
    return groupId;
  }

  @Override
  public boolean equals(Object o) {
    return super.equals(o);
  }

  @Override
  public int hashCode() {
    return super.hashCode();
  }
}
