package com.enduroplus.companion

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View

/**
 * A custom [View] that renders GPS track polylines for multiple participants
 * on a simple Mercator projection scaled to fit the view.
 *
 * Usage:
 *   mapView.setTracks(mapOf("Alice" to listOf(Pair(55.75, 37.61), …)))
 *
 * Each participant is assigned a colour from [PALETTE]; colours cycle when
 * there are more participants than palette entries.
 * A white circle marks the start and a filled circle in the track colour
 * marks the last recorded position of each participant.
 */
class TrackMapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    // Track data: participant name → ordered list of (lat, lon) pairs
    private var tracks: Map<String, List<Pair<Double, Double>>> = emptyMap()

    companion object {
        /** Colour palette used to distinguish participants. */
        private val PALETTE = intArrayOf(
            Color.parseColor("#E53935"), // red
            Color.parseColor("#1E88E5"), // blue
            Color.parseColor("#43A047"), // green
            Color.parseColor("#FB8C00"), // orange
            Color.parseColor("#8E24AA"), // purple
            Color.parseColor("#00ACC1"), // cyan
        )

        private const val TRACK_STROKE_DP = 4f
        private const val DOT_RADIUS_DP   = 8f
        private const val PADDING_DP      = 40f
    }

    private val density  get() = resources.displayMetrics.density
    private val strokePx get() = TRACK_STROKE_DP * density
    private val dotPx    get() = DOT_RADIUS_DP * density
    private val padPx    get() = PADDING_DP * density

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style      = Paint.Style.STROKE
        strokeCap  = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val bgPaint = Paint().apply {
        color = Color.parseColor("#1A1A2E")
        style = Paint.Style.FILL
    }

    private val noDataPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color     = Color.parseColor("#888888")
        textSize  = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, 14f, resources.displayMetrics)
        textAlign = Paint.Align.CENTER
    }

    /** Replace the displayed tracks and trigger a redraw. */
    fun setTracks(newTracks: Map<String, List<Pair<Double, Double>>>) {
        tracks = newTracks
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Background
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        val allPoints = tracks.values.flatten()
        if (allPoints.isEmpty()) {
            canvas.drawText(
                "No track data yet",
                width / 2f,
                height / 2f,
                noDataPaint,
            )
            return
        }

        val minLat = allPoints.minOf { it.first }
        val maxLat = allPoints.maxOf { it.first }
        val minLon = allPoints.minOf { it.second }
        val maxLon = allPoints.maxOf { it.second }

        val mapW = width  - 2 * padPx
        val mapH = height - 2 * padPx
        val latRange = if (maxLat != minLat) maxLat - minLat else 1.0
        val lonRange = if (maxLon != minLon) maxLon - minLon else 1.0

        // Keep square aspect ratio so tracks aren't distorted
        val scale = minOf(mapW / lonRange, mapH / latRange).toFloat()
        val offsetX = padPx + ((mapW - lonRange * scale) / 2).toFloat()
        val offsetY = padPx + ((mapH - latRange * scale) / 2).toFloat()

        fun project(lat: Double, lon: Double): PointF {
            val x = offsetX + ((lon - minLon) * scale).toFloat()
            val y = offsetY + ((maxLat - lat) * scale).toFloat()
            return PointF(x, y)
        }

        trackPaint.strokeWidth = strokePx

        tracks.entries.forEachIndexed { index, (_, pts) ->
            if (pts.size < 2) return@forEachIndexed
            val color = PALETTE[index % PALETTE.size]
            trackPaint.color = color

            val path = Path()
            pts.forEachIndexed { i, (lat, lon) ->
                val p = project(lat, lon)
                if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            }
            canvas.drawPath(path, trackPaint)

            // Start marker: white dot
            dotPaint.color = Color.WHITE
            val start = project(pts.first().first, pts.first().second)
            canvas.drawCircle(start.x, start.y, dotPx, dotPaint)

            // Last position marker: coloured dot
            dotPaint.color = color
            val end = project(pts.last().first, pts.last().second)
            canvas.drawCircle(end.x, end.y, dotPx, dotPaint)
        }
    }
}
