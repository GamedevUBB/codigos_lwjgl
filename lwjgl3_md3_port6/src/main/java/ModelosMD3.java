
import org.lwjgl.opengl.*;
import org.joml.Matrix4f;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ModelosMD3 extends GLApp  {

    MD3Model md3Model;
    static float[] cameraPos = {0f,3f,35f};
    float cameraRotation = 0f;
    final float piover180 = 0.0174532925f;
    static float rotaModel = 0f;
    private SimpleShader shader;
    private Matrix4f projectionMatrix;

    private final String modelPath = "C:\\Users\\lgaja\\Downloads\\lwjgl3_md3_port5\\src\\lara";
    private final String modelName = "lara";
    private final String animationCfgPath = modelPath + "\\" + modelName + "_animation.cfg";
    private JFrame animationFrame;
    private JList<String> animationList;
    
    public void init() {
        System.out.println("ModelosMD3: init()\n");
        initDisplay();
        initInput();

        projectionMatrix = new Matrix4f().perspective((float)Math.toRadians(30.0f), aspectRatio, 0.01f, 500.0f);
        shader = new SimpleShader();

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        
        //buildFont("C:\\Users\\lgaja\\Downloads\\lwjgl3_md3_port5\\src\\images\\font_tahoma.png",14);
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		GL11.glEnable(GL11.GL_TEXTURE_2D);

        md3Model = new MD3Model();
        md3Model.loadModel(modelPath, modelName);
        md3Model.setTorsoAnimation("TORSO_STAND2");
        md3Model.setLegsAnimation("LEGS_LAND");

        crearInterfazAnimaciones(animationCfgPath);

        //GL11.glClearColor(0f, 0f,1f, 1);
    }


    public void run() {
        System.out.println("GLApp: run()\n");
        double delay = 0d;
        int fcount = 0;
        // Carga los ajustes desde el archivo config (tamao del display, resolucion, etc.)
        loadSettings("C:\\Users\\lgaja\\Downloads\\lwjgl3_md3_port5\\src\\main\\java\\GLApp.cfg");
        initBuffers();
        try {
            // Incializa Display, Keyboard, Mouse, OpenGL
            init();
            while (!finished) {
                if (!Display.isVisible()) {  //!!!
                    Thread.sleep(200L);
                }
                else if (Display.isCloseRequested()) {  //!!!
                    finished = true;
                }
                mainLoop();
                Display.update();  //!!!!
            }
        }
        catch (Exception e) {
            System.out.println("GLApp.run(): " + e);
            e.printStackTrace(System.out);
        }
        // preparando para salir
        cleanup();
        System.exit(0);
    }

    /**
     * Llamado por la funcion run().
     * inicializa los input y la animacion para cada cuadro.
     */
    public void mainLoop() {
        //System.out.println("GLApp: mainLoop()\n");
        // maneja los eventos del teclado
        while ( Keyboard.next() )  {
            if (Keyboard.getEventKey() == finishedKey) {
                finished = true;
            }
            // pasa el evento del teclado para manejarlo
            if (Keyboard.getEventKeyState()) {
                keyDown(Keyboard.getEventKey());
            }
            else {
                keyUp(Keyboard.getEventKey());
            }
        }
        // Redibuja la pantalla
        frameCount++;
        if ((getTime()-prevTime) > ticksPerSecond*1) {
            //System.out.println("==============> FramesPerSec=" + (frameCount/1) + " timeinsecs=" + getTimeInSeconds() + " timeinmillis=" + getTimeInMillis());
            prevTime = getTime();
            frameCount = 0;
        }
        render();
    }

    public void render() {
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

        setCameraPosition();

        rotaModel += 0.5f;

        Matrix4f viewMatrix = new Matrix4f()
                .rotateY((float)Math.toRadians(360.0f - cameraRotation))
                .translate(-cameraPos[0], -cameraPos[1], -cameraPos[2]);

        /*
        Matrix4f modelMatrix = new Matrix4f()
                .translate(0f, 1f, 5f)
                .scale(0.1f)
                .rotateY((float)Math.toRadians(rotaModel));
        */
        Matrix4f modelMatrix = new Matrix4f()
                .translate(0f, 1f, 5f)
                .rotateY((float)Math.toRadians(rotaModel))
                .scale(0.1f);

        shader.start();
        shader.loadProjectionMatrix(projectionMatrix);
        shader.loadViewMatrix(viewMatrix);
        md3Model.draw(modelMatrix, shader);
        shader.stop();
    }

    public static void setPerspective() {
        // En la versión con shaders, la matriz de proyección se calcula con JOML
        // y se envía como uniform al shader. Este método queda solo por compatibilidad.
    }
  
    public void setCameraPosition() {
        // Turn left
        if (Keyboard.isKeyDown(Keyboard.KEY_LEFT)) {
            cameraRotation += 1.0f;
        }
        // Turn right
        if (Keyboard.isKeyDown(Keyboard.KEY_RIGHT)) {
            cameraRotation -= 1.0f;
        }
        // move forward in current direction
        if (Keyboard.isKeyDown(Keyboard.KEY_UP)) {
            cameraPos[0] -= (float) Math.sin(cameraRotation * piover180) * .3f;
            cameraPos[2] -= (float) Math.cos(cameraRotation * piover180) * .3f;
        }
        // move backward in current direction
        if (Keyboard.isKeyDown(Keyboard.KEY_DOWN)) {
            cameraPos[0] += (float) Math.sin(cameraRotation * piover180) * .3f;
            cameraPos[2] += (float) Math.cos(cameraRotation * piover180) * .3f;
        }
        // move camera down
        if (Keyboard.isKeyDown(Keyboard.KEY_PRIOR)) {
            cameraPos[1] +=  .3f;
        }
        // move camera up
        if (Keyboard.isKeyDown(Keyboard.KEY_NEXT)) {
            cameraPos[1] -=  .3f;
        }
    }
    
    public void centerMouse() {}

	public void mouseMoved(MouseEvent mouseEvent) {}

    public void mouseMove(int x, int y) {}

    public void mouseMove(MouseEvent mouseEvent) {}

    public void mouseDown(int x, int y) {}
    
    public void mouseUp(int x, int y) {}

    public void keyDown(int keycode) {}

    public void keyUp(int keycode) {}

    private ModelosMD3() {}


    private void crearInterfazAnimaciones(String cfgPath) {
        List<String> opciones = leerOpcionesAnimacionDesdeCfg(cfgPath);

        SwingUtilities.invokeLater(() -> {
            animationFrame = new JFrame("Animaciones MD3");
            animationFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            animationFrame.setSize(300, 500);
            animationFrame.setLocation(40, 40);

            DefaultListModel<String> model = new DefaultListModel<>();
            for (String opcion : opciones) {
                model.addElement(opcion);
            }

            animationList = new JList<>(model);
            animationList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            animationList.addListSelectionListener(e -> {
                if (!e.getValueIsAdjusting()) {
                    String animacion = animationList.getSelectedValue();
                    if (animacion != null && md3Model != null) {
                        aplicarAnimacionSeleccionada(animacion);
                    }
                }
            });

            JLabel label = new JLabel("Seleccione una animación del archivo .cfg");
            animationFrame.add(label, BorderLayout.NORTH);
            animationFrame.add(new JScrollPane(animationList), BorderLayout.CENTER);
            animationFrame.setVisible(true);
        });
    }

    private List<String> leerOpcionesAnimacionDesdeCfg(String cfgPath) {
        List<String> opciones = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(cfgPath))) {
            String linea;
            while ((linea = reader.readLine()) != null) {
                int comentario = linea.indexOf("//");
                if (comentario >= 0) {
                    String opcion = linea.substring(comentario + 2).trim();
                    if (!opcion.isEmpty()) {
                        String[] partes = opcion.split("\\s+");
                        if (!partes[0].equals("animation") && !partes[0].equals("first"))
                            opciones.add(partes[0]);
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("No se pudo leer el archivo de animaciones: " + cfgPath);
            e.printStackTrace(System.out);
        }

        return opciones;
    }

    private void aplicarAnimacionSeleccionada(String animacion) {
        System.out.println("Animación seleccionada: " + animacion);

        if (animacion.startsWith("TORSO")) {
            md3Model.setTorsoAnimation(animacion);
        } else if (animacion.startsWith("LEGS")) {
            md3Model.setLegsAnimation(animacion);
        } else if (animacion.startsWith("BOTH")) {
            md3Model.setTorsoAnimation(animacion);
            md3Model.setLegsAnimation(animacion);
        } else {
            // Si el nombre no indica claramente la parte, se intenta aplicar a ambas.
            md3Model.setTorsoAnimation(animacion);
            md3Model.setLegsAnimation(animacion);
        }
    }


    @Override
    public void cleanup() {
        if (animationFrame != null) {
            animationFrame.dispose();
        }
        if (shader != null) {
            shader.cleanUp();
        }
        super.cleanup();
    }

    public static void main(String args[]) {
        System.out.println("ModelosMD3: main()\n");
        ModelosMD3 demo = new ModelosMD3();
        demo.run();
    }

}