package io.github.leonhover.videorecorder.recorder.mediacodec.surface;

import android.graphics.SurfaceTexture;
import android.opengl.EGLContext;
import android.opengl.Matrix;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;
import android.view.Surface;

import io.github.leonhover.videorecorder.opengl.GLContext;
import io.github.leonhover.videorecorder.opengl.GLSurface;
import io.github.leonhover.videorecorder.opengl.filter.GLSurfaceFilter;

/**
 * 离屏窗口，辅助管理OPENGL的操作以及画面的渲染，再回调给监听器。
 * Created by wangzongliang on 2017/4/24.
 */
public class OffScreenWindow implements Handler.Callback {

    private static final String TAG = "OffScreenWindow";

    private static final String OFFSCREEN_WINDOW_THREAD = "offscreen_window";

    private static final int WINDOW_MSG_CALCULATE_MVP_MATRIX = 1;
    private static final int WINDOW_MSG_ATTACH_SURFACE = 2;
    private static final int WINDOW_MSG_DETACH_SURFACE = 3;
    private static final int WINDOW_MSG_UPDATE = 4;

    private static final int SURFACE_ATTACHED_TIME_NONE = 0;

    private static final String UPDATE_PRESENTATION_TIME_KEY = "presentation_time_us";

    //opengl
    private EGLContext mShareEGLContext;
    private GLContext mGLContext;
    private GLSurface mGLSurface;
    private GLSurfaceFilter mGLSurfaceFilter;
    private long mSurfaceAttachedTime = SURFACE_ATTACHED_TIME_NONE;

    //size
    private int mWidth = 0;
    private int mHeight = 0;
    private int mPreviewWidth = 0;
    private int mPreviewHeight = 0;
    //M.V.P转换矩阵
    private float[] mMVPMatrix = new float[16];
    private float[] mTextureTransformMatrix = new float[16];

    //窗口线程
    private HandlerThread mWindowThread;
    private Handler mWindowHandler;
    private CallBack mCallBack;

    public OffScreenWindow(EGLContext eglContext) {
        this.mShareEGLContext = eglContext;
        this.mWindowThread = new HandlerThread(OFFSCREEN_WINDOW_THREAD);
        this.mWindowThread.start();
        this.mWindowHandler = new Handler(this.mWindowThread.getLooper(), this);
        mGLContext = new GLContext(mShareEGLContext);
        mGLSurface = new GLSurface(mGLContext);
        mGLSurfaceFilter = new GLSurfaceFilter();
        Matrix.setIdentityM(mMVPMatrix, 0);
    }

    /**
     * 设定预览画面的大小
     *
     * @param width  宽
     * @param height 高
     */
    public void setPreviewSize(int width, int height) {
        this.mPreviewWidth = width;
        this.mPreviewHeight = height;
        calculateMVPMatrix();
    }

    /**
     * 设定离屏窗口的大小，这里一般指生成的视频的最终大小。
     *
     * @param width  宽
     * @param height 高
     */
    public void setWindowSize(int width, int height) {
        this.mWidth = width;
        this.mHeight = height;
        calculateMVPMatrix();
    }

    /**
     * 根据预览大小与离屏窗口大小，计算M.V.P转换矩阵，保证画面不会压缩或拉伸。
     */
    private void calculateMVPMatrix() {
        if ((this.mWidth & this.mHeight & this.mPreviewHeight & this.mPreviewWidth) == 0) {
            return;
        }
        mWindowHandler.sendEmptyMessage(WINDOW_MSG_CALCULATE_MVP_MATRIX);
    }

    /**
     * 设定离屏窗口更新的回调，当窗口画面更新完成后，会出发此回调。
     *
     * @param callBack 回调{@link CallBack}
     */
    public void setCallBack(CallBack callBack) {
        this.mCallBack = callBack;
    }

    /**
     * 附着Surface到离屏窗口上。录制中Surface为MediaCodec的{@code MediaCodec.createInputSurface}的返回值。
     *
     * @param surface surface
     */
    public void attachSurface(final Surface surface) {
        Log.d(TAG, "attachSurface");
        mSurfaceAttachedTime = System.nanoTime();
        Message msg = mWindowHandler.obtainMessage(WINDOW_MSG_ATTACH_SURFACE);
        msg.obj = surface;
        msg.sendToTarget();
    }

    /**
     * 从OffScreenWindow中分离附着的Surface。
     */
    public void detachSurface() {
        Log.d(TAG, "detachSurface");
        mSurfaceAttachedTime = SURFACE_ATTACHED_TIME_NONE;
        mWindowHandler.sendEmptyMessage(WINDOW_MSG_DETACH_SURFACE);
    }

    /**
     * 释放离屏窗口的资源
     */
    public void release() {
        Log.d(TAG, "release");
        mSurfaceAttachedTime = SURFACE_ATTACHED_TIME_NONE;
        mWindowHandler.removeCallbacksAndMessages(null);
        mWindowThread.quitSafely();

        releaseGLSurface();

        if (mGLSurfaceFilter != null) {
            mGLSurfaceFilter.release();
        }

        if (mGLContext != null) {
            mGLContext.release();
            mGLContext = null;
        }
    }

    private void releaseGLSurface() {
        Log.d(TAG, "releaseGLSurface");
        if (mGLSurface != null) {
            mGLSurface.releaseEglSurface();
        }
    }

    /**
     * 更新离屏窗口的画面
     *
     * @param textureIndex   纹理索引
     * @param surfaceTexture
     */
    public void update(int textureIndex, SurfaceTexture surfaceTexture) {
        if (mSurfaceAttachedTime != SURFACE_ATTACHED_TIME_NONE) {
            Log.d(TAG, "update");
            Message msg = mWindowHandler.obtainMessage(WINDOW_MSG_UPDATE);
            Bundle extraData = msg.getData();
            if (extraData == null) {
                extraData = new Bundle();
                msg.setData(extraData);
            }

            extraData.putLong(UPDATE_PRESENTATION_TIME_KEY, (System.nanoTime()));
            msg.arg1 = textureIndex;
            msg.obj = surfaceTexture;
            msg.sendToTarget();
        }
    }

    @Override
    public boolean handleMessage(Message msg) {
        switch (msg.what) {
            case WINDOW_MSG_CALCULATE_MVP_MATRIX:
                Matrix.setIdentityM(mMVPMatrix, 0);
                //平铺
                float scaleX = 1.0f;
                float scaleY = 1.0f;
                final double previewRatio = mPreviewWidth * 1.0f / mPreviewHeight;
                final double viewRatio = mWidth * 1.0f / mHeight;

                if (previewRatio < viewRatio) {
                    scaleY = (float) (mPreviewHeight * 1.0f / (mPreviewWidth / viewRatio));
                } else {
                    scaleX = (float) (mPreviewWidth * 1.0f / (mPreviewHeight * viewRatio));
                }

                Matrix.scaleM(mMVPMatrix, 0, scaleX, scaleY, 1.0f);
                break;
            case WINDOW_MSG_ATTACH_SURFACE:
                Log.d(TAG, "WINDOW_MSG_ATTACH_SURFACE");
                Surface surface = (Surface) msg.obj;
                mGLSurface.createSurface(surface);
                mGLSurface.makeCurrent();
                break;
            case WINDOW_MSG_DETACH_SURFACE:
                Log.d(TAG, "WINDOW_MSG_DETACH_SURFACE");
                releaseGLSurface();
                break;
            case WINDOW_MSG_UPDATE:

                Log.d(TAG, "WINDOW_MSG_UPDATE");
                if (mGLSurface == null) {
                    return false;
                }

                SurfaceTexture surfaceTexture = (SurfaceTexture) msg.obj;
                int textureIndex = msg.arg1;
                long presentationTime = msg.getData().getLong(UPDATE_PRESENTATION_TIME_KEY);

                surfaceTexture.getTransformMatrix(mTextureTransformMatrix);
                mGLSurface.makeCurrent();
                mGLSurfaceFilter.draw(textureIndex, mMVPMatrix, mTextureTransformMatrix);
                mGLSurface.setPresentationTime(presentationTime);
                mGLSurface.swapBuffers();

                final CallBack callBack = mCallBack;
                if (callBack != null) {
                    callBack.onOffScreenWindowUpdate();
                }
                break;
        }
        return true;
    }

    public interface CallBack {
        void onOffScreenWindowUpdate();
    }

}
