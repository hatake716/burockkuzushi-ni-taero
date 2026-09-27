package io.github.hatake716.taero

import android.content.Context
import android.graphics.Canvas
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.TypedValue
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/** Visual counters stay current; only publish() emits a polite live update. */
internal class LiveStatusView(context: Context) : View(context) {
    private val paint=TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color=0xffb1bdd5.toInt();typeface=resources.getFont(R.font.dot_gothic)
        textSize=TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,14f,resources.displayMetrics)
    }
    private var value=""
    private var layout: StaticLayout?=null
    init { importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_YES;accessibilityLiveRegion=ACCESSIBILITY_LIVE_REGION_POLITE;isFocusable=true }
    fun update(text: String) {
        if(value==text) return
        value=text
        val oldHeight=layout?.height
        rebuild(width.coerceAtLeast(1))
        if(layout?.height!=oldHeight) requestLayout()
        invalidate()
    }
    private fun rebuild(width: Int) {
        layout=StaticLayout.Builder.obtain(value,0,value.length,paint,width.coerceAtLeast(1))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(true).build()
    }
    override fun onMeasure(widthMeasureSpec: Int,heightMeasureSpec: Int) {
        val width=MeasureSpec.getSize(widthMeasureSpec);rebuild(width)
        setMeasuredDimension(width,resolveSize(layout!!.height,heightMeasureSpec))
    }
    override fun onDraw(canvas: Canvas) { super.onDraw(canvas);layout?.draw(canvas) }
    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info);info.className="android.widget.TextView";info.text=value
    }
    @Suppress("DEPRECATION")
    fun publish() {
        val event=AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
        event.contentChangeTypes=AccessibilityEvent.CONTENT_CHANGE_TYPE_TEXT
        sendAccessibilityEventUnchecked(event)
    }
}
