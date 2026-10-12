package com.jjg.game.sampledata.container;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.processing.Generated;
import com.jjg.game.sampledata.bean.GoldTreasureCfg;

/**
 * GoldTreasure.xlsx配置管理容器
 *
 * @excelName GoldTreasure.xlsx
 * @sheetName GoldTreasure
 * @author auto_generator
 */
@Generated("com.eouna.configtool.generator.template.java.JavaTemplateGenerator")
public class GoldTreasureCfgContainer extends BaseCfgContainer<GoldTreasureCfg> {

  @Override
  public boolean hasRelatedTable() {
    return false;
  }

  @Override
  public boolean isParentConfigNode() {
    return false;
  }

  @Override
  public GoldTreasureCfgContainer getNewContainer(){
    return new GoldTreasureCfgContainer();
  }

  public GoldTreasureCfgContainer() {
    super();
  }

  @Override
  public List<String> getExcelNameList() {
    List<String> excelNameList = new ArrayList<>();
    excelNameList.add("GoldTreasure.xlsx");
    return excelNameList;
  }

  @Override
  protected GoldTreasureCfg createNewBean() {
    return new GoldTreasureCfg();
  }
}
