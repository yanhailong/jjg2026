package com.jjg.game.core.listener;

import com.jjg.game.common.baselogic.IGameSysFuncInterface;
import com.jjg.game.core.constant.AddType;
import com.jjg.game.core.constant.Code;
import com.jjg.game.core.data.CommonResult;
import com.jjg.game.core.data.Order;
import com.jjg.game.core.data.Player;
import com.jjg.game.core.data.PreGenerateOrderData;
import com.jjg.game.core.pb.RechargeType;
import com.jjg.game.core.pb.ReqGenerateOrder;
import com.jjg.game.core.service.CorePlayerService;
import com.jjg.game.sampledata.GameDataManager;
import com.jjg.game.sampledata.bean.GlobalConfigCfg;
import org.apache.commons.lang3.StringUtils;

/**
 * @author lm
 * @date 2025/10/30 13:56
 */
public interface OrderGenerate extends IGameSysFuncInterface {
    /**
     * 获取对应渠道配置的商品品ID,以及配置价格
     *
     * @param player 玩家id
     * @param req    请求
     * @return 商品id,配置价格
     */
    CommonResult<PreGenerateOrderData> generateOrderDetailInfo(Player player, ReqGenerateOrder req);

    /**
     * 预下单业务校验成功、但订单最终未创建时的补偿回调。
     * 默认无需处理；存在限购预占等状态的业务可覆盖。
     */
    default void onOrderCreationFailed(Player player, ReqGenerateOrder req) {
    }

    /**
     * 获取对应充值类型
     *
     * @return 充值类型
     */
    RechargeType getRechargeType();

    /** 业务预占 token 与真实渠道 SKU 分开保存。 */
    default String channelProductId(Player player, ReqGenerateOrder req) { return req.productId; }


    /**
     * 收到充值的处理
     *
     * @param player 玩家数据
     * @param order  订单数据
     * @return `true` 表示处理成功，`false` 表示处理失败
     */
    boolean onReceivedRecharge(Player player, Order order);

    default boolean onReceivedRecharge(Player player, PreGenerateOrderData preGenerateOrderData) {
        return false;
    }


    /**
     * 处理临时购买逻辑
     *
     * @param corePlayerService
     * @param player
     * @param globalCfgId
     * @param addType
     * @param round
     * @return
     */
    default int tmpOrderHandle(CorePlayerService corePlayerService, Player player, int globalCfgId, AddType addType, int round) {
        if (globalCfgId < 1) {
            return Code.SUCCESS;
        }

        GlobalConfigCfg globalCfg = GameDataManager.getGlobalConfigCfg(globalCfgId);
        if (globalCfg == null) {
            return Code.SUCCESS;
        }

        if (globalCfg.getLongValue() > 0) {
            return corePlayerService.deductDiamond(player.getId(), globalCfg.getLongValue(), addType, "tmp order buy", true).code;
        }

        if (StringUtils.isNotEmpty(globalCfg.getValue())) {
            String[] s = globalCfg.getValue().split("_");
            if (s.length < round) {
                return Code.SAMPLE_ERROR;
            }
            long num = Long.parseLong(s[round]);
            return corePlayerService.deductDiamond(player.getId(), num, addType, "tmp order buy " + round, true).code;
        }
        return Code.SUCCESS;
    }

}
