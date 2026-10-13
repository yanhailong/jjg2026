package com.jjg.game.sampledata.bean;

import java.util.*;


import javax.annotation.processing.Generated;
/**
 * 配置bean
 *
 * @excelName StarterPack.xlsx
 * @sheetName StarterPack
 * @author Auto.Generator
 */
@Generated("com.eouna.configtool.generator.template.java.JavaTemplateGenerator")
public class StarterPackCfg extends BaseCfgBean {

  /** 配置表名 */
  public static final String EXCEL_NAME = "StarterPack.xlsx";
  /** 配置表工作薄名 */
  public static final String SHEET_NAME = "StarterPack";

  /** 奖励1 */
  protected Map<Integer,Long> Reward1;
  /** 奖励2 */
  protected Map<Integer,Long> Reward2;
  /** 奖励3 */
  protected Map<Integer,Long> Reward3;

  /** 返回奖励1 */
  public Map<Integer,Long> getReward1() {
    return Reward1;
  }

  /** 返回奖励2 */
  public Map<Integer,Long> getReward2() {
    return Reward2;
  }

  /** 返回奖励3 */
  public Map<Integer,Long> getReward3() {
    return Reward3;
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
