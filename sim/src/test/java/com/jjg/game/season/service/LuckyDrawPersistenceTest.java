package com.jjg.game.season.service;

import com.jjg.game.season.data.LuckyDrawData;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.convert.*;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import static org.junit.jupiter.api.Assertions.*;

class LuckyDrawPersistenceTest {
    @Test void immutableDefinitionAndIntegerItemKeysRoundTripThroughMongoMapping() throws Exception {
        var context=new MongoMappingContext();context.afterPropertiesSet();
        var converter=new MappingMongoConverter(NoOpDbRefResolver.INSTANCE,context);converter.afterPropertiesSet();
        var state=new LuckyDrawData();state.id="7:loop-1:1";state.definition=LuckyDrawServiceTest.definition(2,1);
        state.obtainedRewards.add(3);state.selectedGrandId=2;
        var document=new Document();converter.write(state,document);
        var restored=converter.read(LuckyDrawData.class,document);
        assertEquals(state.definition,restored.definition);assertTrue(restored.obtainedRewards.contains(3));assertEquals(2,restored.selectedGrandId);
    }
}
