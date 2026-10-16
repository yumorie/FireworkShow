package com.yumorie.fireworkshow.render;
public final class RenderBudget {
    private int remaining;
    private int capacity,pendingCost;
    private record Accent(int cost,Runnable emission) {}
    private final java.util.List<Accent> accents=new java.util.ArrayList<>();
    public void reset(int max) { remaining=max;capacity=max;pendingCost=0;accents.clear(); }
    public boolean take(int count) { if(count<0) throw new IllegalArgumentException("Negative particle count");if(count>remaining) return false; remaining-=count; return true; }
    public void reserve(int count) { remaining=Math.max(0,remaining-count); }
    public int remaining() { return remaining; }
    public void deferAccent(int cost,Runnable emission) {
        if(cost<=0) throw new IllegalArgumentException("Accent cost must be positive");
        if(cost>capacity-pendingCost) return;
        accents.add(new Accent(cost,emission));pendingCost+=cost;
    }
    public void flushAccents() {
        try { for(var accent:accents) { if(!take(accent.cost)) break;accent.emission.run(); } }
        finally { accents.clear();pendingCost=0; }
    }
}
