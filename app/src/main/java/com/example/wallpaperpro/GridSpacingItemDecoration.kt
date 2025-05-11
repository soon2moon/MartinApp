package com.example.wallpaperpro

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

class GridSpacingItemDecoration(
    private val spanCount: Int,
    private val spacingPx: Int, // Abstand in Pixeln
    private val includeEdge: Boolean // Ob auch an den äußeren Rändern Abstand sein soll (zusätzlich zum RecyclerView-Padding)
) : RecyclerView.ItemDecoration() {

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        val position = parent.getChildAdapterPosition(view) // Item position
        if (position == RecyclerView.NO_POSITION) {
            return
        }
        val column = position % spanCount // Item column

        if (includeEdge) {
            outRect.left = spacingPx - column * spacingPx / spanCount
            outRect.right = (column + 1) * spacingPx / spanCount
            if (position < spanCount) { // Top edge
                outRect.top = spacingPx
            }
            outRect.bottom = spacingPx // Item bottom
        } else {
            outRect.left = column * spacingPx / spanCount
            outRect.right = spacingPx - (column + 1) * spacingPx / spanCount
            if (position >= spanCount) {
                outRect.top = spacingPx // Item top
            }
        }
    }
}