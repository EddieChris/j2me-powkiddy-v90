
package com.nokia.mid.ui;


import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import org.thenesis.midpath.ui.toolkit.virtual.VirtualGraphics;
import org.thenesis.midpath.ui.toolkit.virtual.VirtualSurface;


/**
 * 
 * @author liang.wu
 *
 */
public class DirectGraphicsImpl
    implements DirectGraphics
{

    Graphics graphics;
    
    final static int MIDP2Manip2NokiaManip[][] = 
    { 
        { 0,                        FLIP_VERTICAL|FLIP_HORIZONTAL|ROTATE_180 },         //Sprite.TRANS_NONE
        { FLIP_VERTICAL,            FLIP_HORIZONTAL|ROTATE_180 },                       //Sprite.TRANS_MIRROR_ROT180
        { FLIP_HORIZONTAL,          FLIP_VERTICAL|ROTATE_180 },                         //Sprite.TRANS_MIRROR
        { ROTATE_180,               FLIP_VERTICAL|FLIP_HORIZONTAL },                    //Sprite.TRANS_ROT180
        { FLIP_VERTICAL|ROTATE_90,  FLIP_HORIZONTAL|ROTATE_270 },                       //Sprite.TRANS_MIRROR_ROT270
        { ROTATE_90,                FLIP_VERTICAL|FLIP_HORIZONTAL|ROTATE_270 },         //Sprite.TRANS_ROT90
        { ROTATE_270,               FLIP_VERTICAL|FLIP_HORIZONTAL|ROTATE_90 },          //Sprite.TRANS_ROT270
        { FLIP_HORIZONTAL|ROTATE_90,FLIP_VERTICAL|ROTATE_270 }                          //Sprite.TRANS_MIRROR_ROT90
    };

    public DirectGraphicsImpl(Graphics g)
    {
        graphics = g;
    }

    /**
     * 
     * @param manip flip or rotate value or a combination of values
     * @return
     */
    public int nokiaManip2MIDP2Manip(int manip)
    {
        for(int i = 0; i < MIDP2Manip2NokiaManip.length; i++)
        {
            for(int j = 0; j < MIDP2Manip2NokiaManip[i].length; j++)
                if(MIDP2Manip2NokiaManip[i][j] == manip)
                    return i;

        }
        return 0;
    }

    public void drawImage(Image image, int x, int y, int anchor, int manipulation)
    {
        int width = image.getWidth();
        int height = image.getHeight();
        int transform = nokiaManip2MIDP2Manip(manipulation);
        graphics.drawRegion(image, 0, 0, width, height, transform, x, y, anchor);
    }

    public void drawPixels(short pixels[], boolean transparency, int offset, int scanlength, int x, int y, int width, int height, int manipulation, int format)
    {
        if (pixels == null) throw new NullPointerException();
        if (width < 0 || height < 0) throw new IllegalArgumentException();
        if (width == 0 || height == 0) return;
        int[] argb = new int[width * height];
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                int p = pixels[offset + row * scanlength + col] & 0xffff;
                int r, g, b, a = 255;
                switch (format) {
                case TYPE_USHORT_565_RGB:
                    r = ((p >> 11) & 31) * 255 / 31; g = ((p >> 5) & 63) * 255 / 63; b = (p & 31) * 255 / 31; break;
                case TYPE_USHORT_555_RGB:
                case TYPE_USHORT_1555_ARGB:
                    r = ((p >> 10) & 31) * 255 / 31; g = ((p >> 5) & 31) * 255 / 31; b = (p & 31) * 255 / 31;
                    if (transparency && format == TYPE_USHORT_1555_ARGB) a = (p & 0x8000) == 0 ? 0 : 255; break;
                case TYPE_USHORT_444_RGB:
                case TYPE_USHORT_4444_ARGB:
                    r = ((p >> 8) & 15) * 17; g = ((p >> 4) & 15) * 17; b = (p & 15) * 17;
                    if (transparency && format == TYPE_USHORT_4444_ARGB) a = ((p >> 12) & 15) * 17; break;
                default:
                    System.out.println("[Nokia] drawPixels short formato nao suportado: " + format); return;
                }
                argb[row * width + col] = (a << 24) | (r << 16) | (g << 8) | b;
            }
        }
        Image image = Image.createRGBImage(argb, width, height, true);
        graphics.drawRegion(image, 0, 0, width, height,
            nokiaManip2MIDP2Manip(manipulation), x, y, Graphics.TOP | Graphics.LEFT);
    }
    
    public void drawPixels(byte pixels[], byte transparencyMask[], int offset, int scanlength, int x, int y, int width, int height, int manipulation, int format)
    {
        if (pixels == null) throw new NullPointerException();
        if (width < 0 || height < 0 || offset < 0 || scanlength <= 0)
            throw new IllegalArgumentException();
        if (width == 0 || height == 0) return;
        if (format != TYPE_BYTE_1_GRAY && format != TYPE_BYTE_1_GRAY_VERTICAL) {
            System.out.println("[Nokia] drawPixels byte formato nao suportado: " + format);
            return;
        }
        int[] argb = new int[width * height];
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                int index, bit;
                if (format == TYPE_BYTE_1_GRAY) {
                    int pos = offset + row * scanlength + col;
                    index = pos / 8;
                    bit = 7 - (pos % 8);
                } else {
                    int baseRow = offset / scanlength + row;
                    index = (baseRow / 8) * scanlength + (offset % scanlength) + col;
                    bit = baseRow % 8;
                }
                if (index < 0 || index >= pixels.length ||
                    (transparencyMask != null && index >= transparencyMask.length))
                    throw new ArrayIndexOutOfBoundsException();
                int gray = ((pixels[index] >> bit) & 1) == 0 ? 255 : 0;
                int alpha = transparencyMask == null || ((transparencyMask[index] >> bit) & 1) != 0 ? 0xff : 0;
                argb[row * width + col] = (alpha << 24) | (gray << 16) | (gray << 8) | gray;
            }
        }
        Image image = Image.createRGBImage(argb, width, height, true);
        graphics.drawRegion(image, 0, 0, width, height,
            nokiaManip2MIDP2Manip(manipulation), x, y, Graphics.TOP | Graphics.LEFT);
    }
    
    public void drawPixels(int pixels[], boolean transparency, int offset, int scanlength, int x, int y, int width, int height, int manipulation, int format)
    {
        if (pixels == null) throw new NullPointerException();
        if (width < 0 || height < 0) throw new IllegalArgumentException();
        if (width == 0 || height == 0) return;
        if (format != TYPE_INT_888_RGB && format != TYPE_INT_8888_ARGB) {
            System.out.println("[Nokia] drawPixels int formato nao suportado: " + format); return;
        }
        int[] argb = new int[width * height];
        for (int row = 0; row < height; row++) for (int col = 0; col < width; col++) {
            int p = pixels[offset + row * scanlength + col];
            argb[row * width + col] = transparency && format == TYPE_INT_8888_ARGB ? p : p | 0xff000000;
        }
        Image image = Image.createRGBImage(argb, width, height, true);
        graphics.drawRegion(image, 0, 0, width, height,
            nokiaManip2MIDP2Manip(manipulation), x, y, Graphics.TOP | Graphics.LEFT);
    }

    public void drawPolygon(int xPoints[], int xOffset, int yPoints[], int yOffset, int nPoints, int argbColor)
    {
        int color = graphics.getColor();
        int strokestyle = graphics.getStrokeStyle();
        graphics.setColor(argbColor);
        graphics.setStrokeStyle(Graphics.SOLID);

        for(int i = 0; i < nPoints-1; i++)
        {
            graphics.drawLine(xPoints[xOffset+i], yPoints[yOffset+i],
                              xPoints[xOffset+i+1], yPoints[yOffset+i+1]);
        }

        graphics.drawLine(xPoints[xOffset+nPoints-1],
                          yPoints[yOffset+nPoints-1],
                          xPoints[xOffset],
                          yPoints[yOffset]);

        graphics.setColor(color);
        graphics.setStrokeStyle(strokestyle);
    }

    public void drawTriangle(int x1, int y1, int x2, int y2, int x3, int y3, int argbColor)
    {
        drawPolygon(new int[] { x1, x2, x3 }, 0, new int[] { y1, y2, y3 }, 0, 3, argbColor);
    }

    public void fillPolygon(int xPoints[], int xOffset, int yPoints[], int yOffset, int nPoints, int argbColor)
    {
        int color = graphics.getColor();
        graphics.setColor(argbColor);
        
        for(int i = 1; i < nPoints - 1; i++)
        {
            graphics.fillTriangle(xPoints[xOffset],
                                  yPoints[yOffset],
                                  xPoints[xOffset + i],
                                  yPoints[yOffset + i],
                                  xPoints[xOffset + i + 1],
                                  yPoints[yOffset + i + 1]);
        }
        
        graphics.setColor(color);
    }

    public void fillTriangle(int x1, int y1, int x2, int y2, int x3, int y3, int argbColor)
    {
        int color = graphics.getColor();
        graphics.setColor(argbColor);
        graphics.fillTriangle(x1, y1, x2, y2, x3, y3);
        graphics.setColor(color);
    }

    public int getAlphaComponent()
    {
        return (graphics.getColor() >> 24) & 0xFF;
    }

    public int getNativePixelFormat()
    {
        return TYPE_INT_8888_ARGB;
    }

    public void getPixels(int pixels[], int offset, int scanlength, int x, int y, int width, int height, int format)
    {
        //System.out.println("getPixels(int) not implemented");
    }

    public void getPixels(short pixels[], int offset, int scanlength, int x, int y, int width, int height, int format)
    {
        // Games such as Block Breaker use 4444 to capture the screen as a background.
        // Preserve the previous behavior for other formats, which remain unsupported.
        if (format != TYPE_USHORT_4444_ARGB) return;
        if (pixels == null) throw new NullPointerException();
        if (width < 0 || height < 0) throw new IllegalArgumentException();
        if (!(graphics instanceof VirtualGraphics)) throw new IllegalStateException("getPixels requires virtual graphics");
        VirtualSurface surface = ((VirtualGraphics) graphics).getSurface();
        long sx = (long)x + graphics.getTranslateX();
        long sy = (long)y + graphics.getTranslateY();
        if (sx < 0 || sy < 0 || sx + width > surface.width || sy + height > surface.height)
            throw new IllegalArgumentException("getPixels region outside surface");
        if (width == 0 || height == 0) return;
        long last = (long)offset + (long)(height - 1) * scanlength;
        if (Math.min((long)offset, last) < 0 || Math.max((long)offset, last) + width > pixels.length)
            throw new ArrayIndexOutOfBoundsException();
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                int argb = surface.data[((int)sy + row) * surface.width + (int)sx + col];
                pixels[offset + row * scanlength + col] = (short)(((argb >>> 16) & 0xf000)
                    | ((argb >>> 12) & 0x0f00) | ((argb >>> 8) & 0x00f0) | ((argb >>> 4) & 0x000f));
            }
        }
    }

    public void getPixels(byte[] pixels, byte[] transparencyMask, int offset, int scanlength, int x, int y, int width, int height, int format)
    {
        //System.out.println("getPixels(byte) not implemented");
    }

    public void setARGBColor(int argbColor)
    {
    	graphics.setColor(argbColor);
    }
}
