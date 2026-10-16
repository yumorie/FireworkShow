package com.yumorie.fireworkshow.command;
import com.yumorie.fireworkshow.FireworkShow;
import com.yumorie.fireworkshow.runtime.ShowManager;
import com.yumorie.fireworkshow.trigger.FireworkItems;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import java.util.*;
public final class ShowCommand implements CommandExecutor,TabCompleter {
    private final FireworkShow plugin;
    private final ShowManager shows;
    private final FireworkItems items;
    public ShowCommand(FireworkShow plugin,ShowManager shows,FireworkItems items) { this.plugin=plugin;this.shows=shows;this.items=items; }
    public boolean onCommand(CommandSender sender,Command command,String label,String[] args) {
        String sub=args.length==0?"help":args[0].toLowerCase(Locale.ROOT);
        String permission=Set.of("help","list").contains(sub)?"fireworkshow.use":"fireworkshow.admin";
        if(!sender.hasPermission(permission)) { sender.sendMessage("缺少权限: "+permission);return true; }
        try {
            switch(sub) {
                case "help" -> sender.sendMessage("/fws list | preview <showId> | give <player> <showId> [1..64] [rotation=x,y,z] | reload | stop (本人全部预览) | stopall | info");
                case "list" -> sender.sendMessage("演出: "+String.join(", ",new TreeSet<>(shows.snapshot().shows().keySet())));
                case "preview" -> {
                    if(!(sender instanceof Player player)) throw new IllegalArgumentException("preview 只能由玩家执行: /fws preview <showId>");
                    if(args.length!=2) throw new IllegalArgumentException("/fws preview <showId>");
                    var settings=shows.snapshot().settings();
                    var direction=player.getEyeLocation().getDirection();
                    var origin=player.getEyeLocation().add(direction.multiply(settings.previewDistance())).add(0,settings.previewHeight(),0);
                    if(origin.getY()<=origin.getWorld().getMinHeight() || origin.getY()>=origin.getWorld().getMaxHeight() || !origin.getBlock().isPassable()) throw new IllegalArgumentException("预览位置受阻，请面向空旷天空");
                    shows.play(args[1],origin,player,true,com.yumorie.fireworkshow.spatial.Rotation3D.ZERO);sender.sendMessage("开始预览 "+args[1]);
                }
                case "give" -> {
                    if(args.length<3) throw new IllegalArgumentException("/fws give <player> <showId> [1..64] [rotation=x,y,z]");
                    var options=GiveOptions.parse(Arrays.copyOfRange(args,3,args.length));
                    var target=Bukkit.getPlayerExact(args[1]);
                    if(target==null) throw new IllegalArgumentException("玩家不在线");
                    var definition=shows.snapshot().shows().get(args[2]);
                    if(definition==null) throw new IllegalArgumentException("未知演出: "+args[2]);
                    int amount=options.amount();
                    shows.validateRotation(definition,options.rotation());
                    var remaining=target.getInventory().addItem(items.create(definition,amount,options.rotation()));
                    remaining.values().forEach(item->target.getWorld().dropItemNaturally(target.getLocation(),item));
                    sender.sendMessage("已发放 "+amount+" 个烟花；背包剩余物品会掉落在玩家脚下");
                }
                case "reload" -> { plugin.reloadShows();sender.sendMessage("重载成功: "+shows.snapshot().shows().size()+" 个演出，0 失败"); }
                case "stop" -> {
                    if(!(sender instanceof Player player)) throw new IllegalArgumentException("stop 只能由玩家执行；控制台使用 stopall");
                    shows.stop(player.getUniqueId());sender.sendMessage("已停止本人全部预览");
                }
                case "stopall" -> { shows.stop(null);sender.sendMessage("已停止全部演出"); }
                case "info" -> sender.sendMessage("FireworkShow "+plugin.getPluginMeta().getVersion()+" | ParticleLib API ready | active="+shows.active()+" | cached clouds="+shows.snapshot().cachedClouds()+" | shows="+shows.snapshot().shows().size());
                default -> throw new IllegalArgumentException("未知子命令；使用 /fws help");
            }
        } catch(Exception e) { sender.sendMessage("FireworkShow: "+e.getMessage());if(sub.equals("reload")) plugin.getLogger().warning(e.getMessage()); }
        return true;
    }
    public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args) {
        var options=new ArrayList<String>();
        boolean admin=sender.hasPermission("fireworkshow.admin");
        if(args.length==1 && sender.hasPermission("fireworkshow.use")) { options.addAll(List.of("help","list"));if(admin) options.addAll(List.of("preview","give","reload","stop","stopall","info")); }
        if(admin && args.length==2 && args[0].equalsIgnoreCase("preview") || admin && args.length==3 && args[0].equalsIgnoreCase("give")) options.addAll(shows.snapshot().shows().keySet());
        if(admin && args.length==2 && args[0].equalsIgnoreCase("give")) Bukkit.getOnlinePlayers().forEach(p->options.add(p.getName()));
        if(admin && args.length>0 && args[0].equalsIgnoreCase("give") && (args.length==4 || args.length==5 && !args[3].startsWith("rotation="))) {
            options.addAll(List.of("rotation=0,0,0","rotation=90,0,0"));
            if(args.length==4) options.addAll(List.of("1","16","64"));
        }
        String prefix=args.length==0?"":args[args.length-1].toLowerCase(Locale.ROOT);
        return options.stream().filter(s->s.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted().toList();
    }
}
