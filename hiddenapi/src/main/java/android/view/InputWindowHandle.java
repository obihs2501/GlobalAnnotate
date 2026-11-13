package android.view;

import android.graphics.Region;
import android.os.IBinder;

public final class InputWindowHandle {
    public String name;
    public IBinder token;
    public int layoutParamsType;
    public long dispatchingTimeoutMillis;
    public int ownerPid;
    public int ownerUid;
    public float scaleFactor;
    public int inputConfig;
    public int displayId;
    public final Region touchableRegion = null;
    public boolean replaceTouchableRegionWithCrop;

    public InputWindowHandle(InputApplicationHandle inputApplicationHandle, int displayId) { throw new RuntimeException("Stub!"); }
    public void replaceTouchableRegionWithCrop(SurfaceControl bounds) { throw new RuntimeException("Stub!"); }
    public void setTrustedOverlay(SurfaceControl.Transaction t, SurfaceControl sc, boolean isTrusted) { throw new RuntimeException("Stub!"); }
}
