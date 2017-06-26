package io.github.leonhover.videorecorder.recorder.mediacodec.encode;

import android.annotation.TargetApi;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.support.annotation.NonNull;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;

import io.github.leonhover.videorecorder.recorder.mediacodec.Utils;
import io.github.leonhover.videorecorder.recorder.mediacodec.muxer.SyncMediaMuxer;

/**
 * Created by wangzongliang on 2017/4/17.
 */

public class AudioEncoder implements Handler.Callback, AudioRecorder.IAudioDataReceiver {

    private static final String TAG = "AudioEncoder";

    private static final String AUDIO_MIME_TYPE = "audio/mp4a-latm";
    private static final String AUDIO_ENCODING_THREAD_NAME = "audio_encoding_thread";

    private static final int ENCODING_MSG_PREPARE = 1;
    private static final int ENCODING_MSG_START = 2;
    private static final int ENCODING_MSG_STOP = 3;
    private static final int ENCODING_MSG_AUDIO_ENCODED = 4;
    private static final int ENCODING_MSG_AUDIO_RECORDING_STOPPED = 5;

    private int mSampleRate;
    private int mBitRate;
    private int mChannelCount = 1;
    private int mChannelMask = AudioFormat.CHANNEL_IN_MONO;

    private HandlerThread mEncodingThread;
    private Handler mEncodingHandler;
    private CallBack mCallBack;

    private MediaCodec.BufferInfo mBufferInfo;
    private int mTrackIndex;

    private volatile boolean isEncoding = false;

    private AudioRecorder mAudioRecorder;
    private SyncMediaMuxer mMediaMuxer;
    private MediaCodec mMediaCodec;

    private boolean isRequestAsynchronousMode = false;
    private ArrayBlockingQueue<AudioFrameData> mAudioFrameDataQueue;

    public AudioEncoder(SyncMediaMuxer mediaMuxer) {
        this.mMediaMuxer = mediaMuxer;
        this.mEncodingThread = new HandlerThread(AUDIO_ENCODING_THREAD_NAME);
        this.mEncodingThread.start();
        this.mEncodingHandler = new Handler(this.mEncodingThread.getLooper(), this);
    }

    /**
     * 设置音频编码器的回调
     *
     * @param callBack
     */
    public void setCallBack(CallBack callBack) {
        this.mCallBack = callBack;
    }

    /**
     * 音频采样率
     *
     * @param sampleRate 采样率
     */
    public void setSampleRate(int sampleRate) {
        this.mSampleRate = sampleRate;
    }

    /**
     * 音频码率
     *
     * @param bitRate 码率
     */
    public void setBitRate(int bitRate) {
        this.mBitRate = bitRate;
    }

    /**
     * 声道数量
     *
     * @param count 数量
     */
    public void setChannelCount(int count) {
        this.mChannelCount = count;
    }

    /**
     * 声道配置
     *
     * @param channelMask 声道配置
     */
    public void setChannelMask(int channelMask) {
        this.mChannelMask = channelMask;
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
     * 释放资源
     */
    public void release() {

        mEncodingHandler.removeCallbacksAndMessages(null);
        mEncodingThread.quitSafely();

        mCallBack = null;
        this.isEncoding = false;

        if (isAsynchronousMode()) {
            mAudioFrameDataQueue.clear();
            mAudioFrameDataQueue = null;
        }

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
            case ENCODING_MSG_AUDIO_ENCODED:
                writeMuxerDataFromEncoding(false);
                break;
            case ENCODING_MSG_AUDIO_RECORDING_STOPPED:
                writeMuxerDataFromEncoding(true);
                break;
            default:
        }
        return true;
    }

    private void handlePrepare() {


        if (isAsynchronousMode()) {
            mAudioFrameDataQueue = new ArrayBlockingQueue<AudioFrameData>(10);
        } else {
            mBufferInfo = new MediaCodec.BufferInfo();
        }

        MediaCodecInfo audioCodecInfo = Utils.chooseSuitableMediaCodec(AUDIO_MIME_TYPE);
        if (audioCodecInfo == null) {
            Log.e(TAG, "unsupported mimetype :" + AUDIO_MIME_TYPE);
            return;
        }

        MediaFormat audioFormat = MediaFormat.createAudioFormat(AUDIO_MIME_TYPE, mSampleRate, mChannelCount);
        audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        audioFormat.setInteger(MediaFormat.KEY_CHANNEL_MASK, mChannelMask);
        audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, mBitRate);
        audioFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, mChannelCount);

        try {
            mMediaCodec = MediaCodec.createEncoderByType(AUDIO_MIME_TYPE);
            mMediaCodec.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        } catch (IOException e) {
            e.printStackTrace();
        }

        mAudioRecorder = new AudioRecorder(this);
        mAudioRecorder.setSampleRate(mSampleRate);
        mAudioRecorder.setChannelConfig(mChannelMask);

        notifyEncoderCallBack(ENCODING_MSG_PREPARE);
    }

    private void handleStart() {
        isEncoding = true;
        mAudioRecorder.start();
        if (isAsynchronousMode()) {
            setMediaCodecCallBack();
        }
        mMediaCodec.start();
        notifyEncoderCallBack(ENCODING_MSG_START);
    }


    private void handleStop() {
        mAudioRecorder.stop();
        if (isAsynchronousMode()) {
            // just wait encoding eos.
        } else {
            writeMuxerDataFromEncoding(true);
            mMediaCodec.flush();
            mMediaCodec.stop();
            mMediaCodec.release();
            notifyEncoderCallBack(ENCODING_MSG_STOP);
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

    @Override
    public void onAudioDataReceived(ByteBuffer byteBuffer, int length, long presentationTime) {
        Log.d(TAG, "onAudioDataReceived");
        if (isAsynchronousMode()) {
            enterFrameQueue(byteBuffer, length, presentationTime);
        } else {
            encode(byteBuffer, length, presentationTime);
            mEncodingHandler.sendEmptyMessage(ENCODING_MSG_AUDIO_ENCODED);
        }
    }

    @Override
    public void onAudioRecorderStopped(long presentation) {
        Log.d(TAG, "onAudioRecorderStopped");
        if (isAsynchronousMode()) {
            enterFrameQueue(null, 0, presentation);
        } else {
            encode(null, 0, presentation);
            mEncodingHandler.sendEmptyMessage(ENCODING_MSG_AUDIO_RECORDING_STOPPED);
        }
    }

    private void encode(ByteBuffer byteBuffer, int length, long presentationTime) {
        Log.d(TAG, "encode length:" + length + ",presentationTime:" + presentationTime);
        if (!isEncoding) {
            return;
        }
        final ByteBuffer[] inputBuffers = mMediaCodec.getInputBuffers();
        while (isEncoding) {
            final int inputBufferIndex = mMediaCodec.dequeueInputBuffer(10);
            if (inputBufferIndex >= 0) {
                final ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
                inputBuffer.clear();
                if (byteBuffer != null) {
                    inputBuffer.put(byteBuffer);
                }
                if (length <= 0) {
                    Log.e(TAG, "audio end of stream");
                    mMediaCodec.queueInputBuffer(inputBufferIndex, 0, 0,
                            presentationTime, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                } else {
                    Log.d(TAG, "queueInputBuffer");
                    mMediaCodec.queueInputBuffer(inputBufferIndex, 0, length,
                            presentationTime, 0);
                }
                break;
            } else if (inputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // wait for MediaCodec encoder is ready to encode
                // nothing to do here because MediaCodec#dequeueInputBuffer(TIMEOUT_USEC)
                // will wait for maximum TIMEOUT_USEC(10msec) on each call
            }
        }
    }

    /**
     * 编码后的数据写入Muxer中
     */
    private void writeMuxerDataFromEncoding(boolean isEOS) {

        if (!isEncoding) {
            return;
        }

        Log.d(TAG, "writeMuxerDataFromEncoding isEOS:" + isEOS);

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
                }

                mMediaCodec.releaseOutputBuffer(outputBufferIndex, false);

                if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    Log.e(TAG, "Encoding  end of stream");
                    isEncoding = false;
                    break;
                }

            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                outputBuffers = mMediaCodec.getOutputBuffers();
            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat mediaFormat = mMediaCodec.getOutputFormat();
                mTrackIndex = mMediaMuxer.addAudioTrack(mediaFormat);
                mMediaMuxer.start();
                Log.d(TAG, "audio mediaMuxer start");
            } else if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!isEOS) {
                    break;
                }
            }

        }
    }

    @Override
    public void onAudioRecorderError() {

    }

    private void enterFrameQueue(ByteBuffer byteBuffer, int length, long presentationTime) {
        Log.d(TAG, "enterFrameQueue");
        AudioFrameData frameData = new AudioFrameData();
        frameData.byteBuffer = byteBuffer;
        frameData.length = length;
        frameData.presentationTime = presentationTime;

        mAudioFrameDataQueue.offer(frameData);

    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void setMediaCodecCallBack() {

        mMediaCodec.setCallback(new MediaCodec.Callback() {
            @Override
            public void onInputBufferAvailable(@NonNull MediaCodec codec, int index) {
                Log.d(TAG, "onInputBufferAvailable index:" + index);
                ByteBuffer byteBuffer = codec.getInputBuffer(index);
                if (byteBuffer == null) {
                    return;
                }

                Log.d(TAG, "frameDataQueue size:" + mAudioFrameDataQueue.size());

                AudioFrameData frameData = mAudioFrameDataQueue.poll();

                if (frameData != null) {
                    if (frameData.length > 0) {
                        byteBuffer.put(frameData.byteBuffer);
                        mMediaCodec.queueInputBuffer(index, 0, frameData.length,
                                frameData.presentationTime, 0);
                    } else {
                        mMediaCodec.queueInputBuffer(index, 0, 0,
                                frameData.presentationTime, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    }
                } else {
                    mMediaCodec.queueInputBuffer(index, 0, 0,
                            0, 0);
                }
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
                mTrackIndex = mMediaMuxer.addAudioTrack(format);
                mMediaMuxer.start();
            }
        });

    }

    private boolean isLollipop() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
    }

    private boolean isAsynchronousMode() {
        return isLollipop() && isRequestAsynchronousMode;
    }

    private static class AudioFrameData {
        ByteBuffer byteBuffer;
        int length;
        long presentationTime;
    }

    public interface CallBack {
        void onPrepared(AudioEncoder audioEncoder);

        void onStarted(AudioEncoder audioEncoder);

        void onStopped(AudioEncoder audioEncoder);

        void onInfo(AudioEncoder audioEncoder, int info);
    }
}
