package io.github.leonhover.videorecorder.opengl;

import android.annotation.TargetApi;
import android.opengl.EGL14;
import android.opengl.EGLSurface;
import android.os.Build;
import android.util.Log;

/**
 * Created by wangzongliang on 17-3-29.
 */
@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class GLSurface {

    private static final String TAG = "GLSurface";

    private GLContext mGLContext;
    private EGLSurface mEGLSurface = EGL14.EGL_NO_SURFACE;

    private int mWidth = -1;
    private int mHeight = -1;

    public GLSurface(GLContext glContext) {
        this.mGLContext = glContext;
    }


    /**
     * Creates a window surface.
     * <p>
     *
     * @param surface May be a Surface or SurfaceTexture.
     */
    public void createSurface(Object surface) {
        if (mEGLSurface != EGL14.EGL_NO_SURFACE) {
            throw new IllegalStateException("surface already created");
        }
        mEGLSurface = mGLContext.createEGLSurface(surface);
    }

    /**
     * Creates an off-screen surface.
     */
    public void createSurface(int width, int height) {
        if (mEGLSurface != EGL14.EGL_NO_SURFACE) {
            throw new IllegalStateException("surface already created");
        }
        mEGLSurface = mGLContext.createEGLSurface(width, height);
        mWidth = width;
        mHeight = height;
    }

    /**
     * Returns the surface's width, in pixels.
     * <p>
     * If this is called on a window surface, and the underlying surface is in the process
     * of changing size, we may not see the new size right away (e.g. in the "surfaceChanged"
     * callback).  The size should match after the next buffer swap.
     */
    public int getWidth() {
        if (mWidth < 0) {
            return mGLContext.querySurface(mEGLSurface, EGL14.EGL_WIDTH);
        } else {
            return mWidth;
        }
    }

    /**
     * Returns the surface's height, in pixels.
     */
    public int getHeight() {
        if (mHeight < 0) {
            return mGLContext.querySurface(mEGLSurface, EGL14.EGL_HEIGHT);
        } else {
            return mHeight;
        }
    }

    /**
     * Release the EGL surface.
     */
    public void releaseEglSurface() {
        mGLContext.releaseEGLSurface(mEGLSurface);
        mEGLSurface = EGL14.EGL_NO_SURFACE;
        mWidth = mHeight = -1;
    }

    /**
     * Makes our EGL context and surface current.
     */
    public void makeCurrent() {
        mGLContext.makeCurrent(mEGLSurface, mEGLSurface);
    }

    /**
     * Makes our EGL context and surface current for drawing, using the supplied surface
     * for reading.
     */
    public void makeCurrentReadFrom(GLSurface readSurface) {
        mGLContext.makeCurrent(mEGLSurface, readSurface.mEGLSurface);
    }

    /**
     * Calls eglSwapBuffers.  Use this to "publish" the current frame.
     *
     * @return false on failure
     */
    public boolean swapBuffers() {
        boolean result = mGLContext.swapBuffers(mEGLSurface);
        if (!result) {
            Log.d(TAG, "WARNING: swapBuffers() failed");
        }
        return result;
    }

    /**
     * Sends the presentation time stamp to EGL.
     *
     * @param nsecs Timestamp, in nanoseconds.
     */
    public void setPresentationTime(long nsecs) {
        mGLContext.setPresentationTime(mEGLSurface, nsecs);
    }

}
