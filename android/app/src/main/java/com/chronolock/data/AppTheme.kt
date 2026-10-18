package com.chronolock.data

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
    DARK("다크", "짙은 차콜 바탕과 라임 포인트", true,
        0xFF10141F, 0xFF1B2231, 0xFFB8F27E, 0xFF10141F, 0xFFF4F6FB, 0xFFACB7CB),
    LIGHT("화이트", "밝고 선명한 흰색 바탕", false,
        0xFFF4F6F8, 0xFFFFFFFF, 0xFF365E37, 0xFFFFFFFF, 0xFF1B2520, 0xFF56645D),
    OCEAN("오션", "깊은 네이비와 시원한 블루", true,
        0xFF0B1C2B, 0xFF132D42, 0xFF71D7F5, 0xFF092333, 0xFFE5F3FC, 0xFFA8BED2),
    FOREST("포레스트", "차분한 세이지 바탕과 짙은 그린", false,
        0xFFC6D9C8, 0xFFD6E4D5, 0xFF245833, 0xFFFFFFFF, 0xFF183824, 0xFF3F5847),
    LAVENDER("라벤더", "보랏빛을 채운 부드러운 라벤더", false,
        0xFFD0BCE3, 0xFFDECCED, 0xFF593080, 0xFFFFFFFF, 0xFF302039, 0xFF584465);

    companion object {
        fun fromStored(value: String?): AppTheme = values().firstOrNull { it.name == value } ?: DARK
    }
}
