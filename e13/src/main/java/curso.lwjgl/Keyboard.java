package curso.lwjgl;

import static org.lwjgl.glfw.GLFW.*;

public class Keyboard {

    private static final boolean[] keys = new boolean[GLFW_KEY_LAST + 1];

    public static void init(long window) {
        glfwSetKeyCallback(window, (win, key, scancode, action, mods) -> {
            if (key < 0 || key >= keys.length) return;

            if (action == GLFW_PRESS) keys[key] = true;
            if (action == GLFW_RELEASE) keys[key] = false;

            if (key == GLFW_KEY_ESCAPE && action == GLFW_RELEASE) {
                glfwSetWindowShouldClose(win, true);
            }
        });
    }

    public static boolean isKeyDown(int key) {
        return key >= 0 && key < keys.length && keys[key];
    }
}
