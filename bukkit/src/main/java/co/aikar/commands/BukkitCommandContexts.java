/*
 * Copyright (c) 2016-2017 Daniel Ennis (Aikar) - MIT License
 *
 *  Permission is hereby granted, free of charge, to any person obtaining
 *  a copy of this software and associated documentation files (the
 *  "Software"), to deal in the Software without restriction, including
 *  without limitation the rights to use, copy, modify, merge, publish,
 *  distribute, sublicense, and/or sell copies of the Software, and to
 *  permit persons to whom the Software is furnished to do so, subject to
 *  the following conditions:
 *
 *  The above copyright notice and this permission notice shall be
 *  included in all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 *  EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 *  MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 *  NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 *  LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 *  OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 *  WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package co.aikar.commands;

import co.aikar.commands.annotation.Optional;
import co.aikar.commands.contexts.OnlinePlayer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SuppressWarnings("WeakerAccess")
public class BukkitCommandContexts extends CommandContexts<BukkitCommandExecutionContext> {

    public BukkitCommandContexts(BukkitCommandManager manager) {
        super(manager);

        registerContext(OnlinePlayer.class, c -> getOnlinePlayer(c.getIssuer(), c.popFirstArg(), c.hasAnnotation(Optional.class)));
        registerContext(OnlinePlayer[].class, (c) ->  {
            CommandSender sender = c.getSender();
            final String input = c.popFirstArg();
            boolean allowMissing = c.hasFlag("allowmissing");
            Set<OnlinePlayer> players = new HashSet<>();
            Pattern split = ACFPatterns.COMMA;
            String splitter = c.getFlagValue("splitter", (String) null);
            if (splitter != null) {
                split = Pattern.compile(Pattern.quote(splitter));
            }
            for (String lookup : split.split(input)) {
                OnlinePlayer player = getOnlinePlayer(c.getIssuer(), lookup, allowMissing);
                if (player != null) {
                    players.add(player);
                }
            }
            if (players.isEmpty() && !c.hasFlag("allowempty")) {
                ACFBukkitUtil.sendMsg(sender, "&cCould not find any players by " + input);
                throw new InvalidCommandArgument(false);
            }
            return players.toArray(new OnlinePlayer[players.size()]);
        });
        registerSenderAwareContext(World.class, (c) -> {
            String firstArg = c.getFirstArg();
            World world = firstArg != null ? Bukkit.getWorld(firstArg) : null;
            if (world != null) {
                c.popFirstArg();
            }
            if (world == null && c.getSender() instanceof Player) {
                world = ((Entity) c.getSender()).getWorld();
            }
            if (world == null) {
                throw new InvalidCommandArgument(BukkitMessageKeys.INVALID_WORLD);
            }
            return world;
        });
        registerSenderAwareContext(CommandSender.class, BukkitCommandExecutionContext::getSender);
        registerSenderAwareContext(Player.class, (c) -> {
            Player player = c.getSender() instanceof Player ? (Player) c.getSender() : null;
            if (player == null && !c.hasAnnotation(Optional.class)) {
                throw new InvalidCommandArgument(MessageKeys.NOT_ALLOWED_ON_CONSOLE, false);
            }
            PlayerInventory inventory = player != null ? player.getInventory() : null;
            if (inventory != null && c.hasFlag("itemheld") && !ACFBukkitUtil.isValidItem(inventory.getItem(inventory.getHeldItemSlot()))) {
                throw new InvalidCommandArgument(BukkitMessageKeys.YOU_MUST_BE_HOLDING_ITEM, false);
            }
            return player;
        });
        registerContext(ChatColor.class, c -> {
            String first = c.popFirstArg();
            Stream<ChatColor> colors = Stream.of(ChatColor.values());
            if (c.hasFlag("colorsonly")) {
                colors = colors.filter(color -> color.ordinal() <= 0xF);
            }
            String filter = c.getFlagValue("filter", (String) null);
            if (filter != null) {
                filter = ACFUtil.simplifyString(filter);
                String finalFilter = filter;
                colors = colors.filter(color -> finalFilter.equals(ACFUtil.simplifyString(color.name())));
            }

            ChatColor match = ACFUtil.simpleMatch(ChatColor.class, first);
            if (match == null) {
                String valid = colors
                        .map(color -> ChatColor.YELLOW + ACFUtil.simplifyString(color.name()))
                        .collect(Collectors.joining("<c2>,</c2> "));

                throw new InvalidCommandArgument(MessageKeys.PLEASE_SPECIFY_ONE_OF, "{valid}", valid);
            }
            return match;
        });
        Pattern versionPattern = Pattern.compile("\\(MC: (\\d)\\.(\\d+)\\.?.*?\\)");
        Matcher matcher = versionPattern.matcher(Bukkit.getVersion());
        if (matcher.find()) {
            int mcMajorVersion = ACFUtil.parseInt(matcher.toMatchResult().group(1), 0);
            int mcMinorVersion = ACFUtil.parseInt(matcher.toMatchResult().group(2), 0);
            manager.log(LogLevel.INFO, "Minecraft Version: " + mcMajorVersion + "." + mcMinorVersion);
            if (mcMajorVersion >= 1 && mcMinorVersion >= 12) {
                BukkitCommandContexts_1_12.register(this);
            }
        }
    }

    @Nullable
    OnlinePlayer getOnlinePlayer(BukkitCommandIssuer issuer, String lookup, boolean allowMissing) throws InvalidCommandArgument {
        CommandSender sender = issuer.getIssuer();
        Player player = ACFBukkitUtil.findPlayerSmart(issuer, lookup);
        if (player == null) {
            if (allowMissing) {
                return null;
            }
            this.manager.sendMessage(sender, MessageType.ERROR, MessageKeys.COULD_NOT_FIND_PLAYER, "{search}", lookup);
            throw new InvalidCommandArgument(false);
        }
        return new OnlinePlayer(player);
    }
}
