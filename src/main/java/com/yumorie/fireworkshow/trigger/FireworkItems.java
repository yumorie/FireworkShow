package com.yumorie.fireworkshow.trigger;
import com.yumorie.fireworkshow.config.ShowDefinition;
import org.bukkit.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
public final class FireworkItems {
    private final NamespacedKey key;
    public FireworkItems(Plugin plugin) { key=new NamespacedKey(plugin,"show_id"); }
    public ItemStack create(ShowDefinition show,int amount) {
        var item=new ItemStack(Material.FIREWORK_ROCKET,amount);
        var meta=(FireworkMeta)item.getItemMeta();
        meta.clearEffects();meta.setPower(1);
        meta.displayName(LegacyComponentSerializer.legacyAmpersand().deserialize(show.displayName()));
        meta.lore(List.of(Component.text("FireworkShow: "+show.id()),Component.text("Launch to play the particle show")));
        meta.getPersistentDataContainer().set(key,PersistentDataType.STRING,show.id());
        item.setItemMeta(meta);return item;
    }
    public String id(ItemStack item) {
        return item.hasItemMeta()?item.getItemMeta().getPersistentDataContainer().get(key,PersistentDataType.STRING):null;
    }
}
