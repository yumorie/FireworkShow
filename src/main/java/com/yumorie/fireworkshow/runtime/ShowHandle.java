package com.yumorie.fireworkshow.runtime;

/** Main-thread control of one show; clip index is zero-based within the named track. */
public final class ShowHandle {
    private final TimelineEngine engine;
    private final Runnable checkThread;
    ShowHandle(TimelineEngine engine,Runnable checkThread) { this.engine=engine;this.checkThread=checkThread; }
    public void stop() { checkThread.run();engine.stop(); }
    public boolean stopClip(String track,int clipIndex) { checkThread.run();return engine.stopClip(track+"/"+clipIndex); }
    public boolean done() { return engine.done(); }
}
