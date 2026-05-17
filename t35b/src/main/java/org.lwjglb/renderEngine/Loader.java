package org.lwjglb.renderEngine;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import org.lwjgl.stb.STBImage;
import org.lwjglb.models.RawModel;
import org.lwjglb.models.TexturedModel;
import org.lwjglb.objConverter.OBJFileLoader;
import org.lwjglb.textures.ModelTexture;
import org.lwjglb.textures.Texture;
import org.lwjglb.textures.TextureData;
import org.lwjglb.util.Config;

/**
 * Handles the loading of geometry data into VAOs. It also keeps track of all
 * the created VAOs and VBOs so that they can all be deleted when the game
 * closes.
 * 
 * @author Karl
 *
 */
public class Loader {

	private List<Integer> vaos = new ArrayList<Integer>();
	private List<Integer> vbos = new ArrayList<Integer>();
	private List<Integer> textures = new ArrayList<Integer>();

	/**
	 * Creates a VAO and stores the position data of the vertices into attribute
	 * 0 of the VAO.
	 * 
	 * @param positions
	 *            - The 3D positions of each vertex in the geometry (in this
	 *            example a quad).
	 * @return The loaded model.
	 */
	public int loadToVAO(float[] positions, float[] textureCoords) {
		int vaoID = createVAO();
		storeDataInAttributeList(0, 2, positions);
		storeDataInAttributeList(1, 2, textureCoords);
		unbindVAO();
		return vaoID;
	}


	public RawModel loadToVAO(float[] positions, float[] textureCoords, float[] normals, int[] indices) {
		int vaoID = createVAO();
		bindIndicesBuffer(indices);
		storeDataInAttributeList(0, 3, positions);
		storeDataInAttributeList(1, 2, textureCoords);
		storeDataInAttributeList(2, 3, normals);
		unbindVAO();
		return new RawModel(vaoID, indices.length);
	}

	public RawModel loadToVAO(float[] positions, int dimensions) {
		int vaoID = createVAO();
		this.storeDataInAttributeList(0, dimensions, positions);
		unbindVAO();
		return new RawModel(vaoID, positions.length/2);
	}

	public int loadTexture(String filename) {
		Texture texture = null;
		try {
			texture = new Texture(new Config().getPath() + "textures//" + filename + ".png");   // <- ❗️ IMPORTANT: This line differs from the tutorial. Feel free to change the path.
			GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_NEAREST);
			GL11.glTexParameterf(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_LOD_BIAS, -0.4f);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		int textureID = texture.getTextureID();
		textures.add(textureID);
		return textureID;
	}

	/**
	 * Deletes all the VAOs and VBOs when the game is closed. VAOs and VBOs are
	 * located in video memory.
	 */
	public void cleanUp() {
		for (int vao : vaos) {
			GL30.glDeleteVertexArrays(vao);
		}
		for (int vbo : vbos) {
			GL15.glDeleteBuffers(vbo);
		}
	}

	// OpenGL 3D Game Tutorial 27: Skybox
	public int loadCubeMap(String[] textureFiles) {
		int texID = GL11.glGenTextures();
		GL13.glActiveTexture(GL13.GL_TEXTURE0);
		GL11.glBindTexture(GL13.GL_TEXTURE_CUBE_MAP, texID);

		for (int i = 0; i < textureFiles.length; i++) {
			TextureData data = decodeTextureFile(new Config().getPath() + "textures//" + textureFiles[i] + ".png");
			GL11.glTexImage2D(GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_X + i, 0, GL11.GL_RGBA,
					data.getWidth(), data.getHeight(),0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE,
					data.getBuffer());
			data.freeBuffer();
		}
		GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
		GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);

		// "-Due to hardware limitations on some computers you may see some visible seams at
		// the edges of the skybox. If this is the case then add these two lines to the end
		// of the loadCubeMap() method, just before returning the texID:"
		GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
		GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

		textures.add(texID);
		return texID;
	}

	private ByteBuffer readByteBufferFromFileInputStream(FileInputStream fs) throws IOException {
		FileChannel fc = fs.getChannel();
		ByteBuffer buffer = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size());
		fc.close();
		fs.close();
		return buffer;
	}

	// OpenGL 3D Game Tutorial 27: Skybox
	private TextureData decodeTextureFile(String fileName) {
		int width = 0;
		int height = 0;
		// int components = 0;
		ByteBuffer buffer = null;

		try {
			FileInputStream fs = new FileInputStream(fileName);
			IntBuffer widthBuffer = BufferUtils.createIntBuffer(1);
			IntBuffer heightBuffer = BufferUtils.createIntBuffer(1);
			IntBuffer componentsBuffer = BufferUtils.createIntBuffer(1);
			ByteBuffer byteBuffer = readByteBufferFromFileInputStream(fs);
			// Caller is responsible for freeing this.
			ByteBuffer data = STBImage.stbi_load_from_memory(byteBuffer, widthBuffer, heightBuffer, componentsBuffer, 4);
			width = widthBuffer.get(0);
			height = heightBuffer.get(0);
			buffer = data;
			fs.close();
		} catch (Exception e) {
			e.printStackTrace();
			System.err.println("Loader: File not found: " + fileName);
			System.exit(-1);
		}
		return new TextureData(buffer, width, height);
	}


	/**
	 * Creates a new VAO and returns its ID. A VAO holds geometry data that we
	 * can render and is physically stored in memory on the GPU, so that it can
	 * be accessed very quickly during rendering.
	 * 
	 * Like most objects in OpenGL, the new VAO is created using a "gen" method
	 * which returns the ID of the new VAO. In order to use the VAO it needs to
	 * be made the active VAO. Only one VAO can be active at a time. To make
	 * this VAO the active VAO (so that we can store stuff in it) we have to
	 * bind it.
	 * 
	 * @return The ID of the newly created VAO.
	 */
	private int createVAO() {
		int vaoID = GL30.glGenVertexArrays();
		vaos.add(vaoID);
		GL30.glBindVertexArray(vaoID);
		return vaoID;
	}

	/**
	 * Stores the position data of the vertices into attribute 0 of the VAO. To
	 * do this the positions must first be stored in a VBO. You can simply think
	 * of a VBO as an array of data that is stored in memory on the GPU for easy
	 * access during rendering.
	 * 
	 * Just like with the VAO, we create a new VBO using a "gen" method, and
	 * make it the active VBO (so that we do stuff to it) by binding it.
	 * 
	 * We then store the positions data in the active VBO by using the
	 * glBufferData method. We also indicate using GL_STATIC_DRAW that this data
	 * won't need to be changed. If we wanted to edit the positions every frame
	 * (perhaps to animate the quad) then we would use GL_DYNAMIC_DRAW instead.
	 * 
	 * We the connect the VBO to the VAO using the glVertexAttribPointer()
	 * method. This needs to know the attribute number of the VAO where we want
	 * to put the data, the number of floats used for each vertex (3 floats in
	 * this case, because each vertex has a 3D position, an x, y, and z value),
	 * the type of data (in this case we used floats) and then some other more
	 * complicated stuff for storing the data in more fancy ways. Don't worry
	 * about the last 3 parameters for now, we don't need them here.
	 * 
	 * Now that we've finished using the VBO we can unbind it. This isn't
	 * totally necessary, but I think it's good practice to unbind the VBO when
	 * you're done using it.
	 * 
	 * @param attributeNumber
	 *            - The number of the attribute of the VAO where the data is to
	 *            be stored.
	 * @param data
	 *            - The geometry data to be stored in the VAO, in this case the
	 *            positions of the vertices.
	 */
	private void storeDataInAttributeList(int attributeNumber, int coordinateSize, float[] data) {
		int vboID = GL15.glGenBuffers();
		vbos.add(vboID);
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboID);
		FloatBuffer buffer = storeDataInFloatBuffer(data);
		GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buffer, GL15.GL_STATIC_DRAW);
		GL20.glVertexAttribPointer(attributeNumber, coordinateSize, GL11.GL_FLOAT, false, 0, 0);
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
	}

    private void bindIndicesBuffer(int[] indices) {
		int vboID = GL15.glGenBuffers();
		vbos.add(vboID);
		GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, vboID);
		IntBuffer buffer = storeDataInIntBuffer(indices);
		GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, buffer, GL15.GL_STATIC_DRAW);
	}

	private IntBuffer storeDataInIntBuffer(int[] data) {
		IntBuffer buffer = BufferUtils.createIntBuffer(data.length);
		buffer.put(data);
		buffer.flip();
		return buffer;
	}


	/**
	 * Unbinds the VAO after we're finished using it. If we want to edit or use
	 * the VAO we would have to bind it again first.
	 */
	private void unbindVAO() {
		GL30.glBindVertexArray(0);
	}

	/**
	 * Before we can store data in a VBO it needs to be in a certain format: in
	 * a buffer. In this case we will use a float buffer because the data we
	 * want to store is float data. If we were storing int data we would use an
	 * IntBuffer.
	 * 
	 * First and empty buffer of the correct size is created. You can think of a
	 * buffer as basically an array with a pointer. After putting the necessary
	 * data into the buffer the pointer will have increased so that it points at
	 * the first empty element of the array. This is so that we could add more
	 * data to the buffer if we wanted and it wouldn't overwrite the data we've
	 * already put in. However, we're done with storing data and we want to make
	 * the buffer ready for reading. To do this we need to make the pointer
	 * point to the start of the data, so that OpenGL knows where in the buffer
	 * to start reading. The "flip()" method does just that, putting the pointer
	 * back to the start of the buffer.
	 * 
	 * @param data
	 *            - The float data that is going to be stored in the buffer.
	 * @return The FloatBuffer containing the data. This float buffer is ready
	 *         to be loaded into a VBO.
	 */
	private FloatBuffer storeDataInFloatBuffer(float[] data) {
		FloatBuffer buffer = BufferUtils.createFloatBuffer(data.length);
		buffer.put(data);
		buffer.flip();
		return buffer;
	}

	// allows creating textured models more easily in main game loop
	public TexturedModel createTexturedModel(
			String objFileName,
			String textureFileName,
			float materialShineDamper,
			float materialReflectivity) {
		RawModel model = OBJFileLoader.loadOBJ(objFileName, this);
		ModelTexture texture = new ModelTexture(this.loadTexture(textureFileName));
		texture.setShineDamper(materialShineDamper);
		texture.setReflectivity(materialReflectivity);
		TexturedModel texturedModel = new TexturedModel(model, texture);
		return texturedModel;
	}

	public TexturedModel createTexturedModel(
			String objFileName,
			String textureFileName,
			float materialShineDamper,
			float materialReflectivity,
			boolean hasTransparency,
			boolean useFakeLighting
	) {
		TexturedModel texturedModel = createTexturedModel(
				objFileName, textureFileName, materialShineDamper, materialReflectivity);
		texturedModel.getTexture().setHasTransparency(hasTransparency);
		texturedModel.getTexture().setUseFakeLighting(useFakeLighting);
		return texturedModel;
	}

	// for OpenGL 3D Game Tutorial 23: Texture Atlases
	public TexturedModel createTexturedModel(
			String objFileName,
			String textureFileName,
			int numberOfRows,
			float materialShineDamper,
			float materialReflectivity,
			boolean hasTransparency,
			boolean useFakeLighting
	) {
		TexturedModel texturedModel = createTexturedModel(
				objFileName, textureFileName,
				materialShineDamper, materialReflectivity,
				hasTransparency, useFakeLighting);
		texturedModel.getTexture().setNumberOfRows(numberOfRows);
		return texturedModel;
	}

}
