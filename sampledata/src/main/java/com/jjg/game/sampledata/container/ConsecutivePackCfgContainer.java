package com.jjg.game.sampledata.container;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.processing.Generated;
import com.jjg.game.sampledata.bean.ConsecutivePackCfg;

/**
 * ConsecutivePack.xlsx配置管理容器
 *
 * @excelName ConsecutivePack.xlsx
 * @sheetName ConsecutivePack
 * @author auto_generator
 */
@Generated("com.eouna.configtool.generator.template.java.JavaTemplateGenerator")
public class ConsecutivePackCfgContainer extends BaseCfgContainer<ConsecutivePackCfg> {

  @Override
  public boolean hasRelatedTable() {
    return false;
  }

  @Override
  public boolean isParentConfigNode() {
    return false;
  }

  @Override
  public ConsecutivePackCfgContainer getNewContainer(){
    return new ConsecutivePackCfgContainer();
  }

  public ConsecutivePackCfgContainer() {
    super();
  }

  @Override
  public List<String> getExcelNameList() {
    List<String> excelNameList = new ArrayList<>();
    excelNameList.add("ConsecutivePack.xlsx");
    return excelNameList;
  }

  @Override
  protected ConsecutivePackCfg createNewBean() {
    return new ConsecutivePackCfg();
  }
}
