package io.github.leonhover.videorecorder.recorder.mediacodec;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.view.Surface;

import java.io.IOException;

import io.github.leonhover.videorecorder.recorder.VideoRecorder;

/**
 * Created by wangzongliang on 17-3-23.
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class MediaCodecRecorder extends VideoRecorder {

    private static final String TAG = "MediaCodecRecorder";

    private MediaCodec mMediaCodec;
    private MediaMuxer mMediaMuxer;
    private Surface mSurface;

    public MediaCodecRecorder() {

    }

    public void encodeFrame() {

    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void reset() {
        mMediaCodec.reset();
    }

    @Override
    public void prepare() {
        try {
            mMediaCodec = MediaCodec.createEncoderByType("video/avc");
        } catch (IOException e) {
            e.printStackTrace();
        }

        MediaFormat videoFormat = MediaFormat.createVideoFormat("video/avc", 480, 480);
        videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, 1024_000);
        videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 15);
        videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 5);
        videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);

        mMediaCodec.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mMediaCodec.setVideoScalingMode(MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING);
        mSurface = mMediaCodec.createInputSurface();
        mMediaCodec.setCallback(new MediaCodec.Callback() {
            @Override
            public void onInputBufferAvailable(@NonNull MediaCodec codec, int index) {
            }

            @Override
            public void onOutputBufferAvailable(@NonNull MediaCodec codec, int index, @NonNull MediaCodec.BufferInfo info) {

            }

            @Override
            public void onError(@NonNull MediaCodec codec, @NonNull MediaCodec.CodecException e) {

            }

            @Override
            public void onOutputFormatChanged(@NonNull MediaCodec codec, @NonNull MediaFormat format) {

            }
        });
    }

    @Override
    public void start() {
        mMediaCodec.start();
    }

    @Override
    public void stop() {
        mMediaCodec.stop();
    }

    @Override
    public void pause() {

    }

    @Override
    public void resume() {

    }

    @Override
    public void release() {

    }
}
