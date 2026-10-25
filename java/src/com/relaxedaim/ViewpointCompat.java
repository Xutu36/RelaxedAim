package com.relaxedaim;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Viewpoint（3D 第一/第三人称）兼容层：反射读取其状态，无硬依赖。
 *
 * 【加载器问题】ZB 给每个 mod 独立 ChildFirst ClassLoader，Class.forName("viewpoint.*", 本类加载器)
 * 会 CNFE。ZB 是 Java agent，故通过反射取其 Instrumentation（Loader.g_instrumentation），
 * 用 public 接口 Instrumentation.getAllLoadedClasses() 找到已加载的 viewpoint.core.View，
 * 再用 View 自己的类加载器 Class.forName 加载同 jar 的兄弟类。
 *
 * 【版本兼容】Viewpoint 更新后删除了 viewpoint.input.ThirdPerson，第三人称状态移到
 * viewpoint.core.View.thirdPerson；并新增 AimAssist、crosshairX/Y 等。这里所有字段都按
 * "可选" 绑定：任一字段不存在只置空，不影响其余字段与整体可用性。
 *
 * 懒解析：每帧 refresh()，未就绪每 2 秒重试；详细日志 + HUD debugStatus。
 *
 * 注意：开发阶段所有成员保持 public（见 RULES.md）。
 */
public final class ViewpointCompat {

    /** Viewpoint 是否已解析（View + Look 可用即 true）。 */
    public static boolean available = false;

    // ---- 每帧状态 ----
    public static boolean active = false;        // View.enabled
    public static boolean thirdPerson = false;   // View.thirdPerson
    public static boolean captured = false;      // Look.captured
    public static boolean freeCam = false;       // FreeCam.active
    public static float lookYawDeg = 0.0f;       // Look.yaw（度）
    public static float lookPitchDeg = 0.0f;     // Look.pitch（度）
    public static float crosshairX = 0.0f;       // View.crosshairX（屏幕）
    public static float crosshairY = 0.0f;       // View.crosshairY（屏幕）
    public static boolean crosshairOnTarget = false;
    public static boolean crosshairShown = false;
    public static float cameraX = 0.0f;
    public static float cameraY = 0.0f;
    public static float cameraUp = 0.0f;
    public static float frameFovY = 0.0f;

    /** Spike：武器瞄准偏移实际应用次数（诊断）。 */
    public static long spikeApplyCount = 0L;

    /** 诊断。 */
    public static String debugStatus = "not resolved";
    public static String debugHow = "";
    public static String debugMissing = "";      // 缺失字段列表

    private static Class<?> viewClass;
    private static Class<?> lookClass;
    private static Class<?> freeClass;

    private static Field fEnabled;
    private static Field fThirdPerson;
    private static Field fCrossX;
    private static Field fCrossY;
    private static Field fCrossOnTarget;
    private static Field fCrossShown;
    private static Field fCamX;
    private static Field fCamY;
    private static Field fCamUp;
    private static Field fFovY;
    private static Field fYaw;
    private static Field fPitch;
    private static Field fCaptured;
    private static Field fFreeActive;

    private static Instrumentation instrumentation;
    private static boolean instrumentationLookedUp = false;

    private static long lastResolveMs = 0L;
    private static long lastLogMs = 0L;
    private static int failLogCount = 0;
    private static final long RESOLVE_INTERVAL_MS = 2000L;

    public ViewpointCompat() {
    }

    public static void refresh() {
        if (!available) {
            tryResolve();
        }
        if (!available) {
            active = false;
            return;
        }
        try {
            active = readBool(fEnabled);
            thirdPerson = readBool(fThirdPerson);
            captured = readBool(fCaptured);
            freeCam = readBool(fFreeActive);
            lookYawDeg = (float) Math.toDegrees(readFloat(fYaw));
            lookPitchDeg = (float) Math.toDegrees(readFloat(fPitch));
            crosshairX = readFloat(fCrossX);
            crosshairY = readFloat(fCrossY);
            crosshairOnTarget = readBool(fCrossOnTarget);
            crosshairShown = readBool(fCrossShown);
            cameraX = readFloat(fCamX);
            cameraY = readFloat(fCamY);
            cameraUp = readFloat(fCamUp);
            frameFovY = readFloat(fFovY);
            debugStatus = "OK";
        } catch (Throwable t) {
            debugStatus = "read fail: " + t.getClass().getSimpleName();
            active = false;
        }
    }

    public static void tryResolve() {
        final long now = System.currentTimeMillis();
        if (now - lastResolveMs < RESOLVE_INTERVAL_MS) {
            return;
        }
        lastResolveMs = now;

        final StringBuilder err = new StringBuilder();

        // 途径 1：Instrumentation 找 View，再用它的加载器加载兄弟类
        final Class<?> viewByInst = findLoadedClass("viewpoint.core.View", err);
        if (viewByInst != null) {
            if (bindAll(viewByInst, err)) {
                return;
            }
        }

        // 途径 2：多加载器 Class.forName
        final ClassLoader[] loaders = new ClassLoader[]{
                ViewpointCompat.class.getClassLoader(),
                safeContextLoader(),
                ClassLoader.getSystemClassLoader()
        };
        for (final ClassLoader cl : loaders) {
            if (cl == null) {
                continue;
            }
            try {
                final Class<?> v = Class.forName("viewpoint.core.View", false, cl);
                if (bindAll(v, err)) {
                    debugHow = "loader:" + loaderName(cl);
                    return;
                }
            } catch (Throwable e) {
                err.append('[').append(loaderName(cl)).append(": ")
                        .append(e.getClass().getSimpleName()).append("] ");
            }
        }

        debugStatus = "FAIL " + err;
        final boolean logNow = failLogCount < 5 || (now - lastLogMs > 8000L);
        if (logNow) {
            failLogCount++;
            lastLogMs = now;
            log("Viewpoint NOT resolved. errors=" + err + " inst=" + (instrumentation != null));
        }
    }

    /** 用找到的 View 类绑定所有字段（字段可选，缺字段不致命）。 */
    private static boolean bindAll(Class<?> view, StringBuilder err) {
        try {
            final ClassLoader vp = view.getClassLoader();
            final Class<?> look = loadFrom(vp, "viewpoint.input.Look");
            Class<?> free = null;
            try {
                free = loadFrom(vp, "viewpoint.input.FreeCam");
            } catch (Throwable t) {
            }

            viewClass = view;
            lookClass = look;
            freeClass = free;

            final StringBuilder missing = new StringBuilder();
            fEnabled = reqField(view, "enabled", missing);
            fThirdPerson = optField(view, "thirdPerson", missing);
            fCrossX = optField(view, "crosshairX", missing);
            fCrossY = optField(view, "crosshairY", missing);
            fCrossOnTarget = optField(view, "crosshairOnTarget", missing);
            fCrossShown = optField(view, "crosshairShown", missing);
            fCamX = optField(view, "cameraX", missing);
            fCamY = optField(view, "cameraY", missing);
            fCamUp = optField(view, "cameraUp", missing);
            fFovY = optField(view, "frameFovY", missing);
            fYaw = reqField(look, "yaw", missing);
            fPitch = optField(look, "pitch", missing);
            fCaptured = optField(look, "captured", missing);
            fFreeActive = optField(free, "active", missing);

            if (fEnabled == null || fYaw == null) {
                err.append("[need View.enabled/Look.yaw]");
                return false;
            }

            available = true;
            debugHow = "instrumentation";
            debugMissing = missing.toString();
            debugStatus = "OK";
            log("resolved Viewpoint (View+Look). missing=" + (debugMissing.isEmpty() ? "none" : debugMissing));
            return true;
        } catch (Throwable e) {
            err.append("[bind: ").append(e.getClass().getSimpleName());
            if (e.getMessage() != null) {
                err.append(' ').append(e.getMessage());
            }
            err.append(']');
            return false;
        }
    }

    private static Field optField(Class<?> c, String name, StringBuilder missing) {
        if (c == null) {
            return null;
        }
        try {
            return c.getField(name);
        } catch (Throwable t) {
            if (missing.length() > 0) {
                missing.append(',');
            }
            missing.append(c.getSimpleName()).append('.').append(name);
            return null;
        }
    }

    private static Field reqField(Class<?> c, String name, StringBuilder missing) throws NoSuchFieldException {
        if (c == null) {
            throw new NoSuchFieldException(name);
        }
        return c.getField(name);
    }

    private static boolean readBool(Field f) {
        try {
            return f != null && f.getBoolean(null);
        } catch (Throwable t) {
            return false;
        }
    }

    private static float readFloat(Field f) {
        try {
            return f != null ? f.getFloat(null) : 0.0f;
        } catch (Throwable t) {
            return 0.0f;
        }
    }

    private static Class<?> findLoadedClass(String name, StringBuilder err) {
        final Instrumentation inst = getInstrumentation(err);
        if (inst == null) {
            return null;
        }
        try {
            final Class<?>[] all = inst.getAllLoadedClasses();
            if (all == null) {
                err.append("[inst: no classes]");
                return null;
            }
            for (final Class<?> c : all) {
                if (c != null && name.equals(c.getName())) {
                    return c;
                }
            }
            err.append("[inst: ").append(name).append(" not loaded]");
            return null;
        } catch (Throwable t) {
            err.append("[inst: ").append(t.getClass().getSimpleName()).append(']');
            return null;
        }
    }

    private static Class<?> loadFrom(ClassLoader loader, String name) throws ClassNotFoundException {
        return Class.forName(name, false, loader);
    }

    private static Instrumentation getInstrumentation(StringBuilder err) {
        if (instrumentationLookedUp) {
            return instrumentation;
        }
        instrumentationLookedUp = true;
        try {
            final Class<?> loaderCls = Class.forName("me.zed_0xff.zombie_buddy.Loader");
            try {
                final Method m = loaderCls.getDeclaredMethod("getInstrumentation");
                m.setAccessible(true);
                final Object o = m.invoke(null);
                if (o instanceof Instrumentation) {
                    instrumentation = (Instrumentation) o;
                    log("got Instrumentation via Loader.getInstrumentation()");
                    return instrumentation;
                }
            } catch (Throwable t) {
            }
            try {
                final Field f = loaderCls.getDeclaredField("g_instrumentation");
                f.setAccessible(true);
                final Object o = f.get(null);
                if (o instanceof Instrumentation) {
                    instrumentation = (Instrumentation) o;
                    log("got Instrumentation via Loader.g_instrumentation");
                    return instrumentation;
                }
            } catch (Throwable t) {
                err.append("[g_instrumentation: ").append(t.getClass().getSimpleName()).append(']');
            }
        } catch (Throwable t) {
            err.append("[Loader: ").append(t.getClass().getSimpleName()).append(']');
        }
        if (instrumentation == null) {
            err.append("[no instrumentation]");
        }
        return instrumentation;
    }

    private static ClassLoader safeContextLoader() {
        try {
            return Thread.currentThread().getContextClassLoader();
        } catch (Throwable t) {
            return null;
        }
    }

    private static String loaderName(ClassLoader cl) {
        if (cl == null) {
            return "null";
        }
        String n = cl.getClass().getSimpleName();
        if (n == null || n.isEmpty()) {
            n = cl.getClass().getName();
        }
        return n + "@" + Integer.toHexString(System.identityHashCode(cl));
    }

    private static void log(String msg) {
        try {
            System.out.println("[RelaxedAim][VP] " + msg);
        } catch (Throwable t) {
        }
    }
}
