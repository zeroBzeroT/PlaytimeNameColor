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
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.zerobzerot.donationapi.DonationAPI;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// TODO: remove colors and decorations that are not accessible for a specific player on login
// TODO: softdepend does not work

public final class PlaytimeNameColor extends JavaPlugin implements Listener {
    public static final List<String> defaultColors = Stream.of(ChatColor.GRAY, ChatColor.WHITE, ChatColor.GREEN, ChatColor.BLUE, ChatColor.DARK_PURPLE, ChatColor.GOLD, ChatColor.RED, ChatColor.YELLOW, ChatColor.AQUA, ChatColor.DARK_RED).map(ChatColor::toString).collect(Collectors.toList());
    public static final List<String> defaultDonnorColors = Stream.of(ChatColor.DARK_AQUA, ChatColor.DARK_BLUE, ChatColor.DARK_GRAY, ChatColor.DARK_GREEN, ChatColor.LIGHT_PURPLE).map(ChatColor::toString).collect(Collectors.toList());

    // TODO: add list for text decorations

    static final String pluginPrefix = ChatColor.WHITE + "<" + ChatColor.DARK_GREEN + "NC" + ChatColor.WHITE + "> " + ChatColor.RESET;
    static final ArrayList<String> muted = new ArrayList<>();

    // sanitize color statements [also at the start of the name string from the old config]
    // always uses first match group
    final Pattern regexPattern = Pattern.compile("^(([Â]?§[0-9abcdefklmno])+).*$");
    List<String> colors;
    List<String> colorsDonnors;
    int maxPlaytime;
    int maxJoinDate;
    boolean boldEnabled;
    int boldIndex;
    boolean itemColorEnabled;
    boolean configModified = false;
    Plugin donationAPI = null;

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
        getConfig().addDefault("colorsDonnors", defaultDonnorColors);

        getConfig().addDefault("play-time-h", 384 * 2);
        getConfig().addDefault("join-date-d", 365 * 2);
        getConfig().addDefault("bold-enabled", true);
        getConfig().addDefault("bold-index", 8);
        getConfig().addDefault("item-color-enabled", false);

        getConfig().options().copyDefaults(true);
        saveConfig();

        colors = getConfig().getStringList("colors");
        colorsDonnors = getConfig().getStringList("colorsDonnors");
        maxPlaytime = getConfig().getInt("play-time-h");
        maxJoinDate = getConfig().getInt("join-date-d");
        boldEnabled = getConfig().getBoolean("bold-enabled");
        boldIndex = getConfig().getInt("bold-index");
        itemColorEnabled = getConfig().getBoolean("item-color-enabled");

        donationAPI = getServer().getPluginManager().getPlugin("DonationAPI");

        getServer().getPluginManager().registerEvents(this, this);

        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
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
                String sanitizedColor = sanitizeColoredName(getConfig().getString(player.getUniqueId().toString()));

                // set the name
                setColoredName(event.getPlayer(), sanitizedColor);
            } else if (getConfig().getString(event.getPlayer().getName()) != null) {
                // If [Name] entry exists then take it and convert it to [UUID]
                String coloredName = getConfig().getString(player.getName());

                // delete the [Name] entry
                getConfig().set(event.getPlayer().getName(), null);

                // add [UUID] entry and set color
                String sanitizedColor = sanitizeColoredName(coloredName);
                saveColoredName(event.getPlayer(), sanitizedColor);

                // set the name
                setColoredName(event.getPlayer(), sanitizedColor);

                configModified = true;
            } else {
                // set default color (first one)
                String sanitizedColor = sanitizeColoredName(colors.get(0));
                saveColoredName(event.getPlayer(), sanitizedColor);

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

                AbstractMap.SimpleEntry<Boolean, String> colorResult = getColorFromCommand(sender, args[1], target);

                if (colorResult.getKey()) {
                    String sanitizedColor = colorResult.getValue();

                    saveColoredName(target, sanitizedColor);
                    setColoredName(target, sanitizedColor);

                    sender.sendMessage(pluginPrefix + "The name color of " + target.getDisplayName() + " has been changed.");
                    target.sendMessage(pluginPrefix + "Your name color has been changed: " + target.getDisplayName() + ".");
                } else {
                    sender.sendMessage(pluginPrefix + "Incorrect color specification or insufficient playing time or joining date. Type " + ChatColor.GOLD + "/nc" + ChatColor.RESET + " for help.");
                }
            } else if (args.length == 1 && sender instanceof Player) {
                // Player Command
                AbstractMap.SimpleEntry<Boolean, String> colorResult = getColorFromCommand(sender, args[0], (Player) sender);

                if (colorResult.getKey()) {
                    String sanitizedColor = colorResult.getValue();

                    saveColoredName((Player) sender, sanitizedColor);
                    setColoredName((Player) sender, sanitizedColor);

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

                boolean isActiveDonner = sender instanceof Player && isActiveDonner(((Player) sender).getUniqueId());

                if (isActiveDonner) {
                    message.append(pluginPrefix).append(ChatColor.AQUA).append(ChatColor.BOLD).append("Thank you for your donation!\n").append(ChatColor.RESET);
                }

                message.append(pluginPrefix).append("Usage: ").append(ChatColor.GOLD).append("/nc").append(ChatColor.RESET);

                if (sender.isOp() || sender instanceof ConsoleCommandSender) {
                    message.append(" [Name]");
                }

                message.append(" <COLOR>");

                int maximumIndex = sender instanceof Player && !sender.isOp() ? getMaximumColorIndex((Player) sender) : colors.size() - 1;

                if (boldEnabled && (maximumIndex >= boldIndex || isActiveDonner)) {
                    // TODO: add decorations
                    message.append("[-BOLD]");
                }

                message.append(" (Available colors: ");

                // Playtime / Joindate Colors
                for (byte b1 = 0; b1 <= maximumIndex; b1++) {
                    String colorString = colors.get(b1);
                    ChatColor color = ChatColor.getByChar(colorString.charAt(colorString.length() - 1));

                    message.append(color).append(color.name().toLowerCase()).append(ChatColor.RESET).append(", ");
                }

                // Show Donnor Colors
                if (sender.isOp() || sender instanceof ConsoleCommandSender || isActiveDonner) {
                    for (String colorString : colorsDonnors) {
                        ChatColor color = ChatColor.getByChar(colorString.charAt(colorString.length() - 1));

                        message.append(color).append(color.name().toLowerCase()).append(ChatColor.RESET).append(", ");
                    }
                }

                message = new StringBuilder(message.substring(0, message.length() - 2));
                message.append(")");

                sender.sendMessage(message.toString());
            }

            return true;
        } else if (itemColorEnabled && cmd.getName().equalsIgnoreCase("ic")) {
            if (args.length == 1 && sender instanceof Player) {
                // Player Command
                AbstractMap.SimpleEntry<Boolean, String> colorResult = getColorFromCommand(sender, args[0], (Player) sender);

                if (colorResult.getKey()) {
                    String sanitizedColor = colorResult.getValue();

                    if (setItemColor((Player) sender, sanitizedColor)) {
                        sender.sendMessage(pluginPrefix + sanitizedColor + "The color of your item has been changed.");
                    } else {
                        sender.sendMessage(pluginPrefix + "There was an error when changing the color of your item.");
                    }
                } else {
                    sender.sendMessage(pluginPrefix + "Incorrect color specification or insufficient playing time or joining date. Type " + ChatColor.GOLD + "/nc" + ChatColor.RESET + " for help.");
                }
            } else {
                // Help
                sender.sendMessage(pluginPrefix + "Type " + ChatColor.GOLD + "/nc" + ChatColor.RESET + " to see all available colors.");
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

    private boolean setItemColor(Player player, String colorString) {
        if (player == null || player.getInventory() == null || player.getInventory().getItemInMainHand() == null)
            return false;

        ItemStack item = player.getInventory().getItemInMainHand();
        String name = ChatColor.stripColor(item.getI18NDisplayName());

        ItemMeta itemMeta = item.getItemMeta();

        if (itemMeta == null)
            return false;

        itemMeta.setDisplayName(colorString + name + ChatColor.RESET);
        item.setItemMeta(itemMeta);

        return true;
    }

    public int getMaximumColorIndex(Player player) {
        double playTime = getPlayTimeInHours(player);
        double joinDate = getJoinDateInDays(player);

        int indexPlayTime = (int) Math.round(colors.size() - 1 - Math.log(Math.ceil(maxPlaytime / playTime)) / Math.log(2));
        int indexJoinDate = (int) Math.round(colors.size() - 1 - Math.log(Math.ceil(maxJoinDate / joinDate)) / Math.log(2));

        return Math.max(0, Math.min(colors.size() - 1, Math.min(indexPlayTime, indexJoinDate)));
    }

    private double getJoinDateInDays(Player player) {
        double joinDateS = (int) ((System.currentTimeMillis() - player.getFirstPlayed()) / 1000L);
        return joinDateS / (24d * 60d * 60d);
    }

    private double getPlayTimeInHours(Player player) {
        Statistic stat;

        try {
            // 1.12.2
            stat = Statistic.valueOf("PLAY_ONE_TICK");
        } catch (IllegalArgumentException ex) {
            // 1.16.5
            // Name is misleading, actually records ticks played.
            stat = Statistic.valueOf("PLAY_ONE_MINUTE");
        }

        double playTimeS = player.getStatistic(stat) / 20d;

        return playTimeS / (60d * 60d);
    }

    private String sanitizeColoredName(String colorString) {
        // use sanitize regex
        Matcher matcher = regexPattern.matcher(colorString);

        if (matcher.matches()) {
            colorString = matcher.group(1);

            // if we somehow managed to use ansi instead of utf-8
            colorString = colorString.replace("Â", "");
        } else {
            colorString = colors.get(0);
        }

        return colorString;
    }

    private void saveColoredName(Player player, String colorString) {
        if (colorString.equals(colors.get(0))) {
            getConfig().set(player.getUniqueId().toString(), null);
        }
        else {
            getConfig().set(player.getUniqueId().toString(), colorString);
        }

        configModified = true;
    }

    private void setColoredName(Player player, String colorString) {
        player.setDisplayName(colorString + player.getName() + ChatColor.RESET);
    }

    private AbstractMap.SimpleEntry<Boolean, String> getColorFromCommand(CommandSender sender, String colorString, Player target) {
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
            return new AbstractMap.SimpleEntry<>(false, "");

        boolean isActiveDonner = sender instanceof Player && isActiveDonner(((Player) sender).getUniqueId());

        // TODO: make this more convenient
        // donnor
        if (isActiveDonner) {
            // check if color is a normal or donnor color (to remove decorations)
            if (!colors.contains(color.toString()) && !colorsDonnors.contains(color.toString())) {
                return new AbstractMap.SimpleEntry<>(false, "");
            }
        }
        // normal name color
        else if (!(sender instanceof ConsoleCommandSender) && !sender.isOp()) {
            int colorIndex = getChatColorIndex(color);

            if (colorIndex < 0 || colorIndex > getMaximumColorIndex(target)) {
                return new AbstractMap.SimpleEntry<>(false, "");
            }

            if (bold && getMaximumColorIndex(target) < boldIndex) {
                bold = false;
            }
        }

        String sanitizedColor = sanitizeColoredName(color.toString() + (bold ? ChatColor.BOLD : ""));

        return new AbstractMap.SimpleEntry<>(true, sanitizedColor);
    }

    public boolean isActiveDonner(UUID uuid) {
        if (donationAPI != null && donationAPI.isEnabled()) {
            return DonationAPI.Instance.isActiveDonner(uuid);
        }

        return false;
    }

    public boolean hasEverDonated(UUID uuid) {
        if (donationAPI != null && donationAPI.isEnabled()) {
            return DonationAPI.Instance.hasEverDonated(uuid);
        }

        return false;
    }

    private int getChatColorIndex(ChatColor color) {
        return colors.indexOf(color.toString());
    }
}
