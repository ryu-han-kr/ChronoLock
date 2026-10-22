package com.chronolock.data

// Palettes follow open-source schemes (Flexoki, Nord, Everforest, Rosé Pine); muted and accent
// tones are nudged where needed so every text pairing stays at WCAG AA (see ThemePaletteTest).
// Enum names are persisted, so keep them stable even when a palette changes.
enum class AppTheme(
    val title: String,
    val description: String,
    val isDark: Boolean,
    val background: Long,
    val surface: Long,
    val primary: Long,
    val onPrimary: Long,
    val text: Long,
    val muted: Long,
) {
    DARK("먹", "따뜻한 먹색 바탕과 주황 포인트", true,
        0xFF100F0F, 0xFF1C1B1A, 0xFFDA702C, 0xFF100F0F, 0xFFCECDC3, 0xFF878580),
    LIGHT("종이", "미색 종이 바탕과 잉크 블루", false,
        0xFFF2F0E5, 0xFFFFFCF0, 0xFF205EA6, 0xFFFFFCF0, 0xFF100F0F, 0xFF6A6964),
    OCEAN("북극", "차가운 슬레이트와 서리 블루", true,
        0xFF2E3440, 0xFF3B4252, 0xFF88C0D0, 0xFF2E3440, 0xFFECEFF4, 0xFFAEB6C4),
    FOREST("숲", "어두운 이끼색과 세이지 그린", true,
        0xFF2D353B, 0xFF343F44, 0xFFA7C080, 0xFF2D353B, 0xFFD3C6AA, 0xFFA3AEA5),
    LAVENDER("새벽", "따뜻한 새벽빛 바탕과 보랏빛 포인트", false,
        0xFFFAF4ED, 0xFFFFFAF3, 0xFF6E5A8C, 0xFFFFFAF3, 0xFF575279, 0xFF6E6A86);

    companion object {
        fun fromStored(value: String?): AppTheme = values().firstOrNull { it.name == value } ?: DARK
    }
}
