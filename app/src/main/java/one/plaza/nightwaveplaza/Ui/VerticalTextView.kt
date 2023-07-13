package one.plaza.nightwaveplaza.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView

private const val VERTICAL_ROTATION = 270f

class VerticalTextView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyle: Int = 0) :
    AppCompatTextView(context, attrs, defStyle) {

    private var _measuredWidth: Int = 0
    private var _measuredHeight: Int = 0
    private val _bounds = Rect()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        _measuredHeight = measuredWidth
        _measuredWidth = measuredHeight
        setMeasuredDimension(_measuredWidth, _measuredHeight)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.save()

        canvas.translate(_measuredWidth.toFloat(), _measuredHeight.toFloat())
        canvas.rotate(VERTICAL_ROTATION)

        val textPaint = paint
        textPaint.color = currentTextColor

        val text = getViewText()

        textPaint.getTextBounds(text, 0, text.length, _bounds)
        canvas.drawText(text, compoundPaddingLeft.toFloat(), ((_bounds.height() - _measuredWidth) / 2).toFloat(), textPaint)

        canvas.restore()
    }

    private fun getViewText(): String = super.getText().toString()
}