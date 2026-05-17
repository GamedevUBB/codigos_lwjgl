import java.nio.*;
import java.io.*;
import java.awt.*;
import java.awt.image.*;
import java.net.URL;

/**
 * Carga una imagen desde un archivo, almacenando los pixeles 
 * en un arreglo de enteros ARGB y el Buffer de Bytes RGBA 
 * Un constructor alternativo crea una GLImagen desde un ByteBuffer que contiene los datos del pixel
 * Contiene funciones estaticas para cargar, mostrar y convertir arreglos de pixeles.
 **/

public class GLImagen {
    public static final int SIZE_BYTE = 1;
    int alto = 0;
    int ancho = 0;
    ByteBuffer pixelBuffer = null;   // para almacenar bytes en formato GL_RGBA
    int[] pixeles = null;
    Image imagen = null;

    public GLImagen() {
    }

    /* Carga los pixeles desde un archivo de una imagen.*/
    public GLImagen(String nombreImg)
    {
        cargaImagen(nombreImg);
    }

    /* Almacena los pixeles pasados en un ByteBuffer */
    public GLImagen(ByteBuffer pixeles, int ancho, int alto) {
        this.pixelBuffer = pixeles;
        this.pixeles = null;
        this.imagen = null;  // la imagen no se carga desde el archivo
        this.alto = alto;
        this.ancho = ancho;
    }

    /* return true si la imagen se ha cargado con �xito */
    public boolean esCargada()
    {
        return (imagen != null);
    }

    /* Mueve los pixeles de la imagen verticalmente.*/
    public void moverPixeles()
    {
        pixeles = GLImagen.moverPixeles(pixeles, ancho, alto);
    }

    /* Carga una imagen desde un archivo manteniendo el alto y el ancho*/
    public void cargaImagen(String nombreImg) {
        Image tmpi = cargaImagenDesdeArchivo(nombreImg);
        if (tmpi != null) {
            ancho = tmpi.getWidth(null);
            alto = tmpi.getHeight(null);
            imagen = tmpi;
            pixeles = getPixelesImagen();  // pixeles en el formato de Java ARGB por defecto
            pixelBuffer = conviertePixelesImagen(pixeles,ancho,alto,true);  // convierte a RGBA bytes
            System.out.println("GLImagen: cargada " + nombreImg + ", ancho=" + ancho + " alto=" + alto);
        }
        else {
            System.out.println("GLImage: ERROR AL CARGAR LA IMAGEN" + nombreImg);
            imagen = null;
            pixeles = null;
            pixelBuffer = null;
            alto = ancho = 0;
        }
    }

    /*Devuelve los pixeles de la imagen en el formato de Java ARGB por defecto.*/
    public int[] getPixelesImagen()
    {
        if (pixeles == null && imagen != null) {
            pixeles = new int[ancho * alto];
            PixelGrabber pg = new PixelGrabber(imagen, 0, 0, ancho, alto, pixeles, 0, ancho);
            try {
                pg.grabPixels();
            }
            catch (Exception e) {
                System.out.println("Pixel Grabbing interrumpido!");
                return null;
            }
        }
        return pixeles;
    }

    /**
     * Retorna un arreglo de enteros que contiene los pixeles en formato de ARGB.
     */
    public int[] getPixelesARGB()
    {
        return pixeles;
    }

    /**
     * Retorna un ByteBuffer que contiene los pixeles en formato de ARGB.
     */
    public ByteBuffer getPixelesRGBA()
    {
        return pixelBuffer;
    }

    //========================================================================
    // 
    // Funciones de uso general para preparar los pixeles para el uso en OpenGL
    //
    //========================================================================

    /* Mueve los pixeles de la imagen verticalmente,segun los parametros*/
    public static int[] moverPixeles(int[] PixelesImg, int imgAncho, int imgAlto)
    {
        int[] pixelesMovidos = null;
        if (PixelesImg != null) {
            pixelesMovidos = new int[imgAncho * imgAlto];
            for (int y = 0; y < imgAlto; y++) {
                for (int x = 0; x < imgAncho; x++) {
                    pixelesMovidos[ ( (imgAlto - y - 1) * imgAncho) + x] = PixelesImg[ (y * imgAncho) + x];
                }
            }
        }
        return pixelesMovidos;
    }

    /**
     * Convierte pixeles del formato ARGB al formato RGBA devolviendolos en un ByteBuffer
     * para poder ser ser dibujado en el modo ORTHO usando
     *         GL.glDrawPixels(imgW, imgH, GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, byteBuffer); 
     * Si movidoVertical es verdadero, 
     * los pixeles seran movidos verticalmente (para el sistema de cordenadas de OpenGL).
     */
    public static ByteBuffer conviertePixelesImagen(int[] jpixeles, int imgAncho, int imgAlto, boolean movidoVertical) {
        byte[] bytes;     // mantiene los pixeles como RGBA bytes
        if (movidoVertical) {
            jpixeles = moverPixeles(jpixeles, imgAncho, imgAlto); // mueve en el eje Y
        }
        bytes = convierteARGB_a_RGBA(jpixeles);
        return asignaBytes(bytes);  // convierte a ByteBuffer y luego retorna
    }

    /*Convierte pixeles del formato ARGB al formato RGBA devolviendolos en un arreglo de bytes. */
    public static byte[] convierteARGB_a_RGBA(int[] jpixeles)
    {
        byte[] bytes = new byte[jpixeles.length*4];  // mantiene los pixeles como RGBA bytes
        int p, r, g, b, a;
        int j=0;
        for (int i = 0; i < jpixeles.length; i++) {
            int outPixel = 0x00000000; // AARRGGBB
            p = jpixeles[i];
            a = (p >> 24) & 0xFF;  // obtiene los bytes del pixel en orden de ARGB
            r = (p >> 16) & 0xFF;
            g = (p >> 8) & 0xFF;
            b = (p >> 0) & 0xFF;
            bytes[j+0] = (byte)r;  // completa los bytes en orden de RGBA 
            bytes[j+1] = (byte)g;
            bytes[j+2] = (byte)b;
            bytes[j+3] = (byte)a;
            j += 4;
        }
        return bytes;
    }


    //========================================================================
    //Funciones de uso general para cargar el archivo en el arreglo de bytes 
    //y crear imagenes desde bytes
    //========================================================================

    /**
     * La misma funcion en GLApp.java Asigna a un ByteBuffer 
     * lo que est� en una arreglo de bytes
	 * retornando el contenido en un ByteBuffer
     */
    public static ByteBuffer asignaBytes(byte[] bytearray) {
        ByteBuffer bb = ByteBuffer.allocateDirect(bytearray.length * SIZE_BYTE).order(ByteOrder.nativeOrder());
        bb.put(bytearray).flip();
        return bb;
    }

    /**
     * Carga una imagen desde un archivo, no necesita esperar en un hilo
     * Si no puede encontrar el archivo en el filesystem, intentara cargarlo del archivo .jar.
     * Si no es encontrado retorna null.
     */
    public static Image cargaImagenDesdeArchivo(String nombreImg) {
        byte[] imagenBytes = getBytesDesdeArchivo(nombreImg);
        Image tmpi = null;
        int intentos = 20;
        if (imagenBytes == null) {
            // no pudo leer la imagen desde el archivo, 
            //intentara ahora desde el archivo .JAR 
            URL url = GLImagen.class.getResource(nombreImg);
            if (url != null) {
                // imagen encontrada en el JAR: comiena a cargarla
                tmpi = Toolkit.getDefaultToolkit().createImage(url);
                // Espera dos segundos para cargar imagen
                int espera = 200;
                while (tmpi.getHeight(null) < 0 && espera > 0) {
                    try {
                        Thread.sleep(10);
                    }
                    catch (Exception e) {}
                }
            }
        }
        else {
            tmpi = Toolkit.getDefaultToolkit().createImage(imagenBytes, 0, imagenBytes.length);
            while (tmpi.getWidth(null) < 0 && intentos-- > 0) {
                try { Thread.sleep(100); }
                catch( InterruptedException e ) {System.out.println(e);}
            }
            while (tmpi.getHeight(null) < 0 && intentos-- > 0) {
                try { Thread.sleep(100); }
                catch( InterruptedException e ) {System.out.println(e);}
            }
        }
        return tmpi;
    }

    public static Image cargaImagenDesdeArchivo_ORIG(String nombreImg) {
        byte[] imagenBytes = getBytesDesdeArchivo(nombreImg);
        Image tmpi = null;
        int intentos = 20;
        if (imagenBytes != null) {
            tmpi = Toolkit.getDefaultToolkit().createImage(imagenBytes, 0, imagenBytes.length);
            while (tmpi.getWidth(null) < 0 && intentos-- > 0) {
                try { Thread.sleep(100); }
                catch( InterruptedException e ) {System.out.println(e);}
            }
            while (tmpi.getHeight(null) < 0 && intentos-- > 0) {
                try { Thread.sleep(100); }
                catch( InterruptedException e ) {System.out.println(e);}
            }
        }
        return tmpi;
    }

    /*retorna un arreglo de bytes segun el nombre de archivo que es pasado como parametro */
    public static byte[] getBytesDesdeArchivo(String filename)
    {
        File f = new File(filename);
        byte[] bytes = null;
        if (f.exists()) {
            try {
                bytes = getBytesDesdeArchivo(f);
            }
            catch (Exception e) {
                System.out.println("getBytesDesdeArchivo() exception: " + e);
            }
        }
        return bytes;
    }


    /*retorna un arreglo de bytes segun el Objeto File que es pasado como parametro */
    public static byte[] getBytesDesdeArchivo(File file) throws IOException {
        byte[] bytes = null;
        if (file != null) {
            InputStream is = new FileInputStream(file);
            long largo = file.length();
            //No puede crear un arreglo usando un tipo long. 
            //Antes de convertir a un tipo int, 
            //cheque para asegurarse de que el archivo no es m�s grande 
            //usando Integer.MAX_VALUE.

            if (largo > Integer.MAX_VALUE) {
                System.out.println("getBytesDesdeArchivo() error: File " + file.getName()+ " es demasiado largo");
            }
            else {
                // crea el arreglo de bytes para traspasar los datos
                bytes = new byte[ (int) largo];
                int offset = 0;
                int lecturas = 0;
                // Leyendo los bytes
                while (offset < bytes.length
                       && (lecturas = is.read(bytes, offset, bytes.length - offset)) >= 0) {
                    offset += lecturas;
                }
                // Asegura que todos los bytes hayan sido leidos
                if (offset < bytes.length) {
                    throw new IOException("getBytesDesdeArchivo() error: No se pudo leer completamente el archivo " + file.getName());
                }
            }
            // Cerrar el flujo de entradas y retorna los bytes.
            is.close();
        }
        return bytes;
    }

}