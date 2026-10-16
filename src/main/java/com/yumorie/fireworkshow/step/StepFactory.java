package com.yumorie.fireworkshow.step;
import org.bukkit.configuration.ConfigurationSection;
import com.yumorie.fireworkshow.runtime.ShowContext;
public interface StepFactory {
    void validate(ConfigurationSection config);
    ShowStep create(ConfigurationSection config,ShowContext context);
}
