package com.yumorie.fireworkshow.runtime;
import com.yumorie.fireworkshow.step.ShowStep;
import java.util.List;
public final class TimelineEngine {
    public record Stage(int duration, ShowStep step) {
        public Stage { if(duration<=0) throw new IllegalArgumentException("duration-ticks must be positive"); }
    }
    private final List<Stage> stages;
    private int index, elapsed;
    private boolean started, stopped;
    public TimelineEngine(List<Stage> stages) { this.stages=List.copyOf(stages); if(stages.isEmpty()) throw new IllegalArgumentException("Empty timeline"); }
    public boolean done() { return stopped; }
    public void tick() {
        if(stopped) return;
        var stage=stages.get(index);
        if(!started) { started=true; stage.step.start(); }
        stage.step.tick((double)elapsed/stage.duration);
        elapsed++;
        if(elapsed>=stage.duration) {
            stage.step.tick(1);
            started=false;
            try { stage.step.stop(); } catch(RuntimeException error) { stopped=true;throw error; }
            if(++index==stages.size()) stopped=true;
            elapsed=0;
        }
    }
    public void stop() {
        if(stopped) return;
        stopped=true;
        if(started) { started=false; stages.get(index).step.stop(); }
    }
}
