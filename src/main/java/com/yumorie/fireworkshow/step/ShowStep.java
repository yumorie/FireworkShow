package com.yumorie.fireworkshow.step;
public interface ShowStep {
    default void start() {}
    void tick(double progress);
    default void stop() {}
}
