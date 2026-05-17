import java.util.ArrayDeque;
import java.util.Queue;
import static org.lwjgl.glfw.GLFW.*;

public final class Keyboard {
    public static final int KEY_ESCAPE = GLFW_KEY_ESCAPE;
    public static final int KEY_LEFT = GLFW_KEY_LEFT;
    public static final int KEY_RIGHT = GLFW_KEY_RIGHT;
    public static final int KEY_UP = GLFW_KEY_UP;
    public static final int KEY_DOWN = GLFW_KEY_DOWN;
    public static final int KEY_PRIOR = GLFW_KEY_PAGE_UP;
    public static final int KEY_NEXT = GLFW_KEY_PAGE_DOWN;

    private static final class KeyEvent { int key; boolean state; KeyEvent(int k, boolean s){key=k;state=s;} }
    private static final Queue<KeyEvent> events = new ArrayDeque<>();
    private static KeyEvent current;

    private Keyboard() {}

    public static void create() {
        glfwSetKeyCallback(Display.getWindow(), (window, key, scancode, action, mods) -> {
            if (action == GLFW_PRESS || action == GLFW_RELEASE) {
                events.add(new KeyEvent(key, action == GLFW_PRESS));
            }
        });
    }

    public static void destroy() { glfwSetKeyCallback(Display.getWindow(), null); }
    public static boolean next() { current = events.poll(); return current != null; }
    public static int getEventKey() { return current != null ? current.key : 0; }
    public static boolean getEventKeyState() { return current != null && current.state; }
    public static boolean isKeyDown(int key) { return glfwGetKey(Display.getWindow(), key) == GLFW_PRESS; }
}
