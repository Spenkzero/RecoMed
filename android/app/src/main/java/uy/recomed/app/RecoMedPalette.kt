package uy.recomed.app

internal data class RecoMedPalette(
    val background: Int,
    val surface: Int,
    val primaryText: Int,
    val secondaryText: Int,
    val accent: Int,
    val accentSoft: Int,
    val border: Int,
    val danger: Int,
    val dangerSoft: Int
) {
    companion object {
        fun forMode(isDark: Boolean) = if (isDark) {
            RecoMedPalette(
                background = 0xFF10221D.toInt(),
                surface = 0xFF19342C.toInt(),
                primaryText = 0xFFF4F8F5.toInt(),
                secondaryText = 0xFFB3C6BC.toInt(),
                accent = 0xFF8AC8A9.toInt(),
                accentSoft = 0xFF244A3F.toInt(),
                border = 0xFF315447.toInt(),
                danger = 0xFFFFB4A8.toInt(),
                dangerSoft = 0xFF4A2824.toInt()
            )
        } else {
            RecoMedPalette(
                background = 0xFFF7F8F2.toInt(),
                surface = 0xFFFFFFFF.toInt(),
                primaryText = 0xFF18332E.toInt(),
                secondaryText = 0xFF64746E.toInt(),
                accent = 0xFF176B5B.toInt(),
                accentSoft = 0xFFDFF2E9.toInt(),
                border = 0xFFDCE7E0.toInt(),
                danger = 0xFFC45D50.toInt(),
                dangerSoft = 0xFFFFEBE6.toInt()
            )
        }
    }
}
