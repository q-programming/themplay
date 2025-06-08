package pl.qprogramming.themplay.player;

import android.content.Context;
import android.os.Handler;

import androidx.annotation.Nullable;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.exoplayer.audio.AudioRendererEventListener;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.audio.DefaultAudioSink;
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;

import java.util.ArrayList;

@UnstableApi
public class CustomAudioRenderersFactory extends DefaultRenderersFactory {

    private final AudioProcessor[] audioProcessors;

    public CustomAudioRenderersFactory(Context context, AudioProcessor... audioProcessors) {
        super(context);
        this.audioProcessors = audioProcessors;
    }


    @Override
    protected void buildAudioRenderers(
            Context context,
            int extensionRendererMode,
            MediaCodecSelector mediaCodecSelector,
            boolean enableDecoderFallback,
            AudioSink audioSink,
            @Nullable Handler eventHandler,
            @Nullable AudioRendererEventListener eventListener,
            ArrayList<Renderer> out) {

        AudioSink customAudioSink = new DefaultAudioSink.Builder(context)
                .setAudioProcessors(this.audioProcessors)
                .build();

        MediaCodecAudioRenderer audioRenderer = new MediaCodecAudioRenderer(
                context,
                mediaCodecSelector,
                enableDecoderFallback,
                eventHandler,
                eventListener,
                customAudioSink
        );
        out.add(audioRenderer);

    }
}