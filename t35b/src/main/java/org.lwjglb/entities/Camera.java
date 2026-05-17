package org.lwjglb.entities;

import org.joml.Vector3f;
import org.lwjglb.renderEngine.Keyboard;
import org.lwjglb.renderEngine.Mouse;

public class Camera {

    private float distanceFromPlayer = 10; //50;
    private float angleAroundPlayer = 0;

    private Vector3f position = new Vector3f(0,0,0);
    private float pitch = 25.0f; //20.0f;
    private float yaw = 0;
    private float roll;

    private Player player;

    public Camera(Player player) {
        this.player = player;
    }

    public void move() {
        calculateZoom();
        calculatePitch();
        calculateAnglePlayer();
        float horizontalDistance = calculateHorizontalDistance();
        float verticalDistance = calculateVerticalDistance();
        calculateCameraPosition(horizontalDistance, verticalDistance);
        this.yaw = 180 - (player.getRotY() + angleAroundPlayer);
    }

    public void invertPitch() {
        this.pitch = -pitch;
    }

    public Vector3f getPosition(){
        return position;
    }

    public void setPositionY(float posY){
        position.y = posY;
    }

    public float getPitch(){
        return pitch;
    }

    public float getYaw(){
        return yaw;
    }

    public float getRoll(){
        return roll;
    }


    private void calculateCameraPosition(float horizDistance, float verticDistance) {
        float theta = player.getRotY() + angleAroundPlayer;
        float offsetX = (float) (horizDistance * Math.sin(Math.toRadians(theta)));
        float offsetZ = (float) (horizDistance * Math.cos(Math.toRadians(theta)));
        position.x = player.getPosition().x - offsetX;
        position.z = player.getPosition().z - offsetZ;
        position.y = player.getPosition().y + verticDistance;
    }

    /*
    private float calculateHorizontalDistance() {
        return (float) (distanceFromPlayer * Math.cos(Math.toRadians(pitch)));
    }

    private float calculateVerticalDistance() {
        return (float) (distanceFromPlayer * Math.sin(Math.toRadians(pitch)));
    }
    */

    //Con algunas correcciones de la cámara: para evitar que la cámara pase por encima del jugador o por debajo del suelo
    private float calculateHorizontalDistance() {
        float hD = (float) (distanceFromPlayer * Math.cos(Math.toRadians(pitch)));
        if(hD < 2)
            hD = 2;
        return hD;
    }

    //Con algunas correcciones de la cámara: para evitar que la cámara pase por encima del jugador o por debajo del suelo
    private float calculateVerticalDistance() {
        float vD = (float) (distanceFromPlayer * Math.sin(Math.toRadians(pitch)));
        if(vD < 4)
            vD = 4;
        return vD;
    }

    private void calculateZoom() {
        float zoomLevel = Mouse.getDWheel() * 0.5f;
        distanceFromPlayer -= zoomLevel;
    }

    //Para evitar que el pitch mire en dirección opuesta a tu jugador cuando hD o vD = 0
    private void calculatePitch() {
        if (Mouse.isLeftButtonPressed()) {
            float pitchChange = Mouse.getDY() * 0.1f;
            pitch -= pitchChange;
        }
    }
   /*
    private void calculatePitch(){
        float pitchChange = Mouse.getDY() * 0.1f;
        pitch -= pitchChange;
        if(pitch < 0)
            pitch = 0;
        else
            if(pitch > 90)
                pitch = 90;
    }
    */

    private void calculateAnglePlayer() {
        if (Mouse.isLeftButtonPressed()) {
            float angleChange = Mouse.getDX() * 0.1f;
            angleAroundPlayer -= angleChange;
        }
    }

}
