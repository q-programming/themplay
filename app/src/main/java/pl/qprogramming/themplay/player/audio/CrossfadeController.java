package pl.qprogramming.themplay.player.audio;

import android.os.Handler;
import android.os.Looper;

import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;

import pl.qprogramming.themplay.logger.Logger;

/**
 * A controller class that manages smooth audio crossfade transitions between ExoPlayer instances.
 *
 * <p>This class provides audio crossfading capabilities for music playback applications,
 * enabling seamless transitions between tracks through real-time volume manipulation. It operates
 * by gradually decreasing the volume of the current audio stream while simultaneously increasing
 * the volume of the next stream over a specified duration.</p>
 *
 * <p>The controller is designed to work with ExoPlayer instances that have been configured with
 * VolumeScalingAudioProcessor instances, allowing for precise volume control that functions
 * even during screen casting and audio routing scenarios where traditional MediaPlayer volume
 * controls may be ineffective.</p>
 *
 * <p><strong>Key Features:</strong></p>
 * <ul>
 *   <li>Smooth crossfade transitions between two audio streams</li>
 *   <li>Independent fade-in and fade-out operations</li>
 *   <li>Configurable fade duration with high-resolution timing</li>
 *   <li>Automatic resource cleanup and error handling</li>
 *   <li>Thread-safe operation on the main looper</li>
 *   <li>Callback-based completion notification</li>
 *   <li>Graceful handling of invalid states and edge cases</li>
 * </ul>
 *
 * <p><strong>Technical Implementation:</strong></p>
 * <ul>
 *   <li>Uses a Handler with 50ms intervals for smooth volume transitions</li>
 *   <li>Employs linear volume curves for predictable fade behavior</li>
 *   <li>Automatically manages ExoPlayer lifecycle during transitions</li>
 *   <li>Prevents overlapping fade operations through automatic cancellation</li>
 * </ul>
 *
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * CrossfadeController controller = new CrossfadeController();
 *
 * // Start a crossfade between two players
 * controller.startCrossfade(3000, processorPair, currentPlayer, nextPlayer,
 *     new CrossfadeCallback() {
 *         public void onCrossfadeComplete(ExoPlayer newPlayer, VolumeScalingAudioProcessor processor) {
 *             // Handle successful transition
 *         }
 *         public void onCrossfadeAborted() {
 *             // Handle failed transition
 *         }
 *     });
 * }</pre>
 *
 * @see VolumeScalingAudioProcessor
 * @see ExoPlayerManager
 * @see AudioProcessorManager
 */
@UnstableApi
public class CrossfadeController {
    private static final String TAG = "CrossfadeController";

    /**
     * The interval between volume adjustment steps during fade operations.
     * Set to 50ms to provide smooth transitions while maintaining reasonable CPU usage.
     * This results in 20 volume adjustments per second during fade operations.
     */
    private static final long CROSSFADE_INTERVAL_MS = 50;

    /**
     * Callback interface for crossfade operation completion events.
     *
     * <p>Implementations of this interface receive notifications when crossfade
     * operations complete successfully or are aborted due to errors or invalid states.</p>
     */
    public interface CrossfadeCallback {
        /**
         * Called when a crossfade operation completes successfully.
         *
         * <p>This callback indicates that the volume transition has finished and the
         * old player has been properly cleaned up. The provided player and processor
         * represent the new active audio stream.</p>
         *
         * @param newCurrentPlayer The ExoPlayer instance that is now the active player
         * @param newMainProcessor The VolumeScalingAudioProcessor associated with the new player
         */
        void onCrossfadeComplete(ExoPlayer newCurrentPlayer, VolumeScalingAudioProcessor newMainProcessor);

        /**
         * Called when a crossfade operation is aborted due to an error or invalid state.
         *
         * <p>This callback indicates that the crossfade could not be completed, typically
         * due to invalid processor pairs, null players, or other error conditions.
         * The calling code should handle this situation appropriately, possibly by
         * performing a hard switch between players.</p>
         */
        void onCrossfadeAborted();
    }

    /**
     * Handler for scheduling and executing fade operations on the main thread.
     * All volume adjustments and callbacks are executed through this handler
     * to ensure thread safety and proper UI interaction.
     */
    private final Handler handler;

    /**
     * The currently executing fade operation, if any.
     * This reference is used to cancel ongoing operations when starting new ones
     * or when cleaning up the controller.
     */
    private Runnable currentFadeRunnable;

    /**
     * Constructs a new CrossfadeController.
     *
     * <p>The controller is initialized with a Handler tied to the main looper,
     * ensuring that all fade operations and callbacks execute on the main thread.
     * This is essential for proper interaction with UI components and ExoPlayer instances.</p>
     */
    public CrossfadeController() {
        this.handler = new Handler(Looper.getMainLooper());
    }

    /**
     * Starts a crossfade operation between two ExoPlayer instances.
     *
     * <p>This method initiates a smooth volume transition where the current player's
     * volume is gradually decreased to zero while the next player's volume is
     * simultaneously increased to full volume. The transition occurs over the
     * specified duration using linear volume curves.</p>
     *
     * <p><strong>Operation Details:</strong></p>
     * <ol>
     *   <li>Validates the processor pair and player instances</li>
     *   <li>Cancels any ongoing fade operations</li>
     *   <li>Calculates the number of steps based on duration and interval</li>
     *   <li>Schedules periodic volume adjustments</li>
     *   <li>Handles completion and cleanup automatically</li>
     * </ol>
     *
     * <p><strong>Special Cases:</strong></p>
     * <ul>
     *   <li>Duration ≤ 0: Performs immediate switch without fading</li>
     *   <li>Invalid processors: Aborts operation and invokes error callback</li>
     *   <li>Null players: Aborts operation during execution</li>
     * </ul>
     *
     * @param durationMs The duration of the crossfade in milliseconds. Must be positive for gradual fade.
     * @param processors A valid ProcessorPair containing the current and next audio processors
     * @param currentPlayer The ExoPlayer instance currently playing audio
     * @param nextPlayer The ExoPlayer instance to transition to
     * @param callback Callback to receive completion or error notifications. May be null.
     *
     * @see AudioProcessorManager.ProcessorPair
     * @see CrossfadeCallback
     */
    public void startCrossfade(int durationMs,
                               AudioProcessorManager.ProcessorPair processors,
                               ExoPlayer currentPlayer, ExoPlayer nextPlayer,
                               CrossfadeCallback callback) {

        if (!processors.isValid()) {
            Logger.e(TAG, "Invalid processor pair for crossfade");
            if (callback != null) callback.onCrossfadeAborted();
            return;
        }
        if (durationMs <= 0) {
            // Immediate switch - no gradual fade
            processors.next.setVolumeFactor(1.0f);
            if (callback != null) callback.onCrossfadeComplete(nextPlayer, processors.next);
            return;
        }
        stopCurrentFade();
        final int steps = Math.max(1, durationMs / (int) CROSSFADE_INTERVAL_MS);
        final float volumeStep = 1.0f / steps;
        currentFadeRunnable = new Runnable() {
            int step = 0;
            @Override
            public void run() {
                if (currentPlayer == null || nextPlayer == null || !processors.isValid()) {
                    Logger.w(TAG, "Crossfade aborted: Invalid state");
                    if (callback != null) callback.onCrossfadeAborted();
                    return;
                }
                // Calculate linear volume curves
                float nextVolume = Math.min(1.0f, step * volumeStep);
                float currentVolume = Math.max(0.0f, 1.0f - (step * volumeStep));
                // Apply volume changes
                AudioProcessorManager.safeSetVolume(processors.next, nextVolume);
                AudioProcessorManager.safeSetVolume(processors.current, currentVolume);
                if (step < steps) {
                    step++;
                    handler.postDelayed(this, CROSSFADE_INTERVAL_MS);
                } else {
                    // Crossfade complete - ensure final volumes
                    AudioProcessorManager.safeSetVolume(processors.next, 1.0f);
                    AudioProcessorManager.safeSetVolume(processors.current, 0.0f);
                    if (callback != null) {
                        callback.onCrossfadeComplete(nextPlayer, processors.next);
                    }
                }
            }
        };
        handler.post(currentFadeRunnable);
    }

    /**
     * Starts a fade-out operation on a single ExoPlayer instance.
     *
     * <p>This method gradually reduces the volume of the specified player to zero
     * over the given duration, then automatically releases the player resources.
     * This is typically used when stopping playback or transitioning away from
     * a player without a corresponding fade-in.</p>
     *
     * <p><strong>Operation Sequence:</strong></p>
     * <ol>
     *   <li>Validates the player and processor</li>
     *   <li>Cancels any ongoing fade operations</li>
     *   <li>Gradually reduces volume to zero</li>
     *   <li>Releases the ExoPlayer when fade completes</li>
     *   <li>Invokes the completion callback</li>
     * </ol>
     *
     * <p><strong>Resource Management:</strong></p>
     * <p>The player is automatically stopped and released when the fade-out completes,
     * ensuring proper cleanup of native resources. If the player stops playing during
     * the fade, the operation completes immediately.</p>
     *
     * @param player The ExoPlayer instance to fade out and release
     * @param processor The VolumeScalingAudioProcessor associated with the player
     * @param durationMs The duration of the fade-out in milliseconds
     * @param onComplete Callback invoked when the fade-out completes. May be null.
     *
     * @see ExoPlayerManager#safeReleasePlayer(ExoPlayer)
     */
    public void startFadeOut(ExoPlayer player, VolumeScalingAudioProcessor processor,
                             int durationMs, Runnable onComplete) {
        if (player == null || processor == null) {
            if (onComplete != null) onComplete.run();
            return;
        }
        stopCurrentFade();
        final int steps = Math.max(1, durationMs / (int) CROSSFADE_INTERVAL_MS);
        final float volumeStep = 1.0f / steps;
        currentFadeRunnable = new Runnable() {
            int step = 0;

            @Override
            public void run() {
                if (player == null || processor == null) {
                    if (onComplete != null) onComplete.run();
                    return;
                }
                float volume = Math.max(0.0f, 1.0f - (step * volumeStep));
                AudioProcessorManager.safeSetVolume(processor, volume);

                if (step < steps && player.isPlaying()) {
                    step++;
                    handler.postDelayed(this, CROSSFADE_INTERVAL_MS);
                } else {
                    // Fade complete - release player and notify
                    ExoPlayerManager.safeReleasePlayer(player);
                    if (onComplete != null) onComplete.run();
                }
            }
        };
        handler.post(currentFadeRunnable);
    }

    /**
     * Starts a fade-in operation on a VolumeScalingAudioProcessor.
     *
     * <p>This method gradually increases the volume of the specified processor from
     * zero to full volume over the given duration. This is typically used when
     * starting playback of a new track or resuming from a paused state with a
     * smooth volume transition.</p>
     *
     * <p><strong>Operation Sequence:</strong></p>
     * <ol>
     *   <li>Sets the processor volume to zero</li>
     *   <li>Cancels any ongoing fade operations</li>
     *   <li>Gradually increases volume to full</li>
     *   <li>Ensures final volume is exactly 1.0</li>
     *   <li>Invokes the completion callback</li>
     * </ol>
     *
     * <p><strong>Initial State:</strong></p>
     * <p>The processor volume is explicitly set to 0.0 at the beginning of the
     * operation to ensure a consistent starting point, regardless of the
     * processor's previous state.</p>
     *
     * @param processor The VolumeScalingAudioProcessor to fade in
     * @param durationMs The duration of the fade-in in milliseconds
     * @param onComplete Callback invoked when the fade-in completes. May be null.
     *
     * @see AudioProcessorManager#safeSetVolume(VolumeScalingAudioProcessor, float)
     */
    public void startFadeIn(VolumeScalingAudioProcessor processor, int durationMs, Runnable onComplete) {
        if (processor == null) {
            if (onComplete != null) onComplete.run();
            return;
        }
        stopCurrentFade();
        final int steps = Math.max(1, durationMs / (int) CROSSFADE_INTERVAL_MS);
        final float volumeStep = 1.0f / steps;
        // Start at 0 volume
        AudioProcessorManager.safeSetVolume(processor, 0.0f);
        currentFadeRunnable = new Runnable() {
            int step = 0;
            @Override
            public void run() {
                if (processor == null) {
                    if (onComplete != null) onComplete.run();
                    return;
                }

                float volume = Math.min(1.0f, step * volumeStep);
                AudioProcessorManager.safeSetVolume(processor, volume);

                if (step < steps) {
                    step++;
                    handler.postDelayed(this, CROSSFADE_INTERVAL_MS);
                } else {
                    // Fade in complete - ensure final volume is 1.0
                    AudioProcessorManager.safeSetVolume(processor, 1.0f);
                    if (onComplete != null) onComplete.run();
                }
            }
        };
        handler.post(currentFadeRunnable);
    }

    /**
     * Stops any currently executing fade operation.
     *
     * <p>This method immediately cancels any ongoing fade operation by removing
     * the scheduled callbacks from the handler and clearing the current fade
     * runnable reference. This is automatically called when starting new fade
     * operations to prevent conflicts.</p>
     *
     * <p><strong>Usage:</strong></p>
     * <ul>
     *   <li>Called automatically when starting new fade operations</li>
     *   <li>Can be called manually to interrupt ongoing fades</li>
     *   <li>Safe to call multiple times or when no fade is active</li>
     * </ul>
     *
     * <p><strong>Note:</strong> Stopping a fade operation does not trigger
     * completion callbacks - the operation is simply abandoned in its current state.</p>
     */
    public void stopCurrentFade() {
        if (currentFadeRunnable != null) {
            handler.removeCallbacks(currentFadeRunnable);
            currentFadeRunnable = null;
        }
    }

    /**
     * Performs complete cleanup of the controller resources.
     *
     * <p>This method should be called when the CrossfadeController is no longer
     * needed, typically in the onDestroy() method of a service or activity.
     * It ensures that all scheduled operations are cancelled and no memory
     * leaks occur.</p>
     *
     * <p><strong>Cleanup Operations:</strong></p>
     * <ul>
     *   <li>Stops any currently executing fade operation</li>
     *   <li>Removes all pending callbacks and messages from the handler</li>
     *   <li>Clears internal references to prevent memory leaks</li>
     * </ul>
     *
     * <p><strong>Important:</strong> After calling cleanup(), this controller
     * instance should not be used for further operations. Create a new instance
     * if crossfade functionality is needed again.</p>
     */
    public void cleanup() {
        stopCurrentFade();
        handler.removeCallbacksAndMessages(null);
    }
}
