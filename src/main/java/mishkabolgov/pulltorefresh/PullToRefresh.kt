package mishkabolgov.pulltorefresh

import android.animation.Animator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.support.constraint.ConstraintLayout
import android.support.constraint.ConstraintSet
import android.support.v7.widget.RecyclerView
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import kotlin.math.min

class PullToRefresh(context: Context, attrs: AttributeSet) : ConstraintLayout(context, attrs) {

    private val ROTATING_ANIMATION_DURATION = 1000
    private val MOVING_UP_ANIMATION_DURATION = 1000

    private val DEFAULT_IMAGE_PADDING_DP = 8
    private val DEFAULT_CIRCLE_SIZE_DP = 40
    private val DEFAULT_CIRCLE_OFFSET_DP = 90

    private val DEFAULT_CIRCLE_ELEVATION = 8

    private var recyclerView: RecyclerView? = null

    private lateinit var circle: ImageView

    private var circleOrigin = 0f
    private var state: State = State.REST
    private var fingerDownY = 0f
    private var lastTouchAction: Int = MotionEvent.ACTION_UP

    var circleImageResource = R.drawable.update_arrows
        set(value) {
            field = value
            circle.setImageResource(value)
        }

    var circleBackgroundResource = R.drawable.refresh_circle_background

    var action: (finishListener: FinishListener) -> Unit = {}

    init {
        addCircleView()
    }

    private fun findNestedRecyclerView(): RecyclerView? {
        for (childIndex in 0..childCount) {
            val child = getChildAt(childIndex)
            if (child is RecyclerView)
                return child
        }

        return null
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)

        if (recyclerView == null) {
            recyclerView = findNestedRecyclerView()
            setRecyclerTouchListener()
            circle.elevation = recyclerView!!.elevation + dpToPx(DEFAULT_CIRCLE_ELEVATION, context)
        }


    }

    private fun setRecyclerTouchListener() {
        recyclerView!!.setOnTouchListener { v, event ->
            println("mylog 1")
            if (!isRecyclerViewScrolledToTop()) {
                return@setOnTouchListener recyclerView!!.onTouchEvent(event)
            }

            println("mylog 2")
            if (state == State.ROTATING) {
                return@setOnTouchListener recyclerView!!.onTouchEvent(event)
            }

            println("mylog 3")
            when (event.action) {
                MotionEvent.ACTION_MOVE -> {
                    if (lastTouchAction == MotionEvent.ACTION_UP) {
                        onFingerDown(event.y)
                    }
                    if (isScrollDirectionUp(event.y)) {
                        if (state == State.MOVE_DOWN) {
                            state = State.MOVE_UP
                            startCircleMoveUpAnimation()
                        }
                    } else {

                        onFingerMove(event.y)
                    }
                    lastTouchAction = MotionEvent.ACTION_MOVE
                    return@setOnTouchListener true
                }

                MotionEvent.ACTION_UP -> {
                    onFingerUp(event.y)
                    lastTouchAction = MotionEvent.ACTION_UP
                }
            }
            recyclerView!!.onTouchEvent(event)
        }
    }

    private fun isScrollDirectionUp(y: Float): Boolean {
        val fromFingerToFingerDown = fingerDownY + y - fingerDownY
        return fromFingerToFingerDown < fingerDownY
    }

    private fun passTouchEventToRecyclerView(event: MotionEvent) {
        recyclerView?.onTouchEvent(event)
    }

    private fun onFingerDown(y: Float) {
        when (state) {
            State.REST -> {
                fingerDownY = y
                state = State.MOVE_DOWN
                circle.elevation = DEFAULT_CIRCLE_ELEVATION.toFloat() //todo: вынести в отдельную функцию
            }
            State.MOVE_DOWN -> {

            }
            State.ROTATING -> {

            }
            State.MOVE_UP -> {

            }
        }
    }

    private fun onFingerMove(y: Float) {

        when (state) {
            State.REST -> {

            }

            State.MOVE_DOWN -> {
                val nextCircleY = min(y - fingerDownY, dpToPx(DEFAULT_CIRCLE_OFFSET_DP, context).toFloat())

                if (circleOrigin + nextCircleY < circleOrigin)
                    return

                circle.y = circleOrigin + nextCircleY

                if (circle.y >= circleOrigin + dpToPx(DEFAULT_CIRCLE_OFFSET_DP, context).toFloat()) {
                    state = State.ROTATING

                    action(object : FinishListener {
                        override fun onFinish() {
                            (context as Activity).runOnUiThread {
                                rotationAnimation?.cancel()
                            }
                        }
                    })

                    startRotation()
                }
            }
            State.ROTATING -> {

            }
            State.MOVE_UP -> {

            }
        }

    }

    var rotationAnimation: ValueAnimator? = null

    private fun startRotation() {

        rotationAnimation = ValueAnimator.ofFloat(0f, 180f)

        rotationAnimation?.apply {
            repeatCount = ValueAnimator.INFINITE
            duration = 500

            addUpdateListener {
                circle.rotation = it.animatedValue as Float
            }

            addListener(object : Animator.AnimatorListener {
                override fun onAnimationRepeat(animation: Animator?) {
                    circleImageTintColorList?.tintColor?.let {
                        circle.drawable.setTint(it)
                    }
                }

                override fun onAnimationEnd(animation: Animator?) {
                    state = State.MOVE_UP
                    startCircleMoveUpAnimation()
                }

                override fun onAnimationCancel(animation: Animator?) {
                }

                override fun onAnimationStart(animation: Animator?) {
                }
            })

        }

        rotationAnimation?.start()
    }

    fun stop() {
        rotationAnimation?.cancel()
        rotationAnimation = null
    }

    private fun startCircleMoveUpAnimation() {
        circle.animate().y(circleOrigin).setDuration(500).setListener(object : Animator.AnimatorListener {
            override fun onAnimationRepeat(animation: Animator?) {
            }

            override fun onAnimationEnd(animation: Animator?) {
                state = State.REST
                lastTouchAction = MotionEvent.ACTION_UP
                circle.elevation = 0f //Элевэйшн устанавливается в ноль, для того чтобы тень от кружка не была видна в неактивном состоянии
                //todo: вынести в отдельную функцию
            }

            override fun onAnimationCancel(animation: Animator?) {
            }

            override fun onAnimationStart(animation: Animator?) {
            }
        }).start()
    }

    private fun onFingerUp(y: Float) {

        when (state) {
            State.REST -> {
            }
            State.MOVE_DOWN -> {
                state = State.MOVE_UP
                startCircleMoveUpAnimation()
            }
            State.ROTATING -> {

            }
            State.MOVE_UP -> {

            }
        }

    }

    private fun addCircleView() {
        val circle = ImageView(context)
        circle.id = View.generateViewId()
        circle.setBackgroundResource(circleBackgroundResource)
        circle.setImageResource(circleImageResource)

        val imagePadding = dpToPx(DEFAULT_IMAGE_PADDING_DP, context)
        circle.setPadding(imagePadding, imagePadding, imagePadding, imagePadding)
        val circleSize = dpToPx(DEFAULT_CIRCLE_SIZE_DP, context)
        val layoutParams = ConstraintLayout.LayoutParams(circleSize, circleSize)
        circle.layoutParams = layoutParams
        addView(circle)

        this.circle = circle

        circle.addOnLayoutChangeListener(object : OnLayoutChangeListener {
            override fun onLayoutChange(v: View?, left: Int, top: Int, right: Int, bottom: Int, oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int) {
                circleOrigin = circle.y
                circle.removeOnLayoutChangeListener(this)
            }
        })

        val constraintSet = ConstraintSet()
        constraintSet.clone(this)
        constraintSet.connect(circle.id, ConstraintSet.END, id, ConstraintSet.END)
        constraintSet.connect(circle.id, ConstraintSet.START, id, ConstraintSet.START)
        constraintSet.connect(circle.id, ConstraintSet.BOTTOM, id, ConstraintSet.TOP, 8)
        constraintSet.applyTo(this)

    }

    private fun isRecyclerViewScrolledToTop() = recyclerView?.computeVerticalScrollOffset() == 0

    private enum class State {
        REST, MOVE_DOWN, ROTATING, MOVE_UP
    }

    class CircleTintColorList(val tintColorList: List<Int>) {
        private var currentColorIndex = 0
        val tintColor: Int
            get() {
                val currentColor = tintColorList[currentColorIndex]

                ++currentColorIndex
                currentColorIndex %= tintColorList.size

                return currentColor
            }
    }

    private var circleImageTintColorList: CircleTintColorList? = null
        set(value) {
            field = value

            val color = value?.tintColor ?: return
            circle.drawable.setTint(color)

        }

    fun setCircleImageTint(list: List<Int>?) {
        if (list == null) {
            circleImageTintColorList = null
            return
        }

        if (list.isEmpty()) {
            circleImageTintColorList = null
            return
        }
        circleImageTintColorList = CircleTintColorList(list)
    }

    interface FinishListener {
        fun onFinish()
    }

    private fun dpToPx(dp: Int, context: Context): Int {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp.toFloat(),
                context.resources.displayMetrics

        ).toInt()
    }

}
