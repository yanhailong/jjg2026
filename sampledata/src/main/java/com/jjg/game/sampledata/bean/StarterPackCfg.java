package com.jjg.game.sampledata.bean;

import java.util.*;
import java.math.BigDecimal;



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
  /** 奖励天数 */
  protected int RewardDays;
  /** 超值 */
  protected BigDecimal bestValue;
  /** 购买金额 */
  protected BigDecimal money;
  /** 原价值 */
  protected BigDecimal wasPrice;

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

  /** 返回奖励天数 */
  public int getRewardDays() {
    return RewardDays;
  }

  /** 返回超值 */
  public BigDecimal getBestValue() {
    return bestValue;
  }

  /** 返回购买金额 */
  public BigDecimal getMoney() {
    return money;
  }

  /** 返回原价值 */
  public BigDecimal getWasPrice() {
    return wasPrice;
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
