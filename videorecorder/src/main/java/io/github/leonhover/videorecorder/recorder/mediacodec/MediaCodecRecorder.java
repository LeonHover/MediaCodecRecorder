package io.github.leonhover.videorecorder.recorder.mediacodec;

import android.graphics.SurfaceTexture;
import android.opengl.EGLContext;
import android.text.TextUtils;
import android.util.Log;

import java.util.concurrent.CountDownLatch;

import io.github.leonhover.videorecorder.recorder.VideoRecorder;
import io.github.leonhover.videorecorder.recorder.mediacodec.encode.AudioEncoder;
import io.github.leonhover.videorecorder.recorder.mediacodec.encode.VideoEncoder;
import io.github.leonhover.videorecorder.recorder.mediacodec.muxer.SyncMediaMuxer;
import io.github.leonhover.videorecorder.recorder.mediacodec.surface.OffScreenWindow;

/**
 * Created by wangzongliang on 2017/4/18.
 */

public class MediaCodecRecorder extends VideoRecorder implements VideoEncoder.CallBack, AudioEncoder.CallBack {

    private static final String TAG = "MediaCodecRecorder";

    private AudioEncoder mAudioEncoder;
    private VideoEncoder mVideoEncoder;
    private SyncMediaMuxer mMediaMuxer;

    private CountDownLatch mPrepareLatch;
    private CountDownLatch mStartLatch;
    private CountDownLatch mStopLatch;

    private int mPreviewWidth = 0;
    private int mPreviewHeight = 0;

    private OffScreenWindow mOffScreenWindow;

    private boolean isRecording = false;

    public void setPreviewSize(int width, int height) {
        this.mPreviewWidth = width;
        this.mPreviewHeight = height;
    }

    public boolean isRecording() {
        return isRecording;
    }

    @Override
    public void reset() {
        if (isRecording) {
            stop();
        }

        release();
    }

    @Override
    public void prepare() {
        Log.d(TAG, "prepare");
        if (isRecording) {
            Log.d(TAG, "prepare you had already start recorder!!");
            return;
        }
        if (TextUtils.isEmpty(mPath)) {
            Log.d(TAG, "output path is empty,can not continue preparing");
            return;
        }

        if (mProfile == null) {
            Log.e(TAG, "Do you setup a profile for MediaCodecRecorder?");
            return;
        }

        mPrepareLatch = new CountDownLatch(2);

        mMediaMuxer = new SyncMediaMuxer(mPath);
        //音频
        mAudioEncoder = new AudioEncoder(mMediaMuxer);
        mAudioEncoder.setCallBack(this);
        mAudioEncoder.setBitRate(mProfile.audioBitRate);
        mAudioEncoder.setSampleRate(mProfile.audioSamplingRate);
        mAudioEncoder.setChannelCount(mProfile.audioChannelCount);
        mAudioEncoder.setChannelMask(mProfile.audioChannelConfig);
        mAudioEncoder.setSampleRate(mProfile.audioSamplingRate);
        mAudioEncoder.prepare();


        //视频
        mVideoEncoder = new VideoEncoder(mMediaMuxer);
        mVideoEncoder.setCallBack(this);
        mVideoEncoder.setBitRate(mProfile.videoBitRate);
        mVideoEncoder.setFrameRate(mProfile.videoFrameRate);
        mVideoEncoder.setIFrameInterval(mProfile.videoIFrameInterval);
        mVideoEncoder.setVideoSize(mProfile.videoWidth, mProfile.videoHeight);
        mVideoEncoder.prepare();

        try {
            mPrepareLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        Log.d(TAG, "prepare end");
    }

    @Override
    public void start() {
        Log.d(TAG, "start");
        if (isRecording) {
            Log.d(TAG, "start you had already start recorder!!");
            return;
        }
        isRecording = true;
        mStartLatch = new CountDownLatch(2);
        mVideoEncoder.start();
        mAudioEncoder.start();
        try {
            mStartLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        Log.d(TAG, "start end");
    }

    @Override
    public void stop() {
        Log.d(TAG, "stop");
        if (isRecording) {
            mStopLatch = new CountDownLatch(2);

            mAudioEncoder.stop();
            mVideoEncoder.stop();
            try {
                mStopLatch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            mMediaMuxer.stop();
        }
        isRecording = false;
        Log.d(TAG, "stop end");
    }

    @Override
    public void release() {
        if (mOffScreenWindow != null) {
            mOffScreenWindow.setCallBack(null);
            mOffScreenWindow.release();
        }

        isRecording = false;
    }

    /**
     * 更新InputSurface用于视频编码
     *
     * @param textureIndex
     * @param surfaceTexture
     */
    public void updateInputSurfaceWindow(final int textureIndex, final SurfaceTexture surfaceTexture) {
        mOffScreenWindow.update(textureIndex, surfaceTexture);
    }

    /**
     * 创建一个离屏的Surface操作的"窗口"。
     *
     * @param eglContext 共享的EGLContext
     */
    public void createInputSurfaceWindow(EGLContext eglContext) {
        mOffScreenWindow = new OffScreenWindow(eglContext);
    }

    @Override
    public void onPrepared(VideoEncoder videoEncoder) {
        Log.d(TAG, "onPrepared videoEncoder");
        if (mOffScreenWindow != null) {
            mOffScreenWindow.setPreviewSize(mPreviewWidth, mPreviewHeight);
            mOffScreenWindow.setWindowSize(mProfile.videoWidth, mProfile.videoHeight);
            mOffScreenWindow.setCallBack(videoEncoder);
            mOffScreenWindow.attachSurface(videoEncoder.getInputSurface());
        }

        mPrepareLatch.countDown();
    }

    @Override
    public void onStarted(VideoEncoder videoEncoder) {
        Log.d(TAG, "onStarted videoEncoder");
        mStartLatch.countDown();
    }

    @Override
    public void onStopped(VideoEncoder videoEncoder) {
        Log.d(TAG, "onStopped videoEncoder");

        if (mOffScreenWindow != null) {
            mOffScreenWindow.detachSurface();
            mOffScreenWindow.setCallBack(null);
        }

        if (videoEncoder != null) {
            videoEncoder.release();
        }
        mStopLatch.countDown();
    }


    @Override
    public void onInfo(VideoEncoder videoEncoder, int info) {

    }

    @Override
    public void onPrepared(AudioEncoder audioEncoder) {
        Log.d(TAG, "onPrepared audioEncoder");
        mPrepareLatch.countDown();

    }

    @Override
    public void onStarted(AudioEncoder audioEncoder) {
        Log.d(TAG, "onStarted audioEncoder");
        mStartLatch.countDown();
    }

    @Override
    public void onStopped(AudioEncoder audioEncoder) {
        Log.d(TAG, "onStopped audioEncoder");
        if (audioEncoder != null) {
            audioEncoder.release();
        }
        mStopLatch.countDown();
    }

    @Override
    public void onInfo(AudioEncoder audioEncoder, int info) {
    }
}
