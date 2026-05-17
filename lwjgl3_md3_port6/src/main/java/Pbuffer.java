public class Pbuffer {

    private final int width;
    private final int height;

    public Pbuffer(int width, int height, PixelFormat format, Object a, Object b) {
        this.width = width;
        this.height = height;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public boolean isBufferLost() {
        return false;
    }

    public void destroy() {}

    public void makeCurrent() {}
}
