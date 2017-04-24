package io.github.leonhover.videorecorder.recorder.mediacodec.muxer;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by wangzongliang on 2017/4/17.
 */

public class SyncMediaMuxer {
    private static final String TAG = "SyncMediaMuxer";

    private static final int DEFAULT_TRACK_COUNT = 2;

    private int mStarterCount = 0;

    private MediaMuxer mMediaMuxer;


    private boolean isStarted = false;

    public SyncMediaMuxer(String output) {
        try {
            mMediaMuxer = new MediaMuxer(output, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (IOException e) {
            e.printStackTrace();
        }
        isStarted = false;
    }

    public synchronized void start() {
        if (++mStarterCount != DEFAULT_TRACK_COUNT) {
            Log.d(TAG, "start mStartCount:" + mStarterCount);
            return;
        }
        if (!isStarted) {
            Log.d(TAG, "start");
            mMediaMuxer.start();
            isStarted = true;
        }
    }


    public synchronized void stop() {
        if (isStarted) {
            mStarterCount = 0;
            mMediaMuxer.stop();
            isStarted = false;
        }
    }

    public synchronized void release() {
        mMediaMuxer.release();
    }

    public synchronized int addTrack(MediaFormat mediaFormat) {
        return mMediaMuxer.addTrack(mediaFormat);
    }

    public synchronized void writeSampleData(int trackIndex, ByteBuffer byteBuffer, MediaCodec.BufferInfo bufferInfo) {
        if (isStarted) {
            mMediaMuxer.writeSampleData(trackIndex, byteBuffer, bufferInfo);
        }
    }
}
