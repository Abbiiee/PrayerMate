package com.ebaa.prayermate;

public class PrayerTime {
    private String name;
    private String time;
    private String emoji; // تغيير من iconResId إلى emoji للإيموجي
    private int iconResId; // للاحتفاظ بالخاصية القديمة للتوافق

    // Constructor جديد للإيموجي
    public PrayerTime(String name, String time, String emoji) {
        this.name = name;
        this.time = time;
        this.emoji = emoji;
        this.iconResId = 0; // default value
    }

    // Constructor قديم للتوافق
    public PrayerTime(String name, String time, int iconResId) {
        this.name = name;
        this.time = time;
        this.iconResId = iconResId;
        this.emoji = "🕌"; // default emoji
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public String getEmoji() {
        return emoji;
    }

    public void setEmoji(String emoji) {
        this.emoji = emoji;
    }

    public int getIconResId() {
        return iconResId;
    }

    public void setIconResId(int iconResId) {
        this.iconResId = iconResId;
    }

    // Helper method to check if using emoji or icon
    public boolean hasEmoji() {
        return emoji != null && !emoji.isEmpty() && !emoji.equals("🕌");
    }
}