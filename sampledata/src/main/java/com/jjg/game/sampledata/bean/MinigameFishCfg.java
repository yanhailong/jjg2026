package com.jjg.game.sampledata.bean;

import java.util.*;


import javax.annotation.processing.Generated;
/**
 * 配置bean
 *
 * @excelName MinigameFish.xlsx
 * @sheetName MinigameFish
 * @author Auto.Generator
 */
@Generated("com.eouna.configtool.generator.template.java.JavaTemplateGenerator")
public class MinigameFishCfg extends BaseCfgBean {

  /** 配置表名 */
  public static final String EXCEL_NAME = "MinigameFish.xlsx";
  /** 配置表工作薄名 */
  public static final String SHEET_NAME = "MinigameFish";

  /** 鱼生成时间间隔(ms) */
  protected int Interval;
  /** 最大出现次数 */
  protected int MaxCount;
  /** 钩爪中鱼回收速度（px/s） */
  protected int ReturnSpeed;
  /** 捕捉获得积分 */
  protected int Score;
  /** 游动速率 */
  protected int Speed;
  /** 出现权重 */
  protected int Weight;
  /** 图标icon */
  protected String icon;
  /** 类型 */
  protected int type;

  /** 返回鱼生成时间间隔(ms) */
  public int getInterval() {
    return Interval;
  }

  /** 返回最大出现次数 */
  public int getMaxCount() {
    return MaxCount;
  }

  /** 返回钩爪中鱼回收速度（px/s） */
  public int getReturnSpeed() {
    return ReturnSpeed;
  }

  /** 返回捕捉获得积分 */
  public int getScore() {
    return Score;
  }

  /** 返回游动速率 */
  public int getSpeed() {
    return Speed;
  }

  /** 返回出现权重 */
  public int getWeight() {
    return Weight;
  }

  /** 返回图标icon */
  public String getIcon() {
    return icon;
  }

  /** 返回类型 */
  public int getType() {
    return type;
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
