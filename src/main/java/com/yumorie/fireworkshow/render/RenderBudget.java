package com.yumorie.fireworkshow.render;
public final class RenderBudget {
    private int remaining;
    public void reset(int max) { remaining=max; }
    public boolean take(int count) { if(count<0) throw new IllegalArgumentException("Negative particle count");if(count>remaining) return false; remaining-=count; return true; }
    public void reserve(int count) { remaining=Math.max(0,remaining-count); }
    public int remaining() { return remaining; }
}
