package me.magnum.melonds.ui.layouteditor

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import me.magnum.melonds.domain.model.*
import me.magnum.melonds.ui.common.LayoutComponentView
import me.magnum.melonds.ui.common.LayoutView
import kotlin.math.*

class LayoutEditorView(context: Context, attrs: AttributeSet?) : LayoutView(context, attrs) {
    enum class Anchor {
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT
    }

    private var onViewSelectedListener: ((LayoutComponentView, Float) -> Unit)? = null
    private var onViewDeselectedListener: ((LayoutComponentView) -> Unit)? = null
    private var otherClickListener: OnClickListener? = null
    private val defaultComponentWidth by lazy { screenUnitsConverter.dpToPixels(100f).toInt() }
    private val minComponentSize by lazy { screenUnitsConverter.dpToPixels(30f).toInt() }
    private var selectedView: LayoutComponentView? = null
    private var selectedViewAnchor = Anchor.TOP_LEFT

    init {
        super.setOnClickListener {
            if (selectedView != null) {
                deselectCurrentView()
            } else {
                otherClickListener?.onClick(it)
            }
        }
    }

    fun setOnViewSelectedListener(listener: (LayoutComponentView, Float) -> Unit) {
        onViewSelectedListener = listener
    }

    fun setOnViewDeselectedListener(listener: (LayoutComponentView) -> Unit) {
        onViewDeselectedListener = listener
    }

    fun addLayoutComponent(component: LayoutComponent) {
        val componentBuilder = viewBuilderFactory.getLayoutComponentViewBuilder(component)
        val componentHeight = defaultComponentWidth / componentBuilder.getAspectRatio()
        val componentView = addPositionedLayoutComponent(PositionedLayoutComponent(Rect(0, 0, defaultComponentWidth, componentHeight.toInt()), component))
        views[component] = componentView
    }

    fun buildCurrentLayout(): UILayout {
        return UILayout(views.values.map { PositionedLayoutComponent(it.getRect(), it.component) })
    }

    override fun setOnClickListener(l: OnClickListener?) {
        otherClickListener = l
    }

    override fun onLayoutComponentViewAdded(layoutComponentView: LayoutComponentView) {
        super.onLayoutComponentViewAdded(layoutComponentView)
        setupDragHandler(layoutComponentView)
        layoutComponentView.view.alpha = 0.5f
    }

    private fun setupDragHandler(layoutComponentView: LayoutComponentView) {
        layoutComponentView.view.setOnTouchListener(object : OnTouchListener {
            private var dragging = false

            private var downOffsetX = -1f
            private var downOffsetY = -1f

            override fun onTouch(view: View?, motionEvent: MotionEvent?): Boolean {
                if (view == null)
                    return false

                if (selectedView != null) {
                    deselectCurrentView()
                }

                return when (motionEvent?.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downOffsetX = motionEvent.x
                        downOffsetY = motionEvent.y
                        view.alpha = 1f
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (!dragging) {
                            val distance = sqrt((motionEvent.x - downOffsetX).pow(2f) + (motionEvent.y - downOffsetY).pow(2f))
                            if (distance >= 25) {
                                dragging = true
                            }
                        } else {
                            dragView(view, motionEvent.x - downOffsetX, motionEvent.y - downOffsetY)
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!dragging) {
                            selectView(layoutComponentView)
                        } else {
                            view.alpha = 0.5f
                            dragging = false
                        }
                        true
                    }
                    else -> false
                }
            }
        })
    }

    private fun selectView(view: LayoutComponentView) {
        val anchorDistances = mutableMapOf<Anchor, Double>()
        anchorDistances[Anchor.TOP_LEFT] = view.getPosition().x.toDouble().pow(2) + view.getPosition().y.toDouble().pow(2)
        anchorDistances[Anchor.TOP_RIGHT] = (width - (view.getPosition().x + view.getWidth())).toDouble().pow(2) + view.getPosition().y.toDouble().pow(2)
        anchorDistances[Anchor.BOTTOM_LEFT] = view.getPosition().x.toDouble().pow(2) + (height - (view.getPosition().y + view.getHeight())).toDouble().pow(2)
        anchorDistances[Anchor.BOTTOM_RIGHT] = (width - (view.getPosition().x + view.getWidth())).toDouble().pow(2) + (height - (view.getPosition().y + view.getHeight())).toDouble().pow(2)

        var anchor = Anchor.TOP_LEFT
        var minDistance = Double.MAX_VALUE
        anchorDistances.keys.forEach {
            if (anchorDistances[it]!! < minDistance) {
                minDistance = anchorDistances[it]!!
                anchor = it
            }
        }

        selectedViewAnchor = anchor
        selectedView = view

        val layoutAspectRatio = width / height.toFloat()
        val selectedViewAspectRatio = view.aspectRatio
        val currentConstrainedDimension: Int
        val maxDimension: Int

        if (layoutAspectRatio > selectedViewAspectRatio) {
            maxDimension = height
            currentConstrainedDimension = view.getHeight()
        } else {
            maxDimension = width
            currentConstrainedDimension = view.getWidth()
        }

        val viewScale = (currentConstrainedDimension - minComponentSize) / (maxDimension - minComponentSize).toFloat()
        onViewSelectedListener?.invoke(view, viewScale)
    }

    private fun deselectCurrentView() {
        selectedView?.let {
            it.view.alpha = 0.5f
            onViewDeselectedListener?.invoke(it)
        }
        selectedView = null
    }

    fun deleteSelectedView() {
        val currentlySelectedView = selectedView ?: return
        deselectCurrentView()
        removeView(currentlySelectedView.view)
        views.remove(currentlySelectedView.component)
    }

    private fun dragView(view: View, offsetX: Float, offsetY: Float) {
        val finalX = min(max(view.x + offsetX, 0f), width - view.width.toFloat())
        val finalY = min(max(view.y + offsetY, 0f), height - view.height.toFloat())
        val newParams = LayoutParams(view.width, view.height).apply {
            leftMargin = finalX.toInt()
            topMargin = finalY.toInt()
        }
        view.layoutParams = newParams
    }

    fun scaleSelectedView(newScale: Float) {
        val currentlySelectedView = selectedView ?: return

        val screenAspectRatio = width / height.toFloat()
        val selectedViewAspectRatio = currentlySelectedView.aspectRatio
        val newViewWidth: Int
        val newViewHeight: Int

        if (screenAspectRatio > selectedViewAspectRatio) {
            // The scale range must go from minComponentSize to height
            val scaledHeight = ((height - minComponentSize) * newScale + minComponentSize).roundToInt()
            newViewWidth = (scaledHeight * selectedViewAspectRatio).toInt()
            newViewHeight = scaledHeight
        } else {
            // The scale range must go from minComponentSize to width
            val scaledWidth = ((width - minComponentSize) * newScale + minComponentSize).roundToInt()
            newViewWidth = scaledWidth
            newViewHeight = (scaledWidth / selectedViewAspectRatio).toInt()
        }

        val viewPosition = currentlySelectedView.getPosition()
        var viewX: Int
        var viewY: Int

        if (selectedViewAnchor == Anchor.TOP_LEFT) {
            viewX = viewPosition.x
            viewY = viewPosition.y
            if (viewX + newViewWidth > width) {
                viewX = width - newViewWidth
            }
            if (viewY + newViewHeight > height) {
                viewY = height - newViewHeight
            }
        } else if (selectedViewAnchor == Anchor.TOP_RIGHT) {
            viewX = viewPosition.x + currentlySelectedView.getWidth() - newViewWidth
            viewY = viewPosition.y
            if (viewX < 0) {
                viewX = 0
            }
            if (viewY + newViewHeight > height) {
                viewY = height - newViewHeight
            }
        } else if (selectedViewAnchor == Anchor.BOTTOM_LEFT) {
            viewX = viewPosition.x
            viewY = viewPosition.y + currentlySelectedView.getHeight() - newViewHeight
            if (viewX + newViewWidth > width) {
                viewX = width - newViewWidth
            }
            if (viewY < 0) {
                viewY = 0
            }
        } else {
            viewX = viewPosition.x + currentlySelectedView.getWidth() - newViewWidth
            viewY = viewPosition.y + currentlySelectedView.getHeight() - newViewHeight
            if (viewX < 0) {
                viewX = 0
            }
            if (viewY < 0) {
                viewY = 0
            }
        }
        currentlySelectedView.setPositionAndSize(Point(viewX, viewY), newViewWidth, newViewHeight)
    }
}