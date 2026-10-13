package com.jjg.game.sim.service;

import com.jjg.game.common.cluster.ClusterClient;
import com.jjg.game.common.config.NodeConfig;
import com.jjg.game.common.curator.NodeType;
import com.jjg.game.common.rpc.ClusterRpcReference;
import com.jjg.game.common.rpc.GameRpcContext;
import com.jjg.game.common.rpc.RpcReqParameterBuilder;
import com.jjg.game.core.listener.SimNoAdsListener;
import com.jjg.game.sim.bridge.ToSimBridge;
import com.jjg.game.sim.dao.SimPlayerGameDao;
import com.jjg.game.sim.data.SimBaseData;
import com.jjg.game.sim.data.SimPlayerContext;
import com.jjg.game.sim.manager.SimPlayerContextRegistry;
import org.springframework.stereotype.Service;

/** 永久免广告权益的SIM数据访问与落库，复用现有节点路由和付费保存队列。 */
@Service
public class SimNoAdsService implements SimNoAdsListener {
    private final SimPlayerContextRegistry contexts;
    private final SimPlayerGameDao dao;
    private final SimAutoSaveService autoSaveService;
    private final SimNodeService nodes;
    private final NodeConfig nodeConfig;
    @ClusterRpcReference
    private ToSimBridge bridge;

    public SimNoAdsService(SimPlayerContextRegistry contexts, SimPlayerGameDao dao,
                           SimAutoSaveService autoSaveService, SimNodeService nodes, NodeConfig nodeConfig) {
        this.contexts = contexts;
        this.dao = dao;
        this.autoSaveService = autoSaveService;
        this.nodes = nodes;
        this.nodeConfig = nodeConfig;
    }

    @Override
    public boolean noads(long playerId) {
        SimPlayerContext context = contexts.getContext(playerId);
        // 付费权益确认落库后才成功返回，非归属节点可直接读此永久字段，无需查询RPC。
        return context == null ? dao.findNoadsById(playerId) : context.getSimBaseData().isNoads();
    }

    @Override
    public boolean enableNoads(long playerId) {
        SimPlayerContext context = contexts.getContext(playerId);
        if (context == null) {
            // Hall登录先创建SIM上下文再补发离线充值；上下文缺失时交由既有充值机制重试。
            if (NodeType.HALL.name().equalsIgnoreCase(nodeConfig.getType())) {
                return false;
            }
            ClusterClient client = nodes.getSimClusterClient(playerId, null);
            if (client == null) {
                return false;
            }
            GameRpcContext rpcContext = GameRpcContext.getContext();
            RpcReqParameterBuilder previous = rpcContext.getReqParameterBuilder();
            try {
                rpcContext.withReqParameterBuilder(RpcReqParameterBuilder.create()
                        .addClusterClient(client).setTryMillisPerClient(1000));
                return bridge.enableNoads(playerId);
            } finally {
                rpcContext.setReqParameterBuilder(previous);
            }
        }
        SimBaseData base = context.getSimBaseData();
        boolean previous = base.isNoads();
        base.setNoads(true);
        try {
            // 与自动落库共用FIFO队列，避免异步旧快照覆盖付费权益。
            autoSaveService.saveAndWait(base);
            return true;
        } catch (RuntimeException e) {
            base.setNoads(previous);
            throw e;
        }
    }
}
