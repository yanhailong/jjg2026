package com.jjg.game.core.utils;

import com.jjg.game.core.data.ChannelType;
import com.jjg.game.core.exception.GameSampleException;
import com.jjg.game.core.pb.RechargeInfo;
import com.jjg.game.sampledata.GameDataManager;
import com.jjg.game.sampledata.bean.GlobalConfigCfg;
import com.jjg.game.sampledata.bean.ShopRechargeListCfg;
import com.jjg.game.sampledata.bean.WarehouseCfg;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

/**
 * 数据处理工具
 *
 * @author 2CL
 */
public class SampleDataUtils {

    /**
     * 通过房间配置获取最大限制
     */
    public static Tuple2<Integer, Integer> getRoomMaxLimit(WarehouseCfg warehouseCfg) {
        String participantsMax = warehouseCfg.getParticipants_max();
        String[] participantsMaxStrArr = participantsMax.split(":");
        return Tuples.of(Integer.parseInt(participantsMaxStrArr[0]), Integer.parseInt(participantsMaxStrArr[1]));
    }

    /**
     * 通过全局表ID获取表中对应的int字段值
     */
    public static int getIntGlobalData(int globalId) {
        GlobalConfigCfg globalConfigCfg = GameDataManager.getGlobalConfigCfg(globalId);
        if (globalConfigCfg == null) {
            throw new GameSampleException("全局表找不到配置ID：" + globalId);
        }
        return globalConfigCfg.getIntValue();
    }

    public static RechargeInfo transRechargeCfgToRechargeInfo(int id, ChannelType channelType) {
        if (id < 1) {
            return null;
        }
        ShopRechargeListCfg shopRechargeListCfg = GameDataManager.getShopRechargeListCfg(id);
        if (shopRechargeListCfg == null) {
            return null;
        }
        RechargeInfo rechargeInfo = new RechargeInfo();
        rechargeInfo.type = shopRechargeListCfg.getType();

        if (shopRechargeListCfg.getPrice() != null) {
            rechargeInfo.price = shopRechargeListCfg.getPrice().toPlainString();

            if (channelType == ChannelType.APPLE) {
                rechargeInfo.channelProductId = shopRechargeListCfg.getIosShopId();
            } else {
                rechargeInfo.channelProductId = shopRechargeListCfg.getGoogleShopId();
            }
        }

        if (shopRechargeListCfg.getRedeem() != null && !shopRechargeListCfg.getRedeem().isEmpty()) {
            rechargeInfo.redeem = ItemUtils.buildItemInfo(shopRechargeListCfg.getRedeem());
        }
        return rechargeInfo;
    }
}
