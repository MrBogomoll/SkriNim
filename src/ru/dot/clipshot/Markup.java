package ru.dot.clipshot;

import android.graphics.Bitmap;
import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;

final class Markup {
    static final int CROP = 0, PEN = 1, OVAL = 2, ARROW = 3, ERASER = 4, MOSAIC = 5;
    static final int LINE = 6, RECT = 7, CIRCLE = 8, SQUARE = 9, HEART = 10, DIAMOND = 11, TEXT = 12;
    static final int TRIANGLE = 13, CHECK = 14, CROSS = 15, STEP = 16, CALLOUT = 17;
    static final int LAST_TOOL = CALLOUT;
    private static final float[] HEART_POINTS = ShapeGeometry.heartPoints();
    private static final float[] ELLIPSE_POINTS = ellipsePoints();
    private static final float[] RECT_POINTS = {-1,-1,1,-1,1,1,-1,1};
    private final ShapeGeometry.Frame shapeFrame = new ShapeGeometry.Frame();
    private final ShapeGeometry.Bounds shapeBounds = new ShapeGeometry.Bounds();
    private final Path shape = new Path();
    private final float[] regularVertices = new float[8];
    private final ArrayList<Mark> marks = new ArrayList<>();
    private final Paint brush = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Mark current;
    private final Bitmap source;
    private final Paint tiles = new Paint();
    private final Paint imagePaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private int[] basePixels = new int[0], overlayPixels = new int[0];
    private Bitmap committed;
    private Canvas committedCanvas;
    private MosaicPreview mosaicPreview;
    private int[] textWords;
    private BitSet hitWords;
    private int[] selectedWords;
    private int selectedCount;
    private final Path arrow = new Path();

    static boolean isShape(int tool) {
        return tool == OVAL || (tool >= RECT && tool <= DIAMOND)
                || (tool >= TRIANGLE && tool <= CROSS);
    }

    static boolean isOpenShape(int tool) { return tool == CHECK || tool == CROSS; }

    Markup(Bitmap source) {
        this.source = source;
        brush.setStrokeCap(Paint.Cap.ROUND);
        brush.setStrokeJoin(Paint.Join.ROUND);
        brush.setStyle(Paint.Style.STROKE);
    }
    void begin(int tool, float x, float y, int color, float width) {
        begin(tool, x, y, color, width, 16);
    }
    void begin(int tool, float x, float y, int color, float width, int blockSize) {
        begin(tool, x, y, color, width, blockSize, null, false);
    }
    void begin(int tool, float x, float y, int color, float width, int blockSize,
               ImageTransform drawingTransform, boolean filled) {
        begin(tool, x, y, color, width, blockSize, drawingTransform, filled,
                tool == CIRCLE ? ShapeGeometry.CENTER_OUT : ShapeGeometry.EDGE_TO_EDGE);
    }
    void begin(int tool, float x, float y, int color, float width, int blockSize,
               ImageTransform drawingTransform, boolean filled, int regularGesture) {
        begin(tool, x, y, color, width, blockSize, drawingTransform, filled, regularGesture, null);
    }
    /** OCR rectangles are immutable source-pixel word boxes, inclusive left/top and exclusive right/bottom. */
    void begin(int tool, float x, float y, int color, float width, int blockSize,
               ImageTransform drawingTransform, boolean filled, int regularGesture, int[] textWords) {
        begin(tool, x, y, color, width, blockSize, drawingTransform, filled, regularGesture, textWords, false);
    }
    void begin(int tool, float x, float y, int color, float width, int blockSize,
               ImageTransform drawingTransform, boolean filled, int regularGesture, int[] textWords,
               boolean freeDirection) {
        begin(tool, x, y, color, width, blockSize, drawingTransform, filled, regularGesture, textWords,
                freeDirection, 0f);
    }
    void begin(int tool, float x, float y, int color, float width, int blockSize,
               ImageTransform drawingTransform, boolean filled, int regularGesture, int[] textWords,
               boolean freeDirection, float editorTilt) {
        cancel();
        if (tool != ERASER && tool != MOSAIC && (color >>> 24) == 0) {
            current = null;
            return;
        }
        if (tool == ERASER) {
            boolean drawing = false;
            for (Mark mark : marks) {
                if (mark.tool != ERASER) {
                    drawing = true;
                    break;
                }
            }
            if (!drawing) {
                current = null;
                return;
            }
        }
        current = new Mark(tool, x, y, color, Math.max(1f, width),
                (isFrameShape(tool) || isRegularPolygon(tool)) ? drawingTransform : null,
                filled && isShape(tool) && !isOpenShape(tool), regularGesture, freeDirection, editorTilt);
        if (tool == MOSAIC) {
            if (textWords == null) {
                current.mask = new MosaicGrid(source.getWidth(), source.getHeight(), blockSize);
            } else {
                this.textWords = textWords;
                hitWords = new BitSet(textWords.length / 4);
                selectedWords = new int[Math.min(16, textWords.length - textWords.length % 4)];
                selectedCount = 0;
                current.wordBlock = Math.max(1, blockSize);
            }
            mosaicPreview = new MosaicPreview();
            extendMosaic(x, y, x, y);
        }
    }
    void extend(float x, float y) {
        if (current == null) return;
        if (x != current.x1 || y != current.y1) {
            if (current.path != null) current.path.lineTo(x, y);
            if (current.tool == MOSAIC)
                extendMosaic(current.x1, current.y1, x, y);
            current.moved = true;
            current.x1 = x;
            current.y1 = y;
        }
    }
    private void extendMosaic(float x0, float y0, float x1, float y1) {
        if (textWords != null) {
            extendTextMosaic(x0, y0, x1, y1);
            return;
        }
        MosaicGrid grid = current.mask;
        grid.cover(x0, y0, x1, y1, current.width * .5f, index -> {
            int x = (index % grid.columns) * grid.cell;
            int y = (index / grid.columns) * grid.cell;
            int w = Math.min(grid.cell, source.getWidth() - x);
            int h = Math.min(grid.cell, source.getHeight() - y);
            tiles.setColor(sample(x, y, w, h));
            mosaicPreview.fill(x, y, w, h);
        });
    }

    private void extendTextMosaic(float x0, float y0, float x1, float y1) {
        float radius = current.width * .5f;
        float sweepLeft = Math.min(x0, x1) - radius, sweepRight = Math.max(x0, x1) + radius;
        float sweepTop = Math.min(y0, y1) - radius, sweepBottom = Math.max(y0, y1) + radius;
        int count = textWords.length / 4;
        for (int i = hitWords.nextClearBit(0); i < count; i = hitWords.nextClearBit(i + 1)) {
            int p = i * 4;
            int l = Math.max(0, textWords[p]), t = Math.max(0, textWords[p + 1]);
            int r = Math.min(source.getWidth(), textWords[p + 2]);
            int b = Math.min(source.getHeight(), textWords[p + 3]);
            if (l >= r || t >= b || l >= sweepRight || r <= sweepLeft ||
                    t >= sweepBottom || b <= sweepTop ||
                    !brushHitsWord(x0, y0, x1, y1, radius, l, t, r, b)) continue;
            hitWords.set(i);
            if (selectedCount == selectedWords.length)
                selectedWords = Arrays.copyOf(selectedWords, Math.min(count * 4, selectedWords.length * 2));
            selectedWords[selectedCount++] = l;
            selectedWords[selectedCount++] = t;
            selectedWords[selectedCount++] = r;
            selectedWords[selectedCount++] = b;
            drawTextWord(mosaicPreview, current.wordBlock, l, t, r, b);
        }
    }

    private void drawTextWord(MosaicPreview preview, int block, int l, int t, int r, int b) {
        for (int y = t; y < b;) {
            int nextY = y + Math.min(block, b - y);
            for (int x = l; x < r;) {
                int nextX = x + Math.min(block, r - x);
                tiles.setColor(sample(x, y, nextX - x, nextY - y));
                preview.fill(x, y, nextX - x, nextY - y);
                x = nextX;
            }
            y = nextY;
        }
    }

    // The exact capsule test avoids touching diagonal boxes just because they lie inside the sweep's AABB.
    private static boolean brushHitsWord(float x0, float y0, float x1, float y1, float radius,
                                         int l, int t, int r, int b) {
        float dx = x1 - x0, dy = y1 - y0;
        float min = 0f, max = 1f;
        boolean crosses = true;
        if (dx == 0f) crosses = x0 >= l && x0 <= r;
        else {
            float p = (l - x0) / dx, q = (r - x0) / dx;
            min = Math.max(min, Math.min(p, q)); max = Math.min(max, Math.max(p, q));
        }
        if (dy == 0f) crosses &= y0 >= t && y0 <= b;
        else {
            float p = (t - y0) / dy, q = (b - y0) / dy;
            min = Math.max(min, Math.min(p, q)); max = Math.min(max, Math.max(p, q));
        }
        if (crosses && min <= max) return true;
        float radius2 = radius * radius;
        if (pointToBox2(x0, y0, l, t, r, b) < radius2 ||
                pointToBox2(x1, y1, l, t, r, b) < radius2) return true;
        float length2 = dx * dx + dy * dy;
        return length2 > 0f && (pointToSegment2(l, t, x0, y0, dx, dy, length2) < radius2 ||
                pointToSegment2(l, b, x0, y0, dx, dy, length2) < radius2 ||
                pointToSegment2(r, t, x0, y0, dx, dy, length2) < radius2 ||
                pointToSegment2(r, b, x0, y0, dx, dy, length2) < radius2);
    }

    private static float pointToBox2(float x, float y, int l, int t, int r, int b) {
        float dx = Math.max(0f, Math.max(l - x, x - r));
        float dy = Math.max(0f, Math.max(t - y, y - b));
        return dx * dx + dy * dy;
    }

    private static float pointToSegment2(float x, float y, float x0, float y0,
                                         float dx, float dy, float length2) {
        float vx = x - x0, vy = y - y0;
        float k = Math.max(0f, Math.min(1f, (vx * dx + vy * dy) / length2));
        vx -= dx * k; vy -= dy * k;
        return vx * vx + vy * vy;
    }

    void finish() {
        if (current == null) return;
        if (textWords != null) {
            if (selectedCount == 0) {
                cancel();
                return;
            }
            current.words = Arrays.copyOf(selectedWords, selectedCount);
        }
        boolean degenerateShape = false;
        if (isRegularPolygon(current.tool)) {
            degenerateShape = !setRegularVertices(current);
        } else if (usesFrame(current)) {
            degenerateShape = !setShapeFrame(current);
        } else if (isShape(current.tool) && (current.tool != OVAL || current.filled)) {
            setShapeBounds(current);
            degenerateShape = !shapeBounds.nonempty();
        }
        if (!degenerateShape && (current.tool == PEN || current.tool == MOSAIC ||
                current.moved || current.tool == ERASER)) {
            ensureCommitted();
            if (current.tool == MOSAIC) mosaicPreview.draw(committedCanvas, null);
            else drawInk(committedCanvas, current);
            marks.add(current);
        }
        cancel();
    }

    void cancel() {
        current = null;
        mosaicPreview = null;
        textWords = null;
        hitWords = null;
        selectedWords = null;
        selectedCount = 0;
    }
    boolean hasMarks() { return !marks.isEmpty(); }

    void clear() {
        cancel();
        marks.clear();
        committed = null;
        committedCanvas = null;
    }

    void undo() {
        if (!hasMarks()) return;
        if (marks.size() == 1) {
            clear();
            return;
        }
        cancel();
        marks.remove(marks.size() - 1);
        // History only needs replay for Undo, never during gesture previews or exports.
        committed.eraseColor(0);
        for (Mark mark : marks) {
            if (mark.words != null) {
                // Rebuild a whole stroke before changing the committed layer. Even overlapping
                // word boxes sample the same pre-stroke frame, just like the original preview.
                MosaicPreview restored = new MosaicPreview();
                for (int i = 0; i < mark.words.length; i += 4)
                    drawTextWord(restored, mark.wordBlock, mark.words[i], mark.words[i+1],
                            mark.words[i+2], mark.words[i+3]);
                restored.draw(committedCanvas, null);
            } else if (mark.tool == MOSAIC) replayMosaic(mark.mask);
            else drawInk(committedCanvas, mark);
        }
    }

    void draw(Canvas canvas, boolean includeCurrent) {
        Mark active = includeCurrent ? current : null;
        if (active != null && active.tool == ERASER && committed != null) {
            int layer = canvas.saveLayer(null, null);
            try {
                canvas.drawBitmap(committed, 0, 0, imagePaint);
                drawInk(canvas, active);
            } finally {
                canvas.restoreToCount(layer);
            }
            return;
        }
        if (committed != null) canvas.drawBitmap(committed, 0, 0, imagePaint);
        if (active == null) return;
        if (active.tool == MOSAIC) mosaicPreview.draw(canvas, null);
        else drawInk(canvas, active);
    }

    int colorAt(int x,int y) {
        x=Math.max(0,Math.min(source.getWidth()-1,x));
        y=Math.max(0,Math.min(source.getHeight()-1,y));
        int base=source.getPixel(x,y);
        return committed==null?base:MosaicGrid.over(base,committed.getPixel(x,y));
    }

    int colorGrid(ImageTransform orientation,int u,int v,int grid,int[] colors) {
        int half=grid/2;
        for (int row=0;row<grid;row++) {
            float oy=Math.max(0,Math.min(orientation.height-1,v+row-half))+.5f;
            for (int col=0;col<grid;col++) {
                float ox=Math.max(0,Math.min(orientation.width-1,u+col-half))+.5f;
                colors[row*grid+col]=colorAt((int)orientation.sourceX(ox,oy),
                        (int)orientation.sourceY(ox,oy));
            }
        }
        return colors[half*grid+half];
    }

    private void ensureCommitted() {
        if (committed != null) return;
        committed = newBitmap(source.getWidth(), source.getHeight());
        committedCanvas = new Canvas(committed);
    }

    private static Bitmap newBitmap(int width, int height) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.setDensity(Bitmap.DENSITY_NONE);
        return bitmap;
    }

    private int sample(int x, int y, int w, int h) {
        int count = w * h;
        if (basePixels.length < count) {
            basePixels = new int[count];
            overlayPixels = new int[count];
        }
        source.getPixels(basePixels, 0, w, x, y, w, h);
        // The screenshot is opaque. Blend the committed edits over it before averaging.
        if (committed == null) return MosaicGrid.average(basePixels, count);
        committed.getPixels(overlayPixels, 0, w, x, y, w, h);
        return MosaicGrid.averageComposite(basePixels, overlayPixels, count);
    }

    private void replayMosaic(MosaicGrid grid) {
        // These source-space rectangles do not overlap within a stroke. Updating one
        // block cannot affect the inputs of a different block in the same stroke.
        for (int i = grid.next(0); i >= 0; i = grid.next(i + 1)) {
            int x = (i % grid.columns) * grid.cell, y = (i / grid.columns) * grid.cell;
            int w = Math.min(grid.cell, source.getWidth() - x);
            int h = Math.min(grid.cell, source.getHeight() - y);
            tiles.setColor(sample(x, y, w, h));
            committedCanvas.drawRect(x, y, x + w, y + h, tiles);
        }
    }

    /** Only touched 128px chunks are rasterized/uploaded while the current stroke changes. */
    private final class MosaicPreview {
        private static final int CHUNK = 128;
        private final int columns = (source.getWidth() + CHUNK - 1) / CHUNK;
        private final java.util.HashMap<Integer, Chunk> chunks = new java.util.HashMap<>();

        void fill(int x, int y, int w, int h) {
            int right = x + w, bottom = y + h;
            for (int cy = y / CHUNK; cy <= (bottom - 1) / CHUNK; cy++) {
                for (int cx = x / CHUNK; cx <= (right - 1) / CHUNK; cx++) {
                    int id = cy * columns + cx;
                    Chunk chunk = chunks.get(id);
                    if (chunk == null) {
                        chunk = new Chunk(cx * CHUNK, cy * CHUNK);
                        chunks.put(id, chunk);
                    }
                    chunk.canvas.drawRect(Math.max(0, x - chunk.x), Math.max(0, y - chunk.y),
                            Math.min(chunk.bitmap.getWidth(), right - chunk.x),
                            Math.min(chunk.bitmap.getHeight(), bottom - chunk.y), tiles);
                }
            }
        }

        void draw(Canvas canvas, Paint paint) {
            for (Chunk chunk : chunks.values())
                canvas.drawBitmap(chunk.bitmap, chunk.x, chunk.y, paint);
        }

        private final class Chunk {
            final int x, y;
            final Bitmap bitmap;
            final Canvas canvas;
            Chunk(int x, int y) {
                this.x = x;
                this.y = y;
                bitmap = newBitmap(Math.min(CHUNK, source.getWidth() - x), Math.min(CHUNK, source.getHeight() - y));
                canvas = new Canvas(bitmap);
            }
        }
    }

    interface Stamp { void draw(Canvas canvas); }

    /** Immutable source-space geometry; can be redrawn both as draft and committed stamp. */
    static final class ShapeDraft implements Stamp {
        private final float[] xy;
        private final int tool, color;
        private final float width;
        private final boolean filled;
        private final Path path = new Path();
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final float left, top, right, bottom;

        private ShapeDraft(float[] points, int tool, int color, float width, boolean filled) {
            this.xy = points;
            this.tool = tool;
            this.color = color;
            this.width = Math.max(1f,width);
            this.filled = filled && !isOpenShape(tool);
            paint.setColor(color);
            paint.setStrokeWidth(this.width);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStyle(this.filled ? Paint.Style.FILL : Paint.Style.STROKE);
            for (int i=0; i<xy.length; i+=2) {
                if (i==0 || (tool==CROSS && i==4)) path.moveTo(xy[i],xy[i+1]);
                else path.lineTo(xy[i],xy[i+1]);
            }
            if (!isOpenShape(tool)) path.close();
            float[] limits=bounds(null);
            left=limits[0]; top=limits[1]; right=limits[2]; bottom=limits[3];
        }

        @Override public void draw(Canvas canvas) { canvas.drawPath(path,paint); }

        float[] bounds(float[] affine) {
            float l=Float.POSITIVE_INFINITY,t=l,r=Float.NEGATIVE_INFINITY,b=r;
            for (int i=0;i<xy.length;i+=2) {
                float x=xy[i],y=xy[i+1];
                float u=affine==null?x:affine[0]*x+affine[1]*y+affine[2];
                float v=affine==null?y:affine[3]*x+affine[4]*y+affine[5];
                l=Math.min(l,u); t=Math.min(t,v); r=Math.max(r,u); b=Math.max(b,v);
            }
            return new float[]{l,t,r,b};
        }

        ShapeDraft transformed(float[] affine) {
            float scale=(float)Math.sqrt(Math.abs(affine[0]*affine[4]-affine[1]*affine[3]));
            return transformed(affine,color,width*scale,filled);
        }

        /** Applies a new style after geometry: strokePixels is in final source pixels. */
        ShapeDraft transformed(float[] affine,int color,float strokePixels,boolean filled) {
            float[] points=new float[xy.length];
            for (int i=0;i<xy.length;i+=2) {
                float x=xy[i],y=xy[i+1];
                points[i]=affine[0]*x+affine[1]*y+affine[2];
                points[i+1]=affine[3]*x+affine[4]*y+affine[5];
            }
            return new ShapeDraft(points,tool,color,strokePixels,filled);
        }

        float strokePixels() { return width; }
    }

    ShapeDraft takeShapeDraft() {
        Mark mark=current;
        if (mark==null || !isShape(mark.tool)) return null;
        cancel();
        if (!mark.moved) return null;
        float[] points;
        if (isRegularPolygon(mark.tool)) {
            if (!setRegularVertices(mark)) return null;
            points=regularVertices.clone();
        } else if (usesFrame(mark)) {
            if (!setShapeFrame(mark)) return null;
            float[] definition=framePoints(mark.tool);
            boolean heart=mark.tool==HEART;
            points=new float[definition.length];
            for (int i=0;i<definition.length;i+=2) {
                float u=heart?definition[i]*2f-1f:definition[i];
                float v=heart?definition[i+1]*2f-1f:definition[i+1];
                points[i]=shapeFrame.sourceX(u,v);
                points[i+1]=shapeFrame.sourceY(u,v);
            }
        } else {
            setShapeBounds(mark);
            if (!shapeBounds.nonempty()) return null;
            float[] definition=mark.tool==RECT?RECT_POINTS:ELLIPSE_POINTS;
            points=new float[definition.length];
            float hw=(shapeBounds.right-shapeBounds.left)/2f;
            float hh=(shapeBounds.bottom-shapeBounds.top)/2f;
            for (int i=0;i<definition.length;i+=2) {
                points[i]=shapeBounds.left+hw*(definition[i]+1f);
                points[i+1]=shapeBounds.top+hh*(definition[i+1]+1f);
            }
        }
        return new ShapeDraft(points,mark.tool,mark.color,mark.width,mark.filled);
    }

    int countStamps(Class<? extends Stamp> stampType) {
        int count = 0;
        for (Mark mark : marks) if (stampType.isInstance(mark.stamp)) count++;
        return count;
    }

    void addStamp(Stamp stamp) {
        cancel();
        Mark mark = new Mark(TEXT,0,0,0,1,null,false,0,false,0);
        mark.stamp=stamp;
        ensureCommitted();
        drawInk(committedCanvas,mark);
        marks.add(mark);
    }

    private void drawInk(Canvas canvas, Mark mark) {
        if (mark.stamp!=null) {
            mark.stamp.draw(canvas);
            return;
        }
        brush.setColor(mark.color);
        brush.setStyle(mark.filled ? Paint.Style.FILL : Paint.Style.STROKE);
        brush.setStrokeWidth(mark.width);
        brush.setBlendMode(mark.tool == ERASER ? BlendMode.CLEAR : null);
        float x = mark.x1, y = mark.y1;
        if (mark.tool == PEN || mark.tool == ERASER) {
            if (mark.moved) canvas.drawPath(mark.path, brush);
            else {
                brush.setStyle(Paint.Style.FILL);
                canvas.drawCircle(mark.x0, mark.y0, brush.getStrokeWidth() / 2f, brush);
                brush.setStyle(Paint.Style.STROKE);
            }
        } else if (mark.tool == LINE) {
            if (!mark.moved) return;
            shape.rewind();
            shape.moveTo(mark.x0, mark.y0);
            shape.lineTo(x, y);
            canvas.drawPath(shape, brush);
        } else if (isRegularPolygon(mark.tool)) {
            if (!mark.moved || !setRegularVertices(mark)) return;
            shape.rewind();
            shape.moveTo(regularVertices[0], regularVertices[1]);
            for (int i = 2; i < regularVertices.length; i += 2)
                shape.lineTo(regularVertices[i], regularVertices[i+1]);
            shape.close();
            canvas.drawPath(shape, brush);
        } else if (usesFrame(mark)) {
            if (!mark.moved || !setShapeFrame(mark)) return;
            boolean heart = mark.tool == HEART;
            float[] points = framePoints(mark.tool);
            shape.rewind();
            for (int i = 0; i < points.length; i += 2) {
                float u = heart ? points[i] * 2f - 1f : points[i];
                float v = heart ? points[i + 1] * 2f - 1f : points[i + 1];
                float px = shapeFrame.sourceX(u,v), py = shapeFrame.sourceY(u,v);
                if (i == 0 || (mark.tool == CROSS && i == 4)) shape.moveTo(px,py);
                else shape.lineTo(px,py);
            }
            if (!isOpenShape(mark.tool)) shape.close();
            canvas.drawPath(shape, brush);
        } else if (isShape(mark.tool)) {
            if (!mark.moved) return;
            setShapeBounds(mark);
            if ((mark.tool != OVAL || mark.filled) && !shapeBounds.nonempty()) return;
            float l = shapeBounds.left, t = shapeBounds.top;
            float r = shapeBounds.right, b = shapeBounds.bottom;
            if (mark.tool == RECT) canvas.drawRect(l, t, r, b, brush);
            else canvas.drawOval(l, t, r, b, brush);
        } else if (mark.tool == ARROW) {
            float[] head = ArrowGeometry.head(mark.x0, mark.y0, x, y, brush.getStrokeWidth());
            if (head == null) return;
            // Draw as one stroke so a translucent arrow does not darken at the joins.
            arrow.rewind();
            arrow.moveTo(mark.x0, mark.y0);
            arrow.lineTo(x, y);
            arrow.moveTo(head[0], head[1]);
            arrow.lineTo(x, y);
            arrow.lineTo(head[2], head[3]);
            canvas.drawPath(arrow, brush);
        }
    }
    private static boolean isRegularPolygon(int tool) {
        return tool == SQUARE || tool == DIAMOND;
    }

    private boolean setRegularVertices(Mark mark) {
        return ShapeGeometry.regular(mark.x0, mark.y0, mark.x1, mark.y1,
                mark.regularGesture, mark.tool == DIAMOND, mark.freeDirection,
                mark.drawingTransform, mark.editorTilt, regularVertices);
    }

    private static boolean isFrameShape(int tool) {
        return tool == RECT || tool == OVAL || tool == HEART || (tool >= TRIANGLE && tool <= CROSS);
    }

    private static boolean usesFrame(Mark mark) {
        return mark.tool == HEART || (mark.tool >= TRIANGLE && mark.tool <= CROSS)
                || (isFrameShape(mark.tool) && (mark.freeDirection ||
                mark.regularGesture != ShapeGeometry.EDGE_TO_EDGE || mark.editorTilt != 0f));
    }

    private static float[] framePoints(int tool) {
        switch (tool) {
            case RECT: return RECT_POINTS;
            case HEART: return HEART_POINTS;
            case TRIANGLE: return ShapeGeometry.TRIANGLE_POINTS;
            case CHECK: return ShapeGeometry.CHECK_POINTS;
            case CROSS: return ShapeGeometry.CROSS_POINTS;
            default: return ELLIPSE_POINTS;
        }
    }

    private boolean setShapeFrame(Mark mark) {
        return shapeFrame.set(mark.x0, mark.y0, mark.x1, mark.y1, mark.regularGesture,
                mark.freeDirection, mark.tool == HEART, mark.drawingTransform, mark.editorTilt,
                isOpenShape(mark.tool));
    }

    private static float[] ellipsePoints() {
        float[] points = new float[192];
        for (int i = 0; i < points.length; i += 2) {
            double angle = Math.PI * 2 * i / points.length;
            points[i] = (float)Math.cos(angle);
            points[i + 1] = (float)Math.sin(angle);
        }
        return points;
    }

    private void setShapeBounds(Mark mark) {
        if (mark.tool == CIRCLE) {
            shapeBounds.setCircle(mark.x0, mark.y0, mark.x1, mark.y1, mark.regularGesture);
        } else {
            shapeBounds.set(mark.x0, mark.y0, mark.x1, mark.y1, false);
        }
    }

    private static final class Mark {
        final int tool;
        final float x0, y0, width;
        final int color, regularGesture;
        final boolean filled, freeDirection;
        final float editorTilt;
        final Path path;
        final ImageTransform drawingTransform;
        float x1, y1;
        MosaicGrid mask;
        int[] words;
        int wordBlock;
        boolean moved;
        Stamp stamp;
        Mark(int tool, float x, float y, int color, float width, ImageTransform drawingTransform,
             boolean filled, int regularGesture, boolean freeDirection, float editorTilt) {
            this.filled = filled;
            this.freeDirection = freeDirection;
            this.editorTilt = editorTilt;
            this.regularGesture = regularGesture;
            this.tool = tool;
            this.drawingTransform = drawingTransform;
            this.color = color;
            this.width = width;
            x0 = x1 = x; y0 = y1 = y;
            path = tool == PEN || tool == ERASER ? new Path() : null;
            if (path != null) path.moveTo(x, y);
        }
    }
}
