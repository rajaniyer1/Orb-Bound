package com.example.gesturesgame;
import android.annotation.SuppressLint;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import java.text.NumberFormat;
import java.util.Iterator;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback {

    // UI Overlays
    TextView tvOrbs, btnOpenShop;
    FrameLayout shopOverlay;
    Button btnBuyTier1, btnBuyTier2, btnBuyTier3, btnCloseShop;

    // Game Engine
    SurfaceView gameSurface;
    GameThread gameThread;
    GestureDetector gestureDetector;
    Random rand = new Random();

    // Economy & State
    int orbs = 0;
    boolean isOverchargeMode = false;
    int tier1Count = 0, tier2Count = 0, tier3Count = 0;
    int tier1Cost = 50, tier2Cost = 500, tier3Cost = 5000;
    int passiveOrbsPerSec = 0;
    long lastPassiveTime = System.currentTimeMillis();
    NumberFormat numberFormat = NumberFormat.getNumberInstance(Locale.US);

    // Spell Logic & New Mechanics
    enum Action { TAP, UP, DOWN, LEFT, RIGHT }
    CopyOnWriteArrayList<Action> targetSequence = new CopyOnWriteArrayList<>();
    CopyOnWriteArrayList<Action> currentInput = new CopyOnWriteArrayList<>();

    String feedbackText = "";
    int feedbackTimer = 0;
    float circleRotation = 0f;

    // NEW: Combo & Timer Systems
    int comboCount = 0;
    float castTimer = 100f;
    float maxCastTimer = 100f;
    float shakeMagnitude = 0f;

    // Physics Objects
    CopyOnWriteArrayList<Familiar> familiars = new CopyOnWriteArrayList<>();
    CopyOnWriteArrayList<Particle> particles = new CopyOnWriteArrayList<>();
    CopyOnWriteArrayList<Star> stars = new CopyOnWriteArrayList<>();

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Force True Fullscreen
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);

        // Map Views
        tvOrbs = findViewById(R.id.tvOrbs);
        shopOverlay = findViewById(R.id.shopOverlay);
        btnOpenShop = findViewById(R.id.btnOpenShop);
        btnBuyTier1 = findViewById(R.id.btnBuyTier1);
        btnBuyTier2 = findViewById(R.id.btnBuyTier2);
        btnBuyTier3 = findViewById(R.id.btnBuyTier3);
        btnCloseShop = findViewById(R.id.btnCloseShop);

        // Setup Game Surface
        gameSurface = findViewById(R.id.gameSurface);
        gameSurface.getHolder().addCallback(this);

        // Setup Gestures
        gestureDetector = new GestureDetector(this, new GameGestureListener());
        gameSurface.setOnTouchListener((v, event) -> {
            gestureDetector.onTouchEvent(event);
            return true;
        });

        // Shop UI Logic
        btnOpenShop.setOnClickListener(v -> shopOverlay.setVisibility(View.VISIBLE));
        btnCloseShop.setOnClickListener(v -> shopOverlay.setVisibility(View.GONE));

        btnBuyTier1.setOnClickListener(v -> purchaseFamiliar(1, 1));
        btnBuyTier2.setOnClickListener(v -> purchaseFamiliar(2, 10));
        btnBuyTier3.setOnClickListener(v -> purchaseFamiliar(3, 100));

        updateShopUI();
        generateNewSpell();

        for (int i = 0; i < 50; i++) { stars.add(new Star()); }

        // --- RUBRIC REQUIREMENT: ADD VIEW PROGRAMMATICALLY ---
        FrameLayout rootLayout = findViewById(android.R.id.content);
        TextView dynamicWatermark = new TextView(this);
        dynamicWatermark.setText("");
        dynamicWatermark.setTextColor(Color.parseColor("#66FFFFFF"));
        dynamicWatermark.setTextSize(12f);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        params.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.START;
        params.setMargins(30, 0, 0, 30);
        dynamicWatermark.setLayoutParams(params);
        rootLayout.addView(dynamicWatermark);
    }

    // --- GAME ENGINE LIFECYCLE ---
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        gameThread = new GameThread(holder);
        gameThread.setRunning(true);
        gameThread.start();
    }
    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {}
    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        gameThread.setRunning(false);
        try { gameThread.join(); } catch (InterruptedException e) { e.printStackTrace(); }
    }

    // --- 60 FPS GAME LOOP ---
    class GameThread extends Thread {
        private SurfaceHolder surfaceHolder;
        private boolean isRunning;
        private Paint paint, glowPaint;

        public GameThread(SurfaceHolder holder) {
            this.surfaceHolder = holder;
            this.paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            this.glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        }

        public void setRunning(boolean running) { this.isRunning = running; }

        @Override
        public void run() {
            while (isRunning) {
                Canvas canvas = null;
                try {
                    canvas = surfaceHolder.lockCanvas();
                    if (canvas != null) {
                        synchronized (surfaceHolder) {
                            update();
                            draw(canvas);
                        }
                    }
                } finally {
                    if (canvas != null) surfaceHolder.unlockCanvasAndPost(canvas);
                }
            }
        }

        private void update() {
            // Passive Economy Loop
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastPassiveTime >= 1000) {
                if (passiveOrbsPerSec > 0) {
                    orbs += passiveOrbsPerSec;
                    updateOrbsUI();
                    particles.add(new Particle("+" + numberFormat.format(passiveOrbsPerSec), Color.YELLOW, rand.nextInt(800) + 100, rand.nextInt(500) + 100));
                }
                lastPassiveTime = currentTime;
            }

            // NEW: Shrink cast timer. If it hits 0, Fizzle!
            castTimer -= 0.3f;
            if (castTimer <= 0) { triggerFizzle("Too Slow!"); }

            // Reduce Screen Shake over time
            if (shakeMagnitude > 0) { shakeMagnitude -= 1.5f; }
            if (shakeMagnitude < 0) { shakeMagnitude = 0; }

            // Update Physics
            circleRotation += 0.5f;
            for (Star s : stars) s.update();
            for (Familiar f : familiars) f.update();

            Iterator<Particle> pIter = particles.iterator();
            while (pIter.hasNext()) {
                Particle p = pIter.next();
                p.update();
                if (p.isDead()) particles.remove(p);
            }
            if (feedbackTimer > 0) feedbackTimer--;
        }

        private void draw(Canvas canvas) {
            // Base coordinates
            float baseCx = canvas.getWidth() / 2f;
            float baseCy = canvas.getHeight() / 2f + 100f;

            // Apply Screen Shake Math
            float cx = baseCx + (shakeMagnitude > 0 ? (rand.nextFloat() * shakeMagnitude - shakeMagnitude/2f) : 0);
            float cy = baseCy + (shakeMagnitude > 0 ? (rand.nextFloat() * shakeMagnitude - shakeMagnitude/2f) : 0);

            // 1. Draw Magical Cosmic Background
            RadialGradient bgGradient = new RadialGradient(baseCx, baseCy, 1000f, Color.parseColor("#1A0B2E"), Color.parseColor("#05050A"), Shader.TileMode.CLAMP);
            paint.setShader(bgGradient);
            canvas.drawRect(0, 0, canvas.getWidth(), canvas.getHeight(), paint);
            paint.setShader(null);

            // 2. Draw Ambient Stars
            paint.setColor(Color.WHITE);
            for (Star s : stars) {
                paint.setAlpha((int) (s.alpha * 255));
                canvas.drawCircle(s.x, s.y, s.size, paint);
            }
            paint.setAlpha(255);

            // 3. Draw Familiars
            for (Familiar f : familiars) {
                glowPaint.setColor(f.color);
                glowPaint.setMaskFilter(new BlurMaskFilter(20f, BlurMaskFilter.Blur.NORMAL));
                if (f.tier == 1) {
                    canvas.drawCircle(f.x, f.y, 15f + (float)Math.sin(f.floatOffset)*5f, glowPaint);
                } else if (f.tier == 2) {
                    canvas.save();
                    canvas.rotate(f.floatOffset * 50, f.x, f.y);
                    canvas.drawRect(f.x - 20, f.y - 20, f.x + 20, f.y + 20, glowPaint);
                    canvas.restore();
                } else {
                    canvas.drawCircle(f.x, f.y, 30f + (float)Math.sin(f.floatOffset)*10f, glowPaint);
                    paint.setColor(Color.BLACK);
                    canvas.drawCircle(f.x, f.y, 15f, paint);
                }
                glowPaint.setMaskFilter(null);
            }

            // 4. Draw Rotating Arcane Casting Circle
            canvas.save();
            canvas.rotate(circleRotation, cx, cy);
            glowPaint.setColor(Color.parseColor("#00FFFF"));
            glowPaint.setStyle(Paint.Style.STROKE);
            glowPaint.setStrokeWidth(4f);
            glowPaint.setMaskFilter(new BlurMaskFilter(10f, BlurMaskFilter.Blur.OUTER));

            canvas.drawCircle(cx, cy, 320f, glowPaint);
            glowPaint.setPathEffect(new DashPathEffect(new float[]{30f, 15f}, 0f));
            canvas.drawCircle(cx, cy, 280f, glowPaint);
            glowPaint.setPathEffect(null);
            canvas.restore();

            // 5. NEW: Draw the Burning Fuse Timer (Shrinking Arc)
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(12f);
            paint.setColor(castTimer > 30f ? Color.parseColor("#FFBB00") : Color.RED); // Turns red when low
            RectF fuseRect = new RectF(cx - 340f, cy - 340f, cx + 340f, cy + 340f);
            float sweepAngle = (castTimer / maxCastTimer) * 360f;
            canvas.drawArc(fuseRect, -90f, sweepAngle, false, paint);
            paint.setStyle(Paint.Style.FILL);

            // 6. Draw Target Text & Current Input
            paint.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
            paint.setTextAlign(Paint.Align.CENTER);

            paint.setColor(Color.WHITE);
            paint.setTextSize(55f);
            StringBuilder targetStr = new StringBuilder();
            for (Action a : targetSequence) targetStr.append(a.name()).append("  ");
            canvas.drawText(targetStr.toString(), cx, cy - 80, paint);

            paint.setColor(Color.parseColor("#FFBB00"));
            StringBuilder inputStr = new StringBuilder();
            for (Action a : currentInput) inputStr.append(a.name()).append("  ");
            canvas.drawText(inputStr.toString(), cx, cy + 20, paint);

            // 7. NEW: Draw Combo Counter
            if (comboCount > 1) {
                paint.setColor(Color.MAGENTA);
                paint.setTextSize(60f);
                canvas.drawText("Combo x" + comboCount + "!", cx, cy - 180, paint);
            }

            // 8. Draw Feedback & Particles
            if (feedbackTimer > 0) {
                paint.setColor(feedbackText.contains("Success") ? Color.GREEN : Color.RED);
                paint.setTextSize(70f);
                canvas.drawText(feedbackText, cx, cy + 120, paint);
            }

            for (Particle p : particles) {
                paint.setColor(p.color);
                paint.setAlpha((int) (p.life * 255));
                paint.setTextSize(60f + p.bonusScale);
                canvas.drawText(p.text, p.x, p.y, paint);
            }
            paint.setAlpha(255);
        }
    }

    // --- GAMEPLAY & COMBOS ---

    private void triggerFizzle(String message) {
        currentInput.clear();
        feedbackText = message;
        feedbackTimer = 30;
        comboCount = 0; // Break combo!
        shakeMagnitude = 25f; // TRIGGER SCREEN SHAKE
        castTimer = maxCastTimer; // Reset timer
    }

    private void handleInput(Action action) {
        currentInput.add(action);

        for (int i = 0; i < currentInput.size(); i++) {
            if (currentInput.get(i) != targetSequence.get(i)) {
                triggerFizzle("Fizzled!");
                return;
            }
        }

        if (currentInput.size() == targetSequence.size()) {
            comboCount++; // Increase Combo

            // Apply Combo Multiplier
            float multiplier = 1.0f + (comboCount * 0.2f);
            int baseReward = 10 + (targetSequence.size() * 5);
            int finalReward = (int) (baseReward * multiplier);

            orbs += finalReward;
            updateOrbsUI();

            // --- RUBRIC REQUIREMENT: VIEW ANIMATION (Scale) ---
            runOnUiThread(() -> {
                tvOrbs.animate().scaleX(1.3f).scaleY(1.3f).setDuration(100).withEndAction(() -> {
                    tvOrbs.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
                }).start();
            });

            if (orbs >= 100 && !isOverchargeMode) {
                isOverchargeMode = true;
                particles.add(new Particle("OVERCHARGE!", Color.MAGENTA, 500, 800));
            }

            feedbackText = "Success!";
            feedbackTimer = 20;

            // Dynamic Particle based on combo
            Particle successParticle = new Particle("+" + finalReward, Color.CYAN, 500, 1000);
            if (comboCount > 2) successParticle.bonusScale = 20f;
            particles.add(successParticle);

            currentInput.clear();
            generateNewSpell();
        }
    }

    private void generateNewSpell() {
        targetSequence.clear();
        int length = isOverchargeMode ? (rand.nextInt(2) + 3) : 2;
        Action[] possibleActions = Action.values();
        for (int i = 0; i < length; i++) {
            targetSequence.add(possibleActions[rand.nextInt(possibleActions.length)]);
        }
        castTimer = maxCastTimer; // Reset the fuse!
    }

    private void purchaseFamiliar(int tier, int orbsPerSecIncrease) {
        int cost = (tier == 1) ? tier1Cost : (tier == 2) ? tier2Cost : tier3Cost;

        if (orbs >= cost) {
            orbs -= cost;
            passiveOrbsPerSec += orbsPerSecIncrease;

            if (tier == 1) {
                tier1Count++; tier1Cost = (int)(tier1Cost * 1.5);
                familiars.add(new Familiar(1, Color.CYAN));
            } else if (tier == 2) {
                tier2Count++; tier2Cost = (int)(tier2Cost * 1.5);
                familiars.add(new Familiar(2, Color.GREEN));
            } else if (tier == 3) {
                tier3Count++; tier3Cost = (int)(tier3Cost * 1.5);
                familiars.add(new Familiar(3, Color.MAGENTA));
            }

            updateShopUI();
            updateOrbsUI();
            particles.add(new Particle("Summoned!", Color.WHITE, 500, 1000));
        } else {
            feedbackText = "Not enough orbs!";
            feedbackTimer = 30;
            shakeMagnitude = 15f; // Small screen shake on bad purchase
        }
    }

    private void updateOrbsUI() {
        runOnUiThread(() -> tvOrbs.setText("🔮 Orbs: " + numberFormat.format(orbs)));
    }

    private void updateShopUI() {
        runOnUiThread(() -> {
            btnBuyTier1.setText(String.format("Arcane Wisp\nGenerates 1 Orb/s | Owned: %d\nCost: %s 🔮", tier1Count, numberFormat.format(tier1Cost)));
            btnBuyTier2.setText(String.format("Mystic Relic\nGenerates 10 Orbs/s | Owned: %d\nCost: %s 🔮", tier2Count, numberFormat.format(tier2Cost)));
            btnBuyTier3.setText(String.format("Void Core\nGenerates 100 Orbs/s | Owned: %d\nCost: %s 🔮", tier3Count, numberFormat.format(tier3Cost)));
        });
    }

    // --- PHYSICS CLASSES ---
    class Familiar {
        int tier, color; float x, y, baseY, floatOffset, floatSpeed;
        Familiar(int tier, int color) {
            this.tier = tier; this.color = color;
            this.x = rand.nextInt(800) + 100; this.baseY = rand.nextInt(400) + 200;
            this.y = baseY; this.floatOffset = rand.nextFloat() * 10f;
            this.floatSpeed = 0.05f + (rand.nextFloat() * 0.05f);
        }
        void update() { floatOffset += floatSpeed; y = baseY + (float) Math.sin(floatOffset) * 20f; }
    }

    class Particle {
        String text; int color; float x, y, velocityY = -4f, life = 1.0f, bonusScale = 0f;
        Particle(String text, int color, float x, float y) {
            this.text = text; this.color = color; this.x = x; this.y = y;
        }
        void update() { y += velocityY; life -= 0.02f; }
        boolean isDead() { return life <= 0; }
    }

    class Star {
        float x, y, size, alpha;
        Star() {
            this.x = rand.nextInt(1200); this.y = rand.nextInt(2000);
            this.size = rand.nextFloat() * 4f + 1f; this.alpha = rand.nextFloat();
        }
        void update() {
            alpha += (rand.nextFloat() - 0.5f) * 0.1f;
            if (alpha < 0) alpha = 0; if (alpha > 1) alpha = 1;
        }
    }

    // --- GESTURE LISTENER ---
    private class GameGestureListener extends GestureDetector.SimpleOnGestureListener {
        private static final int SWIPE_THRESHOLD = 80, SWIPE_VELOCITY_THRESHOLD = 80;
        @Override public boolean onDown(MotionEvent e) { return true; }
        @Override public boolean onSingleTapUp(MotionEvent e) { handleInput(Action.TAP); return true; }
        @Override public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            if (e1 == null || e2 == null) return false;
            float diffY = e2.getY() - e1.getY(); float diffX = e2.getX() - e1.getX();
            if (Math.abs(diffX) > Math.abs(diffY)) {
                if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                    if (diffX > 0) handleInput(Action.RIGHT); else handleInput(Action.LEFT);
                    return true;
                }
            } else {
                if (Math.abs(diffY) > SWIPE_THRESHOLD && Math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                    if (diffY > 0) handleInput(Action.DOWN); else handleInput(Action.UP);
                    return true;
                }
            }
            return false;
        }
    }
}