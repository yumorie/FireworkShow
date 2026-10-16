package com.yumorie.fireworkshow.trigger;

import com.yumorie.fireworkshow.spatial.Rotation3D;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/** Rotation metadata written by the item factory. */
public final class RocketRotation {
    public static final NamespacedKey KEY=new NamespacedKey("fireworkshow","rotation");
    private RocketRotation() {}
    public static void write(PersistentDataContainer data,Rotation3D rotation) {
        data.set(KEY,PersistentDataType.STRING,rotation.encode());
    }
    public static Rotation3D read(PersistentDataContainer data) {
        String text=data.get(KEY,PersistentDataType.STRING);
        if(text==null) throw new IllegalArgumentException("fireworkshow:rotation PDC has invalid type");
        return Rotation3D.parse(text);
    }
}
