package org.lwjglb.glb;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.assimp.*;

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

import static org.lwjgl.assimp.Assimp.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.stb.STBImage.*;
import static org.lwjgl.system.MemoryUtil.memAlloc;
import static org.lwjgl.system.MemoryUtil.memFree;
import static org.lwjglb.shaders.ShaderProgram.matrixBuffer;

/*******************************************************************************************
     Modelo GLB cargado con Assimp, texturas y animación.
     ******************************************************************************************/
public class GlbModel {
    // Variables auxiliares
    private static final int MAX_BONES = 100;
    private static int[] locationBones;
    private static int locationMVPMatrix;
    private static int locationColor;
    private static int locationUseTexture;
    private static int locationHasBones;
    private static int locationModelMatrix;

    // Métodos auxiliares
    private static void enviarBoneMatrix(int index, Matrix4f matrix) {
        if (index < 0 || index >= locationBones.length) return;
        matrixBuffer.clear();
        matrix.get(matrixBuffer);
        glUniformMatrix4fv(locationBones[index], false, matrixBuffer);
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

    /*******************************************************************************************
         }
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
        private final float[] vertexData; // copia en CPU, usada para medir el modelo

        public GlbMesh(float[] vertexData, int materialIndex, float[] color, boolean hasBones) {
            this.vertexData = vertexData;
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

    private final List<GlbMesh> meshes;
    private final Map<Integer, Texture> texturesByMaterial;
    private final Node rootNode;
    private final List<Animation> animations;
    private final String[] animationNames;
    private org.joml.Vector3f centerOffset;
    private float recommendedScale;
    private final List<BoneInfo> boneInfos;
    private final Map<String, Integer> boneIndexByName;
    private final Matrix4f[] finalBoneMatrices;

    private volatile int activeAnimationIndex = 0;
    private double animationTimeSeconds = 0.0;

    private GlbModel(List<GlbMesh> meshes,
                     Map<Integer, Texture> texturesByMaterial,
                     Node rootNode,
                     List<Animation> animations,
                     List<BoneInfo> boneInfos,
                     Map<String, Integer> boneIndexByName) {
        this.meshes = meshes;
        this.texturesByMaterial = texturesByMaterial;
        this.rootNode = rootNode;
        this.animations = animations;
        this.animationNames = buildAnimationNames(animations);
        this.boneInfos = boneInfos;
        this.boneIndexByName = boneIndexByName;
        this.finalBoneMatrices = new Matrix4f[MAX_BONES];

        for (int i = 0; i < MAX_BONES; i++) {
            finalBoneMatrices[i] = new Matrix4f().identity();
        }

        computeFraming();
    }

    /*
     * Calcula el encuadre (escala y desplazamiento) del modelo.
     *
     * Se mide con EXACTAMENTE la misma matemática que se usa al dibujar: las mallas con
     * huesos pasan por el skinning en la CPU (pose de reposo) y las mallas sin huesos usan
     * la transformación global de su nodo. Así el tamaño medido coincide siempre con el
     * tamaño dibujado, sin importar cómo vengan escaladas la jerarquía o las matrices
     * de enlace (inverse bind matrices) del GLB.
     */
    private void computeFraming() {
        Bounds bounds = calculatePoseBounds(null, 0.0);

        if (!bounds.isValid()) {
            centerOffset = new org.joml.Vector3f(0.0f, 0.0f, 0.0f);
            recommendedScale = 1.0f;
            return;
        }

        float centerX = (bounds.minX + bounds.maxX) * 0.5f;
        float centerY = (bounds.minY + bounds.maxY) * 0.5f;
        float centerZ = (bounds.minZ + bounds.maxZ) * 0.5f;

        float sizeX = bounds.maxX - bounds.minX;
        float sizeY = bounds.maxY - bounds.minY;
        float sizeZ = bounds.maxZ - bounds.minZ;
        float maxSize = Math.max(sizeX, Math.max(sizeY, sizeZ));

        /*
         * Aumenta el tamaño visual del modelo sin modificar la cámara
         * ni la proyección: el lado más grande del modelo mide 4.6 unidades.
         */
        recommendedScale = maxSize > 0.0f ? 4.6f / maxSize : 1.0f;

        /*
         * La cámara original mira al punto y=1.0.
         * Para que el espectador vea el modelo justo frente a él,
         * se desplaza el centro visual del modelo a ese mismo punto.
         */
        float cameraTargetY = 1.0f;
        centerOffset = new org.joml.Vector3f(
                -centerX,
                (cameraTargetY / recommendedScale) - centerY,
                -centerZ
        );

        // Diagnóstico: permite comparar el tamaño en reposo con el de cada animación.
        System.out.printf("Encuadre (pose de reposo): tamaño = %.3f x %.3f x %.3f, escala = %.4f%n",
                sizeX, sizeY, sizeZ, recommendedScale);

        for (int i = 0; i < animations.size(); i++) {
            Bounds animBounds = calculatePoseBounds(animations.get(i), 0.0);
            if (animBounds.isValid()) {
                float animMax = Math.max(animBounds.maxX - animBounds.minX,
                        Math.max(animBounds.maxY - animBounds.minY, animBounds.maxZ - animBounds.minZ));
                System.out.printf("  Animación \"%s\" en t=0: tamaño máximo = %.3f (reposo: %.3f)%n",
                        animationNames[i], animMax, maxSize);
            }
        }
    }

    private Bounds calculatePoseBounds(Animation animation, double ticks) {
        for (int i = 0; i < MAX_BONES; i++) {
            finalBoneMatrices[i].identity();
        }

        calculateBoneTransforms(rootNode, new Matrix4f().identity(), animation, ticks);

        Bounds bounds = new Bounds();
        accumulatePoseBounds(rootNode, new Matrix4f().identity(), animation, ticks, bounds);
        return bounds;
    }

    private void accumulatePoseBounds(Node node, Matrix4f parentTransform,
                                      Animation animation, double ticks, Bounds bounds) {
        Matrix4f globalTransform = new Matrix4f(parentTransform)
                .mul(getAnimatedLocalTransform(node, animation, ticks));

        for (Integer meshIndex : node.meshIndices) {
            if (meshIndex < 0 || meshIndex >= meshes.size()) continue;

            GlbMesh mesh = meshes.get(meshIndex);
            float[] data = mesh.vertexData;

            for (int v = 0; v < mesh.vertexCount; v++) {
                int o = v * GlbMesh.FLOATS_PER_VERTEX;
                Vector3f p = new Vector3f(data[o], data[o + 1], data[o + 2]);

                if (mesh.hasBones) {
                    // Igual que el vertex shader: mezcla de matrices de hueso ponderada.
                    float weightSum = data[o + 12] + data[o + 13] + data[o + 14] + data[o + 15];

                    if (weightSum > 0.0f) {
                        Vector3f skinned = new Vector3f();
                        Vector3f transformed = new Vector3f();

                        for (int k = 0; k < 4; k++) {
                            float weight = data[o + 12 + k];
                            if (weight <= 0.0f) continue;

                            int boneId = (int) (data[o + 8 + k] + 0.5f);
                            finalBoneMatrices[boneId].transformPosition(p, transformed);
                            skinned.fma(weight, transformed);
                        }

                        p = skinned;
                    }
                    // Con huesos NO se aplica la transformación del nodo (ver renderNode).
                } else {
                    globalTransform.transformPosition(p);
                }

                bounds.add(p.x, p.y, p.z);
            }
        }

        for (Node child : node.children) {
            accumulatePoseBounds(child, globalTransform, animation, ticks, bounds);
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

            Node root = loadNode(scene.mRootNode());
            List<Animation> loadedAnimations = loadAnimations(scene);

            // El encuadre (escala y centrado) lo calcula el propio GlbModel en su constructor.
            return new GlbModel(loadedMeshes, materialTextures, root, loadedAnimations,
                    boneInfos, boneIndexByName);
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

        renderNode(rootNode, new Matrix4f(rootModelMatrix), rootModelMatrix, vp, activeAnimation, ticks);
    }

    private void calculateBoneTransforms(Node node, Matrix4f parentTransform, Animation animation, double ticks) {
        Matrix4f localTransform = getAnimatedLocalTransform(node, animation, ticks);
        Matrix4f globalTransform = new Matrix4f(parentTransform).mul(localTransform);

        Integer boneIndex = boneIndexByName.get(node.name);
        if (boneIndex != null && boneIndex >= 0 && boneIndex < MAX_BONES) {
            BoneInfo boneInfo = boneInfos.get(boneIndex);
            /*
             * En glTF, la posición final de un vértice con skinning es:
             *     jointGlobal * inverseBindMatrix * vértice
             * Assimp entrega la inverseBindMatrix en mOffsetMatrix.
             * No se debe multiplicar por la inversa del nodo raíz: esa fórmula viene de
             * FBX/Collada y rompe la escala cuando la jerarquía del GLB tiene escalas.
             */
            finalBoneMatrices[boneIndex] = new Matrix4f(globalTransform)
                    .mul(boneInfo.offsetMatrix);
        }

        for (Node child : node.children) {
            calculateBoneTransforms(child, globalTransform, animation, ticks);
        }
    }

    private void renderNode(Node node, Matrix4f parentTransform, Matrix4f rootModelMatrix,
                            Matrix4f vp, Animation animation, double ticks) {
        Matrix4f localTransform = getAnimatedLocalTransform(node, animation, ticks);
        Matrix4f globalTransform = new Matrix4f(parentTransform).mul(localTransform);

        for (Integer meshIndex : node.meshIndices) {
            if (meshIndex < 0 || meshIndex >= meshes.size()) continue;

            GlbMesh mesh = meshes.get(meshIndex);
            Texture texture = texturesByMaterial.get(mesh.materialIndex);

            /*
             * Malla con huesos: el vértice ya sale del shader en espacio de escena
             * (jointGlobal * inverseBindMatrix), por lo que la transformación del nodo
             * que contiene la malla debe IGNORARSE (así lo define glTF). Si se aplicara,
             * las escalas/rotaciones de la jerarquía se contarían dos veces.
             * Malla sin huesos: sí usa la transformación global de su nodo.
             */
            Matrix4f meshTransform = mesh.hasBones ? rootModelMatrix : globalTransform;

            Matrix4f mvp = new Matrix4f(vp).mul(meshTransform);

            enviarMVP(mvp);
            enviarModelo(meshTransform);
            glUniform1i(locationHasBones, mesh.hasBones ? 1 : 0);

            mesh.render(locationColor, locationUseTexture, texture);
        }

        for (Node child : node.children) {
            renderNode(child, globalTransform, rootModelMatrix, vp, animation, ticks);
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
