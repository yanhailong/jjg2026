package com.jjg.game.sampledata.container;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.processing.Generated;
import com.jjg.game.sampledata.bean.BuildingUpgradeAreaCfg;

/**
 * BuildingUpgradeArea.xlsx配置管理容器
 *
 * @excelName BuildingUpgradeArea.xlsx
 * @sheetName BuildingUpgradeArea
 * @author auto_generator
 */
@Generated("com.eouna.configtool.generator.template.java.JavaTemplateGenerator")
public class BuildingUpgradeAreaCfgContainer extends BaseCfgContainer<BuildingUpgradeAreaCfg> {

  @Override
  public boolean hasRelatedTable() {
    return false;
  }

  @Override
  public boolean isParentConfigNode() {
    return false;
  }

  @Override
  public BuildingUpgradeAreaCfgContainer getNewContainer(){
    return new BuildingUpgradeAreaCfgContainer();
  }

  public BuildingUpgradeAreaCfgContainer() {
    super();
  }

  @Override
  public List<String> getExcelNameList() {
    List<String> excelNameList = new ArrayList<>();
    excelNameList.add("BuildingUpgradeArea.xlsx");
    return excelNameList;
  }

  @Override
  protected BuildingUpgradeAreaCfg createNewBean() {
    return new BuildingUpgradeAreaCfg();
  }
}
