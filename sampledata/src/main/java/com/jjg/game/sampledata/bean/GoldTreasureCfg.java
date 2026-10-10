package com.jjg.game.sampledata.bean;

import java.util.*;


import javax.annotation.processing.Generated;
/**
 * 配置bean
 *
 * @excelName GoldTreasure.xlsx
 * @sheetName GoldTreasure
 * @author Auto.Generator
 */
@Generated("com.eouna.configtool.generator.template.java.JavaTemplateGenerator")
public class GoldTreasureCfg extends BaseCfgBean {

  /** 配置表名 */
  public static final String EXCEL_NAME = "GoldTreasure.xlsx";
  /** 配置表工作薄名 */
  public static final String SHEET_NAME = "GoldTreasure";

  /** 累奖达成条 */
  protected Map<Integer,Long> Condition;
  /** 渠道商品ID */
  protected int PayId;
  /** 奖励 */
  protected Map<Integer,Long> getItem;
  /** 解锁前置ID */
  protected int previousId;
  /** 奖励类型 */
  protected int rewardType;

  /** 返回累奖达成条 */
  public Map<Integer,Long> getCondition() {
    return Condition;
  }

  /** 返回渠道商品ID */
  public int getPayId() {
    return PayId;
  }

  /** 返回奖励 */
  public Map<Integer,Long> getGetItem() {
    return getItem;
  }

  /** 返回解锁前置ID */
  public int getPreviousId() {
    return previousId;
  }

  /** 返回奖励类型 */
  public int getRewardType() {
    return rewardType;
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
