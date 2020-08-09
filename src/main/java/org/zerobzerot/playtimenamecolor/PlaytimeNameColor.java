package org.zerobzerot.playtimenamecolor;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Statistic;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class PlaytimeNameColor extends JavaPlugin implements Listener {
    static final String pluginPrefix = ChatColor.WHITE + "<" + ChatColor.DARK_GREEN + "NC" + ChatColor.WHITE + "> " + ChatColor.RESET;

    final List<String> defaultColors = Stream.of(ChatColor.GRAY, ChatColor.WHITE, ChatColor.GREEN, ChatColor.BLUE, ChatColor.DARK_PURPLE, ChatColor.GOLD, ChatColor.RED, ChatColor.YELLOW, ChatColor.AQUA).map(ChatColor::toString).collect(Collectors.toList());
    List<String> colors;

    static final ArrayList<String> muted = new ArrayList<>();

    int maxPlaytime;
    int maxJoinDate;

    boolean boldEnabled;
    boolean configModified = false;

    @Override
    public void onEnable() {
        getConfig().addDefault("colors", defaultColors);
        getConfig().addDefault("play-time-h", 384);
        getConfig().addDefault("join-date-d", 365);
        getConfig().addDefault("bold-enabled", true);

        getConfig().options().copyDefaults(true);
        saveConfig();

        colors = getConfig().getStringList("colors");
        maxPlaytime = getConfig().getInt("play-time-h");
        maxJoinDate = getConfig().getInt("join-date-d");
        boldEnabled = getConfig().getBoolean("bold-enabled");

        getServer().getPluginManager().registerEvents(this, this);

        getServer().getScheduler().scheduleSyncRepeatingTask(this, () -> {
            if (configModified) {
                saveConfig();
                configModified = false;
            }
        }, 0L, 20L * 60L);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            if (getConfig().getString(event.getPlayer().getUniqueId().toString()) != null) {
                // If [UUID] entry exists then use this
                player.setDisplayName(getConfig().getString(player.getUniqueId().toString()));
            } else if (getConfig().getString(event.getPlayer().getName()) != null) {
                // If [Name] entry exists then take it and convert it to [UUID]
                player.setDisplayName(getConfig().getString(player.getName()));

                // delete the [Name] entry
                getConfig().set(event.getPlayer().getName(), null);

                // add [UUID] entry
                getConfig().set(event.getPlayer().getUniqueId().toString(), player.getDisplayName());

                configModified = true;
            } else {
                // set default color (first one)
                setColoredPlayerName(event.getPlayer(), colors.get(0));
            }
        });
    }

    public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args) {
        if (cmd.getName().equalsIgnoreCase("nc")) {
            if (args.length == 2 && (sender instanceof ConsoleCommandSender || sender.isOp())) {
                // Console and OP
                Player target = Bukkit.getPlayer(args[0]);

                if (target == null) {
                    sender.sendMessage(pluginPrefix + "Player not found. Type " + ChatColor.GOLD + "/nc" + ChatColor.RESET + " for help.");
                    return true;
                }

                if (SetColorFromCommand(sender, args[1], target)) {
                    sender.sendMessage(pluginPrefix + "The name color of " + target.getDisplayName() + " has been changed.");
                    target.sendMessage(pluginPrefix + "Your name color has been changed: " + target.getDisplayName() + ".");
                } else {
                    sender.sendMessage(pluginPrefix + "Incorrect color specification or insufficient playing time or joining date. Type " + ChatColor.GOLD + "/nc" + ChatColor.RESET + " for help.");
                }
            } else if (args.length == 1 && sender instanceof Player) {
                // Player Command
                if (SetColorFromCommand(sender, args[0], (Player) sender)) {
                    sender.sendMessage(pluginPrefix + "Your name color has been changed: " + ((Player) sender).getDisplayName() + ".");
                } else {
                    sender.sendMessage(pluginPrefix + "Incorrect color specification or insufficient playing time or joining date. Type " + ChatColor.GOLD + "/nc" + ChatColor.RESET + " for help.");
                }
            } else {
                // Show Colors / Help - TODO: Add playtime
                StringBuilder message = new StringBuilder();

                if (sender instanceof Player) {
                    message.append(pluginPrefix).append(((Player) sender).getDisplayName()).append(", you played ").append(Math.round(getPlayTimeInHours((Player) sender))).append(" hours and you joined ").append(Math.round(getJoinDateInDays((Player) sender))).append(" days ago.\n");
                }

                message.append(pluginPrefix).append("Usage: ").append(ChatColor.GOLD).append("/nc").append(ChatColor.RESET);

                if (sender.isOp() || sender instanceof ConsoleCommandSender) {
                    message.append(" [Name]");
                }

                message.append(" <COLOR>");

                int maximumIndex = sender instanceof Player && !sender.isOp() ? getMaximumColorIndex((Player) sender) : colors.size() - 1;

                if (boldEnabled && maximumIndex == colors.size() - 1) {
                    message.append("[-BOLD]");
                }

                message.append(" (Available colors: ");

                for (byte b1 = 0; b1 <= maximumIndex; b1++) {
                    String colorString = colors.get(b1);
                    ChatColor color = ChatColor.getByChar(colorString.charAt(colorString.length() - 1));

                    message.append(color).append(color.name().toLowerCase()).append(ChatColor.RESET).append(", ");
                }

                message = new StringBuilder(message.substring(0, message.length() - 2));
                message.append(")");

                sender.sendMessage(message.toString());
            }
        } else if (cmd.getName().equalsIgnoreCase("mute")) {
            if (args.length == 1) {
                if (!muted.contains(args[0].toLowerCase())) {
                    Player target = Bukkit.getPlayer(args[0]);

                    if (target != null) {
                        muted.add(target.getName().toLowerCase());
                        sender.sendMessage(pluginPrefix + "You have muted " + ChatColor.GOLD + target.getName() + ChatColor.RESET + ".");
                    } else {
                        sender.sendMessage(pluginPrefix + ChatColor.RED + "This Player is not online!");
                    }
                }
            } else {
                sender.sendMessage(pluginPrefix + "Use " + ChatColor.GOLD + "/mute <Player>" + ChatColor.RESET + " to mute a player.");
            }
        } else if (cmd.getName().equalsIgnoreCase("unmute")) {
            if (args.length == 1) {
                if (muted.contains(args[0].toLowerCase())) {
                    muted.remove(args[0].toLowerCase());
                    sender.sendMessage(pluginPrefix + "You have unmuted " + ChatColor.GOLD + args[0].toLowerCase() + ChatColor.RESET + ".");
                }
            } else {
                sender.sendMessage(pluginPrefix + "Use " + ChatColor.GOLD + "/mute <Player>" + ChatColor.RESET + " to mute a player.");
            }
        }

        return true;
    }

    private int getMaximumColorIndex(Player player) {
        double playTime = getPlayTimeInHours(player);
        double joinDate = getJoinDateInDays(player);

        int indexPlayTime = (int) Math.round((colors.size() - 1) - Math.log(maxPlaytime / playTime) / Math.log(2));
        int indexJoinDate = (int) Math.round((colors.size() - 1) - Math.log(maxJoinDate / joinDate) / Math.log(2));

        return Math.max(0, Math.min(colors.size() - 1, Math.min(indexPlayTime, indexJoinDate)));
    }

    private double getJoinDateInDays(Player player) {
        double joinDateS = (int) ((System.currentTimeMillis() - player.getFirstPlayed()) / 1000L);
        return joinDateS / (24d * 60d * 60d);
    }

    private double getPlayTimeInHours(Player player) {
        double playTimeS = player.getStatistic(Statistic.PLAY_ONE_TICK) / 20d;
        return playTimeS / (60d * 60d);
    }

    private void setColoredPlayerName(Player player, String colorString) {
        String name = player.getName();

        player.setDisplayName(colorString + name + ChatColor.RESET);

        getConfig().set(player.getName(), colorString + name + ChatColor.RESET);
        configModified = true;
    }

    public static ChatColor getChatColor(String color) {
        try {
            for (byte b = 0; b < ChatColor.values().length; b++) {
                ChatColor c = ChatColor.values()[b];
                if (c.name().equals(color))
                    return c;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    private boolean SetColorFromCommand(CommandSender sender, String colorString, Player target) {
        ChatColor color;
        boolean bold = false;

        if (colorString.contains("-")) {
            color = getChatColor(colorString.substring(0, colorString.indexOf("-")).toUpperCase());
            // we do not check what is behind the "-" and set bold, if available
            bold = boldEnabled;
        } else {
            color = getChatColor(colorString.toUpperCase());
        }

        if (color == null)
            return false;

        if (!(sender instanceof ConsoleCommandSender) && !sender.isOp()) {
            int colorIndex = getChatColorIndex(color);

            if (colorIndex < 0 || colorIndex > getMaximumColorIndex(target)) {
                return false;
            }

            if (bold && getMaximumColorIndex(target) < colors.size() - 1) {
                bold = false;
            }
        }

        setColoredPlayerName(target, color.toString() + (bold ? ChatColor.BOLD : ""));

        return true;
    }

    private int getChatColorIndex(ChatColor color) {
        return colors.indexOf(color.toString());
    }
}
