package org.zerobzerot.playtimenamecolor;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Statistic;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class PlaytimeNameColor extends JavaPlugin implements Listener {
    static final String pluginPrefix = ChatColor.WHITE + "<" + ChatColor.DARK_GREEN + "NC" + ChatColor.WHITE + "> " + ChatColor.RESET;
    static final ArrayList<String> muted = new ArrayList<>();
    final List<String> defaultColors = Stream.of(ChatColor.GRAY, ChatColor.WHITE, ChatColor.GREEN, ChatColor.BLUE, ChatColor.DARK_PURPLE, ChatColor.GOLD, ChatColor.RED, ChatColor.YELLOW, ChatColor.AQUA).map(ChatColor::toString).collect(Collectors.toList());
    // sanitize color statements [also at the start of the name string from the old config]
    // always uses first match group
    final Pattern regexPattern = Pattern.compile("^(([Â]?§[0-9abcdefklmno])+).*$");
    List<String> colors;
    int maxPlaytime;
    int maxJoinDate;
    boolean boldEnabled;
    boolean configModified = false;

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
                String sanitizedColor = sanitizeAndSaveColoredName(event.getPlayer(), getConfig().getString(player.getUniqueId().toString()), false);

                // set the name
                setColoredName(event.getPlayer(), sanitizedColor);
            } else if (getConfig().getString(event.getPlayer().getName()) != null) {
                // If [Name] entry exists then take it and convert it to [UUID]
                String coloredName = getConfig().getString(player.getName());

                // delete the [Name] entry
                getConfig().set(event.getPlayer().getName(), null);

                // add [UUID] entry and set color
                String sanitizedColor = sanitizeAndSaveColoredName(event.getPlayer(), coloredName, true);

                // set the name
                setColoredName(event.getPlayer(), sanitizedColor);

                configModified = true;
            } else {
                // set default color (first one)
                String sanitizedColor = sanitizeAndSaveColoredName(event.getPlayer(), colors.get(0), true);

                // set the new name
                setColoredName(event.getPlayer(), sanitizedColor);
            }
        });
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (muted.contains(event.getPlayer().getName().toLowerCase())) {
            event.setCancelled(true);
        }
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

                if (setColorFromCommand(sender, args[1], target)) {
                    sender.sendMessage(pluginPrefix + "The name color of " + target.getDisplayName() + " has been changed.");
                    target.sendMessage(pluginPrefix + "Your name color has been changed: " + target.getDisplayName() + ".");
                } else {
                    sender.sendMessage(pluginPrefix + "Incorrect color specification or insufficient playing time or joining date. Type " + ChatColor.GOLD + "/nc" + ChatColor.RESET + " for help.");
                }
            } else if (args.length == 1 && sender instanceof Player) {
                // Player Command
                if (setColorFromCommand(sender, args[0], (Player) sender)) {
                    sender.sendMessage(pluginPrefix + "Your name color has been changed: " + ((Player) sender).getDisplayName() + ".");
                } else {
                    sender.sendMessage(pluginPrefix + "Incorrect color specification or insufficient playing time or joining date. Type " + ChatColor.GOLD + "/nc" + ChatColor.RESET + " for help.");
                }
            } else {
                // Show Colors / Help
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

            return true;
        } else if (cmd.getName().equalsIgnoreCase("mute") && (sender instanceof ConsoleCommandSender || sender.isOp())) {
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

            return true;
        } else if (cmd.getName().equalsIgnoreCase("unmute") && (sender instanceof ConsoleCommandSender || sender.isOp())) {
            if (args.length == 1) {
                if (muted.contains(args[0].toLowerCase())) {
                    muted.remove(args[0].toLowerCase());
                    sender.sendMessage(pluginPrefix + "You have unmuted " + ChatColor.GOLD + args[0].toLowerCase() + ChatColor.RESET + ".");
                }
            } else {
                sender.sendMessage(pluginPrefix + "Use " + ChatColor.GOLD + "/mute <Player>" + ChatColor.RESET + " to mute a player.");
            }

            return true;
        }

        // we did not process the command
        return false;
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

    private String sanitizeAndSaveColoredName(Player player, String colorString, boolean save) {
        // use sanitize regex
        Matcher matcher = regexPattern.matcher(colorString);

        if (matcher.matches()) {
            colorString = matcher.group(1);

            // if we somehow managed to use ansi instead of utf-8
            colorString = colorString.replace("Â", "");
        } else {
            colorString = colors.get(0);
        }

        if (save) {
            getConfig().set(player.getUniqueId().toString(), colorString);
        }

        configModified = true;

        return colorString;
    }

    private void setColoredName(Player player, String colorString) {
        player.setDisplayName(colorString + player.getName() + ChatColor.RESET);
    }

    private boolean setColorFromCommand(CommandSender sender, String colorString, Player target) {
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

        String sanitizedColor = sanitizeAndSaveColoredName(target, color.toString() + (bold ? ChatColor.BOLD : ""), true);

        setColoredName(target, sanitizedColor);

        return true;
    }

    private int getChatColorIndex(ChatColor color) {
        return colors.indexOf(color.toString());
    }
}
