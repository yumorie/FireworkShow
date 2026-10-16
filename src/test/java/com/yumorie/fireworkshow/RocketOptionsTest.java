package com.yumorie.fireworkshow;

import com.yumorie.fireworkshow.command.GiveOptions;
import com.yumorie.fireworkshow.spatial.Rotation3D;
import com.yumorie.fireworkshow.trigger.RocketRotation;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.*;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RocketOptionsTest {
    @Test void defaultGiveAndOptionalAmount() {
        assertEquals(new GiveOptions(1,Rotation3D.ZERO),GiveOptions.parse());
        assertEquals(new GiveOptions(16,Rotation3D.ZERO),GiveOptions.parse("16"));
        assertEquals(new GiveOptions(1,new Rotation3D(90,0,0)),GiveOptions.parse("rotation=90,0,0"));
        assertEquals(new GiveOptions(2,new Rotation3D(10,20,30)),GiveOptions.parse("2","rotation=10,20,30"));
    }
    @Test void invalidOptionsGiveClearErrors() {
        for(String[] args:new String[][]{{"diagonal"},{"horizontal"},{"0"},{"65"},{"rotation=NaN,0,0"},{"rotation=1,2"},{"rotation=0,0,0","2"},{"2","bad"}})
            assertThrows(IllegalArgumentException.class,()->GiveOptions.parse(args));
    }
    record Value(Object type,Object value) {}
    static PersistentDataContainer container() {
        Map<Object,Value> values=new HashMap<>();
        return (PersistentDataContainer)Proxy.newProxyInstance(RocketOptionsTest.class.getClassLoader(),new Class[]{PersistentDataContainer.class},(proxy,method,args)-> {
            return switch(method.getName()) {
                case "set" -> { values.put(args[0],new Value(args[1],args[2]));yield null; }
                case "get" -> { var v=values.get(args[0]);yield v!=null&&v.type.equals(args[1])?v.value:null; }
                case "has" -> values.containsKey(args[0])&&(args.length==1||values.get(args[0]).type.equals(args[1]));
                case "remove" -> { values.remove(args[0]);yield null; }
                default -> throw new UnsupportedOperationException(method.getName());
            };
        });
    }
    @Test void rotationPdcRoundTripsAndKeepsShowId() {
        var data=container();var show=new NamespacedKey("fireworkshow","show_id");data.set(show,PersistentDataType.STRING,"heart");
        assertThrows(IllegalArgumentException.class,()->RocketRotation.read(data));
        var rotation=new Rotation3D(90,-45,12.5);RocketRotation.write(data,rotation);
        assertEquals(rotation,RocketRotation.read(data));assertEquals("heart",data.get(show,PersistentDataType.STRING));
        data.set(RocketRotation.KEY,PersistentDataType.STRING,"garbage");assertThrows(IllegalArgumentException.class,()->RocketRotation.read(data));
        data.set(RocketRotation.KEY,PersistentDataType.INTEGER,7);assertThrows(IllegalArgumentException.class,()->RocketRotation.read(data));
    }
}
