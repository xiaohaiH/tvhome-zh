package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.widget.FrameLayout;

/** Clips app artwork without ViewOutlineProvider (API 21); hardware clipPath works from API 18. */
public final class RoundedCardLayout extends FrameLayout {
    private final Path outline = new Path();
    private final RectF bounds = new RectF();

    public RoundedCardLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        bounds.set(0, 0, w, h);
        float radius = AppAdapter.cornerRadius(w, getResources().getDisplayMetrics().density);
        outline.reset();
        outline.addRoundRect(bounds, radius, radius, Path.Direction.CW);
    }

    @Override
    public void draw(Canvas canvas) {
        int save = canvas.save();
        canvas.clipPath(outline);
        super.draw(canvas);
        canvas.restoreToCount(save);
    }
}
