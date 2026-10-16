package com.yumorie.fireworkshow.command;

import com.yumorie.fireworkshow.spatial.Rotation3D;

/** Parses only optional give arguments; no Bukkit server needed. */
public record GiveOptions(int amount,Rotation3D rotation) {
    public static GiveOptions parse(String... args) {
        if(args.length>2) throw new IllegalArgumentException("/fws give <player> <showId> [1..64] [rotation=x,y,z]");
        int amount=1,index=0;
        if(args.length>0 && !args[0].startsWith("rotation=")) {
            try { amount=Integer.parseInt(args[0]); } catch(NumberFormatException error) { throw new IllegalArgumentException("Expected amount 1..64 or rotation=x,y,z",error); }
            if(amount<1 || amount>64) throw new IllegalArgumentException("Amount must be 1..64");index++;
        }
        Rotation3D rotation=Rotation3D.ZERO;
        if(index<args.length) {
            if(!args[index].startsWith("rotation=")) throw new IllegalArgumentException("Expected rotation=x,y,z");
            rotation=Rotation3D.parse(args[index++].substring("rotation=".length()));
        }
        if(index!=args.length) throw new IllegalArgumentException("Duplicate or misplaced give argument");
        return new GiveOptions(amount,rotation);
    }
}
