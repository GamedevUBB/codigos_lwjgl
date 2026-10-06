package org.lwjglb.md3;/*
 * Author: Ron Sullivan (modified by Thomas Hourdel).
 * E-mail: thomas.hourdel@libertysurf.fr
 */



/* Standard imports.
 */

import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Matrix4f;
import java.awt.geom.*;
import java.awt.Image;
import java.awt.Graphics2D;
import java.awt.image.*;
import java.io.*;
import java.nio.*;
import java.util.StringTokenizer;
import org.lwjgl.*;
import org.lwjgl.opengl.*;

/** An animated MD3 model. */
/****************************************************************************************/
public final class MD3Model {

	/** This holds the header information that is read in at the beginning of the file.*/
	
	private final class MD3Header {
		String	fileID;							// This stores the file ID - Must be "IDP3"
		int		version;						// This stores the file version - Must be 15
		String	strFile;						// This stores the name of the file
		int		numFrames;						// This stores the number of animation frames
		int		numTags;						// This stores the tag count
		int		numMeshes;						// This stores the number of sub-objects in the mesh
		int		numMaxSkins;					// This stores the number of skins for the mesh
		int		headerSize;						// This stores the mesh header size
		int		tagStart;						// This stores the offset into the file for tags
		int		tagEnd;							// This stores the end offset into the file for tags
		int		fileSize;						// This stores the file size

		protected MD3Header() {
			fileID		= byte2string(4);	m_FilePointer += 4;
			version		= byte2int();
			strFile		= byte2string(68);	m_FilePointer += 68;
			numFrames	= byte2int();
			numTags		= byte2int();
			numMeshes	= byte2int();
			numMaxSkins = byte2int();
			headerSize	= byte2int();
			tagStart	= byte2int();
			tagEnd		= byte2int();
			fileSize	= byte2int();
		}
	
	};

	/** This structure is used to read in the mesh data for the .md3 models.*/
	private final class MD3MeshInfo {
		String	meshID;							// This stores the mesh ID
		String	strName;						// This stores the mesh name
		int		numMeshFrames;					// This stores the mesh aniamtion frame count
		int		numSkins;						// This stores the mesh skin count
		int     numVertices;					// This stores the mesh vertex count
		int		numTriangles;					// This stores the mesh face count
		int		triStart;						// This stores the starting offset for the triangles
		int		headerSize;						// This stores the header size for the mesh
		int     uvStart;						// This stores the starting offset for the UV coordinates
		int		vertexStart;					// This stores the starting offset for the vertex indices
		int		meshSize;						// This stores the total mesh size

		protected MD3MeshInfo() {
			meshID			= byte2string(4);	m_FilePointer += 4;
			strName			= byte2string(68);	m_FilePointer += 68;
			numMeshFrames	= byte2int();
			numSkins		= byte2int();
			numVertices		= byte2int();
			numTriangles	= byte2int();
			triStart		= byte2int();
			headerSize		= byte2int();
			uvStart			= byte2int();
			vertexStart		= byte2int();
			meshSize		= byte2int();
		}
	};

	// This is our tag structure for the .MD3 file format.  These are used link other
	// models to and the rotate and transate the child models of that model.
	private final class MD3Tag {
		String	 strName;						// This stores the name of the tag (I.E. "tag_torso")
		Vector3f vPosition = new Vector3f();	// This stores the translation that should be performed
		float[]	 rotation = new float[9];		// This stores the 3x3 rotation matrix for this frame

		protected MD3Tag() {
			strName = byte2string(64);	m_FilePointer += 64;
			vPosition.x = byte2float();
			vPosition.y = byte2float();
			vPosition.z = byte2float();
			for (int i=0; i < 9; i++ ) {
				rotation[i] = byte2float();
			}
		}
	};

	/** This stores the bone information (useless as far as I can see...).*/
	private final class MD3Bone	{
		float[]	mins = new float[3];			// This is the min (x, y, z) value for the bone
		float[]	maxs = new float[3];			// This is the max (x, y, z) value for the bone
		float[]	position = new float[3];		// This supposedly stores the bone position???
		float	scale;							// This stores the scale of the bone
		String	creator;						// The modeler used to create the model (I.E. "3DS Max")

		protected MD3Bone()	{
			mins[0]		= byte2float();
			mins[1]		= byte2float();
			mins[2]		= byte2float();
			maxs[0]		= byte2float();
			maxs[1]		= byte2float();
			maxs[2]		= byte2float();
			position[0] = byte2float();
			position[1] = byte2float();
			position[2] = byte2float();
			scale		= byte2float();
			creator		= byte2string(16);	m_FilePointer += 16;
		}
	};

	/** This stores the normals and vertex indices. */
	private class MD3Triangle {
	   short[] vertex = new short[3];			// The vertex for this face (scale down by 64.0f)
	   int[] normal = new int[2];				// This stores some crazy normal values (not sure...)

	   protected MD3Triangle() {
			vertex[0] = byte2short();
			vertex[1] = byte2short();
			vertex[2] = byte2short();
			normal[0] = byte2byte();
			normal[1] = byte2byte();
	   }
	};

	/** This stores the indices into the vertex and texture coordinate arrays.*/
	private final class MD3Face {
	   int[] vertexIndices = new int[3];	

	   protected MD3Face() {
			vertexIndices[0] = byte2int();
			vertexIndices[1] = byte2int();
			vertexIndices[2] = byte2int();
	   }
	};

	/** This stores UV coordinates.*/

	private final class MD3TexCoord {
	   float[] textureCoord = new float[2];		// UV coordinates

	   protected MD3TexCoord() {
		   textureCoord[0] = byte2float();
		   textureCoord[1] = byte2float();
	   }
	};

	/** This stores a skin name (We don't use this, just the name of the model to get the texture).*/
		private final class MD3Skin {
		String strName;							// Skin name

		protected MD3Skin()	{
			strName = byte2string(68);	m_FilePointer += 68;
		}
	};

	/** This is our face structure. This is is used for indexing into the vertex 
	 *  and texture coordinate arrays. From this information we know which vertices
	 *  from our vertex array go to which face, along with the correct texture coordinates.
	 */
	private final class Face {
		int[] vertIndex = new int[3];			// Indicies for the verts that make up this triangle
		int[] coordIndex = new int[3];			// Indicies for the tex coords to texture this face

		protected Face()
		{
		}
	};

	/** This holds the information for a material. It may be a texture map of a color.*/
	private final class MaterialInfo {
		String  strName;						// The texture name
		String  strFile;						// The texture file name (If this is set it's a texture map)
		byte[]  color = new byte[3];			// The color of the object (R, G, B)
		int   texureId;							// the texture ID
		float uTile;							// u tiling of texture  (Currently not used)
		float vTile;							// v tiling of texture	(Currently not used)
		float uOffset;							// u offset of texture	(Currently not used)
		float vOffset;							// v offset of texture	(Currently not used)

		protected MaterialInfo()
		{
		}
	};

	/** This holds all the information for our model/scene.*/
	private final class Object3D {
		int  numOfVerts;						// The number of verts in the model
		int  numOfFaces;						// The number of faces in the model
		int  numTexVertex;						// The number of texture coordinates
		int  materialID;						// The texture ID to use, which is the index into our texture array
		boolean bHasTexture;					// This is TRUE if there is a texture map for this object
		String strName;							// The name of the object
		Vector3f[]  pVerts;						// The object's vertices
		Vector3f[]  pNormals;					// The object's normals
		Vector2f[]  pTexVerts;					// The texture's UV coordinates
		Face[] pFaces;							// The faces information of the object

		// NUEVO — cachear VAO/VBOs entre frames
		int vaoID        = -1;
		int verticesVboID = -1;
		int texCoordsVboID = -1;

		protected Object3D()
		{
		}
	};

	/** This holds our information for each animation of the md3 model.*/

	public final class AnimationInfo {
		String strName;							// This stores the name of the animation (I.E. "TORSO_STAND")
		int startFrame;							// This stores the first frame number for this animation
		int endFrame;							// This stores the last frame number for this animation
		int loopingFrames;						// This stores the looping frames for this animation (not used)
		int framesPerSecond;					// This stores the frames per second that this animation runs

		public AnimationInfo()
		{
		}
	};

	/** This holds our model information.  This should also turn into a robust class.*/

	public final class Model3D {
		int numOfObjects;						// The number of objects in the model
		int numOfMaterials;						// The number of materials for the model
		FastVector pMaterials = new FastVector(); // The list of material information (Textures and colors)
		FastVector pObject = new FastVector();	// The object list for our model

		int numOfAnimations;					// The number of animations in this model 
		int currentAnim;						// The current index into pAnimations list 
		int currentFrame;						// The current frame of the current animation 
		int nextFrame;							// The next frame of animation to interpolate too
		float t;								// The ratio of 0.0f to 1.0f between each key frame
		long lastTime;							// This stores the last time that was stored
		FastVector pAnimations= new FastVector(); // The list of animations

		int numOfTags;							// This stores the number of tags in the model
		Model3D[] pLinks;						// This stores a list of pointers that are linked to this model
		MD3Tag[] pTags;							// This stores all the tags for the model animations

		public Model3D()
		{
		}
	};


	  /** This stores the ID for the legs model. **/
	public static final int kLower = 0;

	  /** This stores the ID for the torso model. **/
	public static final int kUpper = 1;

	  /** This stores the ID for the head model. **/
	public static final int kHead = 2;

	  /** This stores the ID for the weapon model. **/
	public static final int kWeapon = 3;

	  /** The first twirling death animation. **/
	public static final int BOTH_DEATH1 = 0;

	  /** The end of the first twirling death animation. **/
	public static final int BOTH_DEAD1 = 1;

	  /** The second twirling death animation. **/
	public static final int BOTH_DEATH2 = 2;

	  /** The end of the second twirling death animation. **/
	public static final int BOTH_DEAD2 = 3;

	  /** The back flip death animation. **/
	public static final int BOTH_DEATH3 = 4;

	  /** The end of the back flip death animation. **/
	public static final int BOTH_DEAD3 = 5;

	  /** The torso's gesturing animation. **/
	public static final int TORSO_GESTURE = 6;
	
	  /** The torso's attack1 animation. **/
	public static final int TORSO_ATTACK = 7;

	  /** The torso's attack2 animation. **/
	public static final int TORSO_ATTACK2 = 8;

	  /** The torso's weapon drop animation. **/
	public static final int TORSO_DROP = 9;

	  /** The torso's weapon pickup animation. **/
	public static final int TORSO_RAISE = 10;

	  /** The torso's idle stand animation. **/
	public static final int TORSO_STAND = 11;

	  /** The torso's idle stand2 animation. **/
	public static final int TORSO_STAND2 = 12;

	  /** The legs's crouching walk animation. **/
	public static final int LEGS_WALKCR = 13;

	  /** The legs's walk animation. **/
	public static final int LEGS_WALK = 14;

	  /** The legs's run animation. **/
	public static final int LEGS_RUN = 15;

	  /** The legs's running backwards animation. **/
	public static final int LEGS_BACK = 16;

	  /** The legs's swimming animation. **/
	public static final int LEGS_SWIM = 17;
	
	  /** The legs's jumping animation. **/
	public static final int LEGS_JUMP = 18;

	  /** The legs's landing animation. **/
	public static final int LEGS_LAND = 19;

	  /** The legs's jumping back animation. **/
	public static final int LEGS_JUMPB = 20;

	  /** The legs's landing back animation. **/
	public static final int LEGS_LANDB = 21;

	  /** The legs's idle stand animation. **/
	public static final int LEGS_IDLE = 22;

	  /** The legs's idle crouching animation. **/
	public static final int LEGS_IDLECR = 23;

	  /** The legs's turn animation. **/
	public static final int LEGS_TURN = 24;

	  /** The define for the maximum amount of animations. **/
	public static final int MAX_ANIMATIONS = 200;

	  /** The header data. **/
	private MD3Header m_Header;

	  /** The skin name data. **/
	private MD3Skin m_pSkins[];

	  /** The texture coordinates. **/
	private MD3TexCoord m_pTexCoords[];

	  /** Face/Triangle data. **/
	private MD3Face m_pTriangles[];

	  /** Vertex/UV indices. **/
	private MD3Triangle m_pVertices[];

	  /** This stores the bone data. **/
	private MD3Bone m_pBones[];

	  /** Model for the character's head. **/
	private Model3D m_Head;

	  /** Model for the character's upper body parts. **/
	private Model3D m_Upper;

	  /** Model for the character's lower body parts. **/
	private Model3D m_Lower;

	  /** This store the players weapon model (optional load). **/
	private Model3D m_Weapon;

	  /** The maximum amount of textures to load. **/
	private final static int MAX_TEXTURES = 20;
	
	  /** All textures. **/
	private int[] m_Textures = new int[MAX_TEXTURES];	
	
	  /** This stores the texture array for each of the textures assigned to this model. **/
	private FastVector strTextures = new FastVector();

	  /** Global file content. **/
	private byte[] fileContents;
	
	  /** Pointer use for file browsing. **/
	private int m_FilePointer = 0;


	/** MD3Model constructor.*/
	public MD3Model() {
		m_Head = new Model3D();
		m_Upper = new Model3D();
		m_Lower = new Model3D();
		m_Weapon = new Model3D();
	}

	/** This returns true if the string strSubString is inside of strString.
	 *  @param strString Main string.
	 *  @param strSubString The string to find.
	 *  @return True if the string strSubString is inside of strString.
	 */
	private final boolean IsInString(String strString, String strSubString) {
		  // Grab the starting index where the sub string is in the original string
		int index = strString.indexOf(strSubString);

		  // Make sure the index returned was valid
		if(index >= 0 && index < strString.length())
			return true;

		  // The sub string does not exist in strString.
		return false;
	}

	/* File browsing utilities methods.*/
	private final int byte2byte() {
		int b1 = (fileContents[m_FilePointer  ] & 0xFF);
		m_FilePointer += 1;
		return (b1);
	}

	private final short byte2short() {
		int s1 = (fileContents[m_FilePointer  ] & 0xFF);
		int s2 = (fileContents[m_FilePointer+1] & 0xFF) << 8;
		m_FilePointer += 2;
		return ((short)(s1 | s2));
	}

	private final int byte2int() {
		int i1 = (fileContents[m_FilePointer  ] & 0xFF);
		int i2 = (fileContents[m_FilePointer+1] & 0xFF) <<  8;
		int i3 = (fileContents[m_FilePointer+2] & 0xFF) << 16;
		int i4 = (fileContents[m_FilePointer+3] & 0xFF) << 24;
		m_FilePointer += 4;
		return (i1 | i2 | i3 | i4);
	}

	private final float byte2float() {
		return Float.intBitsToFloat(byte2int());
	}

	private final String byte2string(int size) {
		for(int i = m_FilePointer; i < m_FilePointer + size; i++)
		{
			if((fileContents[i] & 0xFF)== (byte)0)
				return new String(fileContents, m_FilePointer, i - m_FilePointer);
		}
		return new String(fileContents,m_FilePointer, size);
	}

	/** This returns a specific model from the character (kLower, kUpper, kHead, kWeapon).
	 *  @param whichPart Wanted part ID.
	 *  @return The wanted model.
	 */
	public final Model3D getModel(int whichPart) {
		  // Return the legs model if desired
		if(whichPart == kLower) 
			return m_Lower;

		  // Return the torso model if desired
		if(whichPart == kUpper) 
			return m_Upper;

		  // Return the head model if desired
		if(whichPart == kHead) 
			return m_Head;

		  // Return the weapon model
		return m_Weapon;
	}

	/** This loads the md3 model from the given path and character name.
	 *  @param strPath Model's path.
	 *  @param strModel Model's name.
	 */
	public final void loadModel(String strPath, String strModel) {
		String strLowerModel;					// This stores the file name for the lower.md3 model
		String strUpperModel;					// This stores the file name for the upper.md3 model
		String strHeadModel;					// This stores the file name for the head.md3 model
		String strLowerSkin;					// This stores the file name for the lower.md3 skin
		String strUpperSkin;					// This stores the file name for the upper.md3 skin
		String strHeadSkin;						// This stores the file name for the head.md3 skin

		  // Store the correct files names for the .md3 and .skin file for each body part.
		  // We concatinate this on top of the path name to be loaded from.
		strLowerModel = strPath + "/" + strModel + "_lower.md3";
		strUpperModel = strPath + "/" + strModel + "_upper.md3";
		strHeadModel = strPath + "/" + strModel + "_head.md3";
		
		// Get the skin file names with their path
		strLowerSkin = strPath + "/" + strModel + "_lower.skin";
		strUpperSkin = strPath + "/" + strModel + "_upper.skin";
		strHeadSkin = strPath + "/" + strModel + "_head.skin";

		  // Load the head mesh (*_head.md3) and make sure it loaded properly
		if(!importMD3(m_Head, strHeadModel))
		{
			System.out.println("[Error]: unable to load the HEAD part from model \"" + strModel + "\".");
			System.exit(0);
		}

		  // Load the upper mesh (*_head.md3) and make sure it loaded properly
		if(!importMD3(m_Upper, strUpperModel))		
		{
			System.out.println("[Error]: unable to load the UPPER part from model \"" + strModel + "\".");
			System.exit(0);
		}

		  // Load the lower mesh (*_lower.md3) and make sure it loaded properly
		if(!importMD3(m_Lower, strLowerModel))
		{
			System.out.println("[Error]: unable to load the LOWER part from model \"" + strModel + "\".");
			System.exit(0);
		}

		  // Load the lower skin (*_upper.skin) and make sure it loaded properly
		if(!loadSkin(m_Lower, strLowerSkin))
		{
			System.out.println("[Error]: unable to load the LOWER part from model's skin \"" + strModel + "\".");
			System.exit(0);
		}

		  // Load the upper skin (*_upper.skin) and make sure it loaded properly
		if(!loadSkin(m_Upper, strUpperSkin))
		{
			System.out.println("[Error]: unable to load the UPPER part from model's skin \"" + strModel + "\".");
			System.exit(0);
		}

		  // Load the head skin (*_head.skin) and make sure it loaded properly
		if(!loadSkin(m_Head, strHeadSkin))
		{
			System.out.println("[Error]: unable to load the HEAD part from model's skin \"" + strModel + "\".");
			System.exit(0);
		}

		  // Load the lower, upper and head textures.  
		loadModelTextures(m_Lower, strPath);
		loadModelTextures(m_Upper, strPath);
		loadModelTextures(m_Head, strPath);

		  // Add the path and file name prefix to the animation.cfg file
		String strConfigFile = strPath + "/" + strModel + "_animation.cfg";

		  // Load the animation config file (*_animation.config) and make sure it loaded properly
		if(!loadAnimations(strConfigFile))
		{
			System.out.println("[Error]: unable to load the animation config file from model \"" + strModel + "\".");
			System.exit(0);
		}

		  // Link the lower body to the upper body when the tag "tag_torso" is found in our tag array
		linkModel(m_Lower, m_Upper, "tag_torso");

		  // Link the upper body to the head when the tag "tag_head" is found in our tag array
		linkModel(m_Upper, m_Head, "tag_head");
	}

	/** This loads a md3 weapon model from the given path and weapon name.
	 *  @param strPath Weapon's path.
	 *  @param strModel Weapon's name.
	 */
	public final void loadWeapon(String strPath, String strModel) {
		String strWeaponModel;					// This stores the file name for the weapon model
		String strWeaponShader;					// This stores the file name for the weapon shader.

		  // Concatenate the path and model name together
		strWeaponModel = strPath + "/" + strModel + ".md3";

		  // Load the weapon mesh (*.md3) and make sure it loaded properly
		if(!importMD3(m_Weapon, strWeaponModel))
		{
			System.out.println("[Error]: unable to load the weapon model \"" + strModel + "\".");
			System.exit(0);
		}

		  // Add the path, file name and .shader extension together to get the file name and path
		strWeaponShader = strPath + "/" + strModel + ".shader";

		  // Load our textures associated with the gun from the weapon shader file
		if(!loadShader(m_Weapon, strWeaponShader))
		{
			System.out.println("[Error]: unable to load the shader for weapon \"" + strModel + "\".");
			System.exit(0);
		}

		  // We should have the textures needed for each weapon part loaded from the weapon's
		  // shader, so let's load them in the given path.
		loadModelTextures(m_Weapon, strPath);

		  // Link the weapon to the model's hand that has the weapon tag
		linkModel(m_Upper, m_Weapon, "tag_weapon");
	}

	/** This loads the textures for the current model passed in with a directory.
	 *  @param pModel Current model.
	 *  @param strPath Source path.
	 */
	private final void loadModelTextures(Model3D pModel, String strPath) {
		  // Go through all the materials that are assigned to this model
		for(int i = 0; i < pModel.numOfMaterials; i++)
		{
			  // Check to see if there is a file name to load in this material
			if(((MaterialInfo)pModel.pMaterials.elementAt(i)).strFile != null)
			{
				  // Create a boolean to tell us if we have a new texture to load
				boolean bNewTexture = true;

				  // Go through all the textures in our string list to see if it's already loaded
				for(int j = 0; j < strTextures.size(); j++)
				{
					  // If the texture name is already in our list of texture, don't load it again.
					if(((MaterialInfo)pModel.pMaterials.elementAt(i)).strFile.equals(((String)strTextures.elementAt(j))))
					{
						  // We don't need to load this texture since it's already loaded
						bNewTexture = false;

						  // Assign the texture index to our current material textureID.
						((MaterialInfo)pModel.pMaterials.elementAt(i)).texureId = j;
					}
				}

				  // Make sure before going any further that this is a new texture to be loaded
				if(bNewTexture == false)
					continue;
				
				String strFullPath;

				  // Add the file name and path together so we can load the texture
				strFullPath = strPath + "/" + ((MaterialInfo)pModel.pMaterials.elementAt(i)).strFile;

				  // We pass in a reference to an index into our texture array member variable.
				createTexture(m_Textures, strFullPath, strTextures.size());								

				  // Set the texture ID for this material by getting the current loaded texture count
				((MaterialInfo)pModel.pMaterials.elementAt(i)).texureId = strTextures.size();

				  // Now we increase the loaded texture count by adding the texture name to our
				  // list of texture names.
				strTextures.addElement(((MaterialInfo)pModel.pMaterials.elementAt(i)).strFile);
			}
		}
	}

	/** This loads the .cfg file that stores all the animation information.
	 *  @param strConfigFile Configuration file's path.
	 */
	private final boolean loadAnimations(String strConfigFile) {
		try
		{
			  // Create an animation object for every valid animation in the Quake3 Character
			AnimationInfo[] animations = new AnimationInfo[MAX_ANIMATIONS];
			BufferedReader reader = new BufferedReader(new FileReader(strConfigFile));
			String strWord = "";					// This stores the current word we are reading in
			String strLine = "";					// This stores the current line we read in
			int currentAnim = 0;					// This stores the current animation count
			int torsoOffset = 0;					// The offset between the first torso and leg animation
			StringTokenizer tokenizer;

			  // Here we go through every word in the file until a numeric number is found.
			while((strLine = reader.readLine()) != null)
			{
				  // skip blank lines
				if(strLine.length() == 0)
				{
					continue;
				}
				  // If the first character of the word is NOT a number, we haven't hit an animation line
				if(!Character.isDigit(strLine.charAt(0)))
				{
					continue;
				}

				  // If we get here, we must be on an animation line, so let's parse the data.
				tokenizer = new StringTokenizer(strLine);

				  // Read in the number of frames, the looping frames, then the frames per second
				  // for this current animation we are on.
				int startFrame		= Integer.parseInt(tokenizer.nextToken());
				int numOfFrames		= Integer.parseInt(tokenizer.nextToken());
				int loopingFrames	= Integer.parseInt(tokenizer.nextToken());
				int framesPerSecond = Integer.parseInt(tokenizer.nextToken());
				
				  // Initialize the current animation structure with the data just read in
				animations[currentAnim] = new AnimationInfo();
				animations[currentAnim].startFrame		= startFrame;
				animations[currentAnim].endFrame		= startFrame + numOfFrames;
				animations[currentAnim].loopingFrames	= loopingFrames;
				animations[currentAnim].framesPerSecond = framesPerSecond;

				  // Read past the "//" and read in the animation name (I.E. "BOTH_DEATH1").
				  // This might not be how every config file is set up, so make sure.
				tokenizer.nextToken();

				  // Copy the name of the animation to our animation structure
				animations[currentAnim].strName = tokenizer.nextToken();

				  // If the animation is for both the legs and the torso, add it to their animation list
				if(IsInString(strLine, "BOTH"))
				{
					  // Add the animation to each of the upper and lower mesh lists
					m_Upper.pAnimations.addElement(animations[currentAnim]);
					m_Lower.pAnimations.addElement(animations[currentAnim]);
				}
				  // If the animation is for the torso, add it to the torso's list
				else if(IsInString(strLine, "TORSO"))
				{
					m_Upper.pAnimations.addElement(animations[currentAnim]);
				}
				  // If the animation is for the legs, add it to the legs's list
				else if(IsInString(strLine, "LEGS"))
				{
					  // If the torso offset hasn't been set, set it
					if(torsoOffset == 0)
						torsoOffset = animations[LEGS_WALKCR].startFrame - animations[TORSO_GESTURE].startFrame;

					  // Minus the offset from the legs animation start and end frame.
					animations[currentAnim].startFrame -= torsoOffset;
					animations[currentAnim].endFrame -= torsoOffset;

					  // Add the animation to the list of leg animations
					m_Lower.pAnimations.addElement(animations[currentAnim]);
				}
				  // Increase the current animation count
				currentAnim++;
			}	

			  // Store the number if animations for each list by the size() function
			m_Lower.numOfAnimations = m_Lower.pAnimations.size();
			m_Upper.numOfAnimations = m_Upper.pAnimations.size();
			m_Head.numOfAnimations = m_Head.pAnimations.size();
			m_Weapon.numOfAnimations = m_Head.pAnimations.size();
		}
		catch(Exception e)
		{
			return false;
		}
		  // Return a success
		return true;
	}

	/** This links the body part models to each other, along with the weapon.
	 *  @param pModel Main model.
	 *  @param pLink Model to be linked.
	 *  @param strTagName Tag name.
	 */
	private final void linkModel(Model3D pModel, Model3D pLink, String strTagName) {
		  // Go through all of our tags and find which tag contains the strTagName, then link'em
		for(int i = 0; i < pModel.numOfTags; i++)
		{
			  // If this current tag index has the tag name we are looking for
			if(pModel.pTags[i].strName.equals(strTagName))
			{
				  // Link the model's link index to the link (or model/mesh) and return
				pModel.pLinks[i] = pLink;
				return;
			}
		}
	}

	/** This sets the current frame of animation, depending on it's fps and t.
	 *  @param pModel Current model.
	 */
	private final void updateModel(Model3D pModel) {
		  // Initialize a start and end frame, for models with no animation
		int startFrame = 0;
		int endFrame   = 1;

		  // Here we grab the current animation that we are on from our model's animation list
		AnimationInfo pAnim = (AnimationInfo)pModel.pAnimations.elementAt(pModel.currentAnim);

		  // If there is any animations for this model
		if(pModel.numOfAnimations != 0)
		{
			  // Set the starting and end frame from for the current animation
			startFrame = pAnim.startFrame;
			endFrame   = pAnim.endFrame;
		}

		/*
		  // This gives us the next frame we are going to.
		 pModel.nextFrame = (pModel.currentFrame + 1) % endFrame;

		  // If the next frame is zero, that means that we need to start the animation over.
		if(pModel.nextFrame == 0)
			pModel.nextFrame =  startFrame;
		 */

		pModel.nextFrame = pModel.currentFrame + 1;
		if (pModel.nextFrame >= endFrame)
			pModel.nextFrame = startFrame;


		  // Next, we want to get the current time that we are interpolating by.
		setCurrentTime(pModel);
	}

	/** This recursively draws all the character nodes, starting with the legs.
	 */
	/**
	 * Versión mínima con shader: no usa glPushMatrix/glPopMatrix ni matrices fijas de OpenGL.
	 * La transformación se calcula con JOML y se envía al shader como uniform modelMatrix.
	 */
	public final void draw(Matrix4f modelMatrix, SimpleShader shader) {
		// Compensa la orientación z-up del MD3. Equivale al glRotatef(-90, 1, 0, 0) antiguo.
		Matrix4f rootMatrix = new Matrix4f(modelMatrix).rotateX((float)Math.toRadians(-90.0f));
		updateModel(m_Lower);
		updateModel(m_Upper);
		drawLink(m_Lower, rootMatrix, shader);
	}

	/** This draws the current mesh with an effected matrix stack from the last mesh.
	 *  @param pModel Current model.
	 */
	private final void drawLink(Model3D pModel, Matrix4f currentMatrix, SimpleShader shader) {
		renderModel(pModel, currentMatrix, shader);
		Quaternion qQuat = new Quaternion();
		Quaternion qNextQuat = new Quaternion();
		Quaternion qInterpolatedQuat;
		float[] pMatrix;
		float[] pNextMatrix;
		float[] finalMatrix = new float[16];

		for(int i = 0; i < pModel.numOfTags; i++)
		{
			Model3D pLink = pModel.pLinks[i];
			if(pLink != null)
			{
				Vector3f vOldPosition = pModel.pTags[pModel.currentFrame * pModel.numOfTags + i].vPosition;
				Vector3f vNextPosition = pModel.pTags[pModel.nextFrame * pModel.numOfTags + i].vPosition;

				Vector3f vPosition = new Vector3f();
				vPosition.x = vOldPosition.x + pModel.t * (vNextPosition.x - vOldPosition.x);
				vPosition.y = vOldPosition.y + pModel.t * (vNextPosition.y - vOldPosition.y);
				vPosition.z = vOldPosition.z + pModel.t * (vNextPosition.z - vOldPosition.z);

				pMatrix = pModel.pTags[pModel.currentFrame * pModel.numOfTags + i].rotation;
				pNextMatrix = pModel.pTags[pModel.nextFrame * pModel.numOfTags + i].rotation;

				qQuat.createFromMatrix(pMatrix, 3);
				qNextQuat.createFromMatrix(pNextMatrix, 3);
				qInterpolatedQuat = qQuat.slerp(qQuat, qNextQuat, pModel.t);

				qInterpolatedQuat.createMatrix(finalMatrix);
				finalMatrix[12] = vPosition.x;
				finalMatrix[13] = vPosition.y;
				finalMatrix[14] = vPosition.z;

				//Matrix4f childMatrix = new Matrix4f(currentMatrix).mul(new Matrix4f().set(finalMatrix));

				Matrix4f tagMatrix = new Matrix4f(
						finalMatrix[0], finalMatrix[4], finalMatrix[8],  0f,
						finalMatrix[1], finalMatrix[5], finalMatrix[9],  0f,
						finalMatrix[2], finalMatrix[6], finalMatrix[10], 0f,
						vPosition.x,   vPosition.y,    vPosition.z,     1f
				);

				Matrix4f childMatrix = new Matrix4f(currentMatrix).mul(tagMatrix);

				drawLink(pLink, childMatrix, shader);
			}
		}
	}

	/** This sets time t for the interpolation between the current and next key frame.
	 *  @param pModel Current model.
	 */
	private final void setCurrentTime(Model3D pModel) {
		  // Return if there is no animations in this model
		if(pModel.pAnimations.size() == 0)
			return;

		  // Get the current time in milliseconds
		long time = System.currentTimeMillis();
		
		  // Find the time that has elapsed since the last time that was stored
		long elapsedTime = time - pModel.lastTime;

		  // Store the animation speed for this animation in a local variable
		int animationSpeed = ((AnimationInfo)pModel.pAnimations.elementAt(pModel.currentAnim)).framesPerSecond;

		float t = elapsedTime / (1000f / animationSpeed);

		  // If our elapsed time goes over the desired time segment, start over and go 
		  // to the next key frame.
		if(elapsedTime >= (1000.0f / animationSpeed))
		{
			  // Set our current frame to the next key frame (which could be the start of the anim)
			pModel.currentFrame = pModel.nextFrame;

			  // Set our last time for the model to the current time
			pModel.lastTime = time;
		}

		  // Set the t for the model to be used in interpolation
		pModel.t = t;
	}

	/**
	 * Ruta antigua deshabilitada: el renderizado moderno debe pasar matriz y shader.
	 */
	private final void renderModel(Model3D pModel) {
		throw new UnsupportedOperationException("Use draw(Matrix4f, SimpleShader) para renderizar con OpenGL moderno.");
	}

	/**
	 * Render moderno mínimo: elimina glBegin/glEnd y envía los vértices interpolados
	 * de cada frame a VBOs temporales. Es una migración mínima; para máxima eficiencia
	 * conviene cachear VAOs/VBOs por objeto y actualizar solo los vértices animados.
	 */
	private final void renderModel(Model3D pModel, Matrix4f modelMatrix, SimpleShader shader) {

		if(pModel.pObject == null)
			return;

		shader.loadModelMatrix(modelMatrix);

		for(int i = 0; i < pModel.numOfObjects; i++) {
			Object3D pObject = (Object3D)pModel.pObject.elementAt(i);
			int currentIndex = pModel.currentFrame * pObject.numOfVerts;
			int nextIndex = pModel.nextFrame * pObject.numOfVerts;
			int textureID = ((MaterialInfo)pModel.pMaterials.elementAt(pObject.materialID)).texureId;

			GL13.glActiveTexture(GL13.GL_TEXTURE0);
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, m_Textures[textureID]);

			drawObjectWithVbo(pModel, pObject, currentIndex, nextIndex);
		}
	}

	/*
	private void drawObjectWithVbo(Model3D pModel, Object3D pObject, int currentIndex, int nextIndex) {
		int vertexCount = pObject.numOfFaces * 3;

		FloatBuffer verticesBuffer = BufferUtils.createFloatBuffer(vertexCount * 3);
		FloatBuffer texCoordsBuffer = BufferUtils.createFloatBuffer(vertexCount * 2);

		for(int j = 0; j < pObject.numOfFaces; j++) {
			for(int whichVertex = 0; whichVertex < 3; whichVertex++) {
				int index = pObject.pFaces[j].vertIndex[whichVertex];

				Vector3f vPoint1 = pObject.pVerts[currentIndex + index];
				Vector3f vPoint2 = pObject.pVerts[nextIndex + index];

				verticesBuffer.put(vPoint1.x + pModel.t * (vPoint2.x - vPoint1.x));
				verticesBuffer.put(vPoint1.y + pModel.t * (vPoint2.y - vPoint1.y));
				verticesBuffer.put(vPoint1.z + pModel.t * (vPoint2.z - vPoint1.z));

				if(pObject.pTexVerts != null) {
					texCoordsBuffer.put(pObject.pTexVerts[index].x);
					texCoordsBuffer.put(pObject.pTexVerts[index].y);
				} else {
					texCoordsBuffer.put(0.0f);
					texCoordsBuffer.put(0.0f);
				}
			}
		}

		verticesBuffer.flip();
		texCoordsBuffer.flip();

		int vaoID = GL30.glGenVertexArrays();
		int verticesVboID = GL15.glGenBuffers();
		int texCoordsVboID = GL15.glGenBuffers();

		GL30.glBindVertexArray(vaoID);

		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, verticesVboID);
		GL15.glBufferData(GL15.GL_ARRAY_BUFFER, verticesBuffer, GL15.GL_STREAM_DRAW);
		GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 0, 0L);
		GL20.glEnableVertexAttribArray(0);

		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, texCoordsVboID);
		GL15.glBufferData(GL15.GL_ARRAY_BUFFER, texCoordsBuffer, GL15.GL_STREAM_DRAW);
		GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, 0, 0L);
		GL20.glEnableVertexAttribArray(1);

		GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, vertexCount); //Aquí dibuja

		GL20.glDisableVertexAttribArray(0);
		GL20.glDisableVertexAttribArray(1);
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
		GL30.glBindVertexArray(0);

		GL15.glDeleteBuffers(verticesVboID);
		GL15.glDeleteBuffers(texCoordsVboID);
		GL30.glDeleteVertexArrays(vaoID);
	}
	*/

	private void drawObjectWithVbo(Model3D pModel, Object3D pObject, int currentIndex, int nextIndex) {
		int vertexCount = pObject.numOfFaces * 3;

		FloatBuffer verticesBuffer  = BufferUtils.createFloatBuffer(vertexCount * 3);
		FloatBuffer texCoordsBuffer = BufferUtils.createFloatBuffer(vertexCount * 2);

		for (int j = 0; j < pObject.numOfFaces; j++) {
			for (int whichVertex = 0; whichVertex < 3; whichVertex++) {
				int index = pObject.pFaces[j].vertIndex[whichVertex];
				Vector3f vPoint1 = pObject.pVerts[currentIndex + index];
				Vector3f vPoint2 = pObject.pVerts[nextIndex + index];

				verticesBuffer.put(vPoint1.x + pModel.t * (vPoint2.x - vPoint1.x));
				verticesBuffer.put(vPoint1.y + pModel.t * (vPoint2.y - vPoint1.y));
				verticesBuffer.put(vPoint1.z + pModel.t * (vPoint2.z - vPoint1.z));

				if (pObject.pTexVerts != null) {
					texCoordsBuffer.put(pObject.pTexVerts[index].x);
					texCoordsBuffer.put(pObject.pTexVerts[index].y);
				} else {
					texCoordsBuffer.put(0.0f);
					texCoordsBuffer.put(0.0f);
				}
			}
		}
		verticesBuffer.flip();
		texCoordsBuffer.flip();

		// Crear VAO/VBOs solo la primera vez
		if (pObject.vaoID == -1) {
			pObject.vaoID         = GL30.glGenVertexArrays();
			pObject.verticesVboID  = GL15.glGenBuffers();
			pObject.texCoordsVboID = GL15.glGenBuffers();

			GL30.glBindVertexArray(pObject.vaoID);

			GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, pObject.verticesVboID);
			GL15.glBufferData(GL15.GL_ARRAY_BUFFER, verticesBuffer, GL15.GL_STREAM_DRAW);
			GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 0, 0L);
			GL20.glEnableVertexAttribArray(0);

			GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, pObject.texCoordsVboID);
			GL15.glBufferData(GL15.GL_ARRAY_BUFFER, texCoordsBuffer, GL15.GL_STREAM_DRAW);
			GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, 0, 0L);
			GL20.glEnableVertexAttribArray(1);

			GL30.glBindVertexArray(0);
		} else {
			// ✅ Solo actualizar los vértices animados (UV nunca cambia)
			GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, pObject.verticesVboID);
			GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0, verticesBuffer);
			GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
		}

		// ✅ Dibujar
		GL30.glBindVertexArray(pObject.vaoID);
		GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, vertexCount);
		GL30.glBindVertexArray(0);
	}

	/** This sets the current animation that the upper body will be performing.
	 *  @param strAnimation Animation name.
	 */
	public final void setTorsoAnimation(String strAnimation)
	{
		  // Go through all of the animations in this model
		for(int i = 0; i < m_Upper.numOfAnimations; i++)
		{
			  // If the animation name passed in is the same as the current animation's name
			if(((AnimationInfo)m_Upper.pAnimations.elementAt(i)).strName.equals(strAnimation))
			{
				  // Set the legs animation to the current animation we just found and return
				m_Upper.currentAnim = i;
				m_Upper.currentFrame = ((AnimationInfo)m_Upper.pAnimations.elementAt(m_Upper.currentAnim)).startFrame;
				return;
			}
		}
	}

	/** This sets the current animation that the lower body will be performing.
	 *  @param strAnimation Animation name.
	 */
	public final void setLegsAnimation(String strAnimation)
	{
		  // Go through all of the animations in this model
		for(int i = 0; i < m_Lower.numOfAnimations; i++)
		{
			  // If the animation name passed in is the same as the current animation's name
			if(((AnimationInfo)m_Lower.pAnimations.elementAt(i)).strName.equals(strAnimation))
			{
				  // Set the legs animation to the current animation we just found and return
				m_Lower.currentAnim = i;
				m_Lower.currentFrame = ((AnimationInfo)m_Lower.pAnimations.elementAt(m_Lower.currentAnim)).startFrame;
				return;
			}
		}
	}

	/** This is called by the client to open the .Md3 file, read it, then clean up.
	 *  @param pModel Current model.
	 *  @param file MD3 file name.
	 */
	private final boolean importMD3(Model3D pModel, String file)
	{
		try
		{
			File f = new File(file);

			  // Wrap a buffer to make reading more efficient (faster).
			BufferedInputStream bis = new BufferedInputStream(new FileInputStream(f));

			fileContents = new byte[(int)f.length()];

			  // Read the entire file into memory.
			bis.read(fileContents, 0, (int)f.length());

			  // Close the .md3 file that we opened
			bis.close();

			  // Open the MD3 file in binary
			m_FilePointer = 0;

			  // Read the header data and store it in our m_Header member variable
			m_Header = new MD3Header();

			  // Get the 4 character ID
			String ID = m_Header.fileID;

			  // Make sure the ID == IDP3 and the version is this crazy number '15' or else it's a bad egg
			if(!ID.equals("IDP3") || m_Header.version != 15)
			{
				System.out.println("[Error]: file " + file + " version is not valid.");
				System.exit(0);
			}
			
			  // Read in the model and animation data
			readMD3Data(pModel);
		}
		catch(Exception e)
		{
			System.out.println("[Error]: can't read " + file + " correctly.");
			System.exit(0);
		}
		// Return a success
		return true;
	}

	/** This function reads in all of the model's data, except the animation frames.
	 *  @param pModel Current model.
	 */
	private final void readMD3Data(Model3D pModel)
	{
		int i = 0;
		  // Here we allocate memory for the bone information and read the bones in.
		m_pBones = new MD3Bone[m_Header.numFrames];
		for (i = 0; i < m_Header.numFrames ; i++)
			m_pBones[i] = new MD3Bone();

		  // Free the unused bones
		m_pBones = null;

		  // Next, after the bones are read in, we need to read in the tags.
		pModel.pTags = new MD3Tag[m_Header.numFrames * m_Header.numTags];

		for(i = 0; i < m_Header.numFrames * m_Header.numTags; i++)
			pModel.pTags[i] = new MD3Tag();

		  // Assign the number of tags to our model
		pModel.numOfTags = m_Header.numTags;
		
		  // Now we want to initialize our links.
		pModel.pLinks = new Model3D[m_Header.numTags];
		
		  // Initilialize our link pointers to NULL
		for(i = 0; i < m_Header.numTags; i++)
			pModel.pLinks[i] = null;

		  // Get the current offset into the file
		int meshOffset = m_FilePointer;

		  // Create a local meshHeader that stores the info about the mesh
		MD3MeshInfo meshHeader = new MD3MeshInfo();

		  // Go through all of the sub-objects in this mesh
		for (int j = 0; j < m_Header.numMeshes; j++)
		{
			  // Seek to the start of this mesh and read in it's header
			m_FilePointer = meshOffset;
			meshHeader = new MD3MeshInfo();

			  // Here we allocate all of our memory from the header's information
			m_pSkins     = new MD3Skin[meshHeader.numSkins];
			m_pTexCoords = new MD3TexCoord[meshHeader.numVertices];
			m_pTriangles = new MD3Face[meshHeader.numTriangles];
			m_pVertices  = new MD3Triangle[meshHeader.numVertices * meshHeader.numMeshFrames];

			  // Read in the skin information
			for (i = 0; i < meshHeader.numSkins ; i++)
				m_pSkins[i] = new MD3Skin();
			
			  // Seek to the start of the triangle/face data, then read it in
			m_FilePointer = meshOffset + meshHeader.triStart;

			for (i = 0; i < meshHeader.numTriangles; i++)
				m_pTriangles[i] = new MD3Face();

			  // Seek to the start of the UV coordinate data, then read it in
			m_FilePointer = meshOffset + meshHeader.uvStart;

			for (i = 0; i < meshHeader.numVertices; i++)
				m_pTexCoords[i] = new MD3TexCoord();

			  // Seek to the start of the vertex/face index information, then read it in.
			m_FilePointer = meshOffset + meshHeader.vertexStart;
			for(i = 0; i < meshHeader.numMeshFrames * meshHeader.numVertices; i++)
				m_pVertices[i] = new MD3Triangle();

			  // Now that we have the data loaded into the md3 structures, let's convert them to
			  // our data types like Model3D and Object3D.
			convertDataStructures(pModel, meshHeader);

			  // Free all the memory for this mesh since we just converted it to our structures
			m_pSkins = null;    
			m_pTexCoords = null;
			m_pTriangles = null;
			m_pVertices = null;   

			  // Increase the offset into the file
			meshOffset += meshHeader.meshSize;
		}
	}

	/** This function converts the .md3 structures to our own model and object structures.
	 *  @param pModel Current model.
	 *  @param meshHeader Current mesh header informations.
	 */
	private final void convertDataStructures(Model3D pModel, MD3MeshInfo meshHeader)
	{
		int i = 0;
		// Increase the number of objects (sub-objects) in our model since we are loading a new one
		pModel.numOfObjects++;
	    // Create a empty object structure to store the object's info before we add it to our list
		Object3D currentMesh = new Object3D();
		// Copy the name of the object to our object structure
		currentMesh.strName = meshHeader.strName;
		// Assign the vertex, texture coord and face count to our new structure
		currentMesh.numOfVerts   = meshHeader.numVertices;
		currentMesh.numTexVertex = meshHeader.numVertices;
		currentMesh.numOfFaces   = meshHeader.numTriangles;
		// Allocate memory for the vertices, texture coordinates and face data.
		currentMesh.pVerts    = new Vector3f[currentMesh.numOfVerts * meshHeader.numMeshFrames];
		currentMesh.pTexVerts = new Vector2f[currentMesh.numOfVerts];
		currentMesh.pFaces    = new Face[currentMesh.numOfFaces];
		// Go through all of the vertices and assign them over to our structure
		for(i = 0; i < currentMesh.numOfVerts * meshHeader.numMeshFrames; i++)
		{
			currentMesh.pVerts[i] = new Vector3f();
			currentMesh.pVerts[i].x =  m_pVertices[i].vertex[0] / 64.0f;
			currentMesh.pVerts[i].y =  m_pVertices[i].vertex[1] / 64.0f;
			currentMesh.pVerts[i].z =  m_pVertices[i].vertex[2] / 64.0f;
		}
		// Go through all of the uv coords and assign them over to our structure
		for(i = 0; i < currentMesh.numTexVertex; i++)
		{
			currentMesh.pTexVerts[i] = new Vector2f();
			currentMesh.pTexVerts[i].x =  m_pTexCoords[i].textureCoord[0];
			currentMesh.pTexVerts[i].y = -m_pTexCoords[i].textureCoord[1];
		}
		// Go through all of the face data and assign it over to OUR structure
		for(i = 0; i < currentMesh.numOfFaces; i++)
		{
			// Assign the vertex indices to our face data
			currentMesh.pFaces[i] = new Face();
			currentMesh.pFaces[i].vertIndex[0] = m_pTriangles[i].vertexIndices[0];
			currentMesh.pFaces[i].vertIndex[1] = m_pTriangles[i].vertexIndices[1];
			currentMesh.pFaces[i].vertIndex[2] = m_pTriangles[i].vertexIndices[2];
			// Assign the texture coord indices to our face data (same as the vertex indices)
			currentMesh.pFaces[i].coordIndex[0] = m_pTriangles[i].vertexIndices[0];
			currentMesh.pFaces[i].coordIndex[1] = m_pTriangles[i].vertexIndices[1];
			currentMesh.pFaces[i].coordIndex[2] = m_pTriangles[i].vertexIndices[2];
		}
		// Here we add the current object to our list object list
		pModel.pObject.addElement(currentMesh);
	}

	/** This loads the texture information for the model from the *.skin file.
	 *  @param pModel Current model.
	 *  @param strSkin Skin path.
	 */
	private final boolean loadSkin(Model3D pModel, String strSkin)
	{
		try
		{
			// Wrap a buffer to make reading more efficient (faster)
			BufferedReader reader = new BufferedReader(new FileReader(strSkin));
			// These 2 variables are for reading in each line from the file, then storing
			// the index of where the bitmap name starts after the last '/' character.
			String strLine;
			int textureNameStart = 0;
			// Go through every line in the .skin file
			while((strLine = reader.readLine()) != null)
			{
				// Loop through all of our objects to test if their name is in this line
				for(int i = 0; i < pModel.numOfObjects; i++)
				{
					// Check if the name of this object appears in this line from the skin file
					if(IsInString(strLine, ((Object3D)pModel.pObject.elementAt(i)).strName))			
					{			
						  // To extract the texture name, we loop through the string, starting
						  // at the end of it until we find a '/' character, then save that index + 1.
						textureNameStart = strLine.lastIndexOf("/") + 1;

						  // Create a local material info structure
						MaterialInfo texture = new MaterialInfo();

						  // Copy the name of the file into our texture file name variable.
						texture.strFile = strLine.substring(textureNameStart);
						
						  // The tile or scale for the UV's is 1 to 1 
						texture.uTile = texture.uTile = 1;

						  // Store the material ID for this object and set the texture boolean to true
						((Object3D)pModel.pObject.elementAt(i)).materialID = pModel.numOfMaterials;
						((Object3D)pModel.pObject.elementAt(i)).bHasTexture = true;

						  // Here we increase the number of materials for the model
						pModel.numOfMaterials++;

						  // Add the local material info structure to our model's material list
						pModel.pMaterials.addElement(texture);
					}
				}
			}
			// Close the file and return a success
			reader.close();
		}
		catch(Exception e)
		{
			return false;
		}
		return true;
	}

	/** This loads the basic shader texture info associated with the weapon model.
	 *  @param pModel Current model.
	 *  @param strShader Shader path.
	 */
	private final boolean loadShader(Model3D pModel, String strShader)
	{
		try
		{
			  // Wrap a buffer to make reading more efficient (faster)
			BufferedReader reader = new BufferedReader(new FileReader(strShader));

			  // These variables are used to read in a line at a time from the file, and also
			  // to store the current line being read so that we can use that as an index for the 
			  // textures, in relation to the index of the sub-object loaded in from the weapon model.
			String strLine;
			int currentIndex = 0;
			
			  // Go through and read in every line of text from the file
			while((strLine = reader.readLine()) != null)
			{
				  // Create a local material info structure
				MaterialInfo texture = new MaterialInfo();

				  // Copy the name of the file into our texture file name variable
				texture.strFile = strLine;
						
				  // The tile or scale for the UV's is 1 to 1 
				texture.uTile = texture.uTile = 1;

				  // Store the material ID for this object and set the texture boolean to true
				((Object3D)pModel.pObject.elementAt(currentIndex)).materialID = pModel.numOfMaterials;
				((Object3D)pModel.pObject.elementAt(currentIndex)).bHasTexture = true;

				  // Here we increase the number of materials for the model
				pModel.numOfMaterials++;

				  // Add the local material info structure to our model's material list
				pModel.pMaterials.addElement(texture);

				  // Here we increase the material index for the next texture (if any)
				currentIndex++;
			}

			  // Close the file and return a success
			reader.close();
		}
		catch(Exception e)
		{
			return false;
		}

		return true;
	}

	/** Create a texture.
	 *  @param textureArray The texture array.
	 *  @param strFileName The texture path.
	 *  @param textureID Texture ID.
	 */
	private final void createTexture(int textureArray[], String strFileName, int textureID)
	{
		textureArray[textureID] = loadTexture(strFileName);
	}

	/** Load a texture.
	 *  @param path Texture's path.
	 *  @return The texture ID in OpenGL memory.
	 */
	public int loadTexture(String path)
	{
		Image image = (new javax.swing.ImageIcon(path)).getImage();
		
		  // Extract The Image
		BufferedImage tex = new BufferedImage(image.getWidth(null), image.getHeight(null), BufferedImage.TYPE_3BYTE_BGR);
		Graphics2D g = (Graphics2D)tex.getGraphics();
		g.drawImage(image, null, null);
		g.dispose();

		  // We flip the image to have a "normal" coordinate system (top-left) instead of
		  // OpenGL one (which is bottom-left) for texture coordinates in the rendering method.
		  // It makes things easier.

		AffineTransform tx = AffineTransform.getScaleInstance(1, -1);
		tx.translate(0, -image.getHeight(null));
		AffineTransformOp op = new AffineTransformOp(tx, AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
		tex = op.filter(tex, null);

		  // Put Image In Memory
		byte data[] = (byte[])tex.getRaster().getDataElements(0, 0, tex.getWidth(), tex.getHeight(), null);
		ByteBuffer buffer2 = BufferUtils.createByteBuffer(data.length);
		buffer2.put(data);
		buffer2.rewind();
		IntBuffer buffer = BufferUtils.createIntBuffer(1);
		GL11.glGenTextures(buffer);
		GL11.glBindTexture(  GL11.GL_TEXTURE_2D, buffer.get(0));

		// Linear Filtering
		GL11.glTexParameteri(  GL11.GL_TEXTURE_2D,   GL11.GL_TEXTURE_MIN_FILTER,   GL11.GL_LINEAR);
		GL11.glTexParameteri(  GL11.GL_TEXTURE_2D,   GL11.GL_TEXTURE_MAG_FILTER,   GL11.GL_LINEAR);

		// Generate The Texture
		GL11.glTexImage2D(  GL11.GL_TEXTURE_2D, 0,   GL11.GL_RGB, tex.getWidth(), tex.getHeight(), 0,   GL11.GL_RGB,   GL11.GL_UNSIGNED_BYTE, buffer2);
		buffer2.clear();
		return buffer.get(0);
	}

}