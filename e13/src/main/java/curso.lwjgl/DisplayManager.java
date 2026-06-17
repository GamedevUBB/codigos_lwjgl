package curso.lwjgl;

import org.lwjgl.Version;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.glfw.GLFW.*;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryUtil.NULL;

public class DisplayManager {

    //Resolución de la ventana
    public static final int WIDTH = 1920;
    public static final int HEIGHT = 1080;
    //Título de la ventana
    private static final String TITLE = "Motor Gráfico LWJGL 3";
    //Puntero de la ventana GLFW
    public static long window;

    private static int fps;
    private static long fpsLastTime;
    private static int fpsCounter;
    private static long lastFrameTime;
    private static float delta;

    public static void createDisplay() {
        //Muestra la versión de LWJGL
        System.out.println("LWJGL " + Version.getVersion());
        //Muestra errores GLFW en la consola
        GLFWErrorCallback.createPrint(System.err).set();

        //GLFW debe iniciarse antes de cualquier llamada
        if (!glfwInit()) {
            throw new IllegalStateException("No se pudo inicializar GLFW");
        }

        //Solicita OpenGL 3.3
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 2);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        //Ventana oculta inicialmente
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        //Ventana redimensionable
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);

        //Crear la ventana
        window = glfwCreateWindow(WIDTH, HEIGHT, TITLE, NULL, NULL);
        if (window == NULL) {
            throw new RuntimeException("No se pudo crear la ventana GLFW");
        }

        //Callback de teclado para detectar las teclas de término
        glfwSetKeyCallback(window, (window, key, scancode, action, mods) -> {
            if (key == GLFW_KEY_ESCAPE && action == GLFW_RELEASE) { //Escape cierra ventana
                glfwSetWindowShouldClose(window, true);
            }
        });

        //Usa memoria temporal eficiente
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer pWidth = stack.mallocInt(1);
            IntBuffer pHeight = stack.mallocInt(1);
            //Obtiene el tamaño de la ventana
            glfwGetWindowSize(window, pWidth, pHeight);
            //Obtener la resolución del monitor
            GLFWVidMode vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());
            //Centrar la ventana
            glfwSetWindowPos(window,(vidmode.width() - pWidth.get(0)) / 2,(vidmode.height() - pHeight.get(0)) / 2);
        }

        //Activa el contexto OpenGL
        glfwMakeContextCurrent(window);
        //sincroniza con el refresco del monitor para evitar el tearing
        glfwSwapInterval(1);
        //Mostrar la ventana
        glfwShowWindow(window);
        //Crear capacidades OpenGL, sin esto OpenGL no funciona
        GL.createCapabilities();

        Keyboard.init(window);
        fpsLastTime = getCurrentTime();
        lastFrameTime = getCurrentTime();

        //Define el área de render
        glViewport(0, 0, WIDTH, HEIGHT);
    }

    private static long getCurrentTime() {
        return (long) (glfwGetTime() * 1000);
    }

    public static float getFrameTimeSeconds() {
        return delta;
    }

    public static void updateDisplay() {
        glfwSwapBuffers(window);
        glfwPollEvents();
        long currentFrameTime = getCurrentTime();
        delta = (currentFrameTime - lastFrameTime) / 1000f;
        lastFrameTime = currentFrameTime;

        // FPS
        fpsCounter++;
        if (currentFrameTime - fpsLastTime >= 1000) {
            fps = fpsCounter;
            System.out.println("FPS: " + fps);
            fpsCounter = 0;
            fpsLastTime = currentFrameTime;
        }
    }

    public static boolean shouldClose() {
        //Consulta si debe cerrarse la ventana
        return glfwWindowShouldClose(window);
    }

    public static void closeDisplay() {
        //Destruye la ventana
        glfwDestroyWindow(window);
        //Finaliza GLFW
        glfwTerminate();

        //Liberar el callback
        GLFWErrorCallback callback = glfwSetErrorCallback(null);
        if (callback != null) {
            callback.free();
        }
    }
}