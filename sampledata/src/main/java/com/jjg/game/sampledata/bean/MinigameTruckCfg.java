package com.jjg.game.sampledata.bean;

import java.util.*;


import javax.annotation.processing.Generated;
/**
 * 配置bean
 *
 * @excelName MinigameTruck.xlsx
 * @sheetName MinigameTruck
 * @author Auto.Generator
 */
@Generated("com.eouna.configtool.generator.template.java.JavaTemplateGenerator")
public class MinigameTruckCfg extends BaseCfgBean {

  /** 配置表名 */
  public static final String EXCEL_NAME = "MinigameTruck.xlsx";
  /** 配置表工作薄名 */
  public static final String SHEET_NAME = "MinigameTruck";

  /** 酒水货物加成系数 */
  protected int DangerMultiplier;
  /** 糕点货物加成系数 */
  protected int FragileMultiplier;
  /** 生鲜货物加成系数 */
  protected int NormalMultiplier;

  /** 返回酒水货物加成系数 */
  public int getDangerMultiplier() {
    return DangerMultiplier;
  }

  /** 返回糕点货物加成系数 */
  public int getFragileMultiplier() {
    return FragileMultiplier;
  }

  /** 返回生鲜货物加成系数 */
  public int getNormalMultiplier() {
    return NormalMultiplier;
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
