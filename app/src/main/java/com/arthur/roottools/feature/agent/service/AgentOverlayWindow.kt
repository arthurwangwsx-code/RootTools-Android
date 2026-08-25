package com.arthur.roottools.feature.agent.service

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.arthur.roottools.R
import com.arthur.roottools.core.agent.AgentOverlayMode
import com.arthur.roottools.core.agent.AgentSessionState
import com.arthur.roottools.core.agent.AgentSessionStatus

internal class AgentOverlayWindow(
    private val context: Context,
    private val onToggle: () -> Unit,
    private val onPauseResume: () -> Unit,
    private val onHide: () -> Unit,
    private val onStop: () -> Unit,
    private val onOpenDetails: () -> Unit,
) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var root: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var currentState: AgentSessionState = AgentSessionState()
    private var previewBytes: ByteArray? = null

    fun render(state: AgentSessionState) {
        currentState = state
        if (!Settings.canDrawOverlays(context) || state.overlayMode == AgentOverlayMode.HIDDEN || !state.active) {
            remove()
            return
        }
        val desiredExpanded = state.overlayMode == AgentOverlayMode.EXPANDED
        val currentExpanded = root?.tag == TAG_EXPANDED
        if (root == null || desiredExpanded != currentExpanded) {
            rebuild(desiredExpanded)
        } else {
            updateContent(root ?: return, state)
        }
    }

    fun updatePreview(bytes: ByteArray?) {
        previewBytes = bytes
        val image = root?.findViewById<ImageView>(PREVIEW_ID) ?: return
        val bitmap = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        image.setImageBitmap(bitmap)
        image.visibility = if (bitmap != null) View.VISIBLE else View.GONE
    }

    fun remove() {
        root?.let { view -> runCatching { windowManager.removeView(view) } }
        root = null
        params = null
    }

    private fun rebuild(expanded: Boolean) {
        val previous = params
        remove()
        val view = if (expanded) buildExpanded() else buildCollapsed()
        view.tag = if (expanded) TAG_EXPANDED else TAG_COLLAPSED
        val widthPx = dp(if (expanded) 320 else 58)
        val screenWidth = context.resources.displayMetrics.widthPixels
        val screenHeight = context.resources.displayMetrics.heightPixels
        val margin = dp(16)
        val layout = WindowManager.LayoutParams(
            widthPx,
            if (expanded) WindowManager.LayoutParams.WRAP_CONTENT else dp(58),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val maxX = (screenWidth - widthPx - margin).coerceAtLeast(margin)
            x = when {
                previous == null -> maxX
                !expanded && previous.width > 0 -> {
                    val previousCenter = previous.x + previous.width / 2
                    if (previousCenter < screenWidth / 2) margin else maxX
                }
                else -> previous.x.coerceIn(margin, maxX)
            }
            y = (previous?.y ?: dp(180)).coerceIn(margin, (screenHeight - dp(260)).coerceAtLeast(margin))
        }
        params = layout
        root = view
        attachDrag(view, layout)
        windowManager.addView(view, layout)
        updateContent(view, currentState)
        updatePreview(previewBytes)
    }

    private fun buildCollapsed(): View {
        val frame = FrameLayout(context).apply {
            background = roundedBackground(COLLAPSED_BG, 29f)
            elevation = dp(10).toFloat()
            setPadding(dp(6), dp(6), dp(6), dp(6))
            setOnClickListener { onToggle() }
        }
        val label = TextView(context).apply {
            id = TITLE_ID
            text = context.getString(R.string.agent_overlay_short_label)
            setTextColor(Color.WHITE)
            textSize = 15f
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        frame.addView(label, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        return frame
    }

    private fun buildExpanded(): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = roundedBackground(EXPANDED_BG, 20f)
            elevation = dp(14).toFloat()
        }

        val title = TextView(context).apply {
            id = TITLE_ID
            setTextColor(Color.WHITE)
            textSize = 17f
            maxLines = 1
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        root.addView(title, matchWidth())

        val step = TextView(context).apply {
            id = STEP_ID
            setTextColor(Color.rgb(210, 218, 225))
            textSize = 13f
            maxLines = 2
            setPadding(0, dp(4), 0, dp(8))
        }
        root.addView(step, matchWidth())

        val preview = ImageView(context).apply {
            id = PREVIEW_ID
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = roundedBackground(Color.rgb(35, 42, 49), 12f)
            visibility = View.GONE
        }
        root.addView(preview, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(126)))

        val actions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, 0)
        }
        val pause = smallButton(PAUSE_ID, context.getString(R.string.agent_action_pause)) { onPauseResume() }
        val hide = smallButton(HIDE_ID, context.getString(R.string.agent_action_hide)) { onHide() }
        val details = smallButton(DETAILS_ID, context.getString(R.string.agent_action_details)) { onOpenDetails() }
        val stop = smallButton(STOP_ID, context.getString(R.string.agent_action_stop)) { onStop() }
        listOf(pause, hide, details, stop).forEach { button ->
            actions.addView(button, LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(5) })
        }
        root.addView(actions, matchWidth())
        return root
    }

    private fun updateContent(view: View, state: AgentSessionState) {
        view.findViewById<TextView>(TITLE_ID)?.text = if (state.overlayMode == AgentOverlayMode.EXPANDED) {
            state.title.ifBlank { context.getString(R.string.agent_session_title) }
        } else {
            state.targetLabel?.take(1)?.uppercase()?.takeIf(String::isNotBlank)
                ?: context.getString(R.string.agent_overlay_short_label)
        }
        view.findViewById<TextView>(STEP_ID)?.text = state.currentStep.ifBlank {
            context.getString(R.string.agent_session_idle_step)
        }
        view.findViewById<Button>(PAUSE_ID)?.text = context.getString(
            if (state.status == AgentSessionStatus.PAUSED) R.string.agent_action_resume else R.string.agent_action_pause,
        )
    }

    private fun attachDrag(view: View, layout: WindowManager.LayoutParams) {
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = layout.x
                    startY = layout.y
                    moved = false
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downX).toInt()
                    val dy = (event.rawY - downY).toInt()
                    if (kotlin.math.abs(dx) > dp(4) || kotlin.math.abs(dy) > dp(4)) moved = true
                    if (moved) {
                        layout.x = (startX + dx).coerceAtLeast(0)
                        layout.y = (startY + dy).coerceAtLeast(0)
                        runCatching { windowManager.updateViewLayout(view, layout) }
                        true
                    } else {
                        false
                    }
                }
                else -> false
            }
        }
    }

    private fun smallButton(idValue: Int, label: String, action: () -> Unit) = Button(context).apply {
        id = idValue
        text = label
        textSize = 11f
        isAllCaps = false
        setPadding(dp(2), 0, dp(2), 0)
        setOnClickListener { action() }
    }

    private fun roundedBackground(color: Int, radiusDp: Float) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = dp(radiusDp.toInt()).toFloat()
    }

    private fun matchWidth() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    private companion object {
        const val TAG_COLLAPSED = "collapsed"
        const val TAG_EXPANDED = "expanded"
        const val TITLE_ID = 0x6a0101
        const val STEP_ID = 0x6a0102
        const val PREVIEW_ID = 0x6a0103
        const val PAUSE_ID = 0x6a0104
        const val HIDE_ID = 0x6a0105
        const val DETAILS_ID = 0x6a0106
        const val STOP_ID = 0x6a0107
        const val COLLAPSED_BG = 0xE6202A31.toInt()
        const val EXPANDED_BG = 0xF21B232A.toInt()
    }
}
