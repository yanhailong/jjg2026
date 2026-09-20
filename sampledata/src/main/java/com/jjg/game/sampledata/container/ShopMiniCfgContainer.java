package com.jjg.game.sampledata.container;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.processing.Generated;
import com.jjg.game.sampledata.bean.ShopMiniCfg;

/**
 * ShopMini.xlsx配置管理容器
 *
 * @excelName ShopMini.xlsx
 * @sheetName ShopMini
 * @author auto_generator
 */
@Generated("com.eouna.configtool.generator.template.java.JavaTemplateGenerator")
public class ShopMiniCfgContainer extends BaseCfgContainer<ShopMiniCfg> {

  @Override
  public boolean hasRelatedTable() {
    return false;
  }

  @Override
  public boolean isParentConfigNode() {
    return false;
  }

  @Override
  public ShopMiniCfgContainer getNewContainer(){
    return new ShopMiniCfgContainer();
  }

  public ShopMiniCfgContainer() {
    super();
  }

  @Override
  public List<String> getExcelNameList() {
    List<String> excelNameList = new ArrayList<>();
    excelNameList.add("ShopMini.xlsx");
    return excelNameList;
  }

  @Override
  protected ShopMiniCfg createNewBean() {
    return new ShopMiniCfg();
  }
}
