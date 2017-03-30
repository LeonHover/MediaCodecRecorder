package io.github.leonhover.videorecorder.camera;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.AttributeSet;
import android.util.Log;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import io.github.leonhover.videorecorder.opengl.GLDrawer;

/**
 * 相机预览控件
 * Created by wangzongliang on 17-3-29.
 */

public class CameraView extends GLSurfaceView {

    private static final String TAG = "CameraView";

    private CameraRenderer mRenderer;
    protected SurfaceTexture mSurfaceTexture;
    private CameraSurfaceListener mCameraSurfaceListener;

    public CameraView(Context context) {
        this(context, null);
    }

    public CameraView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mRenderer = new CameraRenderer();
        setEGLContextClientVersion(2);    // GLES 2.0, API >= 8
        setRenderer(mRenderer);
        // the frequency of refreshing of camera preview is at most 15 fps
        // and RENDERMODE_WHEN_DIRTY is better to reduce power consumption
        setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
    }

    public void setCameraSurfaceListener(CameraSurfaceListener cameraSurfaceListener) {
        this.mCameraSurfaceListener = cameraSurfaceListener;
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onPause() {
        if (this.mCameraSurfaceListener != null) {
            this.mCameraSurfaceListener.onCameraSurfaceDestroy(mSurfaceTexture);
        }
        super.onPause();
    }


    private class CameraRenderer implements Renderer {

        private static final String TAG = "CameraRenderer";

        private GLDrawer mGLDrawer;
        private int mTextureId;

        private final float[] mSTMatrix = new float[16];
        private final float[] mMvpMatrix = new float[16];

        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            Log.d(TAG, "onSurfaceCreated");
            mGLDrawer = new GLDrawer();
            mTextureId = mGLDrawer.createTexture();
            mSurfaceTexture = new SurfaceTexture(mTextureId);
            mSurfaceTexture.setOnFrameAvailableListener(new SurfaceTexture.OnFrameAvailableListener() {
                @Override
                public void onFrameAvailable(SurfaceTexture surfaceTexture) {
                    Log.d(TAG, "onFrameAvailable");
                    requestRender();
                }
            });
            Matrix.setIdentityM(mMvpMatrix, 0);
            if (mCameraSurfaceListener != null) {
                mCameraSurfaceListener.onCameraSurfaceCreate(mSurfaceTexture);
            }
        }

        @Override
        public void onSurfaceChanged(GL10 gl, int width, int height) {
            Log.d(TAG, "onSurfaceChanged");
            if (mCameraSurfaceListener != null) {
                mCameraSurfaceListener.onCameraSurfaceChanged(mSurfaceTexture, width, height);
            }
        }

        @Override
        public void onDrawFrame(GL10 gl) {
            Log.d(TAG, "onDrawFrame");
            mSurfaceTexture.updateTexImage();
            // get texture matrix
            mSurfaceTexture.getTransformMatrix(mSTMatrix);
            mGLDrawer.draw(mTextureId, mMvpMatrix, mSTMatrix);
        }

    }

    public interface CameraSurfaceListener {
        void onCameraSurfaceCreate(SurfaceTexture surfaceTexture);

        void onCameraSurfaceChanged(SurfaceTexture surfaceTexture, int width, int height);

        void onCameraSurfaceDestroy(SurfaceTexture surfaceTexture);
    }
}
