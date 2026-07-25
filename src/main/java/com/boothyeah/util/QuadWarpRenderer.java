package com.boothyeah.util;

import com.boothyeah.model.SlotRegion;

import java.awt.image.BufferedImage;

/**
 * Renders a photo into a quadrilateral slot using bilinear inverse warping.
 *
 * For each output pixel (px,py) inside the quad's bounding box:
 *  1. Check if (px,py) is inside the quad (cross-product sign test).
 *  2. Solve for bilinear parameters (s,t) via Newton-Raphson:
 *       P(s,t) = (1-s)(1-t)*TL + s(1-t)*TR + s*t*BR + (1-s)*t*BL
 *  3. Sample the photo at (s*photoW, t*photoH) and write to canvas.
 *
 * The photo is center-cropped to the quad's aspect ratio first.
 */
public final class QuadWarpRenderer {

    private QuadWarpRenderer() {}

    public static void drawWarpedPhoto(BufferedImage canvas, BufferedImage photo, SlotRegion slot) {
        double tlx = slot.getTlX(), tly = slot.getTlY();
        double trx = slot.getTrX(), try_ = slot.getTrY();
        double brx = slot.getBrX(), bry = slot.getBrY();
        double blx = slot.getBlX(), bly = slot.getBlY();

        // Estimate quad dimensions for center-crop aspect ratio
        double wTop   = Math.hypot(trx - tlx, try_ - tly);
        double wBot   = Math.hypot(brx - blx, bry  - bly);
        double hLeft  = Math.hypot(blx - tlx, bly  - tly);
        double hRight = Math.hypot(brx - trx, bry  - try_);
        double quadW  = (wTop + wBot)   / 2.0;
        double quadH  = (hLeft + hRight) / 2.0;
        if (quadW <= 0 || quadH <= 0) return;

        BufferedImage cropped = centerCrop(photo, quadW / quadH);
        int pw = cropped.getWidth();
        int ph = cropped.getHeight();

        int[] photoPixels = new int[pw * ph];
        cropped.getRGB(0, 0, pw, ph, photoPixels, 0, pw);

        int cw = canvas.getWidth();
        int ch = canvas.getHeight();
        int[] canvasPixels = new int[cw * ch];
        canvas.getRGB(0, 0, cw, ch, canvasPixels, 0, cw);

        int minX = (int) Math.max(0,    Math.floor(Math.min(Math.min(tlx, trx), Math.min(brx, blx))));
        int maxX = (int) Math.min(cw-1, Math.ceil (Math.max(Math.max(tlx, trx), Math.max(brx, blx))));
        int minY = (int) Math.max(0,    Math.floor(Math.min(Math.min(tly, try_), Math.min(bry, bly))));
        int maxY = (int) Math.min(ch-1, Math.ceil (Math.max(Math.max(tly, try_), Math.max(bry, bly))));

        for (int py = minY; py <= maxY; py++) {
            for (int px = minX; px <= maxX; px++) {
                if (!pointInQuad(px + 0.5, py + 0.5, tlx, tly, trx, try_, brx, bry, blx, bly))
                    continue;

                double[] st = inverseBilinear(px + 0.5, py + 0.5,
                        tlx, tly, trx, try_, brx, bry, blx, bly);
                double s = Math.max(0, Math.min(1, st[0]));
                double t = Math.max(0, Math.min(1, st[1]));

                double srcXd = s * (pw - 1);
                double srcYd = t * (ph - 1);
                int x0 = (int) srcXd, y0 = (int) srcYd;
                int x1 = Math.min(x0 + 1, pw - 1);
                int y1 = Math.min(y0 + 1, ph - 1);
                double fx = srcXd - x0, fy = srcYd - y0;

                canvasPixels[py * cw + px] = blendBilinear(
                        photoPixels[y0*pw+x0], photoPixels[y0*pw+x1],
                        photoPixels[y1*pw+x0], photoPixels[y1*pw+x1], fx, fy);
            }
        }

        canvas.setRGB(0, 0, cw, ch, canvasPixels, 0, cw);
    }

    private static BufferedImage centerCrop(BufferedImage photo, double targetRatio) {
        double photoRatio = (double) photo.getWidth() / photo.getHeight();
        int srcX, srcY, srcW, srcH;
        if (photoRatio > targetRatio) {
            srcH = photo.getHeight();
            srcW = (int) Math.round(srcH * targetRatio);
            srcX = (photo.getWidth() - srcW) / 2;
            srcY = 0;
        } else {
            srcW = photo.getWidth();
            srcH = (int) Math.round(srcW / targetRatio);
            srcX = 0;
            srcY = (photo.getHeight() - srcH) / 2;
        }
        if (srcW == photo.getWidth() && srcH == photo.getHeight()) return photo;
        return photo.getSubimage(srcX, srcY, srcW, srcH);
    }

    private static boolean pointInQuad(double px, double py,
            double x0, double y0, double x1, double y1,
            double x2, double y2, double x3, double y3) {
        return cross(x1-x0,y1-y0,px-x0,py-y0) >= 0
            && cross(x2-x1,y2-y1,px-x1,py-y1) >= 0
            && cross(x3-x2,y3-y2,px-x2,py-y2) >= 0
            && cross(x0-x3,y0-y3,px-x3,py-y3) >= 0;
    }

    private static double cross(double ax, double ay, double bx, double by) {
        return ax * by - ay * bx;
    }

    private static double[] inverseBilinear(double px, double py,
            double tlx, double tly, double trx, double try_,
            double brx, double bry, double blx, double bly) {
        double minBbX = Math.min(Math.min(tlx, trx), Math.min(brx, blx));
        double minBbY = Math.min(Math.min(tly, try_), Math.min(bry, bly));
        double bbW    = Math.max(Math.max(tlx, trx), Math.max(brx, blx)) - minBbX;
        double bbH    = Math.max(Math.max(tly, try_), Math.max(bry, bly)) - minBbY;
        double s = bbW > 0 ? Math.max(0, Math.min(1, (px - minBbX) / bbW)) : 0.5;
        double t = bbH > 0 ? Math.max(0, Math.min(1, (py - minBbY) / bbH)) : 0.5;

        for (int iter = 0; iter < 5; iter++) {
            double bx = bilinear(s, t, tlx, trx, brx, blx);
            double by = bilinear(s, t, tly, try_, bry, bly);
            double dx = px - bx, dy = py - by;
            if (Math.abs(dx) < 0.01 && Math.abs(dy) < 0.01) break;

            double dBxDs = (1-t)*(trx-tlx) + t*(brx-blx);
            double dBxDt = (1-s)*(blx-tlx) + s*(brx-trx);
            double dByDs = (1-t)*(try_-tly) + t*(bry-bly);
            double dByDt = (1-s)*(bly-tly) + s*(bry-try_);
            double det   = dBxDs*dByDt - dBxDt*dByDs;
            if (Math.abs(det) < 1e-9) break;

            s += ( dByDt*dx - dBxDt*dy) / det;
            t += (-dByDs*dx + dBxDs*dy) / det;
            s = Math.max(0, Math.min(1, s));
            t = Math.max(0, Math.min(1, t));
        }
        return new double[]{s, t};
    }

    private static double bilinear(double s, double t,
            double v00, double v10, double v11, double v01) {
        return (1-s)*(1-t)*v00 + s*(1-t)*v10 + s*t*v11 + (1-s)*t*v01;
    }

    private static int blendBilinear(int c00, int c10, int c01, int c11, double fx, double fy) {
        int a = (int) bilinear(fx, fy, a(c00), a(c10), a(c11), a(c01));
        int r = (int) bilinear(fx, fy, r(c00), r(c10), r(c11), r(c01));
        int g = (int) bilinear(fx, fy, g(c00), g(c10), g(c11), g(c01));
        int b = (int) bilinear(fx, fy, b(c00), b(c10), b(c11), b(c01));
        return (clamp(a)<<24)|(clamp(r)<<16)|(clamp(g)<<8)|clamp(b);
    }

    private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }
    private static int a(int argb) { return (argb>>24)&0xFF; }
    private static int r(int argb) { return (argb>>16)&0xFF; }
    private static int g(int argb) { return (argb>> 8)&0xFF; }
    private static int b(int argb) { return  argb     &0xFF; }
}
