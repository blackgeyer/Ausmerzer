package org.blackgeyer.ausmerzer.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.blackgeyer.ausmerzer.engine.ScheduleManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MessageUtil {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private static final Pattern HEX = Pattern.compile("(?i)&#([a-f0-9]{6})");
    private static final Pattern LEGACY = Pattern.compile("(?i)&([0-9a-fk-or])");

    private static final Map<Character, String> CODES = Map.ofEntries(
            Map.entry('0', "<black>"), Map.entry('1', "<dark_blue>"),
            Map.entry('2', "<dark_green>"), Map.entry('3', "<dark_aqua>"),
            Map.entry('4', "<dark_red>"), Map.entry('5', "<dark_purple>"),
            Map.entry('6', "<gold>"), Map.entry('7', "<gray>"),
            Map.entry('8', "<dark_gray>"), Map.entry('9', "<blue>"),
            Map.entry('a', "<green>"), Map.entry('b', "<aqua>"),
            Map.entry('c', "<red>"), Map.entry('d', "<light_purple>"),
            Map.entry('e', "<yellow>"), Map.entry('f', "<white>"),
            Map.entry('l', "<bold>"), Map.entry('m', "<strikethrough>"),
            Map.entry('n', "<underlined>"), Map.entry('o', "<italic>"),
            Map.entry('k', "<obfuscated>"), Map.entry('r', "<reset>")
    );

    private MessageUtil() {}

    public static String formatLegacyToMiniMessage(String text) {
        if (text == null) return "";

        String result = text.replace('§', '&');
        result = HEX.matcher(result).replaceAll("<#$1>");
        result = LEGACY.matcher(result).replaceAll(m ->
                Matcher.quoteReplacement(CODES.get(Character.toLowerCase(m.group(1).charAt(0)))));
        return result;
    }

    public static Component parse(String text, TagResolver... resolvers) {
        return MINI_MESSAGE.deserialize(formatLegacyToMiniMessage(text), resolvers);
    }

    public static void send(CommandSender sender, String text, TagResolver... resolvers) {
        if (sender == null) return;
        Component message = parse(text, resolvers);

        if (sender instanceof Player player) {
            ScheduleManager.get().runAtEntity(player, () -> player.sendMessage(message));
        } else {
            ScheduleManager.get().runGlobal(() -> sender.sendMessage(message));
        }
    }
}