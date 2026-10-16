package com.yumorie.fireworkshow.trigger;
import com.yumorie.fireworkshow.runtime.ShowManager;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.plugin.Plugin;
public final class FireworkTriggerListener implements Listener {
    private final ShowManager shows;
    private final FireworkItems items;
    private final Plugin plugin;
    public FireworkTriggerListener(ShowManager shows,FireworkItems items,Plugin plugin) { this.shows=shows;this.items=items;this.plugin=plugin; }
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void explode(FireworkExplodeEvent event) {
        var rocket=event.getEntity();String id=items.id(rocket.getItem());
        if(id==null) return;
        if(!shows.snapshot().shows().containsKey(id)) { plugin.getLogger().warning("Tagged rocket references missing show: "+id+"; vanilla fallback");return; }
        try {
            Player focus=rocket.getShooter() instanceof Player p && p.isOnline()?p:null;
            shows.play(id,rocket.getLocation().clone(),focus,false,items.rotation(rocket.getItem()));
            event.setCancelled(true);
        } catch(RuntimeException e) { plugin.getLogger().warning("Rocket show "+id+" rejected; vanilla fallback: "+e.getMessage()); }
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void damage(EntityDamageByEntityEvent event) {
        if(event.getDamager() instanceof Firework rocket && items.id(rocket.getItem())!=null) event.setCancelled(true);
    }
}
