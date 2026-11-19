package ru.dot.clipshot;

final class ShapeGeometry {
    static final int EDGE_TO_EDGE = 0, CENTER_OUT = 1, EDGE_IN = 2;
    private static final int STEPS = 32;

    // Paths in the Frame's normalized [-1,1] axes. Check is one open
    // stroke and cross has two independent strokes (no joining segment).
    static final float[] TRIANGLE_POINTS = {0,-1, 1,1, -1,1};
    static final float[] CHECK_POINTS = {-1,0, -.38f,.76f, 1,-.87f};
    static final float[] CROSS_POINTS = {-1,-1, 1,1, 1,-1, -1,1};

    static final class Bounds {
        float left, top, right, bottom;

        void set(float x0, float y0, float x1, float y1, boolean equalSides) {
            if (equalSides) {
                float side = Math.min(Math.abs(x1 - x0), Math.abs(y1 - y0));
                x1 = x0 + Math.copySign(side, x1 - x0);
                y1 = y0 + Math.copySign(side, y1 - y0);
            }
            left = Math.min(x0, x1);
            top = Math.min(y0, y1);
            right = Math.max(x0, x1);
            bottom = Math.max(y0, y1);
        }

        void setCircle(float centerX, float centerY, float edgeX, float edgeY) {
            setCircle(centerX, centerY, edgeX, edgeY, CENTER_OUT);
        }

        void setCircle(float x0, float y0, float x1, float y1, int gesture) {
            float dx = x1 - x0, dy = y1 - y0;
            float radius = (float) Math.hypot(dx, dy);
            if (gesture == EDGE_TO_EDGE) {
                setCentered(x0 + dx / 2f, y0 + dy / 2f, radius / 2f);
            } else if (gesture == EDGE_IN) {
                setCentered(x1, y1, radius);
            } else {
                setCentered(x0, y0, radius);
            }
        }

        void setSquare(float x0, float y0, float x1, float y1, int gesture) {
            if (gesture == EDGE_TO_EDGE) {
                set(x0, y0, x1, y1, true);
                return;
            }
            float halfSide = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
            if (gesture == EDGE_IN) setCentered(x1, y1, halfSide);
            else setCentered(x0, y0, halfSide);
        }

        private void setCentered(float x, float y, float radius) {
            left = x - radius;
            top = y - radius;
            right = x + radius;
            bottom = y + radius;
        }

        boolean nonempty() { return right > left && bottom > top; }
    }

    /** Four source-space vertices in winding order; false means the gesture has no area. */
    static boolean regular(float x0, float y0, float x1, float y1, int mode, boolean diamond,
                           boolean free, ImageTransform transform, float tilt, float[] vertices) {
        float a = transform == null ? x0 : transform.x(x0, y0);
        float b = transform == null ? y0 : transform.y(x0, y0);
        float c = transform == null ? x1 : transform.x(x1, y1);
        float d = transform == null ? y1 : transform.y(x1, y1);
        float cos = (float) Math.cos(Math.toRadians(tilt));
        float sin = (float) Math.sin(Math.toRadians(tilt));
        // Work relative to the first point. The display's actual rotation pivot then cancels.
        float dx = (c - a) * cos - (d - b) * sin;
        float dy = (c - a) * sin + (d - b) * cos;
        if (!free) {
            if (Math.abs(dx) >= Math.abs(dy)) dy = 0;
            else dx = 0;
        }
        if (dx == 0f && dy == 0f) return false;
        float factor = mode == EDGE_TO_EDGE ? .5f : 1f;
        float vx = dx * factor, vy = dy * factor;
        // From either side to center, keep the end anchored when the direction snaps.
        float cx = mode == EDGE_IN ? (c - a) * cos - (d - b) * sin :
                   mode == EDGE_TO_EDGE ? vx : 0f;
        float cy = mode == EDGE_IN ? (c - a) * sin + (d - b) * cos :
                   mode == EDGE_TO_EDGE ? vy : 0f;
        for (int i = 0; i < 4; ++i) {
            float k = i < 2 ? 1 : -1;
            float j = i == 1 || i == 2 ? 1 : -1;
            if (diamond) {
                k = i == 0 ? 1 : i == 2 ? -1 : 0;
                j = i == 1 ? 1 : i == 3 ? -1 : 0;
            }
            float px = cx + k * vx - j * vy, py = cy + k * vy + j * vx;
            float tx = a + px * cos + py * sin, ty = b - px * sin + py * cos;
            vertices[2*i] = transform == null ? tx : transform.sourceX(tx, ty);
            vertices[2*i+1] = transform == null ? ty : transform.sourceY(tx, ty);
        }
        return true;
    }

    /** Local unit box mapped back to source pixels; shape paths retain screen-relative geometry. */
    static final class Frame {
        private float cx, cy, ux, uy, vx, vy, cos, sin, a, b;
        private ImageTransform orientation;

        boolean set(float x0, float y0, float x1, float y1, int mode, boolean free, boolean heart,
                    ImageTransform orientation, float tilt) {
            return set(x0,y0,x1,y1,mode,free,heart,orientation,tilt,false);
        }

        boolean set(float x0, float y0, float x1, float y1, int mode, boolean free, boolean heart,
                    ImageTransform orientation, float tilt, boolean lockNativeAspect) {
            this.orientation = orientation;
            a = orientation == null ? x0 : orientation.x(x0, y0);
            b = orientation == null ? y0 : orientation.y(x0, y0);
            float sx = (orientation == null ? x1 : orientation.x(x1, y1)) - a;
            float sy = (orientation == null ? y1 : orientation.y(x1, y1)) - b;
            cos = (float)Math.cos(Math.toRadians(tilt));
            sin = (float)Math.sin(Math.toRadians(tilt));
            float dx = sx * cos - sy * sin, dy = sx * sin + sy * cos;
            if (dx == 0f && dy == 0f) return false;
            cx = mode == EDGE_IN ? dx : mode == EDGE_TO_EDGE ? dx / 2f : 0;
            cy = mode == EDGE_IN ? dy : mode == EDGE_TO_EDGE ? dy / 2f : 0;
            if (lockNativeAspect) {
                float factor = mode == EDGE_TO_EDGE ? .5f : 1f;
                if (free) {
                    // The angle follows the gesture line, not its direction: reversing it
                    // must not turn asymmetric glyphs upside down.
                    if (dx < 0f || (dx == 0f && dy < 0f)) factor = -factor;
                    ux = dx * factor; uy = dy * factor;
                    vx = -uy; vy = ux;
                } else {
                    ux = vy = Math.max(Math.abs(dx), Math.abs(dy)) * factor;
                    uy = vx = 0f;
                }
                return true;
            }
            if (free) {
                float factor = mode == EDGE_TO_EDGE ? .5f : 1f;
                float ax = dx * factor, ay = dy * factor;
                if (heart) {
                    // Local +Y is the tip. It points from the center toward the dragged edge.
                    if (mode == EDGE_IN) { ax = -ax; ay = -ay; }
                    vx = ax; vy = ay;
                    ux = ay * 2f / 3f; uy = -ax * 2f / 3f;
                } else {
                    ux = ax; uy = ay;
                    vx = -ay * 2f / 3f; vy = ax * 2f / 3f;
                }
            } else {
                float factor = mode == EDGE_TO_EDGE ? .5f : 1f;
                ux = Math.abs(dx) * factor;
                vy = Math.abs(dy) * factor;
                uy = vx = 0f;
                if (mode != EDGE_TO_EDGE) {
                    // A purely axial centered drag still draws a useful shape.
                    if (ux < .001f) ux = vy * 3f / 2f;
                    if (vy < .001f) vy = ux * 2f / 3f;
                }
                if (ux <= 0f || vy <= 0f) return false;
            }
            return true;
        }

        float sourceX(float u, float v) {
            float px = cx + u * ux + v * vx, py = cy + u * uy + v * vy;
            float x = a + px * cos + py * sin, y = b - px * sin + py * cos;
            return orientation == null ? x : orientation.sourceX(x,y);
        }

        float sourceY(float u, float v) {
            float px = cx + u * ux + v * vx, py = cy + u * uy + v * vy;
            float x = a + px * cos + py * sin, y = b - px * sin + py * cos;
            return orientation == null ? y : orientation.sourceY(x,y);
        }
    }

    // Self-contained normalized heart outline, sampled symmetrically from cubic curves.
    // It is scaled in source-pixel coordinates so changing its size does not scale its stroke.
    static float[] heartPoints() {
        float[][] segments = {
            {.50f,.25f, .30f,-.07f, .01f,.04f, .035f,.38f},
            {.035f,.38f, .045f,.59f, .23f,.76f, .50f,1f},
            {.50f,1f, .77f,.76f, .955f,.59f, .965f,.38f},
            {.965f,.38f, .99f,.04f, .70f,-.07f, .50f,.25f}
        };
        float[] points = new float[segments.length * STEPS * 2];
        int index = 0;
        for (float[] segment : segments) {
            for (int i = 0; i < STEPS; ++i) {
                float t = i / (float) STEPS, u = 1f - t;
                for (int xy = 0; xy < 2; ++xy)
                    points[index++] = u*u*u*segment[xy] + 3f*u*u*t*segment[xy+2]
                            + 3f*u*t*t*segment[xy+4] + t*t*t*segment[xy+6];
            }
        }
        return points;
    }

    private ShapeGeometry() {}
}
