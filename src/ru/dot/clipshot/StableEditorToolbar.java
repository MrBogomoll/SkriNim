package ru.dot.clipshot;

import android.content.Context;
import android.view.View;
import android.widget.ScrollView;

/** A single card. Only windows too short for the four rows scroll internally. */
final class StableEditorToolbar extends ScrollView {
    private final int preferred, canvasReserve;

    StableEditorToolbar(Context context) {
        super(context);
        float density=getResources().getDisplayMetrics().density;
        preferred=EditorToolbarMetrics.preferred(density);
        canvasReserve=Math.round(EditorToolbarMetrics.CANVAS_RESERVE_DP*density);
        setFillViewport(false);
        setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        setVerticalScrollBarEnabled(true);
    }

    @Override protected void onMeasure(int widthSpec,int heightSpec) {
        int mode=MeasureSpec.getMode(heightSpec);
        int height=mode==MeasureSpec.UNSPECIFIED?preferred:
                EditorToolbarMetrics.viewport(MeasureSpec.getSize(heightSpec),preferred,canvasReserve);
        super.onMeasure(widthSpec,MeasureSpec.makeMeasureSpec(height,MeasureSpec.EXACTLY));
    }
}
