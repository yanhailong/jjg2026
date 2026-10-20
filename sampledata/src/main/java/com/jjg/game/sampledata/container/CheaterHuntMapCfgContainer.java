package com.jjg.game.sampledata.container;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.processing.Generated;
import com.jjg.game.sampledata.bean.CheaterHuntMapCfg;

/**
 * CheaterHuntMap.xlsx配置管理容器
 *
 * @excelName CheaterHuntMap.xlsx
 * @sheetName CheaterHuntMap
 * @author auto_generator
 */
@Generated("com.eouna.configtool.generator.template.java.JavaTemplateGenerator")
public class CheaterHuntMapCfgContainer extends BaseCfgContainer<CheaterHuntMapCfg> {

  @Override
  public boolean hasRelatedTable() {
    return false;
  }

  @Override
  public boolean isParentConfigNode() {
    return false;
  }

  @Override
  public CheaterHuntMapCfgContainer getNewContainer(){
    return new CheaterHuntMapCfgContainer();
  }

  public CheaterHuntMapCfgContainer() {
    super();
  }

  @Override
  public List<String> getExcelNameList() {
    List<String> excelNameList = new ArrayList<>();
    excelNameList.add("CheaterHuntMap.xlsx");
    return excelNameList;
  }

  @Override
  protected CheaterHuntMapCfg createNewBean() {
    return new CheaterHuntMapCfg();
  }
}
