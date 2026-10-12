package com.jjg.game.sampledata.bean;

import java.util.*;


import javax.annotation.processing.Generated;
/**
 * 配置bean
 *
 * @excelName SmallDrawDailyGift.xlsx
 * @sheetName SmallDrawDailyGift
 * @author Auto.Generator
 */
@Generated("com.eouna.configtool.generator.template.java.JavaTemplateGenerator")
public class SmallDrawDailyGiftCfg extends BaseCfgBean {

  /** 配置表名 */
  public static final String EXCEL_NAME = "SmallDrawDailyGift.xlsx";
  /** 配置表工作薄名 */
  public static final String SHEET_NAME = "SmallDrawDailyGift";

  /** 每日道具奖励 */
  protected List<Integer> DailyReward;
  /** 活动ID */
  protected int SmallDrawId;

  /** 返回每日道具奖励 */
  public List<Integer> getDailyReward() {
    return DailyReward;
  }

  /** 返回活动ID */
  public int getSmallDrawId() {
    return SmallDrawId;
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
