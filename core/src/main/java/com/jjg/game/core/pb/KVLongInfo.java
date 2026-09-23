package com.jjg.game.core.pb;

import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;

@ProtobufMessage
@ProtoDesc("键值对")
public class KVLongInfo {
    public int key;
    public long value;

    public KVLongInfo() {
    }

    public KVLongInfo(int key, long value) {
        this.key = key;
        this.value = value;
    }
}
