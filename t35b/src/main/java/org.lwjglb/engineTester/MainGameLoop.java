package org.lwjglb.engineTester;

import static org.lwjgl.glfw.GLFW.*;

import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjglb.entities.Camera;
import org.lwjglb.entities.Entity;
import org.lwjglb.entities.Light;
import org.lwjglb.entities.Player;
import org.lwjglb.fontMeshCreator.FontType;
import org.lwjglb.fontMeshCreator.GUIText;
import org.lwjglb.fontRendering.TextMaster;
import org.lwjglb.guis.GuiRenderer;
import org.lwjglb.guis.GuiTexture;
import org.lwjglb.md3.MD3Model;
import org.lwjglb.md3.SimpleShader;
import org.lwjglb.models.TexturedModel;
import org.lwjglb.normalMappingObjConverter.NormalMappedObjLoader;
import org.lwjglb.objConverter.OBJFileLoader;
import org.lwjglb.particles.Particle;
import org.lwjglb.particles.ParticleMaster;
import org.lwjglb.particles.ParticleSystem;
import org.lwjglb.particles.ParticleTexture;
import org.lwjglb.renderEngine.*;
import org.lwjglb.terrains.Terrain;
import org.lwjglb.textures.ModelTexture;
import org.lwjglb.textures.TerrainTexture;
import org.lwjglb.textures.TerrainTexturePack;
import org.lwjglb.util.Config;
import org.lwjglb.util.Maths;
import org.lwjglb.util.MousePicker;
import org.lwjglb.water.WaterFrameBuffers;
import org.lwjglb.water.WaterRenderer;
import org.lwjglb.water.WaterShader;
import org.lwjglb.water.WaterTile;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Vector;
import org.lwjglb.util.Config;

/**
 * This class contains the main method and is used to test the engine.
 * 
 * @author Karl
 *
 */
public class MainGameLoop {

	private static SimpleShader shader;

	static void drawLara(SimpleShader shader, MD3Model lara, MasterRenderer masterRenderer, Camera camera, Matrix4f modelMatrix) {
		GL11.glFrontFace(GL11.GL_CW); // ← MD3 usa clockwise

		shader.start();
		shader.loadProjectionMatrix(masterRenderer.getProjectionMatrix());
		//shader.loadModelMatrix(modelMatrix);
		shader.loadViewMatrix(Maths.createViewMatrix(camera));
		lara.draw(modelMatrix, shader);
		shader.stop();

		GL11.glFrontFace(GL11.GL_CCW); //
	}

	/**
	 * Creates a display and then continuously updates the display until the user tries to close it. 
	 * @param args
	 */
	public static void main(String[] args) {
		DisplayManager.createDisplay();
		Loader loader = new Loader();
		TextMaster.init(loader);

		//Carga de Texto
		//--------------------------------------------------------------------------------
		FontType font = new FontType(loader.loadTexture("candara"), new File(new Config().getPath() + "textures/candara.fnt"));
		//GUIText text = new GUIText("Este es un texto", 3, font, new Vector2f(0.0f, 0.4f), 1f, true);
		//text.setColour(1,1,1);

		//Carga de Texturas
		//--------------------------------------------------------------------------------
		TerrainTexture backgroundTexture = new TerrainTexture(loader.loadTexture("grassy"));
		TerrainTexture rTexture = new TerrainTexture(loader.loadTexture("mud"));
		TerrainTexture gTexture = new TerrainTexture(loader.loadTexture("grass"));
		TerrainTexture bTexture = new TerrainTexture(loader.loadTexture("path"));

		TerrainTexturePack texturePack = new TerrainTexturePack(backgroundTexture, rTexture, gTexture, bTexture);
		TerrainTexture blendMap = new TerrainTexture(loader.loadTexture("blendMap"));
		TerrainTexture blendMapLake = new TerrainTexture(loader.loadTexture("blendMapLake"));

		TexturedModel treeModel = loader.createTexturedModel("tree", "tree", 1, 0);
		TexturedModel lowPolyTreeModel = loader.createTexturedModel("lowPolyTree", "lowPolyTree", 1, 0);
		TexturedModel pineModel = loader.createTexturedModel("pine", "pine", 10, 0.5f);
		TexturedModel grassModel = loader.createTexturedModel("grassModel", "grassTexture", 1, 0, true, true);
		TexturedModel flowerModel = loader.createTexturedModel("grassModel", "flower", 1, 0, true, true);
		TexturedModel fernModel = loader.createTexturedModel("fern", "fern4", 2, 1, 0, true, false);
		TexturedModel lampModel = loader.createTexturedModel("lamp", "lamp", 1, 0, false, true);

		//Creacion de Terreno
		//--------------------------------------------------------------------------------
		//Terrain terrain = new Terrain(0,-1, loader, texturePack, blendMap, "heightmap");
		//Terrain terrain2 = new Terrain(-1,-1, loader, texturePack, blendMap, "heightmap");

		Terrain terrain = new Terrain(0, -1, loader, texturePack, blendMapLake, "heightMapLake");
		List<Terrain> terrains = new ArrayList<Terrain>();
		terrains.add(terrain);

		List<Entity> entities = new ArrayList<>();
		List<Entity> normalMapEntities = new ArrayList<>();


		//Modelos con mapas normales
		//--------------------------------------------------------------------------------

		TexturedModel barrelModel = new TexturedModel(NormalMappedObjLoader.loadOBJ("barrel", loader),
				new ModelTexture(loader.loadTexture("barrel")));
		barrelModel.getTexture().setNormalMap(loader.loadTexture("barrelNormal"));
		barrelModel.getTexture().setShineDamper(10);
		barrelModel.getTexture().setReflectivity(0.5f);

		TexturedModel crateModel = new TexturedModel(NormalMappedObjLoader.loadOBJ("crate", loader),
				new ModelTexture(loader.loadTexture("crate")));
		crateModel.getTexture().setNormalMap(loader.loadTexture("crateNormal"));
		crateModel.getTexture().setShineDamper(10);
		crateModel.getTexture().setReflectivity(0.5f);

		TexturedModel boulderModel = new TexturedModel(NormalMappedObjLoader.loadOBJ("boulder", loader),
				new ModelTexture(loader.loadTexture("boulder")));
		boulderModel.getTexture().setNormalMap(loader.loadTexture("boulderNormal"));
		boulderModel.getTexture().setShineDamper(10);
		boulderModel.getTexture().setReflectivity(0.5f);

		TexturedModel rocks = new TexturedModel(OBJFileLoader.loadOBJ("rocks", loader),
				new ModelTexture(loader.loadTexture("rocks")));

		//Creacion de Entidades
        //--------------------------------------------------------------------------------
		Entity entity = new Entity(barrelModel, new Vector3f(65, 10, -75), 0, 0, 0, 1f);
		Entity entity2 = new Entity(boulderModel, new Vector3f(80, 10, -75), 0, 0, 0, 1f);
		Entity entity3 = new Entity(crateModel, new Vector3f(50, 10, -75), 0, 0, 0, 0.04f);
		normalMapEntities.add(entity);
		normalMapEntities.add(entity2);
		normalMapEntities.add(entity3);

		MasterRenderer masterRenderer = new MasterRenderer(loader);

		//Particulas
 		//------------------------------------------------------------------------------------
		ParticleMaster.init(loader, masterRenderer.getProjectionMatrix());


		ParticleTexture particleStarTexture = new ParticleTexture(loader.loadTexture("particleStar"), 1);
		ParticleTexture particleAtlasTexture = new ParticleTexture(loader.loadTexture("particleAtlas"), 4);
		//ParticleTexture particleAtlasTexture = new ParticleTexture(loader.loadTexture("efecto3"), 4);
		ParticleTexture particleCosmicTexture = new ParticleTexture(loader.loadTexture("cosmic"), 4);
		ParticleTexture particleSmokeTexture = new ParticleTexture(loader.loadTexture("Smoke45Frames"), 7);
		ParticleTexture particleFireTexture = new ParticleTexture(loader.loadTexture("efecto6"), 4);

		ParticleSystem system = new ParticleSystem(particleStarTexture,40, 10, 0.1f, 1,1.6f);
		system.setLifeError(0.1f);
		system.setSpeedError(0.25f);
		system.setScaleError(0.5f);
		system.randomizeRotation();
		//Vector3f starPosition = new Vector3f(300.0f, 400.0f, 30);

		ParticleSystem systemAtlas = new ParticleSystem(particleAtlasTexture, 40, 2, 0.01f, 2, 1.5f);
		systemAtlas.randomizeRotation();
		systemAtlas.setDirection(new Vector3f(0f, 1, 0), 0.1f);
		systemAtlas.setLifeError(0.1f);
		systemAtlas.setSpeedError(0.25f);
		systemAtlas.setScaleError(1.5f);
		Vector3f atlasPosition = new Vector3f(15.0f, 20.0f, -20f);

		ParticleSystem fireSystem = new ParticleSystem(particleFireTexture, 40, 0.4f, -0.02f, 1.5f, 1.5f);
		fireSystem.randomizeRotation();
		//fireSystem.setDirection(new Vector3f(0, 1, 0), 0.2f);
		fireSystem.setLifeError(0.5f);
		fireSystem.setSpeedError(0.25f);
		fireSystem.setScaleError(0.5f);
		Vector3f firePosition = new Vector3f(17f, 6f, -25);

		ParticleSystem smokeSystem = new ParticleSystem(particleSmokeTexture, 50, 1.5f, 0.005f, 1.2f, 3f);
		smokeSystem.setDirection(new Vector3f(0, 1, 0), 0.3f);
		smokeSystem.setLifeError(3.1f);
		smokeSystem.setSpeedError(1.25f);
		smokeSystem.setScaleError(0f);
		smokeSystem.randomizeRotation();
		Vector3f smokePosition = new Vector3f(8.0f, 15.0f, -40f);

		ParticleSystem cosmicSystem = new ParticleSystem(particleCosmicTexture, 50, 0.6f, 0.3f, 1.5f, 100);
		cosmicSystem.setDirection(new Vector3f(0, 1, 0), 0.8f);
		cosmicSystem.setLifeError(0.1f);
		cosmicSystem.setSpeedError(0.25f);
		cosmicSystem.setScaleError(0.5f);
		cosmicSystem.randomizeRotation();
		Vector3f cosmicPosition = new Vector3f(30.0f, 20.0f, -38);


		//Creacion de Iluminación
		//------------------------------------------------------------------------------------
		float ex, ey, ez;
		List<Light> lights = new ArrayList<>();

		Light light = new Light(new Vector3f(10000,10000, 10000), new Vector3f(1,1,1));
		lights.add(light);

		// OpenGL 3D Game Tutorial 25: Multiple Lights
		//Light light2 = new Light(new Vector3f(0, 10000, -7000), new Vector3f(0.4f, 0.4f, 0.4f));
		//lights.add(light2);

		//Light light = new Light(new Vector3f(0,10000, -7000), new Vector3f(1,1,1));
		//List<Light> lights = new ArrayList<>();
		//lights.add(light);
		//lights.add(new Light(new Vector3f(-200,10,-200), new Vector3f(10,0,0)));
		//lights.add(new Light(new Vector3f(200,10,200), new Vector3f(0,0,10)));

		//lights.add(new Light(new Vector3f(0,1000,-7000), new Vector3f(0.4f,0.4f,0.4f)));
		//lights.add(new Light(new Vector3f(185,10,-293), new Vector3f(2,0,0), new Vector3f(1,0.01f,0.002f)));
		//lights.add(new Light(new Vector3f(370,17,-300), new Vector3f(0,2,2), new Vector3f(1,0.01f,0.002f)));
		//lights.add(new Light(new Vector3f(293,7,-305), new Vector3f(2,2,0), new Vector3f(1,0.01f,0.002f)));

		/*
		ex = 100;
		ez = -100;
		ey = terrain.getHeightOfTerrain(ex, ez);
		entities.add(new Entity(lampModel, new Vector3f(ex, ey, ez), 0, 0, 0, 1f));
		lights.add(new Light(new Vector3f(ex, ey+14, ez), new Vector3f(2, 1, 1), new Vector3f(1, 0.01f, 0.002f)));

		ex = 70;
		ez = -200;
		ey = terrain.getHeightOfTerrain(ex, ez);
		entities.add(new Entity(lampModel, new Vector3f(ex, ey, ez), 0, 0, 0, 1f));
		lights.add(new Light(new Vector3f(ex, ey+14, ez), new Vector3f(1, 2, 0), new Vector3f(1, 0.01f, 0.002f)));

		ex = 93;
		ez = -305;
		ey = terrain.getHeightOfTerrain(ex, ez);
		entities.add(new Entity(lampModel, new Vector3f(ex, ey, ez), 0, 0, 0, 1f));
		lights.add(new Light(new Vector3f(ex, ey+14, ez), new Vector3f(0, 1, 2), new Vector3f(1, 0.01f, 0.002f)));

		ex = 82;
		ez = -250;
		ey = terrain.getHeightOfTerrain(ex, ez);
		Entity lampEntity = new Entity(lampModel, new Vector3f(ex, ey, ez), 0, 0, 0, 1f);
		entities.add(lampEntity);
		Light lampLight = new Light(new Vector3f(ex, ey+14, ez), new Vector3f(0, 2, 2), new Vector3f(1, 0.01f, 0.002f));

		lights.add(lampLight);
*/

		//Creacion de Agua
		//------------------------------------------------------------------------------------
		WaterFrameBuffers fbos = new WaterFrameBuffers();
		WaterShader waterShader = new WaterShader();
		WaterRenderer waterRenderer = new WaterRenderer(loader, waterShader, masterRenderer.getProjectionMatrix(), fbos);
		List<WaterTile> waters = new ArrayList<>();
		WaterTile water = new WaterTile(75, -75, 0);
		waters.add(water);

		//Creacion de Guis
		//------------------------------------------------------------------------------------
		List<GuiTexture> guis = new ArrayList<>();
		/*
		GuiTexture refraction = new GuiTexture(fbos.getRefractionTexture(), new Vector2f( 0.5f, -0.5f), new Vector2f(0.25f, 0.25f));
		GuiTexture reflection = new GuiTexture(fbos.getReflectionTexture(), new Vector2f(-0.5f, 0.5f), new Vector2f(0.25f, 0.25f));
		guis.add(refraction);
		guis.add(reflection);
		*/

		//GuiTexture gui = new GuiTexture(fbos.getReflectionTexture(), new Vector2f(-0.5f, 0.5f), new Vector2f(0.5f, 0.5f));
		//guis.add(gui);

		//List<GuiTexture> guis = new ArrayList<GuiTexture>();
		GuiTexture gui1 = new GuiTexture(loader.loadTexture("socuwan"), new Vector2f(0.7f, 0.5f), new Vector2f(0.125f, 0.125f));
		guis.add(gui1);

		GuiTexture gui3 = new GuiTexture(loader.loadTexture("health"), new Vector2f(0.8f, 0.9f), new Vector2f(0.2f, 0.2f));
		guis.add(gui3);
		GuiRenderer guiRenderer = new GuiRenderer(loader);

		//Creacion de Entidades sobre el terreno
		//------------------------------------------------------------------------------------
		Random random = new Random(5666778);
		for (int i = 0; i < 60; i++) {
			if (i % 3 == 0) {
				float x = random.nextFloat() * 150;
				float z = random.nextFloat() * -150;
				if ((x > 50 && x < 100) || (z < -50 && z > -100)) {
				} else {
					float y = terrain.getHeightOfTerrain(x, z);

					entities.add(new Entity(fernModel, 3, new Vector3f(x, y, z), 0,
							random.nextFloat() * 360, 0, 0.9f));
				}
			}
			if (i % 2 == 0) {

				float x = random.nextFloat() * 150;
				float z = random.nextFloat() * -150;
				if ((x > 50 && x < 100) || (z < -50 && z > -100)) {

				} else {
					float y = terrain.getHeightOfTerrain(x, z);
					entities.add(new Entity(pineModel, 1, new Vector3f(x, y, z), 0,
							random.nextFloat() * 360, 0, random.nextFloat() * 0.6f + 0.8f));
				}
			}
		}
		entities.add(new Entity(rocks, new Vector3f(75, 4.6f, -75), 0, 0, 0, 75));


		//Creacion de Personaje
		//------------------------------------------------------------------------------------
		TexturedModel playerModel = loader.createTexturedModel("person", "playerTexture", 10, 1);
		Player player = new Player(playerModel, new Vector3f(0, 0f, 0f), 0, 180, 0, 0.5f);
		entities.add(player);

		//Creacion de Camara
		//------------------------------------------------------------------------------------
		Camera camera = new Camera(player);
		//camera.getPosition().set(0, 200, 0);

		//----------------------------------------------------------------------------------
		//Modelo MD3
		MD3Model lara;
		lara = new MD3Model();
		lara.loadModel(new Config().getPath() + "md3/lara", "lara");
		lara.setTorsoAnimation("TORSO_STAND2");
		lara.setLegsAnimation("LEGS_IDLE");
		float rotaModel = 0f;
		shader = new SimpleShader();

        //Manejo del mouse para seleccionar objetos
		//------------------------------------------------------------------------------------
		MousePicker picker = new MousePicker(camera, masterRenderer.getProjectionMatrix(), terrain);

		//Bucle principal
		//------------------------------------------------------------------------------------
		while (!glfwWindowShouldClose(DisplayManager.window)) { // <- ❗️ IMPORTANT: Use this instead of `while (!Display.isCloseRequested())`
			player.move(terrain);
			camera.move();
			//Mouse.update();
			picker.update();

			/*
			if (Keyboard.isKeyDown(GLFW_KEY_Y)) {
				new Particle(new Vector3f(player.getPosition()), new Vector3f(0, 30, 0), 1, 2, 0, 1);
			}
			*/


			//System.out.println("player: "+player.getPosition());
			system.generateParticles(player.getPosition());

			smokeSystem.generateParticles(smokePosition);
			fireSystem.generateParticles(firePosition);
			cosmicSystem.generateParticles(cosmicPosition);
			systemAtlas.generateParticles(atlasPosition);


			ParticleMaster.update(camera);

			//System.out.println("ray x: " + picker.getCurrentRay().x + "   y: " + picker.getCurrentRay().y + "z: " + picker.getCurrentRay().z);

			//Vector3f terrainPoint = picker.getCurrentTerrainPoint();
			//if (terrainPoint!=null) {
			//	lampEntity.setPosition(terrainPoint);
			//	lampLight.setPosition(new Vector3f(terrainPoint.x, terrainPoint.y + 14, terrainPoint.z));
			//}

			//--------
			entity.increaseRotation(0.1f, 0.2f, 0.3f);
			entity2.increaseRotation(0.3f, 0.1f, 0.2f);
			entity3.increaseRotation(0.2f, 0.3f, 0.1f);

			//masterRenderer.processEntity(player);

			GL11.glEnable(GL30.GL_CLIP_DISTANCE0);

			//render reflection teture
			fbos.bindReflectionFrameBuffer();
			float distance = 2 * (camera.getPosition().y - water.getHeight());
			// change position and pitch of camera to render the reflection
			camera.getPosition().y -= distance;
			camera.invertPitch();
			masterRenderer.renderScene(entities, normalMapEntities, terrains, lights, camera, new Vector4f(0, 1, 0, -water.getHeight()+1f));
			camera.getPosition().y += distance;
			camera.invertPitch();

			//render reflection texture
			fbos.bindRefractionFrameBuffer();
			masterRenderer.renderScene(entities, normalMapEntities, terrains, lights, camera, new Vector4f(0, -1, 0, water.getHeight()));

			//renderer.processTerrain(terrain);
			//renderer.processTerrain(terrain2);
			//renderer.processEntity(entity);
			//for(Entity entity2:entities){
			//	renderer.processEntity(entity2);
			//}
			//renderer.render(lights, camera);

			//MD3 model
			if (Keyboard.isKeyDown(GLFW_KEY_Y)) {
				lara.setLegsAnimation("LEGS_RUN");
			}
			if (Keyboard.isKeyDown(GLFW_KEY_U)) {
				lara.setLegsAnimation("LEGS_IDLE");
			}

			Matrix4f modelMatrix = new Matrix4f()
					.translate(
							player.getPosition().x,
							player.getPosition().y,
							player.getPosition().z
					)
					.rotateY((float) Math.toRadians(-90 + player.getRotY()))
					.scale(0.1f);
			/*
			Matrix4f modelMatrix = new Matrix4f()
					//.translate(5.0f, 5f, -30f)
					.translate(player.getPosition().x+2f , player.getPosition().y+6.5f, player.getPosition().z)
					.rotateY((float)Math.toRadians(player.getRotY()-90))//rotaModel))
					.scale(0.06f);*/
			//.translate(-camera.getPosition().x, -camera.getPosition().y, -camera.getPosition().z);
			//GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
			//GL11.glPolygonOffset(1.0f, 1.0f);

			drawLara(shader, lara, masterRenderer, camera, modelMatrix);

			//GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);

			//render to screen
			GL11.glDisable(GL30.GL_CLIP_DISTANCE0);
			fbos.unbindCurrentFrameBuffer();
			masterRenderer.renderScene(entities, normalMapEntities, terrains, lights, camera, new Vector4f(0, -1, 0, 100000));

			drawLara(shader, lara, masterRenderer, camera, modelMatrix);

			//---------
			waterRenderer.render(waters, camera, light);
			ParticleMaster.renderParticles(camera);
			guiRenderer.render(guis);
			TextMaster.render();
			DisplayManager.updateDisplay();
		}

		ParticleMaster.cleanUp();
		TextMaster.cleanUp();
		fbos.cleanUp();
		waterShader.cleanUp();
		guiRenderer.cleanUp();
		masterRenderer.cleanUp();
		loader.cleanUp();
		DisplayManager.closeDisplay();
	}

}