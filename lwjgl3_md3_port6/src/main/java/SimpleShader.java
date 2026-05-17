import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL20;

import java.nio.FloatBuffer;

public class SimpleShader {
    private final int programID;
    private final int vertexShaderID;
    private final int fragmentShaderID;

    private final int locationProjectionMatrix;
    private final int locationViewMatrix;
    private final int locationModelMatrix;
    private final int locationTextureSampler;

    private final FloatBuffer matrixBuffer = BufferUtils.createFloatBuffer(16);

    private static final String VERTEX_SHADER =
            "#version 330 core\n" +
            "layout(location = 0) in vec3 position;\n" +
            "layout(location = 1) in vec2 textureCoord;\n" +
            "uniform mat4 projectionMatrix;\n" +
            "uniform mat4 viewMatrix;\n" +
            "uniform mat4 modelMatrix;\n" +
            "out vec2 pass_texCoord;\n" +
            "void main(void) {\n" +
            "    pass_texCoord = textureCoord;\n" +
            "    gl_Position = projectionMatrix * viewMatrix * modelMatrix * vec4(position, 1.0);\n" +
            "}\n";

    private static final String FRAGMENT_SHADER =
            "#version 330 core\n" +
            "in vec2 pass_texCoord;\n" +
            "out vec4 out_Color;\n" +
            "uniform sampler2D textureSampler;\n" +
            "void main(void) {\n" +
            "    out_Color = texture(textureSampler, pass_texCoord);\n" +
            "}\n";

    public SimpleShader() {
        vertexShaderID = compileShader(VERTEX_SHADER, GL20.GL_VERTEX_SHADER);
        fragmentShaderID = compileShader(FRAGMENT_SHADER, GL20.GL_FRAGMENT_SHADER);

        programID = GL20.glCreateProgram();
        GL20.glAttachShader(programID, vertexShaderID);
        GL20.glAttachShader(programID, fragmentShaderID);
        GL20.glLinkProgram(programID);

        if (GL20.glGetProgrami(programID, GL20.GL_LINK_STATUS) == 0) {
            throw new RuntimeException("No se pudo enlazar shader:\n" + GL20.glGetProgramInfoLog(programID));
        }

        locationProjectionMatrix = GL20.glGetUniformLocation(programID, "projectionMatrix");
        locationViewMatrix = GL20.glGetUniformLocation(programID, "viewMatrix");
        locationModelMatrix = GL20.glGetUniformLocation(programID, "modelMatrix");
        locationTextureSampler = GL20.glGetUniformLocation(programID, "textureSampler");
    }

    private int compileShader(String source, int type) {
        int shaderID = GL20.glCreateShader(type);
        GL20.glShaderSource(shaderID, source);
        GL20.glCompileShader(shaderID);

        if (GL20.glGetShaderi(shaderID, GL20.GL_COMPILE_STATUS) == 0) {
            throw new RuntimeException("No se pudo compilar shader:\n" + GL20.glGetShaderInfoLog(shaderID));
        }
        return shaderID;
    }

    public void start() {
        GL20.glUseProgram(programID);
        GL20.glUniform1i(locationTextureSampler, 0);
    }

    public void stop() {
        GL20.glUseProgram(0);
    }

    public void loadProjectionMatrix(Matrix4f matrix) {
        loadMatrix(locationProjectionMatrix, matrix);
    }

    public void loadViewMatrix(Matrix4f matrix) {
        loadMatrix(locationViewMatrix, matrix);
    }

    public void loadModelMatrix(Matrix4f matrix) {
        loadMatrix(locationModelMatrix, matrix);
    }

    private void loadMatrix(int location, Matrix4f matrix) {
        matrixBuffer.clear();
        matrix.get(matrixBuffer);
        GL20.glUniformMatrix4fv(location, false, matrixBuffer);
    }

    public void cleanUp() {
        stop();
        GL20.glDetachShader(programID, vertexShaderID);
        GL20.glDetachShader(programID, fragmentShaderID);
        GL20.glDeleteShader(vertexShaderID);
        GL20.glDeleteShader(fragmentShaderID);
        GL20.glDeleteProgram(programID);
    }
}
