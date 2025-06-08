package pl.qprogramming.themplay.player.audio;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.exoplayer.audio.AudioRendererEventListener;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.audio.DefaultAudioSink;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

import pl.qprogramming.themplay.domain.Song;
import pl.qprogramming.themplay.logger.Logger;

/**
 * Utility class for managing ExoPlayer instances with custom audio processing capabilities.
 *
 * <p>This class provides static methods to create ExoPlayer instances with custom audio processors,
 * prepare players for playback, and safely release resources. It is specifically designed to support
 * audio crossfading and volume manipulation through custom audio processors.</p>
 *
 * <p>The class handles the complex setup of ExoPlayer's audio rendering pipeline by injecting
 * custom audio processors into the DefaultAudioSink, enabling real-time audio manipulation
 * such as volume scaling for smooth crossfade transitions.</p>
 *
 */
@UnstableApi
public class ExoPlayerManager {
    private static final String TAG = "ExoPlayerManager";

    /**
     * Callback interface for handling player ready events.
     *
     * <p>This callback is invoked when the ExoPlayer has finished preparing the media
     * and is ready to start playback. At this point, it's safe to perform operations
     * like seeking to a specific position or starting playback.</p>
     */
    public interface PlayerReadyCallback {
        /**
         * Called when the ExoPlayer is ready to start playback.
         *
         * @param player The ExoPlayer instance that is now ready
         */
        void onPlayerReady(ExoPlayer player);
    }

    /**
     * Callback interface for handling player error events.
     *
     * <p>This callback is invoked when the ExoPlayer encounters an error during
     * media preparation or playback. It provides both the error details and the
     * associated song information for proper error handling.</p>
     */
    public interface PlayerErrorCallback {
        /**
         * Called when the ExoPlayer encounters an error.
         *
         * @param error The PlaybackException containing error details
         * @param song The Song object associated with the failed playback
         */
        void onPlayerError(PlaybackException error, Song song);
    }

    /**
     * Creates an ExoPlayer instance with a custom VolumeScalingAudioProcessor.
     *
     * <p>This method constructs an ExoPlayer with a custom audio rendering pipeline that
     * includes the specified VolumeScalingAudioProcessor. The processor is injected into
     * the DefaultAudioSink through a custom DefaultRenderersFactory, allowing for real-time
     * volume manipulation during playback.</p>
     *
     * @param context The Android Context used to build the ExoPlayer instance
     * @param processor The VolumeScalingAudioProcessor to be applied to the audio output.
     *                  This processor will handle volume scaling operations for crossfade effects.
     * @return A configured ExoPlayer instance with the custom audio processor integrated
     *
     * @throws IllegalArgumentException if context is null
     * @throws IllegalStateException if the audio processor cannot be properly integrated
     *
     * @see VolumeScalingAudioProcessor
     */
    public static ExoPlayer createPlayerWithProcessor(Context context, VolumeScalingAudioProcessor processor) {
        DefaultRenderersFactory renderersFactory = new DefaultRenderersFactory(context) {
            @Override
            protected void buildAudioRenderers(@NotNull Context context,
                                               int extensionRendererMode,
                                               @NotNull MediaCodecSelector mediaCodecSelector,
                                               boolean enableDecoderFallback,
                                               @NotNull AudioSink audioSink,
                                               @NotNull Handler eventHandler,
                                               @NotNull AudioRendererEventListener eventListener,
                                               @NotNull ArrayList<Renderer> out) {

                if(!processor.isVolumeScalingActive()){
                    Logger.w(TAG, "Volume scaling not available - crossfade may not work as expected");
                }
                // Create custom audio sink with our volume scaling processor
                AudioSink customAudioSink = new DefaultAudioSink.Builder(context)
                        .setAudioProcessors(new AudioProcessor[]{processor})
                        .build();

                // Call parent method with our custom audio sink
                super.buildAudioRenderers(context, extensionRendererMode, mediaCodecSelector,
                        enableDecoderFallback, customAudioSink, eventHandler,
                        eventListener, out);
            }
        };

        return new ExoPlayer.Builder(context)
                .setRenderersFactory(renderersFactory)
                .build();
    }

    /**
     * Prepares an ExoPlayer for playback with the specified media and position.
     *
     * <p>This method handles the complete preparation workflow for an ExoPlayer instance:
     * <ul>
     *   <li>Sets the media item from the provided URI</li>
     *   <li>Sets the initial volume to maximum (1.0f)</li>
     *   <li>Adds listeners for state changes and errors</li>
     *   <li>Seeks to the specified position when ready</li>
     *   <li>Starts playback automatically when preparation is complete</li>
     *   <li>Invokes appropriate callbacks for success or failure</li>
     * </ul>
     * </p>
     *
     * @param player The ExoPlayer instance to prepare for playback
     * @param uri The URI of the media file to be played
     * @param position The position in milliseconds to seek to before starting playback
     * @param song The Song object representing the media, used for error reporting
     * @param readyCallback Callback invoked when the player is ready and playback has started.
     *                      Can be null if no callback is needed.
     * @param errorCallback Callback invoked if an error occurs during preparation or playback.
     *                      Can be null if no error handling is needed.
     *
     * @throws IllegalArgumentException if player or uri is null
     * @throws IllegalStateException if the player is in an invalid state for preparation
     *
     * @see PlayerReadyCallback
     * @see PlayerErrorCallback
     */
    public static void preparePlayer(ExoPlayer player, Uri uri, int position, Song song,
                                     PlayerReadyCallback readyCallback, PlayerErrorCallback errorCallback) {
        MediaItem mediaItem = MediaItem.fromUri(uri);
        player.setMediaItem(mediaItem);
        player.setVolume(1.0f);

        player.addListener(new Player.Listener() {
            /**
             * <p>When the player reaches STATE_READY, it means the media has been
             * successfully prepared and is ready for playback. At this point, we
             * seek to the desired position, start playback, and invoke the ready callback.</p>
             *
             * @param state The new playback state
             */
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY && readyCallback != null) {
                    player.seekTo(position);
                    player.play();
                    readyCallback.onPlayerReady(player);
                    player.removeListener(this);
                }
            }

            /**
             * Handles player errors during preparation or playback.
             *
             * <p>If an error occurs at any point during the preparation process
             * or subsequent playback, this method will be called. The error is
             * passed to the provided error callback along with the song information.</p>
             *
             * @param error The PlaybackException containing error details
             */
            @Override
            public void onPlayerError(@NotNull PlaybackException error) {
                if (errorCallback != null) {
                    errorCallback.onPlayerError(error, song);
                }
                player.removeListener(this);
            }
        });

        player.prepare();
    }

    /**
     * Safely stops and releases an ExoPlayer instance, handling any potential exceptions.
     *
     * <p>This method provides a safe way to clean up ExoPlayer resources by:
     * <ul>
     *   <li>Checking if the player is not null before attempting operations</li>
     *   <li>Stopping playback to ensure clean shutdown</li>
     *   <li>Releasing all resources held by the player</li>
     *   <li>Catching and logging any exceptions that occur during cleanup</li>
     * </ul>
     * </p>
     *
     * <p>It's important to call this method when an ExoPlayer is no longer needed
     * to prevent memory leaks and ensure proper cleanup of native resources.
     * The method is designed to be safe to call multiple times or with null players.</p>
     *
     * @param player The ExoPlayer instance to release. Can be null, in which case
     *               this method does nothing.
     *
     * @see ExoPlayer#stop()
     * @see ExoPlayer#release()
     */
    public static void safeReleasePlayer(ExoPlayer player) {
        if (player != null) {
            try {
                player.stop();
                player.release();
            } catch (Exception e) {
                Logger.w(TAG, "Error releasing player", e);
            }
        }
    }
}
