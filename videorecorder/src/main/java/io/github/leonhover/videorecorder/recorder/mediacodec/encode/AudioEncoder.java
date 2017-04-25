package io.github.leonhover.videorecorder.recorder.mediacodec.encode;

import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;

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

    public AudioEncoder(SyncMediaMuxer mediaMuxer) {
        this.mMediaMuxer = mediaMuxer;
        this.mEncodingThread = new HandlerThread(AUDIO_ENCODING_THREAD_NAME);
        this.mEncodingThread.start();
        this.mEncodingHandler = new Handler(this.mEncodingThread.getLooper(), this);
    }

    public void setCallBack(CallBack callBack) {
        this.mCallBack = callBack;
    }

    public void setSampleRate(int sampleRate) {
        this.mSampleRate = sampleRate;
    }

    public void setBitRate(int bitRate) {
        this.mBitRate = bitRate;
    }

    public void setChannelCount(int count) {
        this.mChannelCount = count;
    }

    public void setChannelMask(int channelMask) {
        this.mChannelMask = channelMask;
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

        if (mMediaCodec != null) {
            mMediaCodec.flush();
            mMediaCodec.release();
        }

        mEncodingHandler.removeCallbacksAndMessages(null);
        mEncodingThread.quitSafely();

        mCallBack = null;

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

        mBufferInfo = new MediaCodec.BufferInfo();

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
        mMediaCodec.start();
        mAudioRecorder.start();
        notifyEncoderCallBack(ENCODING_MSG_START);
    }


    private void handleStop() {
        mAudioRecorder.stop();
        writeMuxerDataFromEncoding(true);
        mMediaCodec.flush();
        mMediaCodec.stop();
        mMediaCodec.release();
        notifyEncoderCallBack(ENCODING_MSG_STOP);
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

    @Override
    public void onAudioDataReceived(ByteBuffer byteBuffer, int length, long presentationTime) {
        Log.d(TAG, "onAudioDataReceived");
        encode(byteBuffer, length, presentationTime);
        mEncodingHandler.sendEmptyMessage(ENCODING_MSG_AUDIO_ENCODED);
    }

    @Override
    public void onAudioRecorderStopped(long presentation) {
        Log.d(TAG, "onAudioRecorderStopped");
        encode(null, 0, presentation);
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
                Log.d(TAG, "INFO_OUTPUT_FORMAT_CHANGED audio:" + mediaFormat.toString());
                mTrackIndex = mMediaMuxer.addAudioTrack(mediaFormat);
                mMediaMuxer.start();
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

    public interface CallBack {
        void onPrepared(AudioEncoder audioEncoder);

        void onStarted(AudioEncoder audioEncoder);

        void onStopped(AudioEncoder audioEncoder);
    }
}
