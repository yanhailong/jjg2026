package com.jjg.game.minigame.constant;

import com.jjg.game.common.constant.MessageConst;

public interface MiniGameConstant {
    /**
     * 找老千首次进入的关卡。
     */
    int FIRST_CHEATER_HUNT_LEVEL_ID = 1001;
    /**
     * 开放一键通关所需已通关章节关卡的global配置。
     */
    int QUICK_CLEAR_CHAPTER_GLOBAL_ID = 357;
    /** 赌神搓牌只有一条配置。 */
    int GOD_GAMBLERS_CONFIG_ID = 1001;

    /**
     * 单局状态，与客户端协议中的state保持一致。
     */
    interface RoundState {
        int NONE = 0;
        int PLAYING = 1;
        int CLEARED = 2;
        int FAILED = 3;
        int REWARD_PENDING = 4;
    }

    interface MsgBean {
        int BASE_MSG_PREFIX = MessageConst.MessageTypeDef.SIM_MINI_GAME << MessageConst.MessageCommon.RIGHT_MOVE;

        //进入找老千小游戏
        int REQ_ENTER_CHEAT_HUNT_GAME = BASE_MSG_PREFIX | 0x1;
        int RES_ENTER_CHEAT_HUNT_GAME = BASE_MSG_PREFIX | 0x2;

        //获取关卡信息
        int REQ_CHEAT_HUNT_GAME_INFO = BASE_MSG_PREFIX | 0x3;
        int RES_CHEAT_HUNT_GAME_INFO = BASE_MSG_PREFIX | 0x4;

        //寻找作弊点位
        int REQ_FIND_CHEATER_HUNT_SPOT = BASE_MSG_PREFIX | 0x5;
        int RES_FIND_CHEATER_HUNT_SPOT = BASE_MSG_PREFIX | 0x6;

        //退出找老千当前单局
        int REQ_EXIT_CHEATER_HUNT_GAME = BASE_MSG_PREFIX | 0x7;
        int RES_EXIT_CHEATER_HUNT_GAME = BASE_MSG_PREFIX | 0x8;

        //使用配置道具一键通关
        int REQ_QUICK_CLEAR_CHEATER_HUNT_GAME = BASE_MSG_PREFIX | 0x9;
        int RES_QUICK_CLEAR_CHEATER_HUNT_GAME = BASE_MSG_PREFIX | 0xA;

        //解锁下一关
        int REQ_UNLOCK_NEXT_LEVEL = BASE_MSG_PREFIX | 0xB;
        int RES_UNLOCK_NEXT_LEVEL = BASE_MSG_PREFIX | 0xC;

        //进入赌神小游戏
        int REQ_ENTER_GOD_GAMBLERS_GAME = BASE_MSG_PREFIX | 0xD;
        int RES_ENTER_GOD_GAMBLERS_GAME = BASE_MSG_PREFIX | 0xE;

        //开始赌神小游戏
        int REQ_START_GOD_GAMBLERS_GAME = BASE_MSG_PREFIX | 0xF;
        int RES_START_GOD_GAMBLERS_GAME = BASE_MSG_PREFIX | 0x10;

        //获得自己的牌
        int REQ_GOD_GAMBLERS_SELF_POKER = BASE_MSG_PREFIX | 0x11;
        int RES_GOD_GAMBLERS_SELF_POKER = BASE_MSG_PREFIX | 0x12;

        //确定比拼
        int REQ_GOD_GAMBLERS_CONFIRM_BATTLE = BASE_MSG_PREFIX | 0x13;
        int RES_GOD_GAMBLERS_CONFIRM_BATTLE = BASE_MSG_PREFIX | 0x14;

        //一键比拼
        int REQ_GOD_GAMBLERS_QUICK_BATTLE = BASE_MSG_PREFIX | 0x15;
        int RES_GOD_GAMBLERS_QUICK_BATTLE = BASE_MSG_PREFIX | 0x16;
    }
}
