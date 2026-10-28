package com.jjg.game.sampledata.container;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.processing.Generated;
import com.jjg.game.sampledata.bean.MinigameTruckCfg;

/**
 * MinigameTruck.xlsx配置管理容器
 *
 * @excelName MinigameTruck.xlsx
 * @sheetName MinigameTruck
 * @author auto_generator
 */
@Generated("com.eouna.configtool.generator.template.java.JavaTemplateGenerator")
public class MinigameTruckCfgContainer extends BaseCfgContainer<MinigameTruckCfg> {

  @Override
  public boolean hasRelatedTable() {
    return false;
  }

  @Override
  public boolean isParentConfigNode() {
    return false;
  }

  @Override
  public MinigameTruckCfgContainer getNewContainer(){
    return new MinigameTruckCfgContainer();
  }

  public MinigameTruckCfgContainer() {
    super();
  }

  @Override
  public List<String> getExcelNameList() {
    List<String> excelNameList = new ArrayList<>();
    excelNameList.add("MinigameTruck.xlsx");
    return excelNameList;
  }

  @Override
  protected MinigameTruckCfg createNewBean() {
    return new MinigameTruckCfg();
  }
}
