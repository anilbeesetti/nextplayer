package dev.anilbeesetti.nextplayer.feature.videopicker.composables

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

@Composable
fun SegmentedSelectableButton(
    labelOne: @Composable RowScope.() -> Unit,
    labelTwo: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    iconOne: @Composable (() -> Unit)? = null,
    iconTwo: @Composable (() -> Unit)? = null,
    selected: ChipSelected,
    color: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
    selectedColor: Color = MaterialTheme.colorScheme.primary,
    onClick: (ChipSelected) -> Unit = { },
    contentPadding: PaddingValues = PaddingValues(10.dp)
) {
    val borderShapeOne = RoundedCornerShape(
        topStart = 8.dp,
        bottomStart = 8.dp,
        topEnd = 0.dp,
        bottomEnd = 0.dp
    )
    val borderShapeTwo = RoundedCornerShape(
        topStart = 0.dp,
        bottomStart = 0.dp,
        topEnd = 8.dp,
        bottomEnd = 8.dp
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .selectableGroup()
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .border(
                    width = 1.dp,
                    color = if (selected == ChipSelected.ONE) selectedColor else color,
                    shape = borderShapeOne
                )
                .clip(borderShapeOne)
                .selectable(
                    selected = selected == ChipSelected.ONE,
                    onClick = { onClick(ChipSelected.ONE) },
                    role = Role.RadioButton
                )
                .padding(contentPadding)
        ) {
            iconOne?.invoke()
            if (iconOne != null) Spacer(modifier = Modifier.width(8.dp))
            labelOne()
        }
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .border(
                    width = 1.dp,
                    color = if (selected == ChipSelected.TWO) selectedColor else color,
                    shape = borderShapeTwo
                )
                .clip(borderShapeTwo)
                .selectable(
                    selected = selected == ChipSelected.TWO,
                    onClick = { onClick(ChipSelected.TWO) },
                    role = Role.RadioButton
                )
                .padding(contentPadding)
        ) {
            iconTwo?.invoke()
            if (iconTwo != null) Spacer(modifier = Modifier.width(8.dp))
            labelTwo()
        }
    }
}
