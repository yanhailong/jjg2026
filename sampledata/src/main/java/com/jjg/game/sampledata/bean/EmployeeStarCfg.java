package com.jjg.game.sampledata.bean;

import java.util.*;


import javax.annotation.processing.Generated;
/**
 * 配置bean
 *
 * @excelName EmployeeStar.xlsx
 * @sheetName EmployeeStar
 * @author Auto.Generator
 */
@Generated("com.eouna.configtool.generator.template.java.JavaTemplateGenerator")
public class EmployeeStarCfg extends BaseCfgBean {

  /** 配置表名 */
  public static final String EXCEL_NAME = "EmployeeStar.xlsx";
  /** 配置表工作薄名 */
  public static final String SHEET_NAME = "EmployeeStar";

  /** 属性标题多语言 */
  protected int AttributeTitleLocale;
  /** 雇员ID */
  protected int EmployeeID;
  /** 等级上限 */
  protected int LevelCap;
  /** 星级 */
  protected int Star;
  /** 升星碎片消耗 */
  protected int StarUpCost;
  /** 自身属性加成 */
  protected Map<Integer,Integer> SupervisorBonus;
  /** 升星解锁技能 */
  protected int UnlockSkill;

  /** 返回属性标题多语言 */
  public int getAttributeTitleLocale() {
    return AttributeTitleLocale;
  }

  /** 返回雇员ID */
  public int getEmployeeID() {
    return EmployeeID;
  }

  /** 返回等级上限 */
  public int getLevelCap() {
    return LevelCap;
  }

  /** 返回星级 */
  public int getStar() {
    return Star;
  }

  /** 返回升星碎片消耗 */
  public int getStarUpCost() {
    return StarUpCost;
  }

  /** 返回自身属性加成 */
  public Map<Integer,Integer> getSupervisorBonus() {
    return SupervisorBonus;
  }

  /** 返回升星解锁技能 */
  public int getUnlockSkill() {
    return UnlockSkill;
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
