package com.jjg.game.sampledata.bean;

import java.util.*;


import javax.annotation.processing.Generated;
/**
 * 配置bean
 *
 * @excelName Minigamegoods.xlsx
 * @sheetName Minigamegoods
 * @author Auto.Generator
 */
@Generated("com.eouna.configtool.generator.template.java.JavaTemplateGenerator")
public class MinigamegoodsCfg extends BaseCfgBean {

  /** 配置表名 */
  public static final String EXCEL_NAME = "Minigamegoods.xlsx";
  /** 配置表工作薄名 */
  public static final String SHEET_NAME = "Minigamegoods";

  /** 货物图标 */
  protected String Emoji;
  /** 基础分 */
  protected int Score;
  /** 货物单价 */
  protected int UnitPrice;
  /** 刷新权重 */
  protected int Weight;
  /** 货物类型 */
  protected int type;

  /** 返回货物图标 */
  public String getEmoji() {
    return Emoji;
  }

  /** 返回基础分 */
  public int getScore() {
    return Score;
  }

  /** 返回货物单价 */
  public int getUnitPrice() {
    return UnitPrice;
  }

  /** 返回刷新权重 */
  public int getWeight() {
    return Weight;
  }

  /** 返回货物类型 */
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
