package com.yumorie.fireworkshow.step;
import org.bukkit.configuration.ConfigurationSection;
import com.yumorie.fireworkshow.runtime.ShowContext;
public interface StepFactory {
    default boolean usesImage() { return false; }
    default void validateSpatial(ConfigurationSection config,com.yumorie.fireworkshow.spatial.SpatialContext spatial) {}
    void validate(ConfigurationSection config);
    ShowStep create(ConfigurationSection config,ShowContext context);
}
