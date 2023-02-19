package su.plo.voice.sculk.gameevent;

import com.google.common.collect.Maps;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.gameevent.GameEvent;
import org.jetbrains.annotations.NotNull;
import su.plo.voice.api.server.player.VoiceServerPlayer;

import java.util.Map;

public final class ModPlayerGameEvent implements PlayerGameEvent {

    private final Map<String, ResourceLocation> resourceLocations = Maps.newHashMap();

    @Override
    public void sendEvent(@NotNull VoiceServerPlayer voicePlayer, @NotNull String gameEventName) {
        GameEvent gameEvent = Registry.GAME_EVENT.get(resourceLocations.computeIfAbsent(gameEventName, ResourceLocation::new));

        ServerPlayer serverPlayer = voicePlayer.getInstance().getInstance();
        serverPlayer.getServer().execute(() -> serverPlayer.gameEvent(gameEvent));
    }
}
