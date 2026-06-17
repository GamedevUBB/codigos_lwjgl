package curso.lwjgl;

import org.lwjgl.PointerBuffer;
import org.lwjgl.assimp.AIFace;
import org.lwjgl.assimp.AIMesh;
import org.lwjgl.assimp.AIScene;
import org.lwjgl.assimp.AIVector3D;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.assimp.Assimp.*;

public class ObjLoader {

    public static Mesh loadMesh(String file) throws IOException {
        int flags = aiProcess_Triangulate
                | aiProcess_JoinIdenticalVertices
                | aiProcess_ImproveCacheLocality
                | aiProcess_SortByPType;

        AIScene scene = aiImportFile(file, flags);

        if (scene == null || scene.mRootNode() == null) {
            throw new IOException("Assimp no pudo cargar el modelo OBJ: " + file + "\n" + aiGetErrorString());
        }

        List<Float> finalVertices = new ArrayList<>();

        try {
            PointerBuffer meshes = scene.mMeshes();

            if (meshes == null) {
                throw new IOException("El modelo no contiene mallas: " + file);
            }

            for (int meshIndex = 0; meshIndex < scene.mNumMeshes(); meshIndex++) {
                AIMesh aiMesh = AIMesh.create(meshes.get(meshIndex));
                AIVector3D.Buffer vertices = aiMesh.mVertices();
                AIFace.Buffer faces = aiMesh.mFaces();

                for (int i = 0; i < aiMesh.mNumFaces(); i++) {
                    AIFace face = faces.get(i);

                    // aiProcess_Triangulate convierte las caras a triángulos.
                    for (int j = 0; j < face.mNumIndices(); j++) {
                        int vertexIndex = face.mIndices().get(j);
                        AIVector3D vertex = vertices.get(vertexIndex);

                        finalVertices.add(vertex.x());
                        finalVertices.add(vertex.y());
                        finalVertices.add(vertex.z());
                    }
                }
            }
        } finally {
            aiReleaseImport(scene);
        }

        float[] verticesArray = new float[finalVertices.size()];

        for (int i = 0; i < verticesArray.length; i++) {
            verticesArray[i] = finalVertices.get(i);
        }

        return new Mesh(verticesArray);
    }
}
