public class DisplayMode {
    private final int width;
    private final int height;
    private final int bitsPerPixel;
    private final int frequency;

    public DisplayMode(int width, int height, int bitsPerPixel, int frequency) {
        this.width = width;
        this.height = height;
        this.bitsPerPixel = bitsPerPixel;
        this.frequency = frequency;
    }

    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getBitsPerPixel() { return bitsPerPixel; }
    public int getFrequency() { return frequency; }

    @Override
    public String toString() {
        return width + "x" + height + "x" + bitsPerPixel + " @" + frequency + "Hz";
    }
}
