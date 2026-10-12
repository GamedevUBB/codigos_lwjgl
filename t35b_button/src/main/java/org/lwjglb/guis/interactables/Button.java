package org.lwjglb.guis.interactables;

import org.joml.Vector2f;
import org.lwjglb.guis.GuiRenderer;
import org.lwjglb.guis.GuiTexture;
import org.lwjglb.renderEngine.DisplayManager;
import org.lwjglb.renderEngine.Mouse;

import java.util.*;

public class Button {
    private GuiRenderer renderer; // Referencia al renderer de GUIs
    private GuiTexture texture; // Referencia a la textura del botón

    // Delimitadores del vector
    private Vector2f topLeft;
    private Vector2f bottomRight;

    // Referencia al callback. Queda como público en caso de que se
    // requiera extraer algún dato post-evento
    public ButtonCallback callback;

    public Button(
            GuiRenderer renderer,
            GuiTexture texture,
            Vector2f topLeft,
            Vector2f bottomRight,
            ButtonCallback callback
    ) {
        this.renderer = renderer;
        this.texture = texture;
        this.topLeft = topLeft;
        this.bottomRight = bottomRight;
        this.callback = callback;
    }

    // Esto de acá es una copia de como trabaja Unity y JS los botones
    public void Update() {
        Mouse.update();

        int windowHeight = DisplayManager.getWindowHeight();
        int windowWidth = DisplayManager.getWindowWidth();

        boolean isBetweenX =
                (Mouse.getX() / windowWidth <= bottomRight.x) &&
                        (Mouse.getX() / windowWidth >= topLeft.x);

        boolean isBetweenY =
                (Mouse.getY() / windowHeight <= topLeft.y) &&
                        (Mouse.getY() / windowHeight >= bottomRight.y);

        if (isBetweenX && isBetweenY && Mouse.isLeftButtonPressed()) {
            this.callback.OnClick();
        }

        // Renderizar
        List<GuiTexture> textures = new ArrayList<>();
        textures.add(this.texture);

        this.renderer.render(textures);
    }
}
