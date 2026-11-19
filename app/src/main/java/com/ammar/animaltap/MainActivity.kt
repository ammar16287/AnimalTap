package com.ammar.animaltap

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.animation.AccelerateInterpolator
import android.view.animation.BounceInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** The kinds of "happy" animation an animal can do when tapped. */
enum class Move { JUMP, WIGGLE, PULSE, SPIN, SHAKE, HOP }

data class Animal(
    val name: String,
    val emoji: String,
    val says: String,
    val color: Int,
    val soundRes: Int,
    val move: Move,
)

class MainActivity : Activity() {

    private val animals = listOf(
        Animal("Cow", "🐄", "Moo!", 0xFFFFE5B4.toInt(), R.raw.cow, Move.JUMP),
        Animal("Dog", "🐶", "Woof!", 0xFFFFD6A5.toInt(), R.raw.dog, Move.HOP),
        Animal("Cat", "🐱", "Meow!", 0xFFFFC8DD.toInt(), R.raw.cat, Move.WIGGLE),
        Animal("Duck", "🦆", "Quack!", 0xFFFDFFB6.toInt(), R.raw.duck, Move.SHAKE),
        Animal("Sheep", "🐑", "Baa!", 0xFFE4F1EE.toInt(), R.raw.sheep, Move.PULSE),
        Animal("Pig", "🐷", "Oink!", 0xFFFFB5C2.toInt(), R.raw.pig, Move.HOP),
        Animal("Lion", "🦁", "Roar!", 0xFFFFD166.toInt(), R.raw.lion, Move.PULSE),
        Animal("Bird", "🐦", "Tweet!", 0xFFBDE0FE.toInt(), R.raw.bird, Move.SPIN),
        Animal("Frog", "🐸", "Ribbit!", 0xFFCAFFBF.toInt(), R.raw.frog, Move.JUMP),
    )

    private lateinit var soundPool: SoundPool
    private val soundIds = mutableMapOf<Int, Int>()
    private lateinit var overlay: FrameLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupSounds()
        setContentView(buildUi())
    }

    override fun onDestroy() {
        soundPool.release()
        super.onDestroy()
    }

    // ---------- Sound ----------

    private fun setupSounds() {
        soundPool = SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
        animals.forEach { soundIds[it.soundRes] = soundPool.load(this, it.soundRes, 1) }
    }

    private fun playSound(animal: Animal) {
        soundIds[animal.soundRes]?.let { soundPool.play(it, 1f, 1f, 1, 0, 1f) }
    }

    // ---------- Layout ----------

    private fun dp(v: Float) = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics
    )

    private fun buildUi(): View {
        overlay = FrameLayout(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xFFBFE6FF.toInt(), 0xFFE8F7FF.toInt(), 0xFF8BD17C.toInt())
            )
            clipChildren = false
        }

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            setPadding(dp(12f).toInt(), dp(8f).toInt(), dp(12f).toInt(), dp(12f).toInt())
        }

        column.addView(TextView(this).apply {
            text = getString(R.string.title)
            textSize = 30f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setTextColor(0xFF2B4C7E.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(12f).toInt(), 0, dp(12f).toInt())
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val cols = 3
        animals.chunked(cols).forEach { rowAnimals ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                clipChildren = false
            }
            rowAnimals.forEach { animal ->
                row.addView(buildCard(animal), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                    val m = dp(6f).toInt()
                    setMargins(m, m, m, m)
                })
            }
            column.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }

        overlay.addView(column, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        // Keep content clear of the status and navigation bars (edge-to-edge on Android 15+).
        overlay.setOnApplyWindowInsetsListener { v, insets ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            } else {
                @Suppress("DEPRECATION")
                v.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            }
            insets
        }
        return overlay
    }

    private fun buildCard(animal: Animal): View {
        val shape = GradientDrawable().apply {
            cornerRadius = dp(24f)
            setColor(animal.color)
            setStroke(dp(3f).toInt(), Color.WHITE)
        }
        val mask = GradientDrawable().apply { cornerRadius = dp(24f); setColor(Color.WHITE) }

        val emoji = TextView(this).apply {
            text = animal.emoji
            textSize = 54f
            gravity = Gravity.CENTER
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val label = TextView(this).apply {
            text = animal.name
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setTextColor(0xFF3D3D3D.toInt())
            gravity = Gravity.CENTER
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            clipChildren = false
            addView(emoji)
            addView(label)
        }

        return FrameLayout(this).apply {
            background = RippleDrawable(ColorStateList.valueOf(0x55FFFFFF), shape, mask)
            elevation = dp(4f)
            clipChildren = false
            isClickable = true
            isFocusable = true
            contentDescription = "${animal.name}. Tap to hear it."
            addView(inner, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            setOnClickListener { card ->
                card.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                playSound(animal)
                animateAnimal(emoji, animal.move)
                squishCard(card)
                showBubble(card, animal)
                burst(card)
            }
        }
    }

    // ---------- Animations ----------

    private fun animateAnimal(view: View, move: Move) {
        (view.tag as? Animator)?.cancel()
        view.apply { translationX = 0f; translationY = 0f; rotation = 0f; scaleX = 1f; scaleY = 1f }

        val h = dp(1f)
        val anim: Animator = when (move) {
            Move.JUMP -> AnimatorSet().apply {
                val up = ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, 0f, -60 * h).apply {
                    duration = 260; interpolator = DecelerateInterpolator()
                }
                val down = ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, -60 * h, 0f).apply {
                    duration = 520; interpolator = BounceInterpolator()
                }
                val squash = ObjectAnimator.ofPropertyValuesHolder(view,
                    PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 0.85f, 1.1f, 1f),
                    PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.2f, 0.9f, 1f)
                ).apply { duration = 780 }
                play(up).before(down)
                play(squash).with(up)
            }
            Move.HOP -> ObjectAnimator.ofFloat(view, View.TRANSLATION_Y,
                0f, -30 * h, 0f, -30 * h, 0f).apply { duration = 600 }
            Move.WIGGLE -> ObjectAnimator.ofFloat(view, View.ROTATION,
                0f, -18f, 18f, -12f, 12f, -6f, 0f).apply { duration = 700 }
            Move.SHAKE -> ObjectAnimator.ofFloat(view, View.TRANSLATION_X,
                0f, -14 * h, 14 * h, -10 * h, 10 * h, -5 * h, 0f).apply { duration = 600 }
            Move.PULSE -> ObjectAnimator.ofPropertyValuesHolder(view,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.4f, 1f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.4f, 1f)
            ).apply { duration = 650; interpolator = OvershootInterpolator(3f) }
            Move.SPIN -> AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(view, View.ROTATION, 0f, 360f).apply {
                        duration = 700; interpolator = DecelerateInterpolator()
                    },
                    ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, 0f, -40 * h, 0f).apply {
                        duration = 700
                    }
                )
            }
        }
        view.tag = anim
        anim.start()
    }

    private fun squishCard(card: View) {
        ObjectAnimator.ofPropertyValuesHolder(card,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 0.92f, 1.04f, 1f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 0.92f, 1.04f, 1f)
        ).apply { duration = 350 }.start()
    }

    /** Position of [v]'s top-left corner inside the overlay. */
    private fun positionInOverlay(v: View): Pair<Float, Float> {
        val a = IntArray(2); val b = IntArray(2)
        v.getLocationInWindow(a); overlay.getLocationInWindow(b)
        return (a[0] - b[0]).toFloat() to (a[1] - b[1]).toFloat()
    }

    /** A speech bubble ("Moo!") that pops up above the card and floats away. */
    private fun showBubble(card: View, animal: Animal) {
        val bubble = TextView(this).apply {
            text = animal.says
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setTextColor(0xFF2B2B2B.toInt())
            background = GradientDrawable().apply {
                cornerRadius = dp(18f); setColor(Color.WHITE)
                setStroke(dp(2f).toInt(), animal.color)
            }
            setPadding(dp(14f).toInt(), dp(6f).toInt(), dp(14f).toInt(), dp(6f).toInt())
            elevation = dp(10f)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        bubble.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val (x, y) = positionInOverlay(card)
        bubble.x = x + card.width / 2f - bubble.measuredWidth / 2f
        bubble.y = y + dp(4f)
        bubble.scaleX = 0.2f; bubble.scaleY = 0.2f; bubble.alpha = 0f
        overlay.addView(bubble, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val pop = ObjectAnimator.ofPropertyValuesHolder(bubble,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 0.2f, 1f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.2f, 1f),
            PropertyValuesHolder.ofFloat(View.ALPHA, 0f, 1f)
        ).apply { duration = 250; interpolator = OvershootInterpolator(2.5f) }
        val rise = ObjectAnimator.ofPropertyValuesHolder(bubble,
            PropertyValuesHolder.ofFloat(View.Y, bubble.y, bubble.y - dp(50f)),
            PropertyValuesHolder.ofFloat(View.ALPHA, 1f, 0f)
        ).apply { duration = 600; startDelay = 450; interpolator = AccelerateInterpolator() }

        AnimatorSet().apply {
            playSequentially(pop, rise)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) { overlay.removeView(bubble) }
            })
            start()
        }
    }

    /** Little music notes and stars that fly out of the card. */
    private fun burst(card: View) {
        val symbols = listOf("♪", "♫", "★", "✦", "♥")
        val colors = listOf(0xFFFF6B6B, 0xFFFFA62B, 0xFF4D96FF, 0xFF6BCB77, 0xFFC77DFF)
        val (x, y) = positionInOverlay(card)
        val cx = x + card.width / 2f
        val cy = y + card.height / 2f

        repeat(8) { i ->
            val p = TextView(this).apply {
                text = symbols.random()
                textSize = Random.nextInt(16, 26).toFloat()
                setTextColor(colors[i % colors.size].toInt())
                typeface = Typeface.DEFAULT_BOLD
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            p.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
            p.x = cx - p.measuredWidth / 2f
            p.y = cy - p.measuredHeight / 2f
            overlay.addView(p, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))

            val angle = Math.toRadians(i * 45.0 + Random.nextDouble(-15.0, 15.0))
            val dist = dp(Random.nextInt(70, 120).toFloat())
            ObjectAnimator.ofPropertyValuesHolder(p,
                PropertyValuesHolder.ofFloat(View.X, p.x, p.x + (cos(angle) * dist).toFloat()),
                PropertyValuesHolder.ofFloat(View.Y, p.y, p.y + (sin(angle) * dist).toFloat()),
                PropertyValuesHolder.ofFloat(View.ROTATION, 0f, Random.nextInt(-90, 90).toFloat()),
                PropertyValuesHolder.ofFloat(View.ALPHA, 1f, 1f, 0f),
                PropertyValuesHolder.ofFloat(View.SCALE_X, 0.4f, 1.2f, 0.8f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.4f, 1.2f, 0.8f)
            ).apply {
                duration = 750
                interpolator = LinearInterpolator()
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) { overlay.removeView(p) }
                })
                start()
            }
        }
    }
}
