package com.jjg.game.season.pb.struct;
import com.jjg.game.common.pb.ItemInfo;
import com.jjg.game.common.proto.ProtobufMessage;
import java.util.List;

@ProtobufMessage
public class LuckyDrawPrizeInfo {
    public int id;
    public int slot;
    public boolean grand;
    public boolean obtained;
    public List<ItemInfo> items;
}
