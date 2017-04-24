package io.github.leonhover.videorecorder.recorder.mediacodec.encode;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
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

    public VideoEncoder(SyncMediaMuxer mediaMuxer) {
        this.mMediaMuxer = mediaMuxer;
        mEncodingThread = new HandlerThread(VIDEO_ENCODING_THREAD);
        mEncodingThread.start();
        mEncodingHandler = new Handler(mEncodingThread.getLooper(), this);
    }

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

    public void release() {
        mEncodingHandler.removeCallbacksAndMessages(null);
        mEncodingThread.quitSafely();
        if (mMediaCodec != null) {
            mMediaCodec.release();
        }

    }

    public void setCallBack(CallBack callBack) {
        this.mCallBack = callBack;
    }

    public void setVideoSize(int width, int mHeight) {
        this.mWidth = width;
        this.mHeight = mHeight;
    }

    public void setFrameRate(int frameRate) {
        this.mFrameRate = frameRate;
    }

    public void setBitRate(int bitRate) {
        this.mBitRate = bitRate;
    }

    public void setIFrameInterval(int interval) {
        this.mIFrameInterval = interval;
    }

    public void setTrackIndex(int trackIndex) {
        this.mTrackIndex = trackIndex;
    }

    public MediaFormat getOutputFormat() {
        return mMediaCodec.getOutputFormat();
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

        this.mBufferInfo = new MediaCodec.BufferInfo();

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
        mMediaCodec.start();
        isEncoding = true;
        notifyEncoderCallBack(ENCODING_MSG_START);

    }

    private void handleStop() {
        Log.d(TAG, "handleStop");
        mMediaCodec.signalEndOfInputStream();
        writeMuxerDataFromEncoding(true);
        mMediaCodec.flush();
        mMediaCodec.stop();
        mMediaCodec.release();
        notifyEncoderCallBack(ENCODING_MSG_STOP);
    }

    /**
     * 编码后的数据写入Muxer中
     */
    private void writeMuxerDataFromEncoding(boolean isEOS) {

        if (!isEncoding) {
            return;
        }

        ByteBuffer[] outputBuffers = mMediaCodec.getOutputBuffers();

        while (isEncoding) {
            int outputBufferIndex = mMediaCodec.dequeueOutputBuffer(mBufferInfo, 10);
            Log.d(TAG, "outputBufferIndex=" + outputBufferIndex + " flags:" + mBufferInfo.flags);
            if (outputBufferIndex >= 0) {
                // outputBuffers[outputBufferId] is ready to be processed or rendered.

                ByteBuffer encodedData = outputBuffers[outputBufferIndex];
                if (encodedData == null) {
                    throw new RuntimeException("encoderOutputBuffer " + outputBufferIndex +
                            " was null");
                }

                if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    // The codec config data was pulled out and fed to the muxer when we got
                    // the INFO_OUTPUT_FORMAT_CHANGED status.  Ignore it.
                    Log.d(TAG, "ignoring BUFFER_FLAG_CODEC_CONFIG");
                    mBufferInfo.size = 0;
                }

                if (mBufferInfo.size != 0) {
                    // adjust the ByteBuffer values to match BufferInfo (not needed?)
                    encodedData.position(mBufferInfo.offset);
                    encodedData.limit(mBufferInfo.offset + mBufferInfo.size);
                    try {
                        mMediaMuxer.writeSampleData(mTrackIndex, encodedData, mBufferInfo);
                    } catch (Exception e) {

                    }
                    Log.d(TAG, "writeSampleData");
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
                Log.d(TAG, "INFO_OUTPUT_FORMAT_CHANGED video:" + mediaFormat.toString());
                mTrackIndex = mMediaMuxer.addTrack(mediaFormat);
                mMediaMuxer.start();
            } else if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!isEOS) {
                    break;
                }
            }

        }
    }


    @Override
    public void onOffScreenWindowUpdate() {
        Log.d(TAG, "onOffScreenWindowUpdate");
        mEncodingHandler.sendEmptyMessage(ENCODING_MSG_CONSUME_INPUT_SURFACE);
    }

    private void notifyEncoderCallBack(int what) {

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
        }
    }

    public interface CallBack {
        void onPrepared(VideoEncoder videoEncoder);

        void onStarted(VideoEncoder videoEncoder);

        void onStopped(VideoEncoder videoEncoder);
    }
}
