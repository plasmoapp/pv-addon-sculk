package su.plo.voice.sculk.gameevent;

import org.jetbrains.annotations.NotNull;
import su.plo.voice.api.server.player.VoiceServerPlayer;

@FunctionalInterface
public interface PlayerGameEvent {

    void sendEvent(@NotNull VoiceServerPlayer voicePlayer, @NotNull String gameEventName);
}
