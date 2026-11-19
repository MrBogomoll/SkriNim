package ru.dot.clipshot;

/** Independent tilt geometry in post-D4, pre-display image coordinates. */
final class TiltGeometry {
    private static final double EDGE = .0002;
    final float degrees;
    final double c, s;

    TiltGeometry(float degrees) {
        this.degrees = degrees;
        double radians = Math.toRadians(degrees);
        c = Math.cos(radians);
        s = Math.sin(radians);
    }

    float inverseX(float x, float y, float w, float h) {
        return (float) (w * .5 + c * (x - w * .5) + s * (y - h * .5));
    }

    float inverseY(float x, float y, float w, float h) {
        return (float) (h * .5 - s * (x - w * .5) + c * (y - h * .5));
    }

    /** Largest centered rectangle of the requested aspect, entirely in both preview and source. */
    void fit(float w, float h, float ratio, float maxW, float maxH, float[] out) {
        if (!(ratio > 0)) ratio = w / h;
        if (degrees == 0 && ratio == w / h && maxW >= w && maxH >= h) {
            out[0]=0; out[1]=0; out[2]=w; out[3]=h;
            return;
        }
        double ac = Math.abs(c), as = Math.abs(s);
        double hh = Math.min(Math.min(w / ratio, h), Math.min(maxW / ratio, maxH));
        hh = Math.min(hh, Math.min(w / (ac * ratio + as), h / (as * ratio + ac)));
        // A few floating-point ulps of inward rounding keep the preview's
        // model coordinates safe on very large images too. This is subpixel.
        if (degrees != 0) hh *= 1.0 - 4e-7;
        double cw = hh * ratio;
        out[0] = (float) ((w - cw) / 2); out[1] = (float) ((h - hh) / 2);
        out[2] = (float) ((w + cw) / 2); out[3] = (float) ((h + hh) / 2);
    }

    boolean fits(float w, float h, float l, float t, float r, float b) {
        double halfW = (r - l) * .5, halfH = (b - t) * .5;
        double centerX = ((double) l + r - w) * .5;
        double centerY = ((double) t + b - h) * .5;
        return l >= -EDGE && t >= -EDGE && r <= w + EDGE && b <= h + EDGE
                && Math.abs(c * centerX + s * centerY) + Math.abs(c) * halfW + Math.abs(s) * halfH <= w * .5 + EDGE
                && Math.abs(-s * centerX + c * centerY) + Math.abs(s) * halfW + Math.abs(c) * halfH <= h * .5 + EDGE;
    }

    /** Clamps along the drag, keeping its anchor and any aspect constraint intact. */
    void constrain(float w, float h, float l, float t, float r, float b, float[] proposed) {
        if (fits(w,h,proposed[0],proposed[1],proposed[2],proposed[3])) return;
        float dl=proposed[0]-l, dt=proposed[1]-t, dr=proposed[2]-r, db=proposed[3]-b;
        float lo=0, hi=1;
        for (int i=0;i<22;i++) {
            float f=(lo+hi)*.5f;
            if (fits(w,h,l+f*dl,t+f*dt,r+f*dr,b+f*db)) lo=f;
            else hi=f;
        }
        proposed[0]=l+lo*dl; proposed[1]=t+lo*dt; proposed[2]=r+lo*dr; proposed[3]=b+lo*db;
    }

    /** Select integer boundaries inward so rounding cannot reveal rotated corners. */
    void pixels(int w, int h, float l, float t, float r, float b, int numerator, int denominator, int[] out) {
        int left=(int)Math.ceil(l), top=(int)Math.ceil(t), right=(int)Math.floor(r), bottom=(int)Math.floor(b);
        if (numerator > 0 && denominator > 0) {
            int count = Math.min((right-left)/numerator,(bottom-top)/denominator);
            if (count > 0) {
                int cw=count*numerator, ch=count*denominator;
                left+=(right-left-cw)/2; top+=(bottom-top-ch)/2;
                right=left+cw; bottom=top+ch;
            }
        }
        if (right <= left || bottom <= top || !fits(w,h,left,top,right,bottom)) {
            // Rare subpixel frame. The source center is guaranteed inside any tilt.
            left=(w-1)/2; top=(h-1)/2; right=left+1; bottom=top+1;
        }
        out[0]=left; out[1]=top; out[2]=right; out[3]=bottom;
    }
}
