package com.jjg.game.sampledata.bean;

import java.util.*;


import javax.annotation.processing.Generated;
/**
 * 配置bean
 *
 * @excelName SmallDraw.xlsx
 * @sheetName SmallDraw
 * @author Auto.Generator
 */
@Generated("com.eouna.configtool.generator.template.java.JavaTemplateGenerator")
public class SmallDrawCfg extends BaseCfgBean {

  /** 配置表名 */
  public static final String EXCEL_NAME = "SmallDraw.xlsx";
  /** 配置表工作薄名 */
  public static final String SHEET_NAME = "SmallDraw";

  /** 最大累积天数 */
  protected int MaxCumulativeDays;
  /** 单次价格 */
  protected int PricePerUse;
  /** 格子总数 */
  protected int RewardSlotCount;
  /** 活动名称 */
  protected int name;
  /** 活动状态 */
  protected boolean open;
  /** 赛季ID */
  protected int seasonid;

  /** 返回最大累积天数 */
  public int getMaxCumulativeDays() {
    return MaxCumulativeDays;
  }

  /** 返回单次价格 */
  public int getPricePerUse() {
    return PricePerUse;
  }

  /** 返回格子总数 */
  public int getRewardSlotCount() {
    return RewardSlotCount;
  }

  /** 返回活动名称 */
  public int getName() {
    return name;
  }

  /** 返回活动状态 */
  public boolean getOpen() {
    return open;
  }

  /** 返回赛季ID */
  public int getSeasonid() {
    return seasonid;
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
