package com.jjg.game.sampledata.container;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.processing.Generated;
import com.jjg.game.sampledata.bean.TokenShopCfg;

/**
 * TokenShop.xlsx配置管理容器
 *
 * @excelName TokenShop.xlsx
 * @sheetName TokenShop
 * @author auto_generator
 */
@Generated("com.eouna.configtool.generator.template.java.JavaTemplateGenerator")
public class TokenShopCfgContainer extends BaseCfgContainer<TokenShopCfg> {

  @Override
  public boolean hasRelatedTable() {
    return false;
  }

  @Override
  public boolean isParentConfigNode() {
    return false;
  }

  @Override
  public TokenShopCfgContainer getNewContainer(){
    return new TokenShopCfgContainer();
  }

  public TokenShopCfgContainer() {
    super();
  }

  @Override
  public List<String> getExcelNameList() {
    List<String> excelNameList = new ArrayList<>();
    excelNameList.add("TokenShop.xlsx");
    return excelNameList;
  }

  @Override
  protected TokenShopCfg createNewBean() {
    return new TokenShopCfg();
  }
}
