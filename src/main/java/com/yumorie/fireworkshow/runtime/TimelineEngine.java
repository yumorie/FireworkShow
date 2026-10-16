package com.yumorie.fireworkshow.runtime;
import com.yumorie.fireworkshow.step.ShowStep;
import java.util.*;
public final class TimelineEngine {
    public record ScheduledClip(String id,int atTick,int duration,ShowStep step) {
        public ScheduledClip {
            Objects.requireNonNull(id);Objects.requireNonNull(step);
            if(atTick<0 || duration<=0 || (long)atTick+duration>24000) throw new IllegalArgumentException("Invalid clip time");
        }
    }
    private static final class State {
        final ScheduledClip clip;
        boolean started,finished;
        State(ScheduledClip clip) { this.clip=clip; }
    }
    private final List<State> clips;
    private int tick;
    private boolean stopped;
    private TimelineEngine(List<ScheduledClip> clips) {
        if(clips.isEmpty()) throw new IllegalArgumentException("Empty timeline");
        if(clips.stream().map(ScheduledClip::id).distinct().count()!=clips.size()) throw new IllegalArgumentException("Duplicate clip id");
        this.clips=clips.stream().map(State::new).toList();
    }
    public static TimelineEngine scheduled(List<ScheduledClip> clips) { return new TimelineEngine(List.copyOf(clips)); }
    public boolean done() { return stopped; }
    public void tick() {
        if(stopped) return;
        try {
            for(var state:clips) {
                var clip=state.clip;
                if(state.finished || tick<clip.atTick) continue;
                if(!state.started) { state.started=true;clip.step.start(); }
                int elapsed=tick-clip.atTick;
                clip.step.tick((double)elapsed/clip.duration);
                if(elapsed+1>=clip.duration) { clip.step.tick(1);finish(state); }
            }
            tick++;
            if(clips.stream().allMatch(c->c.finished)) stopped=true;
        } catch(RuntimeException error) {
            try { stop(); } catch(RuntimeException cleanup) { error.addSuppressed(cleanup); }
            throw error;
        }
    }
    private void finish(State state) {
        if(state.finished) return;
        state.finished=true;
        if(state.started) state.clip.step.stop();
    }
    public boolean stopClip(String id) {
        for(var state:clips) if(state.clip.id.equals(id)) {
            try { finish(state); } finally { if(clips.stream().allMatch(c->c.finished)) stopped=true; }
            return true;
        }
        return false;
    }
    public int activeClips() { return (int)clips.stream().filter(c->c.started&&!c.finished).count(); }
    public void stop() {
        if(stopped) return;
        stopped=true;
        RuntimeException failure=null;
        for(var state:clips) try { finish(state); } catch(RuntimeException error) {
            if(failure==null) failure=error;else failure.addSuppressed(error);
        }
        if(failure!=null) throw failure;
    }
}
