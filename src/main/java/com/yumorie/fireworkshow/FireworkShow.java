package com.yumorie.fireworkshow;

import com.yumorie.fireworkshow.config.ShowLoader;
import com.yumorie.fireworkshow.integration.particlelib.ParticleLibAdapter;
import com.yumorie.fireworkshow.runtime.ShowManager;
import com.yumorie.fireworkshow.step.StepRegistry;
import com.yumorie.fireworkshow.trigger.*;
import com.yumorie.fireworkshow.command.ShowCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class FireworkShow extends JavaPlugin {
    private ShowManager manager;
    private ShowLoader loader;
    public void reloadShows() throws Exception {
        var next=loader.load(getDataFolder().toPath());manager.replace(next);
        logDiagnostics(next);
    }

    @Override
    public void onEnable() {
        try {
            com.yumorie.fireworkshow.config.BundledResources.install(getDataFolder().toPath());
            var adapter=new ParticleLibAdapter();
            var steps=StepRegistry.defaults(adapter);
            loader=new ShowLoader(steps);
            manager=new ShowManager(this,steps,adapter,loader.load(getDataFolder().toPath()));
            var items=new FireworkItems(this);
            var commands=new ShowCommand(this,manager,items);
            var command=java.util.Objects.requireNonNull(getCommand("fws"));
            command.setExecutor(commands);command.setTabCompleter(commands);
            getServer().getPluginManager().registerEvents(new FireworkTriggerListener(manager,items,this),this);
            getLogger().info("Loaded "+manager.snapshot().shows().size()+" shows; ParticleLib API available");
            logDiagnostics(manager.snapshot());
        } catch(Exception | LinkageError e) {
            getLogger().log(java.util.logging.Level.SEVERE,"FireworkShow startup failed; verify configuration and ParticleLib v0.1-alpha compatibility",e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private void logDiagnostics(ShowLoader.Snapshot snapshot) {
        getLogger().info("Raw pixel preprocessing: "+snapshot.preprocessing());
        snapshot.diagnostics().forEach(getLogger()::warning);
        if(snapshot.settings().unsafeFullFrame()) getLogger().severe("HIGH RISK: experimental.allow-over-budget-full-frames=true; FULL RAW PIXEL frames may exceed normal budget by orders of magnitude, freeze clients/server or disconnect viewers. Disable after diagnosis.");
    }

    @Override
    public void onDisable() {
        if(manager!=null) manager.close();
    }
}
