package io.github.leonhover.videorecorder.recorder.mediacodec.encode;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import java.nio.ByteBuffer;

/**
 * Created by wangzongliang on 2017/4/24.
 */

public class AudioRecorder implements Runnable {
    public static final String TAG = "AudioRecorder";

    private AudioRecord mAudioRecord = null;

    public static final int RECORDING_START_TIME_NOME = 0;

    private boolean isRecording = false;

    public static final int SAMPLES_PER_FRAME = 2048;
    public static final int FRAMES_PER_BUFFER = 25;

    /**
     * 采样率
     */
    private int mSampleRate = 44100;

    private int mChannelConfig = AudioFormat.CHANNEL_IN_STEREO;
    private long mRecordingStartTime = RECORDING_START_TIME_NOME;

    private final Object mLocker = new Object();

    private IAudioDataReceiver mAudioDataReceiver;

    public interface IAudioDataReceiver {
        void onAudioDataReceived(ByteBuffer byteBuffer, int length, long presentationTime);

        void onAudioRecorderStopped(long presentation);

        void onAudioRecorderError();
    }

    public AudioRecorder(IAudioDataReceiver audioDataReceiver) {
        this.mAudioDataReceiver = audioDataReceiver;
    }

    public void setChannelConfig(int channelConfig) {
        this.mChannelConfig = channelConfig;
    }

    /**
     * 设置采样率
     */
    public void setSampleRate(int sampleRate) {
        this.mSampleRate = sampleRate;
    }

    public void start() {
        isRecording = true;
        Thread recordingThread = new Thread(this);
//        recordingThread.setPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
        recordingThread.start();
    }

    public void stop() {
        isRecording = false;
        synchronized (mLocker) {
            try {
                mLocker.wait(30 * 1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void run() {

        Log.d(TAG, " audio thread start!!");
        int minBufferSize = AudioRecord.getMinBufferSize(mSampleRate, mChannelConfig, AudioFormat.ENCODING_PCM_16BIT);

        if (minBufferSize < 0) {
            if (mAudioDataReceiver != null) {
                mAudioDataReceiver.onAudioRecorderError();
            }
            return;
        }

        boolean isErrorOccur = false;

        int buffer_size = SAMPLES_PER_FRAME * FRAMES_PER_BUFFER;
        if (buffer_size < minBufferSize) {
            buffer_size = ((minBufferSize / SAMPLES_PER_FRAME) + 1) * SAMPLES_PER_FRAME * 2;
        }

        Log.d(TAG, " audio thread mMinBufferSize=" + minBufferSize);
        if (isRecording) {
            mAudioRecord = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, mSampleRate,
                    mChannelConfig, AudioFormat.ENCODING_PCM_16BIT, buffer_size);
        } else {
            return;
        }

        int waitCount = 5;


        while (mAudioRecord.getState() != AudioRecord.STATE_INITIALIZED && isRecording && waitCount > 0) {
            Log.d(TAG, " audio thread ==== mAudioRecord.getState() =" + mAudioRecord.getState());
            try {
                Thread.sleep(100);
                waitCount--;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        isErrorOccur = mAudioRecord.getState() != AudioRecord.STATE_INITIALIZED;

        Log.d(TAG, " audio thread mAudioRecord.getState() =" + mAudioRecord.getState());

        if (isRecording) {
            try {
                mAudioRecord.startRecording();
                mRecordingStartTime = getSystemMicroTime();
            } catch (IllegalStateException e) {
                e.printStackTrace();
                if (mAudioDataReceiver != null) {
                    mAudioDataReceiver.onAudioRecorderError();
                }
                isErrorOccur = true;
            }

        }

        final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(SAMPLES_PER_FRAME);

        try {
            while (isRecording && !isErrorOccur) {
                byteBuffer.clear();
                int length = mAudioRecord.read(byteBuffer, SAMPLES_PER_FRAME);
                if (length > 0 && mAudioDataReceiver != null) {
                    byteBuffer.position(length);
                    byteBuffer.flip();
                    mAudioDataReceiver.onAudioDataReceived(byteBuffer, length, getSystemMicroTime());
                }
            }
            if (mAudioDataReceiver != null) {
                mAudioDataReceiver.onAudioRecorderStopped(getSystemMicroTime());
            }

            synchronized (mLocker) {
                mLocker.notify();
            }

        } catch (Exception e) {
            e.printStackTrace();
            if (mAudioDataReceiver != null) {
                mAudioDataReceiver.onAudioRecorderError();
            }
            isErrorOccur = true;
        }
        Log.d(TAG, " audio thread stop!! isErrorOccur =" + isErrorOccur);

        if (mAudioRecord != null) {
            try {
                mAudioRecord.stop();
            } catch (IllegalStateException e) {
                e.printStackTrace();
            }
            mAudioRecord.release();
            mAudioDataReceiver = null;
            mAudioRecord = null;
        }
    }

    private long getSystemMicroTime() {
        return System.nanoTime() / 1000;
    }
}
