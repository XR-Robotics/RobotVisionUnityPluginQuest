package com.xrobotoolkit.visionplugin.quest;
import android.graphics.SurfaceTexture;
import android.util.Log;
import android.view.Surface;

public class FBOPlugin implements SurfaceTexture.OnFrameAvailableListener {
    private static final String TAG = "FBOPlugin";
    private SurfaceTexture mSurfaceTexture;
    private Surface mSurface;
    private FBOTexture mFBOTexture;
    private int frameAvailableCount=0;
    public boolean released=true;
    private int oesTextureId;
    public void init() {
        if (!released) {
            release();
        }
        oesTextureId= FBOUtils.createOESTextureID();
       Log.i(TAG,"init oesTextureId："+oesTextureId + ",thread:" + Thread.currentThread());
        mSurfaceTexture = new SurfaceTexture(oesTextureId);
        mSurface = new Surface(mSurfaceTexture);
        mSurfaceTexture.setOnFrameAvailableListener(this);
        released=false;
    }

    public void BuildTexture(int textureId, int width, int height)
    {
        mSurfaceTexture.setDefaultBufferSize(width, height);
        mFBOTexture = new FBOTexture(width, height, textureId, oesTextureId);
        Log.i(TAG," BuildTexture width:"+width+" height:"+height);
        released=false;
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        Log.i(TAG,"onFrameAvailable");
        frameAvailableCount++;
    }

    public void updateTexture() {

        while (frameAvailableCount>0)
        {
            frameAvailableCount--;
            if(mFBOTexture!=null)
            {
                mSurfaceTexture.updateTexImage();
                mFBOTexture.draw();
            }
        }
    }
    public boolean isUpdateFrame() {
        return frameAvailableCount>0;
    }

    public Surface getSurface() {
        return mSurface;
    }
    public  SurfaceTexture getSurfaceTexture() {
        return mSurfaceTexture;
    }
    public void release() {

        Log.i(TAG,"release");
        FBOUtils.releaseOESTextureID(oesTextureId);
        if (mSurface != null) {
            mSurface.release();
            mSurface = null;
        }
        if (mSurfaceTexture != null) {
            mSurfaceTexture.release();
            mSurfaceTexture = null;
        }

        if (mFBOTexture != null) {
            mFBOTexture.release();
            mFBOTexture = null;
        }
        released=true;
    }

}
