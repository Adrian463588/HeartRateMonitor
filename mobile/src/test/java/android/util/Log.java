package android.util;

/**
 * Drop-in Log stub for JVM unit tests.
 *
 * Android's [android.util.Log] throws RuntimeException when called outside
 * the Android runtime. Placing this class at [src/test/java/android/util/Log.java]
 * puts it on the test classpath BEFORE the android.jar stub, so all Log calls
 * silently no-op instead of crashing.
 *
 * This is a standard technique for projects that cannot use Robolectric or
 * [testOptions.unitTests.returnDefaultValues] (e.g. when the Gradle + JVM
 * version combination prevents build.gradle recompilation).
 */
public class Log {
    public static int d(String tag, String msg) { return 0; }
    public static int d(String tag, String msg, Throwable tr) { return 0; }
    public static int i(String tag, String msg) { return 0; }
    public static int i(String tag, String msg, Throwable tr) { return 0; }
    public static int w(String tag, String msg) { return 0; }
    public static int w(String tag, String msg, Throwable tr) { return 0; }
    public static int w(String tag, Throwable tr) { return 0; }
    public static int e(String tag, String msg) { return 0; }
    public static int e(String tag, String msg, Throwable tr) { return 0; }
    public static int v(String tag, String msg) { return 0; }
    public static int v(String tag, String msg, Throwable tr) { return 0; }
    public static int wtf(String tag, String msg) { return 0; }
    public static int wtf(String tag, Throwable tr) { return 0; }
    public static int wtf(String tag, String msg, Throwable tr) { return 0; }
    public static String getStackTraceString(Throwable tr) { return ""; }
    public static boolean isLoggable(String tag, int level) { return false; }
    public static final int VERBOSE = 2;
    public static final int DEBUG   = 3;
    public static final int INFO    = 4;
    public static final int WARN    = 5;
    public static final int ERROR   = 6;
    public static final int ASSERT  = 7;
}
