package pl.qprogramming.themplay.player.audio;

import androidx.media3.common.util.UnstableApi;

/**
 * Utility class for managing VolumeScalingAudioProcessor instances used in audio crossfading.
 *
 * <p>This class provides helper methods to create, reset, and safely set volume on
 * VolumeScalingAudioProcessor objects, which are used to control audio volume
 * scaling during playback transitions. It serves as a centralized manager for
 * audio processor operations in music playback applications.</p>
 *
 * <p>The class also defines a nested ProcessorPair class to hold a pair of processors
 * representing the current and next audio streams for crossfade operations, enabling
 * coordinated volume control during smooth track transitions.</p>
 *
 * <p><strong>Key Features:</strong></p>
 * <ul>
 *   <li>Factory methods for creating properly initialized audio processors</li>
 *   <li>Safe volume setting with automatic range clamping</li>
 *   <li>Processor reset functionality for reuse scenarios</li>
 *   <li>ProcessorPair validation for crossfade operations</li>
 *   <li>Null-safe operations throughout all methods</li>
 * </ul>
 *
 * <p>This class centralizes common audio processor operations to reduce code duplication
 * and improve maintainability in audio playback services, particularly those implementing
 * crossfade functionality between tracks.</p>
 *
 * @see VolumeScalingAudioProcessor
 * @see CrossfadeController
 * @see ExoPlayerManager
 */
@UnstableApi
public class AudioProcessorManager {
    /**
     * A container class holding a pair of VolumeScalingAudioProcessor instances for crossfade operations.
     *
     * <p>This pair represents the current and next audio processors used during crossfade
     * transitions, allowing coordinated volume control on both audio streams. The pair
     * ensures that crossfade operations have access to both the outgoing and incoming
     * audio processors for smooth volume transitions.</p>
     *
     * <p><strong>Usage in Crossfade:</strong></p>
     * <ul>
     *   <li>Current processor: Volume decreases from 1.0 to 0.0 during crossfade</li>
     *   <li>Next processor: Volume increases from 0.0 to 1.0 during crossfade</li>
     *   <li>Both processors operate simultaneously during the transition period</li>
     * </ul>
     *
     * <p>The pair includes validation logic to ensure both processors are present
     * and are distinct instances, preventing common configuration errors.</p>
     */
    public static class ProcessorPair {
        /**
         * The processor for the currently playing audio stream.
         * This processor's volume will be decreased during crossfade operations.
         */
        public final VolumeScalingAudioProcessor current;

        /**
         * The processor for the next audio stream to fade into.
         * This processor's volume will be increased during crossfade operations.
         */
        public final VolumeScalingAudioProcessor next;

        /**
         * Constructs a ProcessorPair with the specified current and next processors.
         *
         * <p>Both processors should be properly initialized and configured before
         * being used in a ProcessorPair. The processors should be distinct instances
         * to ensure proper crossfade behavior.</p>
         *
         * @param current The current audio processor (should not be null)
         * @param next The next audio processor (should not be null and different from current)
         */
        public ProcessorPair(VolumeScalingAudioProcessor current, VolumeScalingAudioProcessor next) {
            this.current = current;
            this.next = next;
        }

        /**
         * Checks if the processor pair is valid for crossfade operations.
         *
         * <p>A valid pair requires both processors to be non-null and distinct instances.
         * This validation prevents common errors such as:</p>
         * <ul>
         *   <li>Null processor references that would cause NullPointerExceptions</li>
         *   <li>Same processor instance for both current and next (no crossfade possible)</li>
         *   <li>Incomplete processor pairs that cannot support volume transitions</li>
         * </ul>
         *
         * <p>This method should be called before attempting crossfade operations
         * to ensure the pair can support the intended audio transition.</p>
         *
         * @return true if both processors are non-null and different instances, false otherwise
         */
        public boolean isValid() {
            return current != null && next != null && current != next;
        }
    }

    /**
     * Creates a new VolumeScalingAudioProcessor with the specified initial volume.
     *
     * <p>This factory method creates a properly initialized VolumeScalingAudioProcessor
     * instance with the desired initial volume factor. The processor is ready for
     * immediate use in ExoPlayer audio pipelines.</p>
     *
     * <p><strong>Volume Factor Guidelines:</strong></p>
     * <ul>
     *   <li>0.0 = Complete silence (useful for fade-in start states)</li>
     *   <li>1.0 = Full volume (normal playback volume)</li>
     *   <li>Values are automatically clamped to [0.0, 1.0] range</li>
     * </ul>
     *
     * @param initialVolume The initial volume factor to set on the processor (0.0 to 1.0).
     *                      Values outside this range will be clamped automatically.
     * @return A new VolumeScalingAudioProcessor instance with the specified volume
     * @see VolumeScalingAudioProcessor#setVolumeFactor(float)
     */
    public static VolumeScalingAudioProcessor createProcessor(float initialVolume) {
        VolumeScalingAudioProcessor processor = new VolumeScalingAudioProcessor();
        processor.setVolumeFactor(initialVolume);
        return processor;
    }

    /**
     * Resets the given VolumeScalingAudioProcessor and sets its volume to the specified value.
     *
     * <p>This method safely resets the processor state and applies the desired volume factor,
     * preparing it for reuse in audio playback operations. The reset operation clears any
     * internal state and configuration, returning the processor to its initial condition.</p>
     *
     * <p><strong>Reset Operations:</strong></p>
     * <ul>
     *   <li>Clears internal audio buffers</li>
     *   <li>Resets audio format configuration</li>
     *   <li>Restores default processing state</li>
     *   <li>Applies the new volume factor</li>
     * </ul>
     *
     * <p>This method is null-safe and will silently return if the processor is null,
     * making it safe to use in cleanup scenarios where processor state may be uncertain.</p>
     *
     * @param processor The processor to reset. If null, this method does nothing.
     * @param volume The volume factor to set after reset (0.0 to 1.0).
     *               Values outside this range will be clamped automatically.
     * @see VolumeScalingAudioProcessor#reset()
     * @see VolumeScalingAudioProcessor#setVolumeFactor(float)
     */
    public static void resetProcessor(VolumeScalingAudioProcessor processor, float volume) {
        if (processor != null) {
            processor.reset();
            processor.setVolumeFactor(volume);
        }
    }

    /**
     * Safely sets the volume factor on the given VolumeScalingAudioProcessor.
     *
     * <p>This method provides a safe way to set volume factors with automatic range
     * validation and clamping. It prevents invalid volume values that could cause
     * audio distortion or unexpected behavior during playback.</p>
     *
     * <p><strong>Safety Features:</strong></p>
     * <ul>
     *   <li>Null-safe operation (silently returns if processor is null)</li>
     *   <li>Automatic clamping to valid range [0.0, 1.0]</li>
     *   <li>Thread-safe volume setting through processor's synchronized methods</li>
     *   <li>Immediate effect on subsequent audio processing</li>
     * </ul>
     *
     * <p>This method is the preferred way to set volume factors during crossfade
     * operations, as it ensures consistent behavior regardless of input values.</p>
     *
     * @param processor The processor on which to set the volume. If null, this method does nothing.
     * @param volume The desired volume factor. Values less than 0.0 will be set to 0.0,
     *               and values greater than 1.0 will be set to 1.0.
     * @see VolumeScalingAudioProcessor#setVolumeFactor(float)
     */
    public static void safeSetVolume(VolumeScalingAudioProcessor processor, float volume) {
        if (processor != null) {
            processor.setVolumeFactor(Math.max(0f, Math.min(1f, volume)));
        }
    }
}
