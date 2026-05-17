import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryUtil.NULL;

public final class Display {
    private static long window;
    private static DisplayMode displayMode;
    private static String title = "LWJGL 3 Application";
    private static boolean fullscreen = false;
    private static boolean created = false;

    private Display() {}

    public static DisplayMode getDisplayMode() {
        if (!glfwInit()) {
            throw new LWJGLException("No se pudo inicializar GLFW");
        }
        GLFWVidMode vm = glfwGetVideoMode(glfwGetPrimaryMonitor());
        return new DisplayMode(vm.width(), vm.height(), vm.redBits() + vm.greenBits() + vm.blueBits(), vm.refreshRate());
    }

    public static DisplayMode[] getAvailableDisplayModes() {
        if (!glfwInit()) {
            throw new LWJGLException("No se pudo inicializar GLFW");
        }
        GLFWVidMode.Buffer modes = glfwGetVideoModes(glfwGetPrimaryMonitor());
        DisplayMode[] result = new DisplayMode[modes.limit()];
        for (int i = 0; i < modes.limit(); i++) {
            GLFWVidMode m = modes.get(i);
            result[i] = new DisplayMode(m.width(), m.height(), m.redBits() + m.greenBits() + m.blueBits(), m.refreshRate());
        }
        return result;
    }

    public static void setDisplayMode(DisplayMode mode) { displayMode = mode; }
    public static void setTitle(String value) {
        title = value;
        if (window != NULL) glfwSetWindowTitle(window, title);
    }
    public static void setFullscreen(boolean value) { fullscreen = value; }
    public static void setVSyncEnabled(boolean value) { glfwSwapInterval(value ? 1 : 0); }

    public static void create(PixelFormat ignored) {
        if (!glfwInit()) throw new LWJGLException("No se pudo inicializar GLFW");
        if (displayMode == null) displayMode = getDisplayMode();

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_COMPAT_PROFILE);

        long monitor = fullscreen ? glfwGetPrimaryMonitor() : NULL;
        window = glfwCreateWindow(displayMode.getWidth(), displayMode.getHeight(), title, monitor, NULL);
        if (window == NULL) throw new LWJGLException("No se pudo crear la ventana GLFW");

        glfwMakeContextCurrent(window);
        GL.createCapabilities();
        glfwSwapInterval(1);
        glfwShowWindow(window);
        created = true;
    }

    public static boolean isVisible() { return created && glfwGetWindowAttrib(window, GLFW_ICONIFIED) == GLFW_FALSE; }
    public static boolean isCloseRequested() { return created && glfwWindowShouldClose(window); }
    public static void update() { glfwSwapBuffers(window); glfwPollEvents(); }
    public static void destroy() { if (window != NULL) glfwDestroyWindow(window); glfwTerminate(); created = false; }
    public static void makeCurrent() { if (window != NULL) glfwMakeContextCurrent(window); }
    public static long getWindow() { return window; }
    public static int getWidth() { return displayMode != null ? displayMode.getWidth() : 0; }
    public static int getHeight() { return displayMode != null ? displayMode.getHeight() : 0; }
}
