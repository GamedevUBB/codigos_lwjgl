package org.lwjglb.guis;

import org.joml.Matrix4f;
import org.lwjglb.shaders.ShaderProgram;
import org.lwjglb.util.Config;

public class GuiShader extends ShaderProgram {

    private static final String VERTEX_FILE = new Config().getPath() + "guis//" + "guiVertexShader.glsl";
    private static final String FRAGMENT_FILE = new Config().getPath() + "guis//" + "guiFragmentShader.glsl";

    private int location_transformationMatrix;

    public GuiShader() {
        super(VERTEX_FILE, FRAGMENT_FILE);
    }

    public void loadTransformation(Matrix4f matrix) {
        super.loadMatrix(location_transformationMatrix, matrix);
    }

    @Override
    protected void getAllUniformLocations() {
        location_transformationMatrix = super.getUniformLocation("transformationMatrix");
    }

    @Override
    protected void bindAttributes() {
        super.bindAttribute(0, "position");
    }
}