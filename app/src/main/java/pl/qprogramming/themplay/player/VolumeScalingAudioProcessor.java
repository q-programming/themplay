package pl.qprogramming.themplay.player;

import androidx.media3.common.C;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.util.UnstableApi;

import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.Locale;

@UnstableApi
public class VolumeScalingAudioProcessor implements AudioProcessor {

    private static final String TAG = "VolumeProcessor"; // Logging Tag

    private float currentVolumeFactor = 1.0f;
    private AudioFormat inputAudioFormat = AudioFormat.NOT_SET;
    private AudioFormat outputAudioFormat = AudioFormat.NOT_SET;

    private ByteBuffer internalOutputBuffer = EMPTY_BUFFER;
    private boolean inputStreamEnded;

    public synchronized void setVolumeFactor(float volumeFactor) {
        this.currentVolumeFactor = Math.max(0.0f, Math.min(volumeFactor, 1.0f));
    }

    public synchronized float getVolumeFactor() {
        return currentVolumeFactor;
    }

    @Override
    @NotNull
    public AudioFormat configure(AudioFormat inputAudioFormat) throws UnhandledAudioFormatException {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw new UnhandledAudioFormatException("Input encoding must be PCM_16BIT", inputAudioFormat);
        }
        if (inputAudioFormat.sampleRate <= 0) {
            throw new UnhandledAudioFormatException("Input sample rate must be a positive value", inputAudioFormat);
        }
        if (inputAudioFormat.channelCount <= 0) {
            throw new UnhandledAudioFormatException("Input channel count must be a positive value", inputAudioFormat);
        }
        this.inputAudioFormat = inputAudioFormat;
        this.outputAudioFormat = inputAudioFormat;
        return this.outputAudioFormat;
    }

    @Override
    public boolean isActive() {
        return inputAudioFormat != AudioFormat.NOT_SET;
    }

    @Override
    public void queueInput(@NotNull ByteBuffer inputBuffer) {
        if (inputStreamEnded && (internalOutputBuffer == EMPTY_BUFFER || !internalOutputBuffer.hasRemaining())) {
            return;
        }
        if (!inputBuffer.hasRemaining()) {
            return;
        }

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

    @Override
    public void queueEndOfStream() {
        inputStreamEnded = true;
    }

    @Override
    public boolean isEnded() {
        return inputStreamEnded && (internalOutputBuffer == EMPTY_BUFFER || !internalOutputBuffer.hasRemaining());
    }

    @Override
    public void flush() {
        internalOutputBuffer = EMPTY_BUFFER;
        inputStreamEnded = false;
    }

    @Override
    public void reset() {
        flush();
        inputAudioFormat = AudioFormat.NOT_SET;
        outputAudioFormat = AudioFormat.NOT_SET;
        currentVolumeFactor = 1.0f;
    }

    public static String byteBufferToString(ByteBuffer buffer) {
        if (buffer == null) return "null";
        // Make EMPTY_BUFFER's string representation more explicit about its state for clarity
        if (buffer == EMPTY_BUFFER) return "EMPTY_BUFFER[pos=0 lim=0 cap=0]";
        return String.format(Locale.US, "Buffer[pos=%d lim=%d cap=%d rem=%d direct=%b ro=%b order=%s]",
                buffer.position(), buffer.limit(), buffer.capacity(), buffer.remaining(),
                buffer.isDirect(), buffer.isReadOnly(), buffer.order().toString());
    }

    public static String audioFormatToString(AudioFormat format) {
        if (format == null) return "null";
        if (format == AudioFormat.NOT_SET) return "NOT_SET";
        return String.format(Locale.US, "AudioFormat[encoding=%d, sampleRate=%d, channelCount=%d, bytesPerFrame=%d]",
                format.encoding, format.sampleRate, format.channelCount, format.bytesPerFrame);
    }
}