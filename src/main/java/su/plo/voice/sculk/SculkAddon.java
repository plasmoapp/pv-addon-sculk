package su.plo.voice.sculk;

import com.google.common.collect.Maps;
import org.jetbrains.annotations.NotNull;
import su.plo.config.provider.ConfigurationProvider;
import su.plo.config.provider.toml.TomlConfiguration;
import su.plo.voice.api.addon.AddonScope;
import su.plo.voice.api.addon.annotation.Addon;
import su.plo.voice.api.audio.codec.AudioDecoder;
import su.plo.voice.api.audio.codec.CodecException;
import su.plo.voice.api.encryption.EncryptionException;
import su.plo.voice.api.event.EventPriority;
import su.plo.voice.api.event.EventSubscribe;
import su.plo.voice.api.server.PlasmoVoiceServer;
import su.plo.voice.api.server.audio.capture.ServerActivation;
import su.plo.voice.api.server.event.VoiceServerInitializeEvent;
import su.plo.voice.api.server.event.audio.source.PlayerSpeakEvent;
import su.plo.voice.api.server.event.config.VoiceServerConfigLoadedEvent;
import su.plo.voice.api.server.event.connection.UdpClientDisconnectedEvent;
import su.plo.voice.api.server.player.VoiceServerPlayer;
import su.plo.voice.api.util.AudioUtil;
import su.plo.voice.proto.data.audio.codec.CodecInfo;
import su.plo.voice.sculk.gameevent.ModPlayerGameEvent;
import su.plo.voice.sculk.gameevent.PaperPlayerGameEvent;
import su.plo.voice.sculk.gameevent.PlayerGameEvent;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Addon(id = "sculk", scope = AddonScope.SERVER, version = "1.0.0", authors = {"Apehum"})
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
    public void onClientDisconnect(@NotNull UdpClientDisconnectedEvent event) {
        lastActivationByPlayerId.remove(event.getConnection()
                .getPlayer()
                .getInstance()
                .getUUID()
        );
    }

    @EventSubscribe(priority = EventPriority.HIGHEST)
    public void onPlayerSpeak(@NotNull PlayerSpeakEvent event) {
        var activation = voiceServer.getActivationManager()
                .getActivationById(event.getPacket().getActivationId());
        if (activation.isEmpty()) return;

        Optional<Boolean> activationEnabled = config.activations().getByActivationName(activation.get().getName());
        if (event.getPacket().getDistance() == 0) {
            if (!activationEnabled.orElse(config.activations().getDefault())) return;
        } else if (activationEnabled.isPresent() && !activationEnabled.get()) {
            return;
        }

        var player = (VoiceServerPlayer) event.getPlayer();
        if (!config.sneakActivation() && player.getInstance().isSneaking()) return;

        var lastActivation = lastActivationByPlayerId.getOrDefault(player.getInstance().getUUID(), 0L);
        if (System.currentTimeMillis() - lastActivation < 500L) return;

        var packet = event.getPacket();

        short[] decoded;
        try {
            decoded = decode(activation.get(), packet.getData(), packet.isStereo() && activation.get().isStereoSupported());
        } catch (CodecException | EncryptionException e) {
            e.printStackTrace();
            return;
        }

        if (!AudioUtil.containsMinAudioLevel(decoded, config.activationThreshold())) return;

        lastActivationByPlayerId.put(player.getInstance().getUUID(), System.currentTimeMillis());

        playerGameEvent.sendEvent(player, config.gameEvent());
    }

    private short[] decode(@NotNull ServerActivation activation, byte[] data, boolean isStereo)
            throws CodecException, EncryptionException {
        var serverConfig = voiceServer.getConfig();

        var encryption = voiceServer.getDefaultEncryption();
        data = encryption.decrypt(data);

        var encoderInfo = activation.getEncoderInfo()
                .orElseGet(() -> new CodecInfo("opus", Maps.newHashMap()));

        var codecName = encoderInfo.getName() + (isStereo ? "_stereo" : "_mono");

        var decoder = decoders.computeIfAbsent(
                codecName,
                (codec) -> {
                    if (encoderInfo.getName().equals("opus")) {
                        return voiceServer.createOpusDecoder(isStereo);
                    }

                    int sampleRate = serverConfig.voice().sampleRate();
                    return voiceServer.getCodecManager().createDecoder(
                            encoderInfo,
                            sampleRate,
                            isStereo,
                            (sampleRate / 1_000) * 20,
                            serverConfig.voice().mtuSize()
                    );
                }
        );

        decoder.reset();
        return decoder.decode(data);
    }
}
