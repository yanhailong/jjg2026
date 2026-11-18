package com.jjg.game.sampledata.container;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.processing.Generated;
import com.jjg.game.sampledata.bean.RandomEventDetailsCfg;

/**
 * RandomEventDetails.xlsx配置管理容器
 *
 * @excelName RandomEventDetails.xlsx
 * @sheetName RandomEventDetails
 * @author auto_generator
 */
@Generated("com.eouna.configtool.generator.template.java.JavaTemplateGenerator")
public class RandomEventDetailsCfgContainer extends BaseCfgContainer<RandomEventDetailsCfg> {

  @Override
  public boolean hasRelatedTable() {
    return false;
  }

  @Override
  public boolean isParentConfigNode() {
    return false;
  }

  @Override
  public RandomEventDetailsCfgContainer getNewContainer(){
    return new RandomEventDetailsCfgContainer();
  }

  public RandomEventDetailsCfgContainer() {
    super();
  }

  @Override
  public List<String> getExcelNameList() {
    List<String> excelNameList = new ArrayList<>();
    excelNameList.add("RandomEventDetails.xlsx");
    return excelNameList;
  }

  @Override
  protected RandomEventDetailsCfg createNewBean() {
    return new RandomEventDetailsCfg();
  }
}
