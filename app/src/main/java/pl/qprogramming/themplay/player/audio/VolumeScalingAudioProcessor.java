package pl.qprogramming.themplay.player.audio;

import androidx.media3.common.C;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.util.UnstableApi;

import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.Locale;

import pl.qprogramming.themplay.logger.Logger;

/**
 * A custom AudioProcessor implementation that applies real-time volume scaling to PCM audio data.
 *
 * <p>This processor is specifically designed for audio crossfading applications where smooth
 * volume transitions are required. It operates on 16-bit PCM audio data and applies a volume
 * multiplication factor to each audio sample in real-time during playback.</p>
 *
 * <p>The processor is thread-safe for volume factor modifications and integrates seamlessly
 * with ExoPlayer's audio processing pipeline. It's particularly useful for:</p>
 *
 * <p><strong>Technical Details:</strong></p>
 * <ul>
 *   <li>Supports only 16-bit PCM audio (ENCODING_PCM_16BIT) for volume scaling</li>
 *   <li>Falls back to pass-through mode for unsupported formats</li>
 *   <li>Preserves original sample rate and channel configuration</li>
 *   <li>Applies volume scaling by multiplying each sample by the volume factor</li>
 *   <li>Volume factor is clamped between 0.0 (silence) and 1.0 (original volume)</li>
 *   <li>Gracefully handles configuration errors without crashing the application</li>
 * </ul>
 *
 * <p><strong>Pass-Through Mode:</strong></p>
 * <p>When the input audio format is unsupported (non-16-bit PCM), the processor automatically
 * switches to pass-through mode where audio data is copied directly without modification.
 * This ensures the application continues to function even with unexpected audio formats.</p>
 *
 * @see AudioProcessor
 * @see ExoPlayerManager
 * @see CrossfadeController
 */
@UnstableApi
public class VolumeScalingAudioProcessor implements AudioProcessor {

    private static final String TAG = "VolumeProcessor";

    /**
     * Flag indicating whether the processor is operating in pass-through mode.
     * In pass-through mode, audio data is not modified due to unsupported format.
     * This prevents application crashes when encountering non-16-bit PCM audio.
     */
    private boolean isPassThroughMode = false;

    /**
     * The current volume multiplication factor applied to audio samples.
     * Range: 0.0 (silence) to 1.0 (original volume).
     * This value is thread-safe and can be modified during playback.
     */
    private float currentVolumeFactor = 1.0f;

    /**
     * The audio format of the input stream. Set during configuration.
     * Used to validate compatibility and determine processing mode.
     */
    private AudioFormat inputAudioFormat = AudioFormat.NOT_SET;

    /**
     * The audio format of the output stream. May be modified from input format
     * if corrections are needed for invalid parameters.
     */
    private AudioFormat outputAudioFormat = AudioFormat.NOT_SET;

    /**
     * Internal buffer for storing processed audio data before output.
     * Allocated on-demand based on input buffer size to optimize memory usage.
     */
    private ByteBuffer internalOutputBuffer = EMPTY_BUFFER;

    /**
     * Flag indicating whether the input stream has ended.
     * Used to determine when processing is complete and cleanup can occur.
     */
    private boolean inputStreamEnded;

    /**
     * Sets the volume scaling factor to be applied to audio samples.
     *
     * <p>This method is thread-safe and can be called from any thread while audio
     * is being processed. The new volume factor will be applied to subsequent
     * audio samples. The factor is automatically clamped to the valid range.</p>
     *
     * <p><strong>Volume Factor Guidelines:</strong></p>
     * <ul>
     *   <li>0.0 = Complete silence</li>
     *   <li>0.5 = Half volume</li>
     *   <li>1.0 = Original volume (no change)</li>
     *   <li>Values outside 0.0-1.0 are automatically clamped</li>
     * </ul>
     *
     * <p><strong>Note:</strong> In pass-through mode, this method has no effect
     * on the audio output, but the value is still stored for potential future use.</p>
     *
     * @param volumeFactor The volume multiplication factor. Will be clamped to [0.0, 1.0].
     * @see #getVolumeFactor()
     * @see #isVolumeScalingActive()
     */
    public synchronized void setVolumeFactor(float volumeFactor) {
        this.currentVolumeFactor = Math.max(0.0f, Math.min(volumeFactor, 1.0f));
    }

    /**
     * Gets the current volume scaling factor.
     *
     * <p>This method is thread-safe and returns the volume factor that is currently
     * being applied to audio samples (or would be applied if not in pass-through mode).</p>
     *
     * @return The current volume multiplication factor in the range [0.0, 1.0]
     * @see #setVolumeFactor(float)
     * @see #isVolumeScalingActive()
     */
    public synchronized float getVolumeFactor() {
        return currentVolumeFactor;
    }

    /**
     * Configures the audio processor with the input audio format.
     *
     * <p>This method validates the input format and attempts to configure the processor
     * for optimal audio processing. If the input format is unsupported, the processor
     * will operate in pass-through mode (no volume scaling) rather than crashing the
     * application.</p>
     *
     * <p><strong>Supported Formats:</strong></p>
     * <ul>
     *   <li>Encoding: PCM_16BIT (preferred for volume scaling)</li>
     *   <li>Sample Rate: Any positive value</li>
     *   <li>Channels: Any positive count</li>
     * </ul>
     *
     * <p><strong>Fallback Behavior:</strong></p>
     * <ul>
     *   <li>Unsupported encoding: Pass-through mode (no volume scaling)</li>
     *   <li>Invalid sample rate/channels: Use default values and log warnings</li>
     *   <li>Null input: Return NOT_SET format and disable processing</li>
     * </ul>
     *
     * <p>This method never throws exceptions, ensuring application stability
     * even with unexpected or malformed audio formats.</p>
     *
     * @param inputAudioFormat The format of the input audio stream
     * @return The output audio format (may be modified for compatibility)
     * @see #isVolumeScalingActive()
     * @see C#ENCODING_PCM_16BIT
     */
    @Override
    public AudioFormat configure(AudioFormat inputAudioFormat) {
        // Handle null input gracefully
        if (inputAudioFormat == null) {
            Logger.e(TAG, "configure() called with null AudioFormat - disabling processor");
            this.inputAudioFormat = AudioFormat.NOT_SET;
            this.outputAudioFormat = AudioFormat.NOT_SET;
            this.isPassThroughMode = true;
            return AudioFormat.NOT_SET;
        }
        // Log input format for debugging
        Logger.d(TAG, "Configuring processor with format: " + audioFormatToString(inputAudioFormat));
        boolean hasErrors = false;
        // Start with input format values
        int outputSampleRate = inputAudioFormat.sampleRate;
        int outputChannelCount = inputAudioFormat.channelCount;
        int outputEncoding = inputAudioFormat.encoding;
        // Check encoding - critical for volume processing
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            Logger.w(TAG, String.format(Locale.US,
                    "Unsupported audio encoding: %d (expected PCM_16BIT: %d). " +
                            "Volume scaling will be disabled - processor will operate in pass-through mode.",
                    inputAudioFormat.encoding, C.ENCODING_PCM_16BIT));
            this.isPassThroughMode = true;
            hasErrors = true;
        } else {
            this.isPassThroughMode = false;
        }
        if (inputAudioFormat.sampleRate <= 0) {
            Logger.e(TAG, String.format(Locale.US,
                    "Invalid sample rate: %d. Using default 44100 Hz.",
                    inputAudioFormat.sampleRate));
            outputSampleRate = 44100;
            hasErrors = true;
        }
        if (inputAudioFormat.channelCount <= 0) {
            Logger.e(TAG, String.format(Locale.US,
                    "Invalid channel count: %d. Using default stereo (2 channels).",
                    inputAudioFormat.channelCount));
            outputChannelCount = 2;
            hasErrors = true;
        }
        this.inputAudioFormat = inputAudioFormat;
        if (hasErrors) {
            this.outputAudioFormat = new AudioFormat(outputSampleRate, outputChannelCount, outputEncoding);
        } else {
            this.outputAudioFormat = inputAudioFormat;
        }
        if (hasErrors) {
            Logger.w(TAG, "Processor configured with errors. Input: " +
                    audioFormatToString(inputAudioFormat) + ", Output: " +
                    audioFormatToString(outputAudioFormat) +
                    ", Pass-through mode: " + isPassThroughMode);
        } else {
            Logger.d(TAG, "Processor successfully configured for volume scaling");
        }
        return this.outputAudioFormat;
    }

    /**
     * Indicates whether this audio processor is currently active.
     *
     * @return true if the processor has been configured and is ready to process audio
     */
    @Override
    public boolean isActive() {
        return inputAudioFormat != AudioFormat.NOT_SET;
    }

    /**
     * Indicates whether volume scaling is currently functional.
     *
     * <p>This method returns true only when the processor is both active and
     * configured for volume scaling (not in pass-through mode). Use this method
     * to determine if crossfade operations will work as expected.</p>
     *
     * @return true if volume scaling is active, false if in pass-through mode or inactive
     */
    public boolean isVolumeScalingActive() {
        return isActive() && !isPassThroughMode;
    }

    /**
     * Queues input audio data for processing.
     *
     * <p>This method performs the core volume scaling operation by:</p>
     * <ol>
     *   <li>Checking if pass-through mode is active (for unsupported formats)</li>
     *   <li>Converting the input ByteBuffer to a ShortBuffer for 16-bit sample access</li>
     *   <li>Multiplying each audio sample by the current volume factor</li>
     *   <li>Storing the processed samples in the internal output buffer</li>
     *   <li>Handling buffer allocation and management efficiently</li>
     * </ol>
     *
     * <p><strong>Processing Details:</strong></p>
     * <ul>
     *   <li>Pass-through mode: Direct copy without modification</li>
     *   <li>Volume scaling mode: Each 16-bit PCM sample is multiplied by the volume factor</li>
     *   <li>Results are cast back to short (16-bit) with potential clipping</li>
     *   <li>All channels are processed equally (no channel-specific scaling)</li>
     *   <li>Buffer allocation is optimized to reuse existing buffers when possible</li>
     * </ul>
     *
     * <p><strong>Buffer Management:</strong></p>
     * <p>The method automatically manages internal buffer allocation, growing the buffer
     * as needed and reusing existing buffers to minimize garbage collection overhead.</p>
     *
     * @param inputBuffer The buffer containing input audio data to process.
     *                    Must contain PCM samples in native byte order.
     * @see #getOutput()
     * @see #isVolumeScalingActive()
     */
    @Override
    public void queueInput(@NotNull ByteBuffer inputBuffer) {
        if (inputStreamEnded && (internalOutputBuffer == EMPTY_BUFFER || !internalOutputBuffer.hasRemaining())) {
            return;
        }
        if (!inputBuffer.hasRemaining()) {
            return;
        }
        // Pass-through mode: copy input directly to output without processing
        if (isPassThroughMode) {
            int inputSize = inputBuffer.remaining();
            if (internalOutputBuffer == EMPTY_BUFFER || internalOutputBuffer.capacity() < inputSize) {
                internalOutputBuffer = ByteBuffer.allocate(inputSize).order(ByteOrder.nativeOrder());
            } else {
                internalOutputBuffer.clear();
            }
            // Direct copy without volume scaling
            internalOutputBuffer.put(inputBuffer);
            internalOutputBuffer.flip();
        } else {
            // Normal volume scaling processing
            int inputSize = inputBuffer.remaining();
            if (internalOutputBuffer == EMPTY_BUFFER || internalOutputBuffer.capacity() < inputSize) {
                internalOutputBuffer = ByteBuffer.allocate(inputSize).order(ByteOrder.nativeOrder());
            } else {
                internalOutputBuffer.clear();
            }
            ShortBuffer inputShortBuffer = inputBuffer.asShortBuffer();
            ShortBuffer outputShortBuffer = internalOutputBuffer.asShortBuffer();
            float volume = getVolumeFactor();
            while (inputShortBuffer.hasRemaining()) {
                short pcmSample = inputShortBuffer.get();
                short processedSample = (short) (pcmSample * volume);
                outputShortBuffer.put(processedSample);
            }
            internalOutputBuffer.position(outputShortBuffer.position() * 2); // Each short is 2 bytes
            inputBuffer.position(inputBuffer.limit());
        }
    }

    /**
     * Retrieves the processed audio output buffer.
     *
     * <p>This method returns the internal buffer containing processed audio data
     * and resets the internal state for the next processing cycle. The returned
     * buffer is ready for consumption by the audio sink.</p>
     *
     * <p><strong>Buffer Lifecycle:</strong></p>
     * <ol>
     *   <li>The internal buffer is flipped to prepare for reading</li>
     *   <li>The buffer reference is returned to the caller</li>
     *   <li>The internal buffer reference is reset to EMPTY_BUFFER</li>
     *   <li>The caller is responsible for consuming the returned buffer</li>
     * </ol>
     *
     * <p><strong>Important:</strong> The returned buffer should be consumed
     * immediately as the internal reference is reset after this call.</p>
     *
     * @return A ByteBuffer containing processed audio data, ready for playback
     * @see #queueInput(ByteBuffer)
     */
    @Override
    @NotNull
    public ByteBuffer getOutput() {
        ByteBuffer bufferToReturn = internalOutputBuffer;
        bufferToReturn.flip();
        if (bufferToReturn != EMPTY_BUFFER) {
            internalOutputBuffer = EMPTY_BUFFER;
        }
        return bufferToReturn;
    }

    /**
     * Signals that the input stream has ended.
     */
    @Override
    public void queueEndOfStream() {
        inputStreamEnded = true;
    }

    /**
     * Indicates whether the processor has finished processing all data.
     *
     * <p>The processor is considered ended when the input stream has ended
     * and all internal buffers have been consumed. This signals that the
     * audio processing pipeline can be safely terminated.</p>
     *
     * @return true if processing is complete and no more output is available
     */
    @Override
    public boolean isEnded() {
        return inputStreamEnded && (internalOutputBuffer == EMPTY_BUFFER || !internalOutputBuffer.hasRemaining());
    }

    /**
     * Flushes the processor state, clearing any buffered data.
     * <p>This method resets the internal buffer state and stream-ended flag,
     * preparing the processor for a new stream or seeking operation. Any
     * pending audio data in internal buffers is discarded.</p>
     * <p>The volume factor and configuration are preserved across flush operations.</p>
     */
    @Override
    public void flush() {
        internalOutputBuffer = EMPTY_BUFFER;
        inputStreamEnded = false;
    }

    /**
     * Completely resets the processor to its initial state.
     *
     * <p>This method performs a complete reset of the processor, including:</p>
     * <ul>
     *   <li>Clearing all internal buffers (via flush)</li>
     *   <li>Resetting audio format configuration</li>
     *   <li>Restoring volume factor to 1.0 (original volume)</li>
     *   <li>Disabling pass-through mode</li>
     * </ul>
     *
     * <p>After calling this method, the processor must be reconfigured
     * before it can process audio data again.</p>
     */
    @Override
    public void reset() {
        flush();
        inputAudioFormat = AudioFormat.NOT_SET;
        outputAudioFormat = AudioFormat.NOT_SET;
        currentVolumeFactor = 1.0f;
        isPassThroughMode = false;
    }

    /**
     * Converts an AudioFormat to a string representation for debugging.
     *
     * <p>This utility method provides a readable representation of an AudioFormat
     * object, including encoding, sample rate, channel count, and bytes per frame.
     * It handles special cases like null and NOT_SET formats gracefully.</p>
     *
     * @param format The AudioFormat to convert to string. Can be null.
     * @return A string representation of the audio format
     */
    public static String audioFormatToString(AudioFormat format) {
        if (format == null) {
            return "null";
        }
        if (format == AudioFormat.NOT_SET) {
            return "NOT_SET";
        }
        return String.format(Locale.US, "AudioFormat[encoding=%d, sampleRate=%d, channelCount=%d, bytesPerFrame=%d]",
                format.encoding, format.sampleRate, format.channelCount, format.bytesPerFrame);
    }
}
