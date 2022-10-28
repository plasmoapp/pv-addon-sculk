package su.plo.voice.sculk.gameevent;

import com.google.common.collect.Maps;
import org.bukkit.Bukkit;
import org.bukkit.GameEvent;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import su.plo.voice.api.server.player.VoicePlayer;

import java.util.Map;

public final class PaperPlayerGameEvent implements PlayerGameEvent {

    private final Map<String, NamespacedKey> namespacedKeys = Maps.newHashMap();

    @Override
    public void sendEvent(@NotNull VoicePlayer voicePlayer, @NotNull String gameEventName) {
        Player player = voicePlayer.getInstance().getInstance();

        Bukkit.getScheduler().runTask(
                Bukkit.getServer().getPluginManager().getPlugin("PlasmoVoice"), // waytoodank
                () -> player.getWorld().sendGameEvent(player, getGameEvent(gameEventName), player.getLocation().toVector())
        );

        // uwu
    }

    private GameEvent getGameEvent(@NotNull String gameEventName) {
        NamespacedKey gameEventKey = namespacedKeys.get(gameEventName);
        if (gameEventKey == null) {
            String[] split = gameEventName.split(":");
            if (split.length == 2) {
                gameEventKey = new NamespacedKey(split[0], split[1]);
            } else {
                gameEventKey = new NamespacedKey("minecraft", gameEventName);
            }
            namespacedKeys.put(gameEventName, gameEventKey);
        }

        GameEvent gameEvent = GameEvent.getByKey(gameEventKey);
        if (gameEvent == null) gameEvent = GameEvent.STEP;

        return gameEvent;
    }
}
