import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

public final class GLU {
    private GLU() {}

    public static void gluPerspective(float fovy, float aspect, float zNear, float zFar) {
        float ymax = (float)(zNear * Math.tan(Math.toRadians(fovy) / 2.0));
        float xmax = ymax * aspect;
        GL11.glFrustum(-xmax, xmax, -ymax, ymax, zNear, zFar);
    }

    public static void gluLookAt(float eyeX, float eyeY, float eyeZ,
                                 float centerX, float centerY, float centerZ,
                                 float upX, float upY, float upZ) {
        FloatBuffer fb = org.lwjgl.BufferUtils.createFloatBuffer(16);
        new Matrix4f().lookAt(new Vector3f(eyeX, eyeY, eyeZ),
                              new Vector3f(centerX, centerY, centerZ),
                              new Vector3f(upX, upY, upZ)).get(fb);
        GL11.glMultMatrixf(fb);
    }

    public static void gluOrtho2D(float left, float right, float bottom, float top) {
        GL11.glOrtho(left, right, bottom, top, -1.0, 1.0);
    }

    public static int gluBuild2DMipmaps(int target, int components, int width, int height,
                                        int format, int type, java.nio.ByteBuffer data) {
        GL11.glTexImage2D(target, 0, components == 4 ? GL11.GL_RGBA : GL11.GL_RGB,
                width, height, 0, format, type, data);
        GL30.glGenerateMipmap(target);
        return 0;
    }

    public static boolean gluProject(float objX, float objY, float objZ,
                                     FloatBuffer model, FloatBuffer proj, IntBuffer view, FloatBuffer result) {
        float[] m = new float[16];
        float[] p = new float[16];
        int[] v = new int[4];
        for (int i=0;i<16;i++) {
            m[i]=model.get(i);
            p[i]=proj.get(i);
        }
        for (int i=0;i<4;i++)
            v[i]=view.get(i);
        Vector4f in = new Vector4f(objX,objY,objZ,1f);
        new Matrix4f().set(m).mul(new Matrix4f().set(p));
        Vector4f out = new Matrix4f().set(p).mul(new Matrix4f().set(m)).transform(in);
        if (out.w == 0f)
            return false;
        out.div(out.w);
        result.put(0, v[0] + (1 + out.x) * v[2] / 2);
        result.put(1, v[1] + (1 + out.y) * v[3] / 2);
        result.put(2, (1 + out.z) / 2);
        return true;
    }

    public static boolean gluUnProject(float winX, float winY, float winZ,
                                       FloatBuffer model, FloatBuffer proj, IntBuffer view, FloatBuffer result) {
        float[] m = new float[16];
        float[] p = new float[16];
        int[] v = new int[4];
        for (int i=0;i<16;i++) {
            m[i]=model.get(i);
            p[i]=proj.get(i);
        }
        for (int i=0;i<4;i++)
            v[i]=view.get(i);
        Matrix4f inv = new Matrix4f().set(p).mul(new Matrix4f().set(m)).invert();
        Vector4f in = new Vector4f(
                (winX - v[0]) / v[2] * 2f - 1f,
                (winY - v[1]) / v[3] * 2f - 1f,
                2f * winZ - 1f,
                1f);
        Vector4f out = inv.transform(in);
        if (out.w == 0f)
            return false;
        out.div(out.w);
        result.put(0, out.x);
        result.put(1, out.y);
        result.put(2, out.z);
        return true;
    }
}
