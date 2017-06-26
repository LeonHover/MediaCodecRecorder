package io.github.leonhover.videorecorder.recorder.mediacodec.encode;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;

import io.github.leonhover.videorecorder.recorder.mediacodec.Utils;
import io.github.leonhover.videorecorder.recorder.mediacodec.muxer.SyncMediaMuxer;
import io.github.leonhover.videorecorder.recorder.mediacodec.surface.OffScreenWindow;

/**
 * Created by wangzongliang on 2017/4/17.
 */

public class VideoEncoder implements Handler.Callback, OffScreenWindow.CallBack {

    private static final String TAG = "VideoEncoder";

    private static final String VIDEO_MIME_TYPE = "video/avc";
    private static final String VIDEO_ENCODING_THREAD = "video_encoder_thread";

    private static final int ENCODING_MSG_PREPARE = 1;
    private static final int ENCODING_MSG_START = 2;
    private static final int ENCODING_MSG_STOP = 3;
    private static final int ENCODING_MSG_CONSUME_INPUT_SURFACE = 4;

    private volatile boolean isEncoding = false;

    private int mWidth;
    private int mHeight;

    private int mFrameRate;
    private int mBitRate;
    private int mIFrameInterval = 1;

    private Surface mInputSurface;

    private HandlerThread mEncodingThread;
    private Handler mEncodingHandler;
    private CallBack mCallBack;

    private SyncMediaMuxer mMediaMuxer;
    private MediaCodec mMediaCodec;
    private MediaCodec.BufferInfo mBufferInfo;
    private int mTrackIndex;

    private boolean isRequestAsynchronousMode = false;
    //是否请求了结束数据流的信号
    private boolean isRequestEOS = false;
    //是否有往Muxer中写入过Frame数据
    private boolean hasFrameData = false;

    public VideoEncoder(SyncMediaMuxer mediaMuxer) {
        this.mMediaMuxer = mediaMuxer;
        mEncodingThread = new HandlerThread(VIDEO_ENCODING_THREAD);
        mEncodingThread.start();
        mEncodingHandler = new Handler(mEncodingThread.getLooper(), this);
    }

    /**
     * 获取视频编码器的供输入用的Surface。需要在{@code prepare}后方可调用。
     *
     * @return Surface
     */
    public Surface getInputSurface() {
        return this.mInputSurface;
    }

    public void prepare() {
        mEncodingHandler.sendEmptyMessage(ENCODING_MSG_PREPARE);
    }

    public void start() {
        mEncodingHandler.sendEmptyMessage(ENCODING_MSG_START);
    }

    public void stop() {
        mEncodingHandler.sendEmptyMessage(ENCODING_MSG_STOP);
    }

    /**
     * 释放视频编码器
     */
    public void release() {
        mEncodingHandler.removeCallbacksAndMessages(null);
        mEncodingThread.quitSafely();
        this.mCallBack = null;
        this.isEncoding = false;
    }

    /**
     * 设定视频编码器的回调
     *
     * @param callBack 回调
     */
    public void setCallBack(CallBack callBack) {
        this.mCallBack = callBack;
    }

    /**
     * 录制的视频大小
     *
     * @param width   宽
     * @param mHeight 高
     */
    public void setVideoSize(int width, int mHeight) {
        this.mWidth = width;
        this.mHeight = mHeight;
    }

    /**
     * 视频帧率
     *
     * @param frameRate 帧率，单位为frame/sec
     */
    public void setFrameRate(int frameRate) {
        this.mFrameRate = frameRate;
    }

    /**
     * 视频码率
     *
     * @param bitRate 码率，单位为bit/sec
     */
    public void setBitRate(int bitRate) {
        this.mBitRate = bitRate;
    }

    /**
     * 关键帧间隔
     *
     * @param interval 间隔时间，单位秒
     */
    public void setIFrameInterval(int interval) {
        this.mIFrameInterval = interval;
    }

    /**
     * 尝试使用异步模式进行编码，如果设备支持的话，目前只有在{@link android.os.Build.VERSION_CODES#LOLLIPOP}
     * 以上版本才支持。
     *
     * @param on true 开，false关
     */
    public void setAsynchronousMode(boolean on) {
        this.isRequestAsynchronousMode = on;
    }

    @Override
    public boolean handleMessage(Message msg) {
        switch (msg.what) {
            case ENCODING_MSG_PREPARE:
                handlePrepare();
                break;
            case ENCODING_MSG_START:
                handleStart();
                break;
            case ENCODING_MSG_STOP:
                handleStop();
                break;
            case ENCODING_MSG_CONSUME_INPUT_SURFACE:
                writeMuxerDataFromEncoding(false);
                break;
        }
        return true;
    }

    private void handlePrepare() {

        if (!isAsynchronousMode()) {
            this.mBufferInfo = new MediaCodec.BufferInfo();
        }

        MediaCodecInfo videoCodecInfo = Utils.chooseSuitableMediaCodec(VIDEO_MIME_TYPE);
        if (videoCodecInfo == null) {
            Log.e(TAG, "none supported mediacodec for mimetype:" + VIDEO_MIME_TYPE);
            return;
        }

        Log.d(TAG, "mediacodec name:" + videoCodecInfo.getName());

        //组建VideoFormat
        MediaFormat mVideoFormat = MediaFormat.createVideoFormat(VIDEO_MIME_TYPE, mWidth, mHeight);
        mVideoFormat.setInteger(MediaFormat.KEY_BIT_RATE, mBitRate);
        mVideoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, mFrameRate);
        mVideoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, mIFrameInterval);
        mVideoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);

        try {
            mMediaCodec = MediaCodec.createByCodecName(videoCodecInfo.getName());
            mMediaCodec.setVideoScalingMode(MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING);
            mMediaCodec.configure(mVideoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mInputSurface = mMediaCodec.createInputSurface();
        } catch (IOException e) {
            e.printStackTrace();
        }

        notifyEncoderCallBack(ENCODING_MSG_PREPARE);
    }

    private void handleStart() {
        Log.d(TAG, "handleStart");

        if (isAsynchronousMode()) {
            setMediaCodecCallBack();
        }

        mMediaCodec.start();

        isEncoding = true;
        notifyEncoderCallBack(ENCODING_MSG_START);

    }

    private void handleStop() {
        Log.d(TAG, "handleStop");
        if (isAsynchronousMode()) {
            mMediaCodec.signalEndOfInputStream();
            //just wait codec encoding eos
        } else {
            isRequestEOS = true;
            writeMuxerDataFromEncoding(true);
            mMediaCodec.flush();
            mMediaCodec.stop();
            mMediaCodec.release();
            notifyEncoderCallBack(ENCODING_MSG_STOP);
        }
    }

    /**
     * 编码后的数据写入Muxer中
     */
    private void writeMuxerDataFromEncoding(boolean isEOS) {
        Log.d(TAG, "writeMuxerDataFromEncoding start isEOS:" + isEOS);
        if (!isEncoding) {
            Log.d(TAG, "writeMuxerDataFromEncoding end isEOS:" + isEOS + ",isEncoding:" + isEncoding);
            return;
        }

        if (isRequestEOS != isEOS) {
            //请求停止了，但是仍旧有离屏的画面更新回调，要求编码，丢弃
            Log.d(TAG, "Already request eos,so ignore dequeuebuffer.");
            Log.d(TAG, "writeMuxerDataFromEncoding end isEOS:" + isEOS + ",isRequestEOS:" + isRequestEOS);
            isEncoding = true;
            return;
        }

        if (isEOS && !hasFrameData) {
            //请求停止，但是没有写入过一帧数据，不能从MediaCodec中拉取数据，直接返回
            Log.d(TAG, "writeMuxerDataFromEncoding end without frame!");
            mMediaMuxer.cancel();
            isEncoding = true;
            return;
        }

        if (isEOS) {
            mMediaCodec.signalEndOfInputStream();
        }

        ByteBuffer[] outputBuffers = mMediaCodec.getOutputBuffers();

        while (isEncoding) {
            int outputBufferIndex = mMediaCodec.dequeueOutputBuffer(mBufferInfo, 10);
            Log.d(TAG, "outputBufferIndex=" + outputBufferIndex + " flags:" + mBufferInfo.flags);
            if (outputBufferIndex >= 0) {

                if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    mBufferInfo.size = 0;
                }

                ByteBuffer encodedData = outputBuffers[outputBufferIndex];

                if (mBufferInfo.size != 0) {
                    mMediaMuxer.writeSampleData(mTrackIndex, encodedData, mBufferInfo);
                    hasFrameData = true;
                }

                mMediaCodec.releaseOutputBuffer(outputBufferIndex, false);


                if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    Log.e(TAG, "Encoding  end of stream");
                    isEncoding = false;
                    break;      // out of while
                }

            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                outputBuffers = mMediaCodec.getOutputBuffers();
            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat mediaFormat = mMediaCodec.getOutputFormat();
                mTrackIndex = mMediaMuxer.addVideoTrack(mediaFormat);
                mMediaMuxer.start();
            } else if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!isEOS) {
                    break;
                }
            }

        }
        Log.d(TAG, "writeMuxerDataFromEncoding end isEOS:" + isEOS);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void setMediaCodecCallBack() {

        mMediaCodec.setCallback(new MediaCodec.Callback() {
            @Override
            public void onInputBufferAvailable(@NonNull MediaCodec codec, int index) {
                Log.d(TAG, "onInputBufferAvailable");
            }

            @Override
            public void onOutputBufferAvailable(@NonNull MediaCodec codec, int index, @NonNull MediaCodec.BufferInfo info) {
                Log.d(TAG, "onOutputBufferAvailable index:" + index);
                if (index >= 0) {

                    if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        mBufferInfo.size = 0;
                    }

                    ByteBuffer outputBuffer = codec.getOutputBuffer(index);

                    if (info.size != 0) {
                        mMediaMuxer.writeSampleData(mTrackIndex, outputBuffer, info);
                    }

                    mMediaCodec.releaseOutputBuffer(index, false);

                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        Log.e(TAG, "Encoding  end of stream");
                        isEncoding = false;
                        mMediaCodec.flush();
                        mMediaCodec.stop();
                        mMediaCodec.release();
                        notifyEncoderCallBack(ENCODING_MSG_STOP);
                    }

                }

            }

            @Override
            public void onError(@NonNull MediaCodec codec, @NonNull MediaCodec.CodecException e) {
                Log.d(TAG, "onError");
            }

            @Override
            public void onOutputFormatChanged(@NonNull MediaCodec codec, @NonNull MediaFormat format) {
                Log.d(TAG, "onOutputFormatChanged");
                mTrackIndex = mMediaMuxer.addVideoTrack(format);
                mMediaMuxer.start();
            }
        });

    }

    private boolean isLollipop() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
    }

    public boolean isAsynchronousMode() {
        return isLollipop() && isRequestAsynchronousMode;
    }

    @Override
    public void onOffScreenWindowUpdate() {
        Log.d(TAG, "onOffScreenWindowUpdate");
        if (!isAsynchronousMode()) {
            mEncodingHandler.sendEmptyMessage(ENCODING_MSG_CONSUME_INPUT_SURFACE);
        }
    }

    private void notifyEncoderCallBack(int what) {
        Log.d(TAG, "notifyEncoderCallBack what:" + what);
        final CallBack callBack = mCallBack;
        if (callBack == null) {
            return;
        }

        switch (what) {
            case ENCODING_MSG_PREPARE:
                callBack.onPrepared(this);
                break;
            case ENCODING_MSG_START:
                callBack.onStarted(this);
                break;
            case ENCODING_MSG_STOP:
                callBack.onStopped(this);
                break;
            default:
                callBack.onInfo(this, what);
        }
    }

    public interface CallBack {
        void onPrepared(VideoEncoder videoEncoder);

        void onStarted(VideoEncoder videoEncoder);

        void onStopped(VideoEncoder videoEncoder);

        void onInfo(VideoEncoder videoEncoder, int info);
    }
}
