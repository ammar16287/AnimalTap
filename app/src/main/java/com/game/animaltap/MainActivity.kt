package com.game.animaltap

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
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
import android.view.WindowInsetsController
import android.view.animation.AccelerateDecelerateInterpolator
import android.window.OnBackInvokedDispatcher
import android.view.animation.AccelerateInterpolator
import android.view.animation.BounceInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
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

/** A background theme: gradient colors, text color and floating decorations. */
data class BgTheme(
    val name: String,
    val emoji: String,
    val colors: IntArray,
    val textColor: Int,
    val dark: Boolean,
    val decor: List<String>,
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
        Animal("Leopard", "🐆", "Grrr!", 0xFFFFE0A3.toInt(), R.raw.leopard, Move.SHAKE),
        Animal("Elephant", "🐘", "Pawoo!", 0xFFDCDCF0.toInt(), R.raw.elephant, Move.PULSE),
        Animal("Rhino", "🦏", "Snort!", 0xFFE6E2D3.toInt(), R.raw.rhino, Move.HOP),
        Animal("Monkey", "🐵", "Ooh-aah!", 0xFFEBCDAE.toInt(), R.raw.monkey, Move.WIGGLE),
        Animal("Eagle", "🦅", "Screech!", 0xFFCDE7F0.toInt(), R.raw.eagle, Move.SPIN),
        Animal("Tiger", "🐯", "Roarr!", 0xFFFFC28A.toInt(), R.raw.tiger, Move.JUMP),
    )

    private val themes = listOf(
        BgTheme("Meadow", "🌻", intArrayOf(0xFFBFE6FF.toInt(), 0xFFE8F7FF.toInt(), 0xFF8BD17C.toInt()),
            0xFF2B4C7E.toInt(), false, listOf("☁️", "🌼", "🦋", "🌷")),
        BgTheme("Ocean", "🌊", intArrayOf(0xFF89F7FE.toInt(), 0xFF66A6FF.toInt(), 0xFF1E5799.toInt()),
            0xFF0B3C6E.toInt(), false, listOf("🐠", "🐟", "🐚", "💧")),
        BgTheme("Sunset", "🌅", intArrayOf(0xFFFFE29F.toInt(), 0xFFFFA99F.toInt(), 0xFFFF719A.toInt()),
            0xFF7A2048.toInt(), false, listOf("☁️", "🌸", "🎈", "🌞")),
        BgTheme("Night", "🌙", intArrayOf(0xFF0F2027.toInt(), 0xFF203A43.toInt(), 0xFF2C5364.toInt()),
            Color.WHITE, true, listOf("⭐", "✨", "🌙", "🌟")),
        BgTheme("Candy", "🍭", intArrayOf(0xFFFFDEE9.toInt(), 0xFFE3D5FF.toInt(), 0xFFB5FFFC.toInt()),
            0xFF8E3A9D.toInt(), false, listOf("🍬", "🍭", "🧁", "🍩")),
        BgTheme("Jungle", "🌴", intArrayOf(0xFFD4FC79.toInt(), 0xFF96E6A1.toInt(), 0xFF3A7D44.toInt()),
            0xFF1F4D2B.toInt(), false, listOf("🌿", "🍃", "🌴", "🐒")),
    )

    private lateinit var soundPool: SoundPool
    private val soundIds = mutableMapOf<Int, Int>()

    /** Root view: themed background, decorations, current screen, and pop-up effects on top. */
    private lateinit var overlay: FrameLayout
    private lateinit var decorLayer: FrameLayout
    private lateinit var screen: FrameLayout

    private var themeIndex = 0
    private val theme get() = themes[themeIndex]
    private var onHome = true
    private val decorAnimators = mutableListOf<Animator>()
    private val screenAnimators = mutableListOf<Animator>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        themeIndex = getPreferences(MODE_PRIVATE).getInt(KEY_THEME, 0).coerceIn(themes.indices)
        setupSounds()
        setContentView(buildRoot())
        applyTheme(themeIndex)
        showHome(animate = true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+: onBackPressed() is not called for apps targeting API 36.
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT) { handleBack() }
        }
    }

    override fun onDestroy() {
        decorAnimators.forEach { it.cancel() }
        screenAnimators.forEach { it.cancel() }
        soundPool.release()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() = handleBack()   // Android 8-12

    /** Back from the game returns to the home page; back on home closes the app. */
    private fun handleBack() {
        if (!onHome) showHome(animate = true) else exitApp()
    }

    private fun exitApp() = finishAndRemoveTask()

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

    // ---------- Root, themes & decorations ----------

    private fun dp(v: Float) = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics
    )

    /** Grows text and buttons on tablets (1.0 on phones, up to 1.8 on 10" tablets). */
    private val uiScale by lazy {
        (resources.configuration.smallestScreenWidthDp / 400f).coerceIn(1f, 1.8f)
    }

    /** dp scaled for the screen size. */
    private fun sdp(v: Float) = dp(v * uiScale)

    private fun matchParent() = FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

    private fun buildRoot(): View {
        overlay = FrameLayout(this).apply { clipChildren = false }
        decorLayer = FrameLayout(this).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        // Re-scatter decorations whenever the background area changes size (first layout, insets).
        decorLayer.addOnLayoutChangeListener { _, l, t, r, b, ol, ot, or, ob ->
            if (r - l != or - ol || b - t != ob - ot) decorLayer.post { buildDecorations() }
        }
        screen = FrameLayout(this).apply { clipChildren = false }
        overlay.addView(decorLayer, matchParent())
        overlay.addView(screen, matchParent())

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

    private fun applyTheme(index: Int) {
        themeIndex = index
        getPreferences(MODE_PRIVATE).edit().putInt(KEY_THEME, index).apply()
        overlay.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, theme.colors)
        styleSystemBars()
        decorLayer.post { buildDecorations() }
    }

    @Suppress("DEPRECATION")
    private fun styleSystemBars() {
        window.statusBarColor = theme.colors.first()
        window.navigationBarColor = theme.colors.last()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val light = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            window.insetsController?.setSystemBarsAppearance(if (theme.dark) 0 else light, light)
        } else {
            val light = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            val flags = window.decorView.systemUiVisibility
            window.decorView.systemUiVisibility = if (theme.dark) flags and light.inv() else flags or light
        }
    }

    /** Scatter the theme's emojis over the background and let them gently float. */
    private fun buildDecorations() {
        decorAnimators.forEach { it.cancel() }
        decorAnimators.clear()
        decorLayer.removeAllViews()
        val w = decorLayer.width.toFloat()
        val h = decorLayer.height.toFloat()
        if (w <= 0f || h <= 0f) return

        repeat(12) { i ->
            val d = TextView(this).apply {
                text = theme.decor[i % theme.decor.size]
                textSize = Random.nextInt(22, 40) * uiScale
                alpha = Random.nextDouble(0.35, 0.7).toFloat()
            }
            d.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
            // Spread evenly down the screen, random left/right.
            d.x = Random.nextFloat() * (w - d.measuredWidth).coerceAtLeast(1f)
            d.y = (i + Random.nextFloat()) / 12f * h - d.measuredHeight / 2f
            decorLayer.addView(d, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))

            val drift = dp(Random.nextInt(12, 30).toFloat())
            ObjectAnimator.ofPropertyValuesHolder(d,
                PropertyValuesHolder.ofFloat(View.TRANSLATION_X, d.translationX, d.translationX + drift),
                PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, d.translationY, d.translationY - drift),
                PropertyValuesHolder.ofFloat(View.ROTATION, -8f, 8f)
            ).apply {
                duration = Random.nextLong(2500, 5000)
                startDelay = Random.nextLong(0, 1500)
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                interpolator = AccelerateDecelerateInterpolator()
                decorAnimators += this
                start()
            }
        }
    }

    private fun switchScreen(content: View) {
        screenAnimators.forEach { it.cancel() }
        screenAnimators.clear()
        screen.removeAllViews()
        screen.addView(content, matchParent())
    }

    // ---------- Home page ----------

    private fun showHome(animate: Boolean) {
        onHome = true
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            clipChildren = false
            clipToPadding = false
            setPadding(dp(24f).toInt(), dp(24f).toInt(), dp(24f).toInt(), dp(24f).toInt())
        }
        val wrap = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        column.addView(View(this), LinearLayout.LayoutParams(0, 0, 1f))

        // Bouncing animal parade.
        val parade = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            clipChildren = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        listOf("🐄", "🐶", "🐱", "🐷", "🐸").forEachIndexed { i, e ->
            val t = TextView(this).apply { text = e; textSize = 40f * uiScale; setPadding(sdp(4f).toInt(), 0, sdp(4f).toInt(), 0) }
            parade.addView(t)
            ObjectAnimator.ofFloat(t, View.TRANSLATION_Y, 0f, -sdp(18f)).apply {
                duration = 450
                startDelay = i * 120L
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                interpolator = DecelerateInterpolator()
                screenAnimators += this
                start()
            }
        }
        column.addView(parade, wrap)

        val title = TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 46f * uiScale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setTextColor(theme.textColor)
            gravity = Gravity.CENTER
            setShadowLayer(dp(4f), 0f, dp(2f), 0x40000000)
            setPadding(0, dp(12f).toInt(), 0, 0)
        }
        column.addView(title, wrap)

        column.addView(TextView(this).apply {
            text = getString(R.string.tagline)
            textSize = 18f * uiScale
            setTextColor(theme.textColor)
            alpha = 0.85f
            gravity = Gravity.CENTER
        }, wrap)

        column.addView(View(this), LinearLayout.LayoutParams(0, sdp(36f).toInt()))

        val play = pillButton(getString(R.string.play), 0xFF4CAF50.toInt(), 30f) { showGame() }
        column.addView(play, LinearLayout.LayoutParams(sdp(240f).toInt(), sdp(84f).toInt()))
        // Gentle "tap me" pulse on the Play button.
        ObjectAnimator.ofPropertyValuesHolder(play,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.06f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.06f)
        ).apply {
            duration = 700
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            screenAnimators += this
            start()
        }

        column.addView(View(this), LinearLayout.LayoutParams(0, sdp(32f).toInt()))

        column.addView(TextView(this).apply {
            text = getString(R.string.themes)
            textSize = 18f * uiScale
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(theme.textColor)
            gravity = Gravity.CENTER
        }, wrap)
        column.addView(buildThemePicker(), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        column.addView(View(this), LinearLayout.LayoutParams(0, 0, 1f))

        column.addView(pillButton(getString(R.string.exit), 0xFFE53935.toInt(), 20f) { exitApp() },
            LinearLayout.LayoutParams(sdp(170f).toInt(), sdp(56f).toInt()))

        switchScreen(column)

        if (animate) {
            title.scaleX = 0.3f; title.scaleY = 0.3f; title.alpha = 0f
            title.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(500)
                .setInterpolator(OvershootInterpolator(2f)).start()
        }
    }

    private fun buildThemePicker(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            setPadding(dp(4f).toInt(), dp(12f).toInt(), dp(4f).toInt(), dp(12f).toInt())
        }
        themes.forEachIndexed { i, t ->
            val selected = i == themeIndex
            val swatch = FrameLayout(this).apply {
                background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, t.colors).apply {
                    shape = GradientDrawable.OVAL
                    if (selected) setStroke(dp(5f).toInt(), 0xFFFFC300.toInt())
                    else setStroke(dp(3f).toInt(), Color.WHITE)
                }
                elevation = dp(if (selected) 8f else 3f)
                addView(TextView(this@MainActivity).apply {
                    text = t.emoji; textSize = 26f * uiScale; gravity = Gravity.CENTER
                }, matchParent())
            }
            val chip = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                clipChildren = false
                isClickable = true
                isFocusable = true
                contentDescription = "${t.name} theme" + if (selected) ", selected" else ""
                addView(swatch, LinearLayout.LayoutParams(sdp(62f).toInt(), sdp(62f).toInt()))
                addView(TextView(this@MainActivity).apply {
                    text = t.name
                    textSize = 13f * uiScale
                    typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                    setTextColor(theme.textColor)
                    gravity = Gravity.CENTER
                    setPadding(0, dp(4f).toInt(), 0, 0)
                })
                setOnClickListener {
                    it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    if (i != themeIndex) {
                        applyTheme(i)
                        showHome(animate = false)
                    }
                }
            }
            row.addView(chip, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(sdp(8f).toInt(), 0, sdp(8f).toInt(), 0)
            })
        }
        return HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipChildren = false
            addView(row)
            // Keep the selected theme in view after the picker is rebuilt.
            post {
                val child = row.getChildAt(themeIndex) ?: return@post
                scrollTo((child.left + child.width / 2 - width / 2).coerceAtLeast(0), 0)
            }
        }
    }

    /** A big rounded button with a ripple and a squish on press. */
    private fun pillButton(label: String, color: Int, size: Float, onClick: () -> Unit): TextView {
        val shape = GradientDrawable().apply {
            cornerRadius = dp(100f)
            setColor(color)
            setStroke(dp(4f).toInt(), Color.WHITE)
        }
        val mask = GradientDrawable().apply { cornerRadius = dp(100f); setColor(Color.WHITE) }
        return TextView(this).apply {
            text = label
            textSize = size * uiScale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = RippleDrawable(ColorStateList.valueOf(0x55FFFFFF), shape, mask)
            elevation = dp(6f)
            isClickable = true
            isFocusable = true
            setOnClickListener { v ->
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                squishCard(v)
                v.postDelayed({ onClick() }, 150)
            }
        }
    }

    // ---------- Game page ----------

    private fun showGame() {
        if (isFinishing) return
        onHome = false
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            setPadding(dp(12f).toInt(), dp(8f).toInt(), dp(12f).toInt(), dp(12f).toInt())
        }

        // Top bar: home button + title.
        val bar = FrameLayout(this).apply { clipChildren = false }
        bar.addView(TextView(this).apply {
            text = getString(R.string.title)
            textSize = 28f * uiScale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setTextColor(theme.textColor)
            gravity = Gravity.CENTER
            setPadding(sdp(56f).toInt(), dp(12f).toInt(), sdp(56f).toInt(), dp(12f).toInt())
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        bar.addView(pillButton("🏠", 0x66FFFFFF, 22f) { showHome(animate = true) }.apply {
            contentDescription = getString(R.string.home)
            elevation = dp(2f)
        }, FrameLayout.LayoutParams(sdp(52f).toInt(), sdp(52f).toInt(), Gravity.START or Gravity.CENTER_VERTICAL))
        column.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

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

        switchScreen(column)
        column.alpha = 0f; column.translationY = dp(40f)
        column.animate().alpha(1f).translationY(0f).setDuration(300)
            .setInterpolator(DecelerateInterpolator()).start()
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
            textSize = 46f * uiScale
            gravity = Gravity.CENTER
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val label = TextView(this).apply {
            text = animal.name
            textSize = 15f * uiScale
            maxLines = 1
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
            textSize = 22f * uiScale
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
                textSize = Random.nextInt(16, 26) * uiScale
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

    private companion object {
        const val KEY_THEME = "theme"
    }
}
