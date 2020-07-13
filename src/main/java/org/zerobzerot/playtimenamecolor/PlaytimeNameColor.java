package org.zerobzerot.playtimenamecolor;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class PlaytimeNameColor extends JavaPlugin implements Listener {
    final List<String> defaultColors = Stream.of(ChatColor.GRAY, ChatColor.WHITE, ChatColor.GREEN, ChatColor.BLUE, ChatColor.DARK_PURPLE, ChatColor.GOLD, ChatColor.RED, ChatColor.YELLOW, ChatColor.AQUA).map(ChatColor::toString).collect(Collectors.toList());
    List<String> colors;

    int maxPlaytime;
    int maxJoindate;

    @Override
    public void onEnable() {
        getConfig().addDefault("colors", defaultColors);
        getConfig().addDefault("playtime", 8);
        getConfig().addDefault("joindate", 365);

        getConfig().options().copyDefaults(true);
        saveConfig();

        colors = getConfig().getStringList("colors");
        maxPlaytime = getConfig().getInt("playtime");
        maxJoindate = getConfig().getInt("joindate");

        getServer().getPluginManager().registerEvents(this, this);

        new BukkitRunnable() {
            @Override
            public void run() {
                getServer().getOnlinePlayers().forEach(player -> setNameColor(player));
            }
        }.runTaskTimer(this, 600, 6000);

    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            setNameColor(event.getPlayer());
        });
    }

    private void setNameColor(Player player) {

        double joindate = (int) ((System.currentTimeMillis() - player.getFirstPlayed()) / 1000L);
        double playtime = player.getStatistic(Statistic.PLAY_ONE_TICK) / 20d;

        int indexPlaytime = (int) Math.floor((colors.size() - 1) * Math.min(1d, playtime / (maxPlaytime * 60d * 60d)));
        int indexJoindate = (int) Math.floor((colors.size() - 1) * Math.min(1d, joindate / (maxJoindate * 24d * 60d * 60d)));

        int index = Math.min(indexJoindate, indexPlaytime);

        player.setDisplayName(colors.get(index) + player.getName() + ChatColor.RESET);

    }

}
