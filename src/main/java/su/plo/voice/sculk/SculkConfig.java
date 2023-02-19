package su.plo.voice.sculk;

import com.google.common.collect.Maps;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.NotNull;
import su.plo.config.Config;
import su.plo.config.ConfigField;
import su.plo.config.ConfigValidator;
import su.plo.config.entry.SerializableConfigEntry;

import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

@Config
@Data
@Accessors(fluent = true)
public final class SculkConfig {

    @ConfigField(comment = "Should activate sculks while sneaking")
    private boolean sneakActivation = true;
    @ConfigField(comment = """
            Allowed double values: [-60.0;0.0]
            Default value: -30.0
            """)
    @ConfigValidator( value = ActivationThresholdValidator.class, allowed = "[-60;0]")
    private Double activationThreshold = -30D;

    @ConfigField(comment = """
            Allowed values: https://minecraft.fandom.com/wiki/Sculk_Sensor#Redstone_emission
            Default value: minecraft:eat
            """)
    private String gameEvent = "minecraft:eat";

    @ConfigField(comment = """
            Here you can enable or disable activations. For example:
            groups = true # activates sculks while speaking in groups
            whisper = true # activates sculks while using whisper
            
            Default value will be used if distance in activation is 0
            """)
    private Activations activations = new Activations();

    public static class Activations implements SerializableConfigEntry {

        private final Map<String, Boolean> activations = Maps.newHashMap();

        public Activations() {
            activations.put("default", false);
        }

        public Optional<Boolean> getByActivationName(@NotNull String activationName) {
            return Optional.ofNullable(activations.get(activationName));
        }

        public boolean getDefault() {
            return activations.getOrDefault("default", false);
        }

        @Override
        public void deserialize(Object serialized) {
            Map<String, Object> map = (Map<String, Object>) serialized;
            map.forEach((key, value) -> {
                if (!(value instanceof Boolean)) return;
                activations.put(key, (Boolean) value);
            });
        }

        @Override
        public Object serialize() {
            return activations;
        }
    }

    @NoArgsConstructor
    public static class ActivationThresholdValidator implements Predicate<Object> {

        @Override
        public boolean test(Object o) {
            if (!(o instanceof Double)) return false;
            Double port = (Double) o;
            return port >= -60D && port <= 0D;
        }
    }
}
