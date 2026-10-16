package com.yumorie.fireworkshow;
import com.yumorie.fireworkshow.runtime.TimelineEngine;
import com.yumorie.fireworkshow.step.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class TimelineTest {
    @Test void transitionsAndConcurrentClocksAreIndependent() {
        List<String> events=new ArrayList<>();List<TimelineEngine.Stage> stages=new ArrayList<>();
        int[] durations={30,40,40,50};
        for(int i=0;i<4;i++) { final int id=i;stages.add(new TimelineEngine.Stage(durations[i],new ShowStep(){
            public void start(){events.add("start"+id);}public void tick(double p){assertTrue(p>=0&&p<=1);}public void stop(){events.add("stop"+id);}
        })); }
        var a=new TimelineEngine(stages);var b=new TimelineEngine(List.of(new TimelineEngine.Stage(200,p->{})));
        for(int i=0;i<160;i++){a.tick();b.tick();}
        assertTrue(a.done());assertFalse(b.done());assertEquals(List.of("start0","stop0","start1","stop1","start2","stop2","start3","stop3"),events);
        a.stop();assertEquals(8,events.size());b.stop();assertTrue(b.done());
    }
    @Test void stopIsIdempotentAndCleansStartedStageOnError() {
        int[] stopped={0};var t=new TimelineEngine(List.of(new TimelineEngine.Stage(5,new ShowStep(){public void tick(double p){throw new IllegalStateException("failed");}public void stop(){stopped[0]++;}})));
        assertThrows(IllegalStateException.class,t::tick);t.stop();t.stop();assertEquals(1,stopped[0]);
    }
    @Test void deterministicScatterAndEasingEndpoints() {
        assertArrayEquals(Animation.direction(100,4),Animation.direction(100,4));
        assertFalse(Arrays.equals(Animation.direction(100,4),Animation.direction(101,4)));
        for(String name:List.of("linear","ease-in-cubic","ease-out-cubic")){assertEquals(0,Animation.ease(name,0));assertEquals(1,Animation.ease(name,1));assertTrue(Animation.ease(name,0.5001)-Animation.ease(name,0.5)<0.001);}
        for(int i=0;i<100;i++){assertFalse(Animation.visible(123,i,1));assertTrue(Animation.visible(123,i,0));}
    }
    @Test void revealEndsExactlyOnTargetAndCleanupFailureTerminatesClock() {
        assertEquals(0.15,Animation.revealScale(0.15,0));assertEquals(1,Animation.revealScale(0.15,1));
        var timeline=new TimelineEngine(List.of(new TimelineEngine.Stage(1,new ShowStep(){public void tick(double p){}public void stop(){throw new IllegalStateException("cleanup");}})));
        assertThrows(IllegalStateException.class,timeline::tick);assertTrue(timeline.done());timeline.stop();
    }
}
