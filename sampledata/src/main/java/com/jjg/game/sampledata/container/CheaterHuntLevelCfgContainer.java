package com.jjg.game.sampledata.container;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.processing.Generated;
import com.jjg.game.sampledata.bean.CheaterHuntLevelCfg;

/**
 * CheaterHuntLevel.xlsx配置管理容器
 *
 * @excelName CheaterHuntLevel.xlsx
 * @sheetName CheaterHuntLevel
 * @author auto_generator
 */
@Generated("com.eouna.configtool.generator.template.java.JavaTemplateGenerator")
public class CheaterHuntLevelCfgContainer extends BaseCfgContainer<CheaterHuntLevelCfg> {

  @Override
  public boolean hasRelatedTable() {
    return false;
  }

  @Override
  public boolean isParentConfigNode() {
    return false;
  }

  @Override
  public CheaterHuntLevelCfgContainer getNewContainer(){
    return new CheaterHuntLevelCfgContainer();
  }

  public CheaterHuntLevelCfgContainer() {
    super();
  }

  @Override
  public List<String> getExcelNameList() {
    List<String> excelNameList = new ArrayList<>();
    excelNameList.add("CheaterHuntLevel.xlsx");
    return excelNameList;
  }

  @Override
  protected CheaterHuntLevelCfg createNewBean() {
    return new CheaterHuntLevelCfg();
  }
}
