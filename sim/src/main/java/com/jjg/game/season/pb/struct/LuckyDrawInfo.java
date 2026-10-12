package com.jjg.game.season.pb.struct;
import com.jjg.game.common.pb.ItemInfo;
import com.jjg.game.common.proto.ProtobufMessage;
import java.util.List;

@ProtobufMessage
public class LuckyDrawInfo {
    public int activityId;
    public int drawType;
    public String seasonKey;
    public String name;
    public String iconPath;
    public long serverTime;
    public long startTime;
    public long endTime;
    public long nextDailyReset;
    public long price;
    public int priceType;
    public String channelProductId;
    public int availableDraws;
    public int totalDrawn;
    public int selectedGrandId;
    public boolean grandObtained;
    public boolean redDot;
    public boolean dailyClaimed;
    public boolean dailyRedDot;
    public boolean pendingPayment;
    public boolean drawPending;
    public boolean skinDrawUnlocked;
    public List<LuckyDrawPrizeInfo> prizes;
    public List<LuckyDrawPrizeInfo> grandChoices;
    public List<ItemInfo> dailyRewards;
}
