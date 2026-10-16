package com.yumorie.fireworkshow;

import com.yumorie.fireworkshow.command.GiveOptions;
import com.yumorie.fireworkshow.spatial.Rotation3D;
import com.yumorie.fireworkshow.trigger.*;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;
import java.util.Locale;
import static org.junit.jupiter.api.Assertions.*;

class FireworkLoreTest {
    @Test void defaultRotationAndExistingLoreArePreserved() {
        var data=RocketOptionsTest.container();RocketRotation.write(data,GiveOptions.parse().rotation());
        var lore=FireworkItems.lore("multitrack",RocketRotation.read(data));
        assertEquals(3,lore.size());
        assertEquals(Component.text("FireworkShow: multitrack"),lore.get(0));
        assertEquals(Component.text("Rotation: X=0°, Y=0°, Z=0°"),lore.get(1));
        assertEquals(Component.text("Launch to play the particle show"),lore.get(2));
    }
    @Test void loreUsesPersistedAnglesAndDoesNotRoundFractionalRotation() {
        var rotation=GiveOptions.parse("rotation=90,-12.3456789,15.125").rotation();
        var data=RocketOptionsTest.container();RocketRotation.write(data,rotation);
        assertEquals(Component.text("Rotation: X=90°, Y=-12.3456789°, Z=15.125°"),FireworkItems.lore("heart",RocketRotation.read(data)).get(1));
    }
    @Test void tinyAnglesAndNegativeZeroAreFormattedWithoutRoundingOrScientificNotation() {
        assertEquals(Component.text("Rotation: X=0.0000000001°, Y=0°, Z=-0.00000025°"),FireworkItems.lore("text",new Rotation3D(1e-10,-0.0,-2.5e-7)).get(1));
    }
    @Test void angleFormattingDoesNotDependOnServerLocale() {
        var previous=Locale.getDefault();
        try {
            Locale.setDefault(Locale.FRANCE);
            assertEquals(Component.text("Rotation: X=1.5°, Y=2°, Z=3.25°"),FireworkItems.lore("text",new Rotation3D(1.5,2,3.25)).get(1));
        } finally { Locale.setDefault(previous); }
    }
}
