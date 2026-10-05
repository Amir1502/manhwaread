package com.manhwaread.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Формы и радиусы скругления в стиле Mangalib:
// - 12.dp: карточки тайтлов, диалоги, нижние шторки;
// - 8.dp: кнопки, текстовые поля ввода, чипы фильтрации;
// - 6.dp: бейджи (18+, тип тайтла), чипы статусов чтения.
object ManhwareadRadius {
    val Badge = 6.dp
    val Chip = 6.dp
    val Button = 8.dp
    val Input = 8.dp
    val Card = 12.dp
    val Dialog = 12.dp
    val BottomSheet = 16.dp
}

object ManhwareadShapes {
    val Badge = RoundedCornerShape(ManhwareadRadius.Badge)
    val Chip = RoundedCornerShape(ManhwareadRadius.Chip)
    val Button = RoundedCornerShape(ManhwareadRadius.Button)
    val Input = RoundedCornerShape(ManhwareadRadius.Input)
    val Card = RoundedCornerShape(ManhwareadRadius.Card)
    val Dialog = RoundedCornerShape(ManhwareadRadius.Dialog)
    val BottomSheet = RoundedCornerShape(
        topStart = ManhwareadRadius.BottomSheet,
        topEnd = ManhwareadRadius.BottomSheet,
    )

    val MaterialShapes = Shapes(
        extraSmall = Badge,
        small = Button,
        medium = Card,
        large = RoundedCornerShape(ManhwareadRadius.BottomSheet),
        extraLarge = RoundedCornerShape(28.dp),
    )
}
