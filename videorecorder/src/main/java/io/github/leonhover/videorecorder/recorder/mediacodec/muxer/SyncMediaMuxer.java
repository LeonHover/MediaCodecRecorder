package io.github.leonhover.videorecorder.recorder.mediacodec.muxer;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;

/**
 * Created by wangzongliang on 2017/4/17.
 */

public class SyncMediaMuxer {
    private static final String TAG = "SyncMediaMuxer";
    private MediaMuxer mMediaMuxer;
    private boolean isStarted = false;
    private final Object mLocker = new Object();
    //音频和视频两个轨道
    private CountDownLatch mStartCountDownLatch = new CountDownLatch(2);

    public SyncMediaMuxer(String output) {
        try {
            Log.d(TAG, "SyncMediaMuxer");
            mMediaMuxer = new MediaMuxer(output, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (IOException e) {
            e.printStackTrace();
        }
        isStarted = false;
    }

    public void start() {
        try {
            mStartCountDownLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        synchronized (mLocker) {
            if (!isStarted) {
                Log.d(TAG, "start");
                mMediaMuxer.start();
                isStarted = true;
            }
        }
    }

    public void stop() {
        synchronized (mLocker) {
            if (isStarted) {
                Log.d(TAG, "stop");
                mMediaMuxer.stop();
                isStarted = false;
            }
        }
    }

    public void release() {
        synchronized (mLocker) {
            Log.d(TAG, "release");
            mMediaMuxer.release();
        }
    }

    public int addAudioTrack(MediaFormat mediaFormat) {
        synchronized (mLocker) {
            int trackIndex = mMediaMuxer.addTrack(mediaFormat);
            Log.d(TAG, "addAudioTrack");
            mStartCountDownLatch.countDown();
            return trackIndex;
        }
    }

    public int addVideoTrack(MediaFormat mediaFormat) {
        synchronized (mLocker) {
            int trackIndex = mMediaMuxer.addTrack(mediaFormat);
            Log.d(TAG, "addVideoTrack");
            mStartCountDownLatch.countDown();
            return trackIndex;
        }
    }

    public void writeSampleData(int trackIndex, ByteBuffer byteBuffer, MediaCodec.BufferInfo bufferInfo) {
        synchronized (mLocker) {
            if (isStarted) {
                mMediaMuxer.writeSampleData(trackIndex, byteBuffer, bufferInfo);
            }
        }
    }
}
