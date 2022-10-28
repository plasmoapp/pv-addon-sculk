package su.plo.voice.sculk;

import com.google.common.collect.Maps;
import org.jetbrains.annotations.NotNull;
import su.plo.config.provider.ConfigurationProvider;
import su.plo.config.provider.toml.TomlConfiguration;
import su.plo.voice.api.addon.annotation.Addon;
import su.plo.voice.api.audio.codec.AudioDecoder;
import su.plo.voice.api.audio.codec.CodecException;
import su.plo.voice.api.encryption.EncryptionException;
import su.plo.voice.api.event.EventPriority;
import su.plo.voice.api.event.EventSubscribe;
import su.plo.voice.api.server.PlasmoVoiceServer;
import su.plo.voice.api.server.audio.source.ServerAudioSource;
import su.plo.voice.api.server.config.ServerConfig;
import su.plo.voice.api.server.event.VoiceServerConfigLoadedEvent;
import su.plo.voice.api.server.event.VoiceServerInitializeEvent;
import su.plo.voice.api.server.event.audio.source.PlayerSpeakEvent;
import su.plo.voice.api.server.event.connection.UdpDisconnectEvent;
import su.plo.voice.api.server.player.VoicePlayer;
import su.plo.voice.api.util.AudioUtil;
import su.plo.voice.api.util.Params;
import su.plo.voice.proto.data.audio.source.SourceInfo;
import su.plo.voice.sculk.gameevent.ModPlayerGameEvent;
import su.plo.voice.sculk.gameevent.PaperPlayerGameEvent;
import su.plo.voice.sculk.gameevent.PlayerGameEvent;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Addon(id = "sculk", scope = Addon.Scope.SERVER, version = "1.0.0", authors = {"Apehum"})
public final class SculkAddon {

    private static final ConfigurationProvider toml = ConfigurationProvider.getProvider(TomlConfiguration.class);

    private final Map<String, AudioDecoder> decoders = Maps.newHashMap();
    private final Map<UUID, Long> lastActivationByPlayerId = Maps.newConcurrentMap();

    private final PlayerGameEvent playerGameEvent;

    private PlasmoVoiceServer voiceServer;
    private SculkConfig config;

    public SculkAddon() {
        PlayerGameEvent playerGameEvent;
        try {
            Class.forName("org.bukkit.entity.Player");
            playerGameEvent = new PaperPlayerGameEvent();
        } catch (ClassNotFoundException e) {
            playerGameEvent = new ModPlayerGameEvent();
        }

        this.playerGameEvent = playerGameEvent;
    }

    @EventSubscribe
    public void onInitialize(@NotNull VoiceServerInitializeEvent event) {
        this.voiceServer = event.getServer();
    }

    @EventSubscribe
    public void onConfigLoaded(@NotNull VoiceServerConfigLoadedEvent event) {
        try {
            File addonFolder = new File(voiceServer.getConfigFolder(), "addons");
            File configFile = new File(addonFolder, "sculk.toml");

            this.config = toml.load(SculkConfig.class, configFile, false);
            toml.save(SculkConfig.class, config, configFile);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load config", e);
        }
    }

    @EventSubscribe
    public void onClientDisconnect(@NotNull UdpDisconnectEvent event) {
        lastActivationByPlayerId.remove(event.getConnection()
                .getPlayer()
                .getInstance()
                .getUUID()
        );
    }

    @EventSubscribe(priority = EventPriority.HIGHEST)
    public void onPlayerSpeak(@NotNull PlayerSpeakEvent event) {
        if (event.isCancelled()) return;

        var activation = voiceServer.getActivationManager()
                .getActivationById(event.getPacket().getActivationId());
        if (activation.isEmpty()) return;

        Optional<Boolean> activationEnabled = config.getActivations().getByActivationName(activation.get().getName());
        if (event.getPacket().getDistance() == 0) {
            if (!activationEnabled.orElse(config.getActivations().getDefault())) return;
        } else if (activationEnabled.isPresent() && !activationEnabled.get()) {
            return;
        }

        var player = event.getPlayer();
        if (!config.isSneakActivation() && player.getInstance().isSneaking()) return;

        var lastActivation = lastActivationByPlayerId.getOrDefault(player.getInstance().getUUID(), 0L);
        if (System.currentTimeMillis() - lastActivation < 500L) return;

        getPlayerSource(event.getPlayer()).ifPresent((source) -> {
            SourceInfo sourceInfo = source.getInfo();

            short[] decoded;
            try {
                decoded = decode(sourceInfo, event.getPacket().getData());
            } catch (CodecException | EncryptionException e) {
                e.printStackTrace();
                return;
            }

            if (!AudioUtil.containsMinAudioLevel(decoded, config.getActivationThreshold())) return;

            lastActivationByPlayerId.put(player.getInstance().getUUID(), System.currentTimeMillis());

            playerGameEvent.sendEvent(player, config.getGameEvent());
        });
    }

    private short[] decode(@NotNull SourceInfo sourceInfo, byte[] data) throws CodecException, EncryptionException {
        var encryption = voiceServer.getEncryptionManager().create(
                "AES/CBC/PKCS5Padding",
                voiceServer.getTcpConnectionManager().getAesEncryptionKey()
        );
        data = encryption.decrypt(data);

        AudioDecoder decoder = null;
        if (sourceInfo.getCodec() != null) {
            var codecName = sourceInfo.getCodec() + (sourceInfo.isStereo() ? "_stereo" : "_mono");

            decoder = decoders.computeIfAbsent(
                    codecName,
                    (codec) -> {
                        ServerConfig serverConfig = voiceServer.getConfig()
                                .orElseThrow(() -> new NullPointerException("Server config is null"));

                        Params params;
                        if (sourceInfo.getCodec().equals("opus")) {
                            params = Params.builder()
                                    .set("mode", serverConfig.getVoice().getOpus().getMode())
                                    .set("bitrate", String.valueOf(serverConfig.getVoice().getOpus().getBitrate()))
                                    .build();
                        } else {
                            params = Params.EMPTY;
                        }

                        int sampleRate = serverConfig.getVoice().getSampleRate();

                        return voiceServer.getCodecManager().createDecoder(
                                sourceInfo.getCodec(),
                                sampleRate,
                                sourceInfo.isStereo(),
                                (sampleRate / 1_000) * 20,
                                serverConfig.getVoice().getMtuSize(),
                                params
                        );
                    }
            );
        }

        if (decoder != null) {
            decoder.reset();
            return decoder.decode(data);
        } else {
            return AudioUtil.bytesToShorts(data);
        }
    }

    private Optional<ServerAudioSource<?>> getPlayerSource(@NotNull VoicePlayer player) {
        return voiceServer.getSourceManager().getSourceById(player.getInstance().getUUID());
    }
}
