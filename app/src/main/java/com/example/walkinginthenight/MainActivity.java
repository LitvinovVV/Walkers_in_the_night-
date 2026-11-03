package com.example.walkinginthenight;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private StoryDatabaseHelper dbHelper;
    private LinearLayout rootLayout;
    private TextView tvBookHeader;
    private TextView tvChapterTitle;
    private TextView tvStoryText;
    private TextView tvTapHint;
    private View topDivider;
    private Button btnSettings;
    private Button btnChapters;
    private LinearLayout choicesContainer;
    private ScrollView scrollViewStory;

    private MediaPlayer mediaPlayer;
    private int musicVolume = 60;

    private int currentSceneId = 101;
    private int currentBookId = 1;
    private String fullSceneText = "";
    private boolean isTyping = false;
    private int charIndex = 0;

    private int speedProgress = 24;
    private long typingDelay = 16L;
    private float storyFontSize = 17f;

    private final Handler typingHandler = new Handler(Looper.getMainLooper());
    private Runnable typingRunnable;

    private int currentBgDeepColor = Color.parseColor("#0D0B0A");
    private int currentCardBgColor = Color.parseColor("#171311");
    private int currentCardStrokeColor = Color.parseColor("#6B3A32");
    private int currentHeaderColor = Color.parseColor("#D7B377");
    private int currentChapterColor = Color.parseColor("#E8C39E");
    private int currentHintColor = Color.parseColor("#8E7968");
    private int currentBtnBgColor = Color.parseColor("#2E1917");
    private int currentBtnStrokeColor = Color.parseColor("#8A423B");
    private int currentTextColor = Color.parseColor("#E6DFD5");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dbHelper = new StoryDatabaseHelper(this);

        rootLayout = findViewById(R.id.rootLayout);
        tvBookHeader = findViewById(R.id.tvBookHeader);
        tvChapterTitle = findViewById(R.id.tvChapterTitle);
        tvStoryText = findViewById(R.id.tvStoryText);
        tvTapHint = findViewById(R.id.tvTapHint);
        topDivider = findViewById(R.id.topDivider);
        btnSettings = findViewById(R.id.btnSettings);
        btnChapters = findViewById(R.id.btnChapters);
        choicesContainer = findViewById(R.id.choicesContainer);
        scrollViewStory = findViewById(R.id.scrollViewStory);

        final int basePadLeft = dpToPx(16);
        final int basePadRight = dpToPx(16);
        final int basePadTop = dpToPx(12);
        final int basePadBottom = dpToPx(12);

        ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(
                    basePadLeft + insets.left,
                    basePadTop + insets.top,
                    basePadRight + insets.right,
                    basePadBottom + insets.bottom
            );
            return WindowInsetsCompat.CONSUMED;
        });

        SharedPreferences prefs = getSharedPreferences("QuestProgress", MODE_PRIVATE);
        currentSceneId = prefs.getInt("saved_scene_id", 101);
        musicVolume = prefs.getInt("music_volume", 60);
        speedProgress = prefs.getInt("speed_progress", 24);
        typingDelay = (40 - speedProgress);
        storyFontSize = prefs.getFloat("story_font_size", 17f);
        tvStoryText.setTextSize(storyFontSize);

        unlockScene(101);
        initBackgroundMusic();

        btnSettings.setOnClickListener(v -> showSettingsDialog());
        btnChapters.setOnClickListener(v -> showBookSelectionDialog());

        tvStoryText.setOnClickListener(v -> {
            if (isTyping) {
                finishTypingImmediately();
            }
        });

        loadScene(currentSceneId);
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void unlockScene(int sceneId) {
        SharedPreferences prefs = getSharedPreferences("QuestProgress", MODE_PRIVATE);
        Set<String> existing = prefs.getStringSet("unlocked_scenes", new HashSet<>());
        Set<String> updated = new HashSet<>(existing);
        updated.add(String.valueOf(sceneId));
        prefs.edit().putStringSet("unlocked_scenes", updated).apply();
    }

    private boolean isSceneUnlocked(int sceneId) {
        if (sceneId == 101) return true;
        SharedPreferences prefs = getSharedPreferences("QuestProgress", MODE_PRIVATE);
        Set<String> unlocked = prefs.getStringSet("unlocked_scenes", new HashSet<>());
        return unlocked.contains(String.valueOf(sceneId));
    }

    private void resetStoryToBeginning() {
        SharedPreferences prefs = getSharedPreferences("QuestProgress", MODE_PRIVATE);
        Set<String> initialSet = new HashSet<>();
        initialSet.add("101");
        prefs.edit()
                .putStringSet("unlocked_scenes", initialSet)
                .putInt("saved_scene_id", 101)
                .apply();
        loadScene(101);
        showStylizedNoticeToast("↺", "История началась с самого начала.");
    }

    private void initBackgroundMusic() {
        try {
            int rawResId = findFirstRawAudioResId();
            if (rawResId != 0) {
                mediaPlayer = MediaPlayer.create(this, rawResId);
                if (mediaPlayer != null) {
                    mediaPlayer.setLooping(true);
                    applyMusicVolume();
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void applyMusicVolume() {
        if (mediaPlayer == null) return;
        float vol = musicVolume / 100f;
        mediaPlayer.setVolume(vol, vol);
        if (musicVolume > 0) {
            if (!mediaPlayer.isPlaying()) {
                mediaPlayer.start();
            }
        } else {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
            }
        }
    }

    private int findFirstRawAudioResId() {
        String[] commonNames = {"music", "bg_music", "ambient", "theme", "night", "soundtrack", "ost", "audio"};
        for (String name : commonNames) {
            int id = getResources().getIdentifier(name, "raw", getPackageName());
            if (id != 0) return id;
        }
        try {
            Class<?> rawClass = Class.forName(getPackageName() + ".R$raw");
            Field[] fields = rawClass.getDeclaredFields();
            if (fields.length > 0) {
                return fields[0].getInt(null);
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mediaPlayer != null && musicVolume > 0 && !mediaPlayer.isPlaying()) {
            mediaPlayer.start();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (typingRunnable != null) {
            typingHandler.removeCallbacks(typingRunnable);
        }
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    private void loadScene(int sceneId) {
        if (typingRunnable != null) {
            typingHandler.removeCallbacks(typingRunnable);
        }

        Cursor sceneCursor = dbHelper.getScene(sceneId);
        if (sceneCursor != null && sceneCursor.moveToFirst()) {
            currentSceneId = sceneId;
            currentBookId = sceneCursor.getInt(sceneCursor.getColumnIndexOrThrow(StoryDatabaseHelper.COL_BOOK_ID));
            String chapterTitle = sceneCursor.getString(sceneCursor.getColumnIndexOrThrow(StoryDatabaseHelper.COL_CHAPTER_TITLE));
            fullSceneText = sceneCursor.getString(sceneCursor.getColumnIndexOrThrow(StoryDatabaseHelper.COL_STORY_TEXT));

            unlockScene(currentSceneId);

            getSharedPreferences("QuestProgress", MODE_PRIVATE)
                    .edit()
                    .putInt("saved_scene_id", currentSceneId)
                    .apply();

            applyBookTheme(currentBookId);
            tvChapterTitle.setText(chapterTitle);
            sceneCursor.close();
        } else {
            if (sceneCursor != null) sceneCursor.close();
            if (sceneId != 101) {
                loadScene(101);
                return;
            }
        }

        choicesContainer.removeAllViews();
        choicesContainer.setVisibility(View.INVISIBLE);

        Cursor choicesCursor = dbHelper.getChoicesForScene(sceneId);
        if (choicesCursor != null) {
            while (choicesCursor.moveToNext()) {
                String choiceText = choicesCursor.getString(choicesCursor.getColumnIndexOrThrow(StoryDatabaseHelper.COL_CHOICE_TEXT));
                int nextSceneId = choicesCursor.getInt(choicesCursor.getColumnIndexOrThrow(StoryDatabaseHelper.COL_NEXT_SCENE));

                Button choiceButton = createStyledChoiceButton(choiceText, nextSceneId);
                choicesContainer.addView(choiceButton);
            }
            choicesCursor.close();
        }

        scrollViewStory.post(() -> scrollViewStory.fullScroll(ScrollView.FOCUS_UP));
        startTypewriterEffect();
    }

    private void startTypewriterEffect() {
        if (typingDelay <= 0L) {
            finishTypingImmediately();
            return;
        }

        isTyping = true;
        charIndex = 0;
        tvStoryText.setText("");
        tvTapHint.setVisibility(View.VISIBLE);

        typingRunnable = new Runnable() {
            @Override
            public void run() {
                if (charIndex < fullSceneText.length()) {
                    int step = Math.min(3, fullSceneText.length() - charIndex);
                    charIndex += step;
                    tvStoryText.setText(fullSceneText.substring(0, charIndex));
                    typingHandler.postDelayed(this, typingDelay);
                } else {
                    finishTypingImmediately();
                }
            }
        };
        typingHandler.post(typingRunnable);
    }

    private void finishTypingImmediately() {
        if (typingRunnable != null) {
            typingHandler.removeCallbacks(typingRunnable);
        }
        isTyping = false;
        tvStoryText.setText(fullSceneText);
        tvTapHint.setVisibility(View.GONE);
        choicesContainer.setVisibility(View.VISIBLE);
    }

    private void applyBookTheme(int bookId) {
        int chapterBannerBg;

        if (bookId == 2) {
            tvBookHeader.setText("Наследники скорби");
            currentBgDeepColor = Color.parseColor("#0A1017");
            currentCardBgColor = Color.parseColor("#111B27");
            currentCardStrokeColor = Color.parseColor("#2E5370");
            chapterBannerBg = Color.parseColor("#152436");
            currentHeaderColor = Color.parseColor("#8AB4D6");
            currentChapterColor = Color.parseColor("#B5D3EB");
            currentHintColor = Color.parseColor("#63849E");
            currentBtnBgColor = Color.parseColor("#162838");
            currentBtnStrokeColor = Color.parseColor("#3B698C");
            currentTextColor = Color.parseColor("#E1EAF2");
        } else if (bookId == 3) {
            tvBookHeader.setText("Пленники раздора");
            currentBgDeepColor = Color.parseColor("#0F0B16");
            currentCardBgColor = Color.parseColor("#191326");
            currentCardStrokeColor = Color.parseColor("#4E386E");
            chapterBannerBg = Color.parseColor("#221933");
            currentHeaderColor = Color.parseColor("#B399D4");
            currentChapterColor = Color.parseColor("#D0BEE8");
            currentHintColor = Color.parseColor("#7E6899");
            currentBtnBgColor = Color.parseColor("#241B35");
            currentBtnStrokeColor = Color.parseColor("#63488C");
            currentTextColor = Color.parseColor("#EAE3F2");
        } else {
            tvBookHeader.setText("Жнецы страданий");
            currentBgDeepColor = Color.parseColor("#0D0B0A");
            currentCardBgColor = Color.parseColor("#171311");
            currentCardStrokeColor = Color.parseColor("#6B3A32");
            chapterBannerBg = Color.parseColor("#241614");
            currentHeaderColor = Color.parseColor("#D7B377");
            currentChapterColor = Color.parseColor("#E8C39E");
            currentHintColor = Color.parseColor("#8E7968");
            currentBtnBgColor = Color.parseColor("#2E1917");
            currentBtnStrokeColor = Color.parseColor("#8A423B");
            currentTextColor = Color.parseColor("#E6DFD5");
        }

        rootLayout.setBackgroundColor(currentBgDeepColor);
        tvBookHeader.setTextColor(currentHeaderColor);
        tvChapterTitle.setTextColor(currentChapterColor);
        tvStoryText.setTextColor(currentTextColor);
        tvTapHint.setTextColor(currentHintColor);
        topDivider.setBackgroundColor(currentCardStrokeColor);

        ColorStateList topBtnTint = ColorStateList.valueOf(currentBtnBgColor);
        btnSettings.setBackgroundTintList(topBtnTint);
        btnChapters.setBackgroundTintList(topBtnTint);
        btnSettings.setTextColor(currentTextColor);
        btnChapters.setTextColor(currentTextColor);

        GradientDrawable chapterDrawable = new GradientDrawable();
        chapterDrawable.setColor(chapterBannerBg);
        chapterDrawable.setCornerRadius(dpToPx(8));
        chapterDrawable.setStroke(dpToPx(1), currentCardStrokeColor);
        tvChapterTitle.setBackground(chapterDrawable);

        GradientDrawable storyCardDrawable = new GradientDrawable();
        storyCardDrawable.setColor(currentCardBgColor);
        storyCardDrawable.setCornerRadius(dpToPx(12));
        storyCardDrawable.setStroke(dpToPx(2), currentCardStrokeColor);
        tvStoryText.setBackground(storyCardDrawable);
    }

    private Button createStyledChoiceButton(String text, int nextSceneId) {
        Button btn = new Button(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, dpToPx(10));
        btn.setLayoutParams(params);

        btn.setText(text);
        btn.setAllCaps(false);
        btn.setTypeface(Typeface.SERIF);
        btn.setTextColor(currentTextColor);
        btn.setTextSize(15.5f);

        boolean isContinueBtn = text.trim().equals("Далее") || text.trim().equals("Далее ➔");
        btn.setGravity(isContinueBtn ? Gravity.CENTER : (Gravity.CENTER_VERTICAL | Gravity.START));
        btn.setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12));

        GradientDrawable btnDrawable = new GradientDrawable();
        btnDrawable.setColor(currentBtnBgColor);
        btnDrawable.setCornerRadius(dpToPx(10));
        btnDrawable.setStroke(dpToPx(1), currentBtnStrokeColor);
        btn.setBackground(btnDrawable);

        btn.setOnClickListener(v -> loadScene(nextSceneId));
        return btn;
    }

    private void showSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LinearLayout container = createDialogContainer(currentCardBgColor, currentCardStrokeColor);

        TextView titleView = createDialogTitle("Настройки", currentHeaderColor);
        container.addView(titleView);
        container.addView(createDialogDivider(currentCardStrokeColor));

        SharedPreferences prefs = getSharedPreferences("QuestProgress", MODE_PRIVATE);

        // 1. Ползунок: Скорость текста
        TextView tvSpeedLabel = createSliderLabel("Скорость текста: " + getSpeedTextDescription(speedProgress));
        SeekBar sbSpeed = createStyledSeekBar(40, speedProgress);
        sbSpeed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                speedProgress = progress;
                typingDelay = (40 - progress);
                tvSpeedLabel.setText("Скорость текста: " + getSpeedTextDescription(progress));
                prefs.edit().putInt("speed_progress", speedProgress).apply();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        container.addView(tvSpeedLabel);
        container.addView(sbSpeed);

        // 2. Ползунок: Громкость
        TextView tvVolumeLabel = createSliderLabel("Громкость: " + musicVolume + "%");
        SeekBar sbVolume = createStyledSeekBar(100, musicVolume);
        sbVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                musicVolume = progress;
                tvVolumeLabel.setText("Громкость: " + musicVolume + "%");
                applyMusicVolume();
                prefs.edit().putInt("music_volume", musicVolume).apply();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        container.addView(tvVolumeLabel);
        container.addView(sbVolume);

        // 3. Ползунок: Размер текста
        int initialFontProgress = Math.max(0, Math.min(10, Math.round(storyFontSize - 14f)));
        TextView tvSizeLabel = createSliderLabel("Размер текста: " + Math.round(storyFontSize));
        SeekBar sbSize = createStyledSeekBar(10, initialFontProgress);
        sbSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                storyFontSize = 14f + progress;
                tvSizeLabel.setText("Размер текста: " + Math.round(storyFontSize));
                tvStoryText.setTextSize(storyFontSize);
                prefs.edit().putFloat("story_font_size", storyFontSize).apply();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        container.addView(tvSizeLabel);
        container.addView(sbSize);

        container.addView(createDialogDivider(currentCardStrokeColor));

        builder.setView(container);
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        Button btnRestart = createDialogButton(
                "Пройти историю с начала.",
                currentBtnBgColor,
                currentBtnStrokeColor,
                currentChapterColor,
                Gravity.CENTER
        );
        btnRestart.setOnClickListener(v -> {
            dialog.dismiss();
            showStylizedResetConfirmDialog();
        });
        container.addView(btnRestart);

        Button btnOpenSite = createDialogButton(
                "Открыть сайт проекта",
                currentBgDeepColor,
                currentCardStrokeColor,
                currentHeaderColor,
                Gravity.CENTER
        );
        btnOpenSite.setOnClickListener(v -> {
            dialog.dismiss();
            android.content.Intent intent = new android.content.Intent(MainActivity.this, WebViewActivity.class);
            startActivity(intent);
        });
        container.addView(btnOpenSite);

        Button btnClose = createDialogButton(
                "Закрыть",
                currentBgDeepColor,
                currentCardStrokeColor,
                currentHintColor,
                Gravity.CENTER
        );
        btnClose.setOnClickListener(v -> dialog.dismiss());
        container.addView(btnClose);

        dialog.show();
    }

    private void showStylizedResetConfirmDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LinearLayout container = createDialogContainer(currentCardBgColor, currentCardStrokeColor);
        container.setGravity(Gravity.CENTER_HORIZONTAL);

        // Стилизованная круглая иконка-символ в стиле Цитадели
        TextView iconBadge = new TextView(this);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dpToPx(58), dpToPx(58));
        iconParams.setMargins(0, dpToPx(4), 0, dpToPx(12));
        iconBadge.setLayoutParams(iconParams);
        iconBadge.setText("↺");
        iconBadge.setTextSize(26f);
        iconBadge.setTextColor(currentHeaderColor);
        iconBadge.setGravity(Gravity.CENTER);

        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setShape(GradientDrawable.OVAL);
        badgeBg.setColor(currentBtnBgColor);
        badgeBg.setStroke(dpToPx(2), currentBtnStrokeColor);
        iconBadge.setBackground(badgeBg);
        container.addView(iconBadge);

        TextView titleView = createDialogTitle("Пройти историю с начала?", currentHeaderColor);
        container.addView(titleView);

        TextView messageView = new TextView(this);
        messageView.setText("Весь текущий прогресс чтения будет сброшен, а непрочитанные главы снова закроются до их прохождения.");
        messageView.setTypeface(Typeface.SERIF);
        messageView.setTextColor(currentTextColor);
        messageView.setTextSize(15f);
        messageView.setGravity(Gravity.CENTER);
        messageView.setPadding(dpToPx(6), dpToPx(4), dpToPx(6), dpToPx(14));
        container.addView(messageView);

        container.addView(createDialogDivider(currentCardStrokeColor));

        builder.setView(container);
        AlertDialog confirmDialog = builder.create();
        if (confirmDialog.getWindow() != null) {
            confirmDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        Button btnConfirm = createDialogButton(
                "Да, сбросить прогресс",
                currentBtnBgColor,
                currentBtnStrokeColor,
                currentChapterColor,
                Gravity.CENTER
        );
        btnConfirm.setOnClickListener(v -> {
            confirmDialog.dismiss();
            resetStoryToBeginning();
        });
        container.addView(btnConfirm);

        Button btnCancel = createDialogButton(
                "Отмена",
                currentBgDeepColor,
                currentCardStrokeColor,
                currentHintColor,
                Gravity.CENTER
        );
        btnCancel.setOnClickListener(v -> confirmDialog.dismiss());
        container.addView(btnCancel);

        confirmDialog.show();
    }


    @SuppressWarnings("deprecation")
    private void showStylizedNoticeToast(String symbol, String message) {
        LinearLayout toastRoot = new LinearLayout(this);
        toastRoot.setOrientation(LinearLayout.HORIZONTAL);
        toastRoot.setGravity(Gravity.CENTER_VERTICAL);
        int padH = dpToPx(16);
        int padV = dpToPx(12);
        toastRoot.setPadding(padH, padV, padH, padV);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(currentCardBgColor);
        bg.setCornerRadius(dpToPx(14));
        bg.setStroke(dpToPx(2), currentBtnStrokeColor);
        toastRoot.setBackground(bg);

        TextView iconView = new TextView(this);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dpToPx(34), dpToPx(34));
        iconParams.setMargins(0, 0, dpToPx(12), 0);
        iconView.setLayoutParams(iconParams);
        iconView.setText(symbol);
        iconView.setTextSize(16f);
        iconView.setTextColor(currentHeaderColor);
        iconView.setGravity(Gravity.CENTER);

        GradientDrawable iconCircle = new GradientDrawable();
        iconCircle.setShape(GradientDrawable.OVAL);
        iconCircle.setColor(currentBtnBgColor);
        iconCircle.setStroke(dpToPx(1), currentCardStrokeColor);
        iconView.setBackground(iconCircle);

        TextView textView = new TextView(this);
        textView.setText(message);
        textView.setTypeface(Typeface.SERIF);
        textView.setTextColor(currentTextColor);
        textView.setTextSize(14.5f);

        toastRoot.addView(iconView);
        toastRoot.addView(textView);

        Toast toast = new Toast(getApplicationContext());
        toast.setDuration(Toast.LENGTH_SHORT);
        toast.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, dpToPx(48));
        toast.setView(toastRoot);
        toast.show();
    }

    private String getSpeedTextDescription(int progress) {
        if (progress >= 38) return "Мгновенно";
        if (progress >= 26) return "Быстро";
        if (progress >= 14) return "Средне";
        return "Медленно";
    }

    private void showBookSelectionDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LinearLayout container = createDialogContainer(currentCardBgColor, currentCardStrokeColor);

        TextView titleView = createDialogTitle("Книги трилогии", currentHeaderColor);
        container.addView(titleView);
        container.addView(createDialogDivider(currentCardStrokeColor));

        builder.setView(container);
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        String[] bookTitles = {
                "Книга 1. Жнецы страданий",
                "Книга 2. Наследники скорби",
                "Книга 3. Пленники раздора"
        };
        int[] firstScenes = {101, 201, 301};
        int[] bookBgColors = {
                Color.parseColor("#2E1917"),
                Color.parseColor("#162838"),
                Color.parseColor("#241B35")
        };
        int[] bookBorderColors = {
                Color.parseColor("#8A423B"),
                Color.parseColor("#3B698C"),
                Color.parseColor("#63488C")
        };
        int[] bookTitleColors = {
                Color.parseColor("#E8C39E"),
                Color.parseColor("#B5D3EB"),
                Color.parseColor("#D0BEE8")
        };

        for (int i = 0; i < 3; i++) {
            final int bookId = i + 1;
            final String title = bookTitles[i];
            boolean isBookStarted = isSceneUnlocked(firstScenes[i]);

            String displayLabel = isBookStarted ? title : ("🔒  " + title);
            int textColor = isBookStarted ? bookTitleColors[i] : Color.parseColor("#77706B");

            Button bookBtn = createDialogButton(
                    displayLabel,
                    bookBgColors[i],
                    bookBorderColors[i],
                    textColor,
                    Gravity.START | Gravity.CENTER_VERTICAL
            );

            bookBtn.setOnClickListener(v -> {
                dialog.dismiss();
                showChaptersForBookDialog(bookId, title);
            });
            container.addView(bookBtn);
        }

        container.addView(createDialogDivider(currentCardStrokeColor));

        Button btnClose = createDialogButton(
                "Закрыть",
                currentBgDeepColor,
                currentCardStrokeColor,
                currentHintColor,
                Gravity.CENTER
        );
        btnClose.setOnClickListener(v -> dialog.dismiss());
        container.addView(btnClose);

        dialog.show();
    }

    private void showChaptersForBookDialog(int bookId, String bookFullTitle) {
        int dlgCardBg;
        int dlgStroke;
        int dlgHeader;
        int dlgBtnBg;
        int dlgBtnStroke;
        int dlgText;

        if (bookId == 2) {
            dlgCardBg = Color.parseColor("#111B27");
            dlgStroke = Color.parseColor("#2E5370");
            dlgHeader = Color.parseColor("#8AB4D6");
            dlgBtnBg = Color.parseColor("#162838");
            dlgBtnStroke = Color.parseColor("#3B698C");
            dlgText = Color.parseColor("#E1EAF2");
        } else if (bookId == 3) {
            dlgCardBg = Color.parseColor("#191326");
            dlgStroke = Color.parseColor("#4E386E");
            dlgHeader = Color.parseColor("#B399D4");
            dlgBtnBg = Color.parseColor("#241B35");
            dlgBtnStroke = Color.parseColor("#63488C");
            dlgText = Color.parseColor("#EAE3F2");
        } else {
            dlgCardBg = Color.parseColor("#171311");
            dlgStroke = Color.parseColor("#6B3A32");
            dlgHeader = Color.parseColor("#D7B377");
            dlgBtnBg = Color.parseColor("#2E1917");
            dlgBtnStroke = Color.parseColor("#8A423B");
            dlgText = Color.parseColor("#E6DFD5");
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LinearLayout container = createDialogContainer(dlgCardBg, dlgStroke);

        TextView titleView = createDialogTitle(bookFullTitle, dlgHeader);
        container.addView(titleView);
        container.addView(createDialogDivider(dlgStroke));

        ScrollView chaptersScroll = new ScrollView(this);
        chaptersScroll.setVerticalScrollBarEnabled(false);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(340)
        );
        chaptersScroll.setLayoutParams(scrollParams);

        LinearLayout listLayout = new LinearLayout(this);
        listLayout.setOrientation(LinearLayout.VERTICAL);
        chaptersScroll.addView(listLayout);

        builder.setView(container);
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        Cursor cursor = dbHelper.getChaptersByBook(bookId);
        if (cursor != null) {
            while (cursor.moveToNext()) {
                String chTitle = cursor.getString(cursor.getColumnIndexOrThrow(StoryDatabaseHelper.COL_CHAPTER_TITLE));
                int scId = cursor.getInt(cursor.getColumnIndexOrThrow(StoryDatabaseHelper.COL_SCENE_ID));
                boolean unlocked = isSceneUnlocked(scId);

                String label = unlocked ? ("•  " + chTitle) : ("🔒  " + chTitle);
                int bg = unlocked ? dlgBtnBg : Color.parseColor("#121010");
                int stroke = unlocked ? dlgBtnStroke : Color.parseColor("#2E2927");
                int txtColor = unlocked ? dlgText : Color.parseColor("#665F5A");

                Button chBtn = createDialogButton(
                        label,
                        bg,
                        stroke,
                        txtColor,
                        Gravity.START | Gravity.CENTER_VERTICAL
                );

                chBtn.setOnClickListener(v -> {
                    if (unlocked) {
                        dialog.dismiss();
                        loadScene(scId);
                    } else {
                        showStylizedNoticeToast("🔒", "Глава закрыта. Дойдите до неё по сюжету.");
                    }
                });
                listLayout.addView(chBtn);
            }
            cursor.close();
        }

        container.addView(chaptersScroll);
        container.addView(createDialogDivider(dlgStroke));

        Button btnBackToBooks = createDialogButton(
                "← К списку книг",
                dlgBtnBg,
                dlgBtnStroke,
                dlgHeader,
                Gravity.CENTER
        );
        btnBackToBooks.setOnClickListener(v -> {
            dialog.dismiss();
            showBookSelectionDialog();
        });
        container.addView(btnBackToBooks);

        Button btnClose = createDialogButton(
                "Закрыть",
                currentBgDeepColor,
                dlgStroke,
                currentHintColor,
                Gravity.CENTER
        );
        btnClose.setOnClickListener(v -> dialog.dismiss());
        container.addView(btnClose);

        dialog.show();
    }

    private LinearLayout createDialogContainer(int bgColor, int strokeColor) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = dpToPx(20);
        container.setPadding(pad, pad, pad, pad);

        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(bgColor);
        drawable.setCornerRadius(dpToPx(16));
        drawable.setStroke(dpToPx(2), strokeColor);
        container.setBackground(drawable);
        return container;
    }

    private TextView createDialogTitle(String title, int color) {
        TextView tv = new TextView(this);
        tv.setText(title);
        tv.setTypeface(Typeface.SERIF, Typeface.BOLD);
        tv.setTextSize(19f);
        tv.setTextColor(color);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(0, 0, 0, dpToPx(10));
        return tv;
    }

    private View createDialogDivider(int color) {
        View divider = new View(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(1)
        );
        params.setMargins(0, dpToPx(8), 0, dpToPx(12));
        divider.setLayoutParams(params);
        divider.setBackgroundColor(color);
        return divider;
    }

    private TextView createSliderLabel(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTypeface(Typeface.SERIF);
        tv.setTextColor(currentTextColor);
        tv.setTextSize(15f);
        tv.setPadding(0, dpToPx(6), 0, dpToPx(4));
        return tv;
    }

    private SeekBar createStyledSeekBar(int max, int progress) {
        SeekBar seekBar = new SeekBar(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, dpToPx(10));
        seekBar.setLayoutParams(params);
        seekBar.setMax(max);
        seekBar.setProgress(progress);
        seekBar.setProgressTintList(ColorStateList.valueOf(currentHeaderColor));
        seekBar.setThumbTintList(ColorStateList.valueOf(currentChapterColor));
        seekBar.setProgressBackgroundTintList(ColorStateList.valueOf(currentBtnStrokeColor));
        return seekBar;
    }

    private Button createDialogButton(String text, int bgColor, int strokeColor, int textColor, int gravity) {
        Button btn = new Button(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, dpToPx(8));
        btn.setLayoutParams(params);

        btn.setText(text);
        btn.setAllCaps(false);
        btn.setTypeface(Typeface.SERIF);
        btn.setTextColor(textColor);
        btn.setTextSize(15f);
        btn.setGravity(gravity);
        btn.setPadding(dpToPx(14), dpToPx(10), dpToPx(14), dpToPx(10));

        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(bgColor);
        drawable.setCornerRadius(dpToPx(10));
        drawable.setStroke(dpToPx(1), strokeColor);
        btn.setBackground(drawable);

        return btn;
    }
}