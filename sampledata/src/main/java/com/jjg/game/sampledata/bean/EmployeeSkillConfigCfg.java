package com.jjg.game.sampledata.bean;

import java.util.*;


import javax.annotation.processing.Generated;
/**
 * 配置bean
 *
 * @excelName EmployeeSkillConfig.xlsx
 * @sheetName EmployeeSkillConfig
 * @author Auto.Generator
 */
@Generated("com.eouna.configtool.generator.template.java.JavaTemplateGenerator")
public class EmployeeSkillConfigCfg extends BaseCfgBean {

  /** 配置表名 */
  public static final String EXCEL_NAME = "EmployeeSkillConfig.xlsx";
  /** 配置表工作薄名 */
  public static final String SHEET_NAME = "EmployeeSkillConfig";

  /** 属性名称多语言 */
  protected List<Integer> AttributeLocale;
  /** 加成百分比 */
  protected List<Integer> Bonus;
  /** 加成固定值 */
  protected List<Integer> BonusFlat;
  /** 对建筑固定值加成 */
  protected List<List<Integer>> Buff;
  /** 对雇员生效的属性加成 */
  protected List<List<Integer>> Employee;
  /** 对建筑加成（建筑ID_属性类型_百分比） */
  protected List<List<Integer>> Modifier;
  /** 技能描述多语言 */
  protected List<Integer> SkillLocale;
  /** 技能名称多语言 */
  protected int SkillName;
  /** 技能组ID */
  protected int groupId;
  /** 等级 */
  protected int skillLevel;
  /** 技能类型 */
  protected int skillType;
  /** 当前等级提升至下一级需要消耗 */
  protected Map<Integer,Integer> upCost;

  /** 返回属性名称多语言 */
  public List<Integer> getAttributeLocale() {
    return AttributeLocale;
  }

  /** 返回加成百分比 */
  public List<Integer> getBonus() {
    return Bonus;
  }

  /** 返回加成固定值 */
  public List<Integer> getBonusFlat() {
    return BonusFlat;
  }

  /** 返回对建筑固定值加成 */
  public List<List<Integer>> getBuff() {
    return Buff;
  }

  /** 返回对雇员生效的属性加成 */
  public List<List<Integer>> getEmployee() {
    return Employee;
  }

  /** 返回对建筑加成（建筑ID_属性类型_百分比） */
  public List<List<Integer>> getModifier() {
    return Modifier;
  }

  /** 返回技能描述多语言 */
  public List<Integer> getSkillLocale() {
    return SkillLocale;
  }

  /** 返回技能名称多语言 */
  public int getSkillName() {
    return SkillName;
  }

  /** 返回技能组ID */
  public int getGroupId() {
    return groupId;
  }

  /** 返回等级 */
  public int getSkillLevel() {
    return skillLevel;
  }

  /** 返回技能类型 */
  public int getSkillType() {
    return skillType;
  }

  /** 返回当前等级提升至下一级需要消耗 */
  public Map<Integer,Integer> getUpCost() {
    return upCost;
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
