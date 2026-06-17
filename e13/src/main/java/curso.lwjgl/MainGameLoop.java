package curso.lwjgl;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.assimp.*;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static curso.lwjgl.DisplayManager.HEIGHT;
import static curso.lwjgl.DisplayManager.WIDTH;
import static org.lwjgl.assimp.Assimp.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.stb.STBImage.*;
import static org.lwjgl.system.MemoryUtil.memAlloc;
import static org.lwjgl.system.MemoryUtil.memFree;

/*******************************************************************************************
 Bucle principal de una aplicación LWJGL/OpenGL.
 Esta versión modifica solo MainGameLoop para cargar un modelo 3D en formato GLB usando
 Assimp. No requiere modificar DisplayManager, Mesh, ObjLoader ni Keyboard.

 Se mantiene la configuración original de cámara y proyección del archivo subido.

 Controles:
 - Flecha izquierda / derecha: orbitar la cámara.
 - Flecha arriba / abajo: acercar o alejar la cámara.
 - W / S: subir o bajar la cámara.
 - A / D: rotar el modelo.
 - ESC: cerrar la ventana.

 Interfaz:
 - JComboBox para seleccionar la animación.
 - Al seleccionar una animación, se reinicia y se reproduce inmediatamente.
 - Botones para pausar/continuar y reiniciar.

 Ajuste visual:
 - El modelo se escala más grande para que se vea más cercano al espectador,
   sin modificar la cámara ni la proyección.

 Requisito:
 Copiar el modelo GLB de ejemplo en:
 src/main/resources/models/17_deadpool_mua.glb
 ******************************************************************************************/
public class MainGameLoop {

    private static final String GLB_MODEL_PATH = "src/main/resources/models/17_deadpool_mua.glb";
    private static final int MAX_BONES = 100;

    private static int shaderProgram;

    private static int locationMVPMatrix;
    private static int locationModelMatrix;
    private static int locationColor;
    private static int locationUseTexture;
    private static int locationTextureSampler;
    private static int locationLightDirection;
    private static int locationHasBones;
    private static int[] locationBones;

    private static GlbModel model;

    // Configuración original de cámara: no modificar.
    private static float cameraAngle = 30.0f;
    private static float cameraDistance = 5.0f;
    private static float cameraHeight = 2.0f;
    private static float modelAngle = 0.0f;

    private static boolean animationPaused = false;
    private static JFrame animationFrame;
    private static JComboBox<String> animationCombo;

    private static final FloatBuffer matrixBuffer = BufferUtils.createFloatBuffer(16);

    public static void main(String[] args) {
        DisplayManager.createDisplay();

        glClearColor(0.92f, 0.95f, 1.0f, 1.0f);
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);

        crearShader();
        cargarModeloGLB();
        crearInterfazAnimaciones();

        while (!DisplayManager.shouldClose()) {
            float delta = DisplayManager.getFrameTimeSeconds();

            leerTeclado(delta);

            if (model != null && !animationPaused) {
                model.update(delta);
            }

            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            render();

            DisplayManager.updateDisplay();
        }

        clean();
        DisplayManager.closeDisplay();
    }

    private static void cargarModeloGLB() {
        try {
            model = GlbModel.load(GLB_MODEL_PATH);
            System.out.println("Mallas cargadas: " + model.getMeshCount());
            System.out.println("Texturas cargadas: " + model.getTextureCount());
            System.out.println("Animaciones encontradas: " + model.getAnimationCount());
            System.out.println("Huesos encontrados: " + model.getBoneCount());
        } catch (IOException e) {
            throw new RuntimeException("No fue posible cargar el modelo GLB usando Assimp: " + GLB_MODEL_PATH, e);
        }
    }

    private static void crearInterfazAnimaciones() {
        if (model == null || model.getAnimationCount() == 0) {
            System.out.println("El modelo no contiene animaciones seleccionables.");
            return;
        }

        SwingUtilities.invokeLater(() -> {
            animationFrame = new JFrame("Selector de animación GLB");
            animationFrame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
            animationFrame.setLayout(new BorderLayout(8, 8));

            animationCombo = new JComboBox<>(model.getAnimationNames());
            animationCombo.addActionListener(e -> {
                int index = animationCombo.getSelectedIndex();
                if (index >= 0 && model != null) {
                    model.setActiveAnimationIndex(index);
                    model.resetAnimation();
                    animationPaused = false;
                    System.out.println("Animación seleccionada: " + animationCombo.getSelectedItem());
                }
            });

            JButton pauseButton = new JButton("Pausar / Continuar");
            pauseButton.addActionListener(e -> animationPaused = !animationPaused);

            JButton resetButton = new JButton("Reiniciar");
            resetButton.addActionListener(e -> {
                if (model != null) {
                    model.resetAnimation();
                    animationPaused = false;
                }
            });

            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER));
            buttons.add(pauseButton);
            buttons.add(resetButton);

            animationFrame.add(new JLabel("Seleccione la animación:"), BorderLayout.NORTH);
            animationFrame.add(animationCombo, BorderLayout.CENTER);
            animationFrame.add(buttons, BorderLayout.SOUTH);
            animationFrame.pack();
            animationFrame.setLocation(40, 40);
            animationFrame.setVisible(true);
        });
    }

    private static void leerTeclado(float delta) {
        float cameraSpeed = 90.0f * delta;
        float zoomSpeed = 3.0f * delta;
        float heightSpeed = 2.0f * delta;
        float modelRotationSpeed = 60.0f * delta;

        if (glfwGetKey(DisplayManager.window, GLFW_KEY_LEFT) == GLFW_PRESS)
            cameraAngle -= cameraSpeed;

        if (glfwGetKey(DisplayManager.window, GLFW_KEY_RIGHT) == GLFW_PRESS)
            cameraAngle += cameraSpeed;

        if (glfwGetKey(DisplayManager.window, GLFW_KEY_UP) == GLFW_PRESS)
            cameraDistance -= zoomSpeed;

        if (glfwGetKey(DisplayManager.window, GLFW_KEY_DOWN) == GLFW_PRESS)
            cameraDistance += zoomSpeed;

        if (glfwGetKey(DisplayManager.window, GLFW_KEY_W) == GLFW_PRESS)
            cameraHeight += heightSpeed;

        if (glfwGetKey(DisplayManager.window, GLFW_KEY_S) == GLFW_PRESS)
            cameraHeight -= heightSpeed;

        if (glfwGetKey(DisplayManager.window, GLFW_KEY_A) == GLFW_PRESS)
            modelAngle -= modelRotationSpeed;

        if (glfwGetKey(DisplayManager.window, GLFW_KEY_D) == GLFW_PRESS)
            modelAngle += modelRotationSpeed;

        if (cameraDistance < 1.0f) cameraDistance = 1.0f;
        if (cameraDistance > 5.0f) cameraDistance = 5.0f;
        if (cameraHeight < -5.0f) cameraHeight = -5.0f;
        if (cameraHeight > 10.0f) cameraHeight = 10.0f;
    }

    private static void render() {
        if (model == null) return;

        // Configuración original de proyección: no modificar.
        Matrix4f projection = new Matrix4f()
                .perspective((float) Math.toRadians(60.0f),
                        (float) WIDTH / HEIGHT,
                        0.1f,
                        100.0f);

        float camX = (float) Math.sin(Math.toRadians(cameraAngle)) * cameraDistance;
        float camZ = (float) Math.cos(Math.toRadians(cameraAngle)) * cameraDistance;

        // Configuración original de cámara: no modificar.
        Matrix4f view = new Matrix4f()
                .lookAt(
                        camX, cameraHeight, camZ,
                        0.0f, 1.0f, 0.0f,
                        0.0f, 1.0f, 0.0f
                );

        Matrix4f vp = new Matrix4f(projection).mul(view);

        Matrix4f modelMatrix = new Matrix4f()
                .identity()
                .scale(model.getRecommendedScale())
                .rotateY((float) Math.toRadians(modelAngle))
                .translate(model.getCenterOffset());

        glUseProgram(shaderProgram);

        glUniform3f(locationLightDirection, -0.35f, -1.0f, -0.25f);
        glUniform1i(locationTextureSampler, 0);

        model.render(vp, modelMatrix);

        glUseProgram(0);
    }

    private static void enviarMVP(Matrix4f mvp) {
        matrixBuffer.clear();
        mvp.get(matrixBuffer);
        glUniformMatrix4fv(locationMVPMatrix, false, matrixBuffer);
    }

    private static void enviarModelo(Matrix4f modelMatrix) {
        matrixBuffer.clear();
        modelMatrix.get(matrixBuffer);
        glUniformMatrix4fv(locationModelMatrix, false, matrixBuffer);
    }

    private static void enviarBoneMatrix(int index, Matrix4f matrix) {
        if (index < 0 || index >= locationBones.length) return;
        matrixBuffer.clear();
        matrix.get(matrixBuffer);
        glUniformMatrix4fv(locationBones[index], false, matrixBuffer);
    }

    private static void crearShader() {
        String vertexShaderSource =
                "#version 150 core\n" +
                "const int MAX_BONES = " + MAX_BONES + ";\n" +
                "in vec3 position;\n" +
                "in vec3 normal;\n" +
                "in vec2 texCoord;\n" +
                "in vec4 boneIds;\n" +
                "in vec4 weights;\n" +
                "\n" +
                "uniform mat4 uMVP;\n" +
                "uniform mat4 uModel;\n" +
                "uniform mat4 uBones[MAX_BONES];\n" +
                "uniform bool uHasBones;\n" +
                "\n" +
                "out vec3 passNormal;\n" +
                "out vec2 passTexCoord;\n" +
                "\n" +
                "void main() {\n" +
                "    vec4 localPosition = vec4(position, 1.0);\n" +
                "    vec3 localNormal = normal;\n" +
                "\n" +
                "    if (uHasBones && (weights.x + weights.y + weights.z + weights.w) > 0.0) {\n" +
                "        int b0 = int(boneIds.x + 0.5);\n" +
                "        int b1 = int(boneIds.y + 0.5);\n" +
                "        int b2 = int(boneIds.z + 0.5);\n" +
                "        int b3 = int(boneIds.w + 0.5);\n" +
                "        mat4 skin = mat4(0.0);\n" +
                "        skin += uBones[b0] * weights.x;\n" +
                "        skin += uBones[b1] * weights.y;\n" +
                "        skin += uBones[b2] * weights.z;\n" +
                "        skin += uBones[b3] * weights.w;\n" +
                "        localPosition = skin * localPosition;\n" +
                "        localNormal = mat3(skin) * normal;\n" +
                "    }\n" +
                "\n" +
                "    gl_Position = uMVP * localPosition;\n" +
                "    passNormal = mat3(transpose(inverse(uModel))) * localNormal;\n" +
                "    passTexCoord = texCoord;\n" +
                "}\n";

        String fragmentShaderSource =
                "#version 150 core\n" +
                "in vec3 passNormal;\n" +
                "in vec2 passTexCoord;\n" +
                "\n" +
                "uniform vec3 uColor;\n" +
                "uniform bool uUseTexture;\n" +
                "uniform sampler2D uTexture;\n" +
                "uniform vec3 uLightDirection;\n" +
                "\n" +
                "out vec4 out_Color;\n" +
                "\n" +
                "void main() {\n" +
                "    vec4 base = uUseTexture ? texture(uTexture, passTexCoord) : vec4(uColor, 1.0);\n" +
                "    if (base.a < 0.1) discard;\n" +
                "    vec3 n = normalize(passNormal);\n" +
                "    vec3 l = normalize(-uLightDirection);\n" +
                "    float diffuse = max(dot(n, l), 0.0);\n" +
                "    float ambient = 0.38;\n" +
                "    vec3 finalColor = base.rgb * (ambient + diffuse * 0.62);\n" +
                "    out_Color = vec4(finalColor, base.a);\n" +
                "}\n";

        int vertexShader = compileShader(vertexShaderSource, GL_VERTEX_SHADER);
        int fragmentShader = compileShader(fragmentShaderSource, GL_FRAGMENT_SHADER);

        shaderProgram = glCreateProgram();

        glAttachShader(shaderProgram, vertexShader);
        glAttachShader(shaderProgram, fragmentShader);

        glBindAttribLocation(shaderProgram, 0, "position");
        glBindAttribLocation(shaderProgram, 1, "normal");
        glBindAttribLocation(shaderProgram, 2, "texCoord");
        glBindAttribLocation(shaderProgram, 3, "boneIds");
        glBindAttribLocation(shaderProgram, 4, "weights");

        glLinkProgram(shaderProgram);

        if (glGetProgrami(shaderProgram, GL_LINK_STATUS) == GL_FALSE) {
            throw new RuntimeException("Error enlazando shader:\n" + glGetProgramInfoLog(shaderProgram));
        }

        locationMVPMatrix = glGetUniformLocation(shaderProgram, "uMVP");
        locationModelMatrix = glGetUniformLocation(shaderProgram, "uModel");
        locationColor = glGetUniformLocation(shaderProgram, "uColor");
        locationUseTexture = glGetUniformLocation(shaderProgram, "uUseTexture");
        locationTextureSampler = glGetUniformLocation(shaderProgram, "uTexture");
        locationLightDirection = glGetUniformLocation(shaderProgram, "uLightDirection");
        locationHasBones = glGetUniformLocation(shaderProgram, "uHasBones");

        locationBones = new int[MAX_BONES];
        for (int i = 0; i < MAX_BONES; i++) {
            locationBones[i] = glGetUniformLocation(shaderProgram, "uBones[" + i + "]");
        }

        glDeleteShader(vertexShader);
        glDeleteShader(fragmentShader);
    }

    private static int compileShader(String source, int type) {
        int shaderID = glCreateShader(type);
        glShaderSource(shaderID, source);
        glCompileShader(shaderID);

        if (glGetShaderi(shaderID, GL_COMPILE_STATUS) == GL_FALSE) {
            throw new RuntimeException("Error compilando shader:\n" + glGetShaderInfoLog(shaderID));
        }

        return shaderID;
    }

    private static void clean() {
        if (animationFrame != null) {
            SwingUtilities.invokeLater(() -> animationFrame.dispose());
        }
        if (model != null) model.clean();
        glDeleteProgram(shaderProgram);
    }

    /*******************************************************************************************
     Modelo GLB cargado con Assimp, texturas y animación.
     ******************************************************************************************/
    private static class GlbModel {

        private final List<GlbMesh> meshes;
        private final Map<Integer, Texture> texturesByMaterial;
        private final Node rootNode;
        private final List<Animation> animations;
        private final String[] animationNames;
        private final org.joml.Vector3f centerOffset;
        private final float recommendedScale;
        private final List<BoneInfo> boneInfos;
        private final Map<String, Integer> boneIndexByName;
        private final Matrix4f globalInverseTransform;
        private final Matrix4f[] finalBoneMatrices;

        private volatile int activeAnimationIndex = 0;
        private double animationTimeSeconds = 0.0;

        private GlbModel(List<GlbMesh> meshes,
                         Map<Integer, Texture> texturesByMaterial,
                         Node rootNode,
                         List<Animation> animations,
                         org.joml.Vector3f centerOffset,
                         float recommendedScale,
                         List<BoneInfo> boneInfos,
                         Map<String, Integer> boneIndexByName,
                         Matrix4f globalInverseTransform) {
            this.meshes = meshes;
            this.texturesByMaterial = texturesByMaterial;
            this.rootNode = rootNode;
            this.animations = animations;
            this.animationNames = buildAnimationNames(animations);
            this.centerOffset = centerOffset;
            this.recommendedScale = recommendedScale;
            this.boneInfos = boneInfos;
            this.boneIndexByName = boneIndexByName;
            this.globalInverseTransform = globalInverseTransform;
            this.finalBoneMatrices = new Matrix4f[MAX_BONES];

            for (int i = 0; i < MAX_BONES; i++) {
                finalBoneMatrices[i] = new Matrix4f().identity();
            }
        }

        public static GlbModel load(String file) throws IOException {
            int flags = aiProcess_Triangulate
                    | aiProcess_JoinIdenticalVertices
                    | aiProcess_GenSmoothNormals
                    | aiProcess_ImproveCacheLocality
                    | aiProcess_LimitBoneWeights
                    | aiProcess_SortByPType
                    | aiProcess_FlipUVs;

            AIScene scene = aiImportFile(file, flags);

            if (scene == null || scene.mRootNode() == null) {
                throw new IOException("Assimp no pudo cargar el archivo GLB: " + file + "\n" + aiGetErrorString());
            }

            List<GlbMesh> loadedMeshes = new ArrayList<>();
            Map<Integer, Texture> materialTextures = new HashMap<>();
            List<BoneInfo> boneInfos = new ArrayList<>();
            Map<String, Integer> boneIndexByName = new HashMap<>();

            float minX = Float.POSITIVE_INFINITY;
            float minY = Float.POSITIVE_INFINITY;
            float minZ = Float.POSITIVE_INFINITY;
            float maxX = Float.NEGATIVE_INFINITY;
            float maxY = Float.NEGATIVE_INFINITY;
            float maxZ = Float.NEGATIVE_INFINITY;

            try {
                materialTextures.putAll(loadMaterialTextures(scene, file));

                PointerBuffer sceneMeshes = scene.mMeshes();

                if (sceneMeshes == null || scene.mNumMeshes() == 0) {
                    throw new IOException("El GLB no contiene mallas: " + file);
                }

                for (int meshIndex = 0; meshIndex < scene.mNumMeshes(); meshIndex++) {
                    AIMesh aiMesh = AIMesh.create(sceneMeshes.get(meshIndex));

                    AIVector3D.Buffer vertices = aiMesh.mVertices();
                    AIVector3D.Buffer normals = aiMesh.mNormals();
                    AIVector3D.Buffer texCoords = aiMesh.mTextureCoords(0);
                    AIFace.Buffer faces = aiMesh.mFaces();

                    VertexBoneData[] vertexBoneData = new VertexBoneData[aiMesh.mNumVertices()];
                    for (int i = 0; i < vertexBoneData.length; i++) {
                        vertexBoneData[i] = new VertexBoneData();
                    }
                    loadBones(aiMesh, vertexBoneData, boneInfos, boneIndexByName);

                    List<Float> vertexData = new ArrayList<>();

                    for (int faceIndex = 0; faceIndex < aiMesh.mNumFaces(); faceIndex++) {
                        AIFace face = faces.get(faceIndex);

                        for (int indexInFace = 0; indexInFace < face.mNumIndices(); indexInFace++) {
                            int vertexIndex = face.mIndices().get(indexInFace);

                            AIVector3D position = vertices.get(vertexIndex);
                            AIVector3D normal = normals != null ? normals.get(vertexIndex) : null;
                            AIVector3D uv = texCoords != null ? texCoords.get(vertexIndex) : null;
                            VertexBoneData boneData = vertexBoneData[vertexIndex];
                            boneData.normalizeWeights();

                            float x = position.x();
                            float y = position.y();
                            float z = position.z();

                            minX = Math.min(minX, x);
                            minY = Math.min(minY, y);
                            minZ = Math.min(minZ, z);
                            maxX = Math.max(maxX, x);
                            maxY = Math.max(maxY, y);
                            maxZ = Math.max(maxZ, z);

                            vertexData.add(x);
                            vertexData.add(y);
                            vertexData.add(z);

                            if (normal != null) {
                                vertexData.add(normal.x());
                                vertexData.add(normal.y());
                                vertexData.add(normal.z());
                            } else {
                                vertexData.add(0.0f);
                                vertexData.add(1.0f);
                                vertexData.add(0.0f);
                            }

                            if (uv != null) {
                                vertexData.add(uv.x());
                                vertexData.add(uv.y());
                            } else {
                                vertexData.add(0.0f);
                                vertexData.add(0.0f);
                            }

                            vertexData.add((float) boneData.ids[0]);
                            vertexData.add((float) boneData.ids[1]);
                            vertexData.add((float) boneData.ids[2]);
                            vertexData.add((float) boneData.ids[3]);

                            vertexData.add(boneData.weights[0]);
                            vertexData.add(boneData.weights[1]);
                            vertexData.add(boneData.weights[2]);
                            vertexData.add(boneData.weights[3]);
                        }
                    }

                    float[] data = new float[vertexData.size()];
                    for (int i = 0; i < data.length; i++) {
                        data[i] = vertexData.get(i);
                    }

                    float[] color = getMaterialColor(scene, aiMesh);
                    loadedMeshes.add(new GlbMesh(data, aiMesh.mMaterialIndex(), color, aiMesh.mNumBones() > 0));
                }

                /*
                 * Encuadre corregido:
                 * El cálculo anterior usaba solo las coordenadas locales de cada malla.
                 * En modelos GLB animados, las mallas suelen estar bajo nodos con
                 * transformaciones propias. Si esas transformaciones no se consideran,
                 * el centro calculado puede quedar lejos del origen y el modelo no aparece
                 * frente a la cámara.
                 *
                 * Aquí se calcula el bounding box en espacio de escena, aplicando la
                 * jerarquía de nodos original de Assimp. No se modifica la proyección ni
                 * la configuración de cámara solicitada.
                 */
                Bounds sceneBounds = calculateSceneBounds(scene, scene.mRootNode());

                if (!sceneBounds.isValid()) {
                    sceneBounds.add(minX, minY, minZ);
                    sceneBounds.add(maxX, maxY, maxZ);
                }

                float centerX = (sceneBounds.minX + sceneBounds.maxX) * 0.5f;
                float centerY = (sceneBounds.minY + sceneBounds.maxY) * 0.5f;
                float centerZ = (sceneBounds.minZ + sceneBounds.maxZ) * 0.5f;

                float sizeX = sceneBounds.maxX - sceneBounds.minX;
                float sizeY = sceneBounds.maxY - sceneBounds.minY;
                float sizeZ = sceneBounds.maxZ - sceneBounds.minZ;
                float maxSize = Math.max(sizeX, Math.max(sizeY, sizeZ));

                /*
                 * Aumenta el tamaño visual del modelo sin modificar la cámara
                 * ni la proyección. Antes se usaba 2.5f / maxSize;
                 * ahora se usa 4.6f / maxSize para que el personaje se vea
                 * más grande y cercano frente al espectador.
                 */
                float scale = maxSize > 0.0f ? 4.6f / maxSize : 1.0f;

                /*
                 * La cámara original mira al punto y=1.0.
                 * Para que el espectador vea el modelo justo frente a él,
                 * se desplaza el centro visual del modelo a ese mismo punto.
                 */
                float cameraTargetY = 1.0f;
                org.joml.Vector3f offset = new org.joml.Vector3f(
                        -centerX,
                        (cameraTargetY / scale) - centerY,
                        -centerZ
                );

                Node root = loadNode(scene.mRootNode());
                List<Animation> loadedAnimations = loadAnimations(scene);
                Matrix4f globalInverse = new Matrix4f(toMatrix(scene.mRootNode().mTransformation())).invert();

                return new GlbModel(loadedMeshes, materialTextures, root, loadedAnimations, offset, scale,
                        boneInfos, boneIndexByName, globalInverse);
            } finally {
                aiReleaseImport(scene);
            }
        }

        private static void loadBones(AIMesh aiMesh,
                                      VertexBoneData[] vertexBoneData,
                                      List<BoneInfo> boneInfos,
                                      Map<String, Integer> boneIndexByName) {
            PointerBuffer bones = aiMesh.mBones();
            if (bones == null) return;

            for (int boneIdx = 0; boneIdx < aiMesh.mNumBones(); boneIdx++) {
                AIBone aiBone = AIBone.create(bones.get(boneIdx));
                String boneName = aiBone.mName().dataString();

                Integer globalBoneIndex = boneIndexByName.get(boneName);
                if (globalBoneIndex == null) {
                    if (boneInfos.size() >= MAX_BONES) {
                        System.err.println("Advertencia: se superó MAX_BONES=" + MAX_BONES + ". Se omitirá el hueso: " + boneName);
                        continue;
                    }

                    globalBoneIndex = boneInfos.size();
                    boneIndexByName.put(boneName, globalBoneIndex);
                    boneInfos.add(new BoneInfo(boneName, toMatrix(aiBone.mOffsetMatrix())));
                }

                AIVertexWeight.Buffer weights = aiBone.mWeights();
                for (int weightIndex = 0; weightIndex < aiBone.mNumWeights(); weightIndex++) {
                    AIVertexWeight weight = weights.get(weightIndex);
                    int vertexId = weight.mVertexId();

                    if (vertexId >= 0 && vertexId < vertexBoneData.length) {
                        vertexBoneData[vertexId].addBoneData(globalBoneIndex, weight.mWeight());
                    }
                }
            }
        }

        private static Node loadNode(AINode aiNode) {
            Node node = new Node(aiNode.mName().dataString(), toMatrix(aiNode.mTransformation()));

            IntBuffer meshIndices = aiNode.mMeshes();
            if (meshIndices != null) {
                for (int i = 0; i < aiNode.mNumMeshes(); i++) {
                    node.meshIndices.add(meshIndices.get(i));
                }
            }

            PointerBuffer children = aiNode.mChildren();
            if (children != null) {
                for (int i = 0; i < aiNode.mNumChildren(); i++) {
                    node.children.add(loadNode(AINode.create(children.get(i))));
                }
            }

            return node;
        }

        private static List<Animation> loadAnimations(AIScene scene) {
            List<Animation> result = new ArrayList<>();
            PointerBuffer animations = scene.mAnimations();

            if (animations == null) return result;

            for (int i = 0; i < scene.mNumAnimations(); i++) {
                AIAnimation aiAnimation = AIAnimation.create(animations.get(i));
                result.add(Animation.from(aiAnimation, i));
            }

            return result;
        }

        private static Map<Integer, Texture> loadMaterialTextures(AIScene scene, String modelFile) {
            Map<Integer, Texture> result = new HashMap<>();
            PointerBuffer materials = scene.mMaterials();
            if (materials == null) return result;

            for (int materialIndex = 0; materialIndex < scene.mNumMaterials(); materialIndex++) {
                AIMaterial material = AIMaterial.create(materials.get(materialIndex));

                Texture texture = null;

                texture = loadTextureForMaterial(scene, material, modelFile, aiTextureType_BASE_COLOR);

                if (texture == null) texture = loadTextureForMaterial(scene, material, modelFile, aiTextureType_DIFFUSE);
                if (texture == null) texture = loadTextureForMaterial(scene, material, modelFile, aiTextureType_AMBIENT);
                if (texture == null) texture = loadTextureForMaterial(scene, material, modelFile, aiTextureType_UNKNOWN);

                if (texture != null) {
                    result.put(materialIndex, texture);
                    System.out.println("Textura asignada al material " + materialIndex);
                }
            }

            System.out.println("Texturas cargadas: " + result.size());
            return result;
        }

        private static Texture loadTextureForMaterial(AIScene scene, AIMaterial material, String modelFile, int textureType) {
            AIString path = AIString.calloc();

            try {
                int textureCount = aiGetMaterialTextureCount(material, textureType);
                if (textureCount <= 0) return null;

                int result = aiGetMaterialTexture(
                        material,
                        textureType,
                        0,
                        path,
                        (IntBuffer) null,
                        null,
                        null,
                        null,
                        null,
                        null
                );

                if (result != aiReturn_SUCCESS) return null;

                String texturePath = path.dataString();
                if (texturePath == null || texturePath.isEmpty()) return null;

                if (texturePath.startsWith("*")) {
                    int textureIndex = Integer.parseInt(texturePath.substring(1));
                    return loadEmbeddedTexture(scene, textureIndex);
                }

                Path modelDirectory = Paths.get(modelFile).toAbsolutePath().getParent();
                Path externalTexture = modelDirectory.resolve(texturePath).normalize();

                if (!Files.exists(externalTexture)) {
                    System.err.println("Textura externa no encontrada: " + externalTexture);
                    return null;
                }

                return Texture.fromFile(externalTexture.toString());
            } catch (Exception e) {
                System.err.println("No fue posible cargar textura del material: " + e.getMessage());
                return null;
            } finally {
                path.free();
            }
        }

        private static Texture loadEmbeddedTexture(AIScene scene, int textureIndex) {
            PointerBuffer textures = scene.mTextures();

            if (textures == null || textureIndex < 0 || textureIndex >= scene.mNumTextures()) {
                return null;
            }

            AITexture aiTexture = AITexture.create(textures.get(textureIndex));

            try {
                if (aiTexture.mHeight() == 0) {
                    ByteBuffer compressed = aiTexture.pcDataCompressed();
                    return Texture.fromMemory(compressed);
                }

                int width = aiTexture.mWidth();
                int height = aiTexture.mHeight();
                AITexel.Buffer texels = aiTexture.pcData();
                ByteBuffer rgba = BufferUtils.createByteBuffer(width * height * 4);

                for (int i = 0; i < width * height; i++) {
                    AITexel texel = texels.get(i);
                    rgba.put(texel.r());
                    rgba.put(texel.g());
                    rgba.put(texel.b());
                    rgba.put(texel.a());
                }

                rgba.flip();
                return Texture.fromRGBA(rgba, width, height);
            } catch (Exception e) {
                System.err.println("No fue posible cargar textura embebida: " + e.getMessage());
                return null;
            }
        }

        private static float[] getMaterialColor(AIScene scene, AIMesh mesh) {
            float[] defaultColor = new float[] {0.75f, 0.75f, 0.75f};

            PointerBuffer materials = scene.mMaterials();
            if (materials == null || mesh.mMaterialIndex() < 0 || mesh.mMaterialIndex() >= scene.mNumMaterials()) {
                return defaultColor;
            }

            AIMaterial material = AIMaterial.create(materials.get(mesh.mMaterialIndex()));
            AIColor4D color = AIColor4D.create();

            int result = aiGetMaterialColor(material, AI_MATKEY_COLOR_DIFFUSE, aiTextureType_NONE, 0, color);

            if (result == aiReturn_SUCCESS) {
                return new float[] {
                        clamp(color.r(), 0.05f, 1.0f),
                        clamp(color.g(), 0.05f, 1.0f),
                        clamp(color.b(), 0.05f, 1.0f)
                };
            }

            return defaultColor;
        }


        private static Bounds calculateSceneBounds(AIScene scene, AINode rootNode) {
            Bounds bounds = new Bounds();
            calculateSceneBoundsRecursive(scene, rootNode, new Matrix4f().identity(), bounds);
            return bounds;
        }

        private static void calculateSceneBoundsRecursive(AIScene scene,
                                                          AINode aiNode,
                                                          Matrix4f parentTransform,
                                                          Bounds bounds) {
            Matrix4f localTransform = toMatrix(aiNode.mTransformation());
            Matrix4f globalTransform = new Matrix4f(parentTransform).mul(localTransform);

            IntBuffer meshIndices = aiNode.mMeshes();
            PointerBuffer sceneMeshes = scene.mMeshes();

            if (meshIndices != null && sceneMeshes != null) {
                for (int i = 0; i < aiNode.mNumMeshes(); i++) {
                    int meshIndex = meshIndices.get(i);
                    if (meshIndex < 0 || meshIndex >= scene.mNumMeshes()) continue;

                    AIMesh mesh = AIMesh.create(sceneMeshes.get(meshIndex));
                    AIVector3D.Buffer vertices = mesh.mVertices();

                    for (int v = 0; v < mesh.mNumVertices(); v++) {
                        AIVector3D vertex = vertices.get(v);
                        Vector3f p = new Vector3f(vertex.x(), vertex.y(), vertex.z());
                        globalTransform.transformPosition(p);
                        bounds.add(p.x, p.y, p.z);
                    }
                }
            }

            PointerBuffer children = aiNode.mChildren();
            if (children != null) {
                for (int i = 0; i < aiNode.mNumChildren(); i++) {
                    calculateSceneBoundsRecursive(scene, AINode.create(children.get(i)), globalTransform, bounds);
                }
            }
        }

        private static class Bounds {
            private float minX = Float.POSITIVE_INFINITY;
            private float minY = Float.POSITIVE_INFINITY;
            private float minZ = Float.POSITIVE_INFINITY;
            private float maxX = Float.NEGATIVE_INFINITY;
            private float maxY = Float.NEGATIVE_INFINITY;
            private float maxZ = Float.NEGATIVE_INFINITY;

            private void add(float x, float y, float z) {
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                minZ = Math.min(minZ, z);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
                maxZ = Math.max(maxZ, z);
            }

            private boolean isValid() {
                return Float.isFinite(minX) && Float.isFinite(minY) && Float.isFinite(minZ)
                        && Float.isFinite(maxX) && Float.isFinite(maxY) && Float.isFinite(maxZ)
                        && maxX >= minX && maxY >= minY && maxZ >= minZ;
            }
        }

        private static Matrix4f toMatrix(AIMatrix4x4 ai) {
            return new Matrix4f(
                    ai.a1(), ai.b1(), ai.c1(), ai.d1(),
                    ai.a2(), ai.b2(), ai.c2(), ai.d2(),
                    ai.a3(), ai.b3(), ai.c3(), ai.d3(),
                    ai.a4(), ai.b4(), ai.c4(), ai.d4()
            );
        }

        private static float clamp(float value, float min, float max) {
            return Math.max(min, Math.min(max, value));
        }

        public void update(float delta) {
            if (animations.isEmpty()) return;
            animationTimeSeconds += delta;
        }

        public void resetAnimation() {
            animationTimeSeconds = 0.0;
        }

        public void setActiveAnimationIndex(int index) {
            if (index >= 0 && index < animations.size()) {
                activeAnimationIndex = index;
            }
        }

        public void render(Matrix4f vp, Matrix4f rootModelMatrix) {
            Animation activeAnimation = null;

            if (!animations.isEmpty()) {
                int index = Math.max(0, Math.min(activeAnimationIndex, animations.size() - 1));
                activeAnimation = animations.get(index);
            }

            double ticks = 0.0;
            if (activeAnimation != null) {
                ticks = animationTimeSeconds * activeAnimation.ticksPerSecond;
                if (activeAnimation.duration > 0.0) {
                    ticks = ticks % activeAnimation.duration;
                }
            }

            for (int i = 0; i < MAX_BONES; i++) {
                finalBoneMatrices[i].identity();
            }

            calculateBoneTransforms(rootNode, new Matrix4f().identity(), activeAnimation, ticks);

            for (int i = 0; i < Math.min(boneInfos.size(), MAX_BONES); i++) {
                enviarBoneMatrix(i, finalBoneMatrices[i]);
            }

            renderNode(rootNode, new Matrix4f(rootModelMatrix), vp, activeAnimation, ticks);
        }

        private void calculateBoneTransforms(Node node, Matrix4f parentTransform, Animation animation, double ticks) {
            Matrix4f localTransform = getAnimatedLocalTransform(node, animation, ticks);
            Matrix4f globalTransform = new Matrix4f(parentTransform).mul(localTransform);

            Integer boneIndex = boneIndexByName.get(node.name);
            if (boneIndex != null && boneIndex >= 0 && boneIndex < MAX_BONES) {
                BoneInfo boneInfo = boneInfos.get(boneIndex);
                finalBoneMatrices[boneIndex] = new Matrix4f(globalInverseTransform)
                        .mul(globalTransform)
                        .mul(boneInfo.offsetMatrix);
            }

            for (Node child : node.children) {
                calculateBoneTransforms(child, globalTransform, animation, ticks);
            }
        }

        private void renderNode(Node node, Matrix4f parentTransform, Matrix4f vp, Animation animation, double ticks) {
            Matrix4f localTransform = getAnimatedLocalTransform(node, animation, ticks);
            Matrix4f globalTransform = new Matrix4f(parentTransform).mul(localTransform);

            for (Integer meshIndex : node.meshIndices) {
                if (meshIndex < 0 || meshIndex >= meshes.size()) continue;

                GlbMesh mesh = meshes.get(meshIndex);
                Texture texture = texturesByMaterial.get(mesh.materialIndex);

                Matrix4f mvp = new Matrix4f(vp).mul(globalTransform);

                enviarMVP(mvp);
                enviarModelo(globalTransform);
                glUniform1i(locationHasBones, mesh.hasBones ? 1 : 0);

                mesh.render(locationColor, locationUseTexture, texture);
            }

            for (Node child : node.children) {
                renderNode(child, globalTransform, vp, animation, ticks);
            }
        }

        private Matrix4f getAnimatedLocalTransform(Node node, Animation animation, double ticks) {
            if (animation != null) {
                NodeAnimation nodeAnimation = animation.channels.get(node.name);
                if (nodeAnimation != null) {
                    return nodeAnimation.interpolate(ticks);
                }
            }

            return node.localTransform;
        }

        public void clean() {
            for (GlbMesh mesh : meshes) {
                mesh.clean();
            }
            for (Texture texture : texturesByMaterial.values()) {
                texture.clean();
            }
        }

        public org.joml.Vector3f getCenterOffset() {
            return centerOffset;
        }

        public float getRecommendedScale() {
            return recommendedScale;
        }

        public int getMeshCount() {
            return meshes.size();
        }

        public int getTextureCount() {
            return texturesByMaterial.size();
        }

        public int getAnimationCount() {
            return animations.size();
        }

        public int getBoneCount() {
            return boneInfos.size();
        }

        public String[] getAnimationNames() {
            return animationNames.clone();
        }

        private static String[] buildAnimationNames(List<Animation> animations) {
            String[] names = new String[animations.size()];

            for (int i = 0; i < animations.size(); i++) {
                String name = animations.get(i).name;
                if (name == null || name.isBlank()) {
                    name = "Animación " + (i + 1);
                }
                names[i] = name;
            }

            return names;
        }
    }

    /*******************************************************************************************
     Submalla renderizable.
     Cada vértice almacena:
     - position: 3 floats
     - normal:   3 floats
     - texCoord: 2 floats
     - boneIds:  4 floats
     - weights:  4 floats
     ******************************************************************************************/
    private static class GlbMesh {

        private static final int FLOATS_PER_VERTEX = 16;
        private static final int STRIDE_BYTES = FLOATS_PER_VERTEX * Float.BYTES;

        private final int vaoID;
        private final int vboID;
        private final int vertexCount;
        private final int materialIndex;
        private final float[] color;
        private final boolean hasBones;

        public GlbMesh(float[] vertexData, int materialIndex, float[] color, boolean hasBones) {
            this.vertexCount = vertexData.length / FLOATS_PER_VERTEX;
            this.materialIndex = materialIndex;
            this.color = color;
            this.hasBones = hasBones;

            vaoID = glGenVertexArrays();
            glBindVertexArray(vaoID);

            vboID = glGenBuffers();
            glBindBuffer(GL_ARRAY_BUFFER, vboID);

            FloatBuffer buffer = BufferUtils.createFloatBuffer(vertexData.length);
            buffer.put(vertexData).flip();

            glBufferData(GL_ARRAY_BUFFER, buffer, GL_STATIC_DRAW);

            glVertexAttribPointer(0, 3, GL_FLOAT, false, STRIDE_BYTES, 0);
            glEnableVertexAttribArray(0);

            glVertexAttribPointer(1, 3, GL_FLOAT, false, STRIDE_BYTES, 3L * Float.BYTES);
            glEnableVertexAttribArray(1);

            glVertexAttribPointer(2, 2, GL_FLOAT, false, STRIDE_BYTES, 6L * Float.BYTES);
            glEnableVertexAttribArray(2);

            glVertexAttribPointer(3, 4, GL_FLOAT, false, STRIDE_BYTES, 8L * Float.BYTES);
            glEnableVertexAttribArray(3);

            glVertexAttribPointer(4, 4, GL_FLOAT, false, STRIDE_BYTES, 12L * Float.BYTES);
            glEnableVertexAttribArray(4);

            glBindBuffer(GL_ARRAY_BUFFER, 0);
            glBindVertexArray(0);
        }

        public void render(int locationColor, int locationUseTexture, Texture texture) {
            glUniform3f(locationColor, color[0], color[1], color[2]);

            if (texture != null) {
                glUniform1i(locationUseTexture, 1);
                texture.bind();
            } else {
                glUniform1i(locationUseTexture, 0);
                glBindTexture(GL_TEXTURE_2D, 0);
            }

            glBindVertexArray(vaoID);
            glDrawArrays(GL_TRIANGLES, 0, vertexCount);
            glBindVertexArray(0);
        }

        public void clean() {
            glDeleteBuffers(vboID);
            glDeleteVertexArrays(vaoID);
        }
    }

    /*******************************************************************************************
     Estructuras de animación.
     ******************************************************************************************/
    private static class Node {
        private final String name;
        private final Matrix4f localTransform;
        private final List<Integer> meshIndices = new ArrayList<>();
        private final List<Node> children = new ArrayList<>();

        private Node(String name, Matrix4f localTransform) {
            this.name = name;
            this.localTransform = localTransform;
        }
    }

    private static class Animation {
        private final String name;
        private final double duration;
        private final double ticksPerSecond;
        private final Map<String, NodeAnimation> channels;

        private Animation(String name, double duration, double ticksPerSecond, Map<String, NodeAnimation> channels) {
            this.name = name;
            this.duration = duration;
            this.ticksPerSecond = ticksPerSecond > 0.0 ? ticksPerSecond : 25.0;
            this.channels = channels;
        }

        private static Animation from(AIAnimation aiAnimation, int index) {
            Map<String, NodeAnimation> channels = new HashMap<>();
            PointerBuffer aiChannels = aiAnimation.mChannels();

            if (aiChannels != null) {
                for (int i = 0; i < aiAnimation.mNumChannels(); i++) {
                    AINodeAnim channel = AINodeAnim.create(aiChannels.get(i));
                    NodeAnimation nodeAnimation = NodeAnimation.from(channel);
                    channels.put(nodeAnimation.nodeName, nodeAnimation);
                }
            }

            String name = aiAnimation.mName().dataString();
            if (name == null || name.isBlank()) {
                name = "Animación " + (index + 1);
            }

            return new Animation(name, aiAnimation.mDuration(), aiAnimation.mTicksPerSecond(), channels);
        }
    }

    private static class NodeAnimation {
        private final String nodeName;
        private final List<VectorKey> positions;
        private final List<QuatKey> rotations;
        private final List<VectorKey> scales;

        private NodeAnimation(String nodeName, List<VectorKey> positions, List<QuatKey> rotations, List<VectorKey> scales) {
            this.nodeName = nodeName;
            this.positions = positions;
            this.rotations = rotations;
            this.scales = scales;
        }

        private static NodeAnimation from(AINodeAnim channel) {
            List<VectorKey> positions = new ArrayList<>();
            AIVectorKey.Buffer aiPositions = channel.mPositionKeys();

            for (int i = 0; i < channel.mNumPositionKeys(); i++) {
                AIVectorKey key = aiPositions.get(i);
                positions.add(new VectorKey(key.mTime(),
                        new Vector3f(key.mValue().x(), key.mValue().y(), key.mValue().z())));
            }

            List<QuatKey> rotations = new ArrayList<>();
            AIQuatKey.Buffer aiRotations = channel.mRotationKeys();

            for (int i = 0; i < channel.mNumRotationKeys(); i++) {
                AIQuatKey key = aiRotations.get(i);
                AIQuaternion q = key.mValue();
                rotations.add(new QuatKey(key.mTime(), new Quaternionf(q.x(), q.y(), q.z(), q.w())));
            }

            List<VectorKey> scales = new ArrayList<>();
            AIVectorKey.Buffer aiScales = channel.mScalingKeys();

            for (int i = 0; i < channel.mNumScalingKeys(); i++) {
                AIVectorKey key = aiScales.get(i);
                scales.add(new VectorKey(key.mTime(),
                        new Vector3f(key.mValue().x(), key.mValue().y(), key.mValue().z())));
            }

            return new NodeAnimation(channel.mNodeName().dataString(), positions, rotations, scales);
        }

        private Matrix4f interpolate(double ticks) {
            Vector3f position = interpolateVector(positions, ticks, new Vector3f(0.0f, 0.0f, 0.0f));
            Quaternionf rotation = interpolateQuat(rotations, ticks, new Quaternionf());
            Vector3f scale = interpolateVector(scales, ticks, new Vector3f(1.0f, 1.0f, 1.0f));

            return new Matrix4f().translationRotateScale(position, rotation, scale);
        }

        private static Vector3f interpolateVector(List<VectorKey> keys, double ticks, Vector3f fallback) {
            if (keys.isEmpty()) return fallback;
            if (keys.size() == 1) return new Vector3f(keys.get(0).value);

            int index = findVectorKey(keys, ticks);
            VectorKey current = keys.get(index);
            VectorKey next = keys.get(index + 1);

            double delta = next.time - current.time;
            float factor = delta == 0.0 ? 0.0f : (float) ((ticks - current.time) / delta);
            factor = Math.max(0.0f, Math.min(1.0f, factor));

            return new Vector3f(current.value).lerp(next.value, factor);
        }

        private static Quaternionf interpolateQuat(List<QuatKey> keys, double ticks, Quaternionf fallback) {
            if (keys.isEmpty()) return fallback;
            if (keys.size() == 1) return new Quaternionf(keys.get(0).value);

            int index = findQuatKey(keys, ticks);
            QuatKey current = keys.get(index);
            QuatKey next = keys.get(index + 1);

            double delta = next.time - current.time;
            float factor = delta == 0.0 ? 0.0f : (float) ((ticks - current.time) / delta);
            factor = Math.max(0.0f, Math.min(1.0f, factor));

            return new Quaternionf(current.value).slerp(next.value, factor);
        }

        private static int findVectorKey(List<VectorKey> keys, double ticks) {
            for (int i = 0; i < keys.size() - 1; i++) {
                if (ticks < keys.get(i + 1).time) return i;
            }
            return keys.size() - 2;
        }

        private static int findQuatKey(List<QuatKey> keys, double ticks) {
            for (int i = 0; i < keys.size() - 1; i++) {
                if (ticks < keys.get(i + 1).time) return i;
            }
            return keys.size() - 2;
        }
    }

    private static class VectorKey {
        private final double time;
        private final Vector3f value;

        private VectorKey(double time, Vector3f value) {
            this.time = time;
            this.value = value;
        }
    }

    private static class QuatKey {
        private final double time;
        private final Quaternionf value;

        private QuatKey(double time, Quaternionf value) {
            this.time = time;
            this.value = value;
        }
    }

    private static class BoneInfo {
        private final String name;
        private final Matrix4f offsetMatrix;

        private BoneInfo(String name, Matrix4f offsetMatrix) {
            this.name = name;
            this.offsetMatrix = offsetMatrix;
        }
    }

    private static class VertexBoneData {
        private final int[] ids = new int[]{0, 0, 0, 0};
        private final float[] weights = new float[]{0.0f, 0.0f, 0.0f, 0.0f};

        private void addBoneData(int boneId, float weight) {
            for (int i = 0; i < 4; i++) {
                if (weights[i] == 0.0f) {
                    ids[i] = boneId;
                    weights[i] = weight;
                    return;
                }
            }

            int smallestIndex = 0;
            for (int i = 1; i < 4; i++) {
                if (weights[i] < weights[smallestIndex]) {
                    smallestIndex = i;
                }
            }

            if (weight > weights[smallestIndex]) {
                ids[smallestIndex] = boneId;
                weights[smallestIndex] = weight;
            }
        }

        private void normalizeWeights() {
            float sum = weights[0] + weights[1] + weights[2] + weights[3];

            if (sum > 0.0f) {
                for (int i = 0; i < 4; i++) {
                    weights[i] /= sum;
                }
            }
        }
    }

    /*******************************************************************************************
     Textura OpenGL.
     Permite usar texturas embebidas en el GLB o texturas externas referenciadas por el material.
     ******************************************************************************************/
    private static class Texture {
        private final int id;

        private Texture(int id) {
            this.id = id;
        }

        public static Texture fromFile(String file) throws IOException {
            ByteBuffer imageBuffer = null;

            try {
                byte[] bytes = Files.readAllBytes(Paths.get(file));
                imageBuffer = memAlloc(bytes.length);
                imageBuffer.put(bytes).flip();
                return fromMemory(imageBuffer);
            } finally {
                if (imageBuffer != null) memFree(imageBuffer);
            }
        }

        public static Texture fromMemory(ByteBuffer imageData) throws IOException {
            IntBuffer width = BufferUtils.createIntBuffer(1);
            IntBuffer height = BufferUtils.createIntBuffer(1);
            IntBuffer channels = BufferUtils.createIntBuffer(1);

            stbi_set_flip_vertically_on_load(false);

            ByteBuffer decoded = stbi_load_from_memory(imageData, width, height, channels, 4);

            if (decoded == null) {
                throw new IOException("STB no pudo decodificar la textura: " + stbi_failure_reason());
            }

            try {
                return fromRGBA(decoded, width.get(0), height.get(0));
            } finally {
                stbi_image_free(decoded);
            }
        }

        public static Texture fromRGBA(ByteBuffer rgba, int width, int height) {
            int textureID = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, textureID);

            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);

            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, rgba);
            glGenerateMipmap(GL_TEXTURE_2D);

            glBindTexture(GL_TEXTURE_2D, 0);
            return new Texture(textureID);
        }

        public void bind() {
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, id);
        }

        public void clean() {
            glDeleteTextures(id);
        }
    }
}
