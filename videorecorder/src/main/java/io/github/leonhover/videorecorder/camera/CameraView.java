package io.github.leonhover.videorecorder.camera;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.SurfaceTexture;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;

import java.lang.ref.WeakReference;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import io.github.leonhover.videorecorder.R;
import io.github.leonhover.videorecorder.opengl.filter.GLSurfaceFilter;

/**
 * 相机预览控件
 * Created by wangzongliang on 17-3-29.
 */

public class CameraView extends GLSurfaceView implements SurfaceTexture.OnFrameAvailableListener {

    private static final String TAG = "CameraView";

    private final static float LAYOUT_RATIO_NONE = 0.0f;

    private final static int PREVIEW_ROTATION_0 = 0;
    private final static int PREVIEW_ROTATION_90 = 90;
    private final static int PREVIEW_ROTATION_180 = 180;
    private final static int PREVIEW_ROTATION_270 = 270;

    private CameraRenderer mRenderer;
    protected SurfaceTexture mSurfaceTexture;
    private CameraSurfaceListener mCameraSurfaceListener;
    //预览大小
    private int mPreviewWidth = 0;
    private int mPreviewHeight = 0;
    private int mPreviewRotation = 0;
    private GLSurfaceFilter mGLSurfaceFilter;
    private float mLayoutRatio = LAYOUT_RATIO_NONE;

    public CameraView(Context context) {
        this(context, null);
    }

    public CameraView(Context context, AttributeSet attrs) {
        super(context, attrs);

        TypedArray typedArray = null;

        try {
            typedArray = context.obtainStyledAttributes(attrs, R.styleable.CameraView);
            mLayoutRatio = typedArray.getFloat(R.styleable.CameraView_layout_ratio, LAYOUT_RATIO_NONE);
        } finally {
            if (typedArray != null) {
                typedArray.recycle();
            }
        }

        mRenderer = new CameraRenderer(this);
        setEGLContextClientVersion(2);
        setRenderer(mRenderer);
        setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
    }

    public void setCameraSurfaceListener(CameraSurfaceListener cameraSurfaceListener) {
        this.mCameraSurfaceListener = cameraSurfaceListener;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        super.surfaceCreated(holder);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        super.surfaceDestroyed(holder);
        this.mPreviewHeight = 0;
        this.mPreviewWidth = 0;
        mSurfaceTexture.setOnFrameAvailableListener(null);
        if (this.mCameraSurfaceListener != null) {
            this.mCameraSurfaceListener.onCameraSurfaceDestroy(mSurfaceTexture);
        }
        mSurfaceTexture.release();
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        requestRender();
    }

    private static class CameraRenderer implements Renderer {

        private static final String TAG = "CameraRenderer";
        //纹理ID
        private int mTextureId;
        //纹理坐标矩阵
        private final float[] mSTMatrix = new float[16];
        //坐标转换矩阵
        private final float[] mMvpMatrix = new float[16];

        private WeakReference<CameraView> mCameraViewRef;

        public CameraRenderer(CameraView cameraView) {
            this.mCameraViewRef = new WeakReference<CameraView>(cameraView);
        }

        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            Log.d(TAG, "onSurfaceCreated");

            final CameraView cameraView = mCameraViewRef.get();
            if (cameraView != null) {
                cameraView.mGLSurfaceFilter = new GLSurfaceFilter();

                Matrix.setIdentityM(mMvpMatrix, 0);
                mTextureId = cameraView.mGLSurfaceFilter.createTexture();
                cameraView.mSurfaceTexture = new SurfaceTexture(mTextureId);
                cameraView.mSurfaceTexture.setOnFrameAvailableListener(cameraView);
                if (cameraView.mCameraSurfaceListener != null) {
                    cameraView.mCameraSurfaceListener.onCameraSurfaceCreate(cameraView.mSurfaceTexture);
                }
            }
        }

        @Override
        public void onSurfaceChanged(GL10 gl, int width, int height) {
            Log.d(TAG, "onSurfaceChanged " + "width:" + width + ",height:" + height);
            updateViewPort();
            final CameraView cameraView = mCameraViewRef.get();
            if (cameraView != null) {
                if (cameraView.mCameraSurfaceListener != null) {
                    cameraView.mCameraSurfaceListener.onCameraSurfaceChanged(cameraView.mSurfaceTexture, width, height);
                }
            }
        }

        @Override
        public void onDrawFrame(GL10 gl) {
            final CameraView cameraView = mCameraViewRef.get();
            if (cameraView != null) {
                cameraView.mSurfaceTexture.updateTexImage();
                if (cameraView.mCameraSurfaceListener != null) {
                    cameraView.mCameraSurfaceListener.onCameraSurfaceUpdate(cameraView.mSurfaceTexture, mTextureId);
                }

                cameraView.mSurfaceTexture.getTransformMatrix(mSTMatrix);

                cameraView.mGLSurfaceFilter.draw(mTextureId, mSTMatrix);
            }

        }

        public void updateViewPort() {
            final CameraView cameraView = mCameraViewRef.get();
            if (cameraView != null) {
                final int viewWidth = cameraView.getWidth();
                final int viewHeight = cameraView.getHeight();
                final int previewWidth = cameraView.mPreviewWidth;
                final int previewHeight = cameraView.mPreviewHeight;

                //CenterCrop
                float scaleX = 1.0f;
                float scaleY = 1.0f;
                final double previewRatio = previewWidth * 1.0f / previewHeight;
                final double viewRatio = viewWidth * 1.0f / viewHeight;

                if (previewRatio < viewRatio) {
                    scaleY = (float) (previewHeight * 1.0f / (previewWidth / viewRatio));
                } else {
                    scaleX = (float) (previewWidth * 1.0f / (previewHeight * viewRatio));
                }

                Log.d(TAG, "scaleX:" + scaleX + ",scaleY:" + scaleY + ",previewRatio:" + previewRatio + ",viewRatio:" + viewRatio);
                Matrix.setIdentityM(mMvpMatrix, 0);
                Matrix.scaleM(mMvpMatrix, 0, scaleX, scaleY, 1.0f);

                if (cameraView.mGLSurfaceFilter != null) {
                    cameraView.mGLSurfaceFilter.setMvpMatrix(mMvpMatrix);
                }

            }
        }

    }

    /**
     * 设置预览的旋转角度，请在调用{link setPreviewSize}前调用，否则会抛出IllegalStateException.
     *
     * @param rotation 旋转角度
     */
    void setPreviewRotation(int rotation) {
        this.mPreviewRotation = rotation;
        if (this.mPreviewWidth * this.mPreviewHeight != 0) {
            throw new IllegalStateException("please invoke setPreviewRotation before setPreviewSize");
        }
    }

    /**
     * 设置预览大小
     * @param width 宽度
     * @param height 高度
     */
    public void setPreviewSize(int width, int height) {
        this.mPreviewWidth = width;
        this.mPreviewHeight = height;

        queueEvent(new Runnable() {
            @Override
            public void run() {
                mRenderer.updateViewPort();
            }
        });
    }

    public void setRatio(float ratio) {
        this.mLayoutRatio = ratio;
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (mLayoutRatio > 0) {
            int widthSpec = MeasureSpec.getSize(widthMeasureSpec);
            int newHeightMeasureSpec = MeasureSpec.makeMeasureSpec((int) (widthSpec / mLayoutRatio), MeasureSpec.EXACTLY);
            super.onMeasure(widthMeasureSpec, newHeightMeasureSpec);
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }

    public interface CameraSurfaceListener {
        void onCameraSurfaceCreate(SurfaceTexture surfaceTexture);

        void onCameraSurfaceChanged(SurfaceTexture surfaceTexture, int width, int height);

        void onCameraSurfaceDestroy(SurfaceTexture surfaceTexture);

        void onCameraSurfaceUpdate(SurfaceTexture surfaceTexture, int textureId);
    }
}
