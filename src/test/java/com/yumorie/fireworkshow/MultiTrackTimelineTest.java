package com.yumorie.fireworkshow;

import com.yumorie.fireworkshow.runtime.TimelineEngine;
import com.yumorie.fireworkshow.step.ShowStep;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MultiTrackTimelineTest {
    static ShowStep step(String name,List<String> events) { return new ShowStep() {
        public void start() { events.add("start:"+name); }
        public void tick(double p) { assertTrue(p>=0&&p<=1); }
        public void stop() { events.add("stop:"+name); }
    }; }
    @Test void tracksAndClipsOverlapIndependently() {
        var events=new ArrayList<String>();
        var engine=TimelineEngine.scheduled(List.of(
                new TimelineEngine.ScheduledClip("a/0",0,4,step("a0",events)),
                new TimelineEngine.ScheduledClip("a/1",1,5,step("a1",events)),
                new TimelineEngine.ScheduledClip("b/0",0,3,step("b0",events))));
        engine.tick();assertEquals(2,engine.activeClips());
        engine.tick();assertEquals(3,engine.activeClips());
        engine.tick();assertEquals(2,engine.activeClips());
        assertTrue(engine.stopClip("a/0"));assertEquals(1,engine.activeClips());
        engine.stop();engine.stop();
        assertEquals(1,Collections.frequency(events,"stop:a0"));assertEquals(1,Collections.frequency(events,"stop:a1"));assertEquals(1,Collections.frequency(events,"stop:b0"));assertTrue(engine.done());
    }
    @Test void cancelledFutureClipNeverStartsAndClockWaitsForGaps() {
        var events=new ArrayList<String>();
        var engine=TimelineEngine.scheduled(List.of(new TimelineEngine.ScheduledClip("a/0",5,2,step("future",events)),new TimelineEngine.ScheduledClip("a/1",8,1,step("last",events))));
        assertTrue(engine.stopClip("a/0"));
        for(int i=0;i<8;i++) engine.tick();assertTrue(events.isEmpty());assertFalse(engine.done());
        engine.tick();assertEquals(List.of("start:last","stop:last"),events);assertTrue(engine.done());
    }
    @Test void playbackFailureAndCleanupFailureStillReleaseAllOtherClips() {
        var events=new ArrayList<String>();
        var engine=TimelineEngine.scheduled(List.of(
                new TimelineEngine.ScheduledClip("a",0,5,step("a",events)),
                new TimelineEngine.ScheduledClip("b",0,5,new ShowStep(){public void tick(double p){throw new IllegalStateException("playback");}public void stop(){events.add("stop:b");throw new IllegalStateException("cleanup");}})));
        assertThrows(IllegalStateException.class,engine::tick);
        assertTrue(engine.done());assertEquals(1,Collections.frequency(events,"stop:a"));assertEquals(1,Collections.frequency(events,"stop:b"));engine.stop();
    }
}
