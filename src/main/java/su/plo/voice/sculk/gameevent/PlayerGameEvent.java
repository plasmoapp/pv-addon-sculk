package su.plo.voice.sculk.gameevent;

import org.jetbrains.annotations.NotNull;
import su.plo.voice.api.server.player.VoicePlayer;

@FunctionalInterface
public interface PlayerGameEvent {

    void sendEvent(@NotNull VoicePlayer voicePlayer, @NotNull String gameEventName);
}
