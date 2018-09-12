package io.o2oa.signatureview

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View


/**
 * Created by fancyLou on 2018/7/16.
 */


class SignatureView : View {

    val MIN_PEN_SIZE = 1f
    val MAX_VELOCITY_BOUND = 15f
    private val MIN_INCREMENT = 0.01f
    private val INCREMENT_CONSTANT = 0.0005f
    private val DRAWING_CONSTANT = 0.0085f
    private val MIN_VELOCITY_BOUND = 1.6f
    private val STROKE_DES_VELOCITY = 1.0f
    private val VELOCITY_FILTER_WEIGHT = 0.2f

    private lateinit var canvasSV: Canvas
    private lateinit var paintBitmap: Paint
    private lateinit var paintSV: Paint
    private var bitmap: Bitmap? = null
    private var ignoreTouch = false
    private var previousPoint: SVPoint? = null
    private var startPoint: SVPoint? = null
    private var currentPoint: SVPoint? = null
    private var lastVelocity: Float = 0f
    private var lastWidth: Float = 0f
    private var layoutLeft: Int = 0
    private var layoutTop: Int = 0
    private var layoutRight: Int = 0
    private var layoutBottom: Int = 0
    private lateinit var drawViewRect: Rect

    private var penColor = Color.BLACK
    private var svBackgroundColor = Color.WHITE
    private var enableSignature = true
    private var penSize: Float = MIN_PEN_SIZE


    constructor(context: Context) : super(context)

    constructor(context: Context, attributeSet: AttributeSet) : super(context, attributeSet) {
        this.setWillNotDraw(false)
        this.isDrawingCacheEnabled = true
        val array = context.obtainStyledAttributes(attributeSet, R.styleable.SignatureView, 0, 0)
        try {
            penColor = array.getColor(R.styleable.SignatureView_penColor, Color.BLACK)
            svBackgroundColor = array.getColor(R.styleable.SignatureView_svBackgroundColor, Color.WHITE)
            enableSignature = array.getBoolean(R.styleable.SignatureView_enableSignature, true)
            penSize = array.getDimension(R.styleable.SignatureView_penSize, MIN_PEN_SIZE)
        } finally {
            array.recycle()
        }
        paintSV = Paint(Paint.ANTI_ALIAS_FLAG)
        paintSV.color = penColor
        paintSV.isAntiAlias = true
        paintSV.style = Paint.Style.FILL_AND_STROKE
        paintSV.strokeJoin = Paint.Join.ROUND
        paintSV.strokeCap = Paint.Cap.ROUND
        paintSV.strokeWidth = penSize

        paintBitmap = Paint(Paint.ANTI_ALIAS_FLAG)
        paintBitmap.isAntiAlias = true
        paintBitmap.style = Paint.Style.STROKE
        paintBitmap.strokeJoin = Paint.Join.ROUND
        paintBitmap.strokeCap = Paint.Cap.ROUND
        paintBitmap.color = Color.BLACK

    }

    /************** public ***************/

    fun clear() {
        previousPoint = null
        startPoint = null
        currentPoint = null
        lastVelocity = 0f
        lastWidth = 0f

        newBitmapCanvas(layoutLeft, layoutTop, layoutRight, layoutBottom)
        postInvalidate()
    }

    fun getSignatureBitmap(): Bitmap? {
        return if (bitmap != null) {
            Bitmap.createScaledBitmap(bitmap, bitmap!!.width, bitmap!!.height, true)
        } else {
            null
        }
    }

    fun setSignatureBitmap(signBt: Bitmap) {
        bitmap = signBt
        canvasSV = Canvas(bitmap)
        postInvalidate()
    }


    /*********** override ***********/

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        layoutLeft = left
        layoutTop = top
        layoutRight = right
        layoutBottom = bottom
        if (bitmap == null) {
            newBitmapCanvas(layoutLeft, layoutTop, layoutRight, layoutBottom)
        }
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (!enableSignature) {
            return false
        }
        if (event == null || event.pointerCount > 1) {
            return false
        }


        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                ignoreTouch = false
                drawViewRect = Rect(this.left, this.top, this.right,
                        this.bottom)
                onTouchDownEvent(event.x, event.y)
            }
            MotionEvent.ACTION_MOVE -> if (!drawViewRect.contains(left + event.x.toInt(),
                            this.top + event.y.toInt())) {
                //You are out of drawing area
                if (!ignoreTouch) {
                    ignoreTouch = true
                    onTouchUpEvent(event.x, event.y)
                }
            } else {
                //You are in the drawing area
                if (ignoreTouch) {
                    ignoreTouch = false
                    onTouchDownEvent(event.x, event.y)
                } else {
                    onTouchMoveEvent(event.x, event.y)
                }
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> onTouchUpEvent(event.x, event.y)
            else -> {
            }
        }

        return true
    }

    private fun onTouchMoveEvent(x: Float, y: Float) {
        if (previousPoint == null) {
            return
        }
        startPoint = previousPoint
        previousPoint = currentPoint
        currentPoint = SVPoint(x, y, System.currentTimeMillis())

        var velocity = currentPoint!!.velocityFrom(previousPoint!!)
        velocity = VELOCITY_FILTER_WEIGHT * velocity + (1 - VELOCITY_FILTER_WEIGHT) * lastVelocity

        val strokeWidth = getStrokeWidth(velocity)
        drawLine(lastWidth, strokeWidth, velocity)

        lastVelocity = velocity
        lastWidth = strokeWidth

        postInvalidate()
    }

    private fun getStrokeWidth(velocity: Float): Float = penSize - (velocity * STROKE_DES_VELOCITY)

    private fun onTouchUpEvent(x: Float, y: Float) {
        if (previousPoint == null) {
            return
        }
        startPoint = previousPoint
        previousPoint = currentPoint
        currentPoint = SVPoint(x, y, System.currentTimeMillis())

        drawLine(lastWidth, 0f, lastVelocity)
        postInvalidate()

    }

    private fun drawLine(lastWidth: Float, currentWidth: Float, velocity: Float) {
        val mid1 = midPoint(previousPoint!!, startPoint!!)
        val mid2 = midPoint(currentPoint!!, previousPoint!!)

        draw(mid1, previousPoint!!, mid2, lastWidth,
                currentWidth, velocity)
    }

    private fun draw(p0: SVPoint, p1:SVPoint, p2: SVPoint, lastWidth: Float, currentWidth: Float, velocity: Float) {
        var xa: Float
        var xb: Float
        var ya: Float
        var yb: Float
        var x: Float
        var y: Float
        val increment: Float = if (velocity > MIN_VELOCITY_BOUND && velocity < MAX_VELOCITY_BOUND) {
            DRAWING_CONSTANT - velocity * INCREMENT_CONSTANT
        } else {
            MIN_INCREMENT
        }

        var i = 0f
        while (i < 1f) {
            xa = getPt(p0.x, p1.x, i)
            ya = getPt(p0.y, p1.y, i)
            xb = getPt(p1.x, p2.x, i)
            yb = getPt(p1.y, p2.y, i)

            x = getPt(xa, xb, i)
            y = getPt(ya, yb, i)

            val strokeVal = lastWidth + (currentWidth - lastWidth) * i
            paintSV.strokeWidth = if (strokeVal < MIN_PEN_SIZE) MIN_PEN_SIZE else strokeVal
            canvasSV.drawPoint(x, y, paintSV)
            i += increment
        }
    }

    private fun getPt(n1: Float, n2: Float, perc: Float): Float {
        val diff = n2 - n1
        return n1 + (diff * perc)
    }

    private fun midPoint(p1: SVPoint, p2: SVPoint): SVPoint {
        return SVPoint((p1.x + p2.x) / 2.0f, (p1.y + p2.y) / 2, (p1.time + p2.time) / 2)
    }

    private fun onTouchDownEvent(x: Float, y: Float) {
        previousPoint = null
        startPoint = null
        currentPoint = null
        lastVelocity = 0f
        lastWidth = penSize

        currentPoint = SVPoint(x, y, System.currentTimeMillis())
        previousPoint = currentPoint
        startPoint = previousPoint
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas?) {
        canvas?.drawBitmap(bitmap, 0f, 0f, paintBitmap)
    }

    private fun newBitmapCanvas(left: Int, top: Int, right: Int, bottom: Int) {
        bitmap = null
        if ((right - left) > 0 && (bottom - top) > 0) {
            bitmap = Bitmap.createBitmap(right - left, bottom - top, Bitmap.Config.ARGB_8888)
            canvasSV = Canvas(bitmap)
            canvasSV.drawColor(svBackgroundColor)
        }
    }


}