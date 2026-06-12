package app.wakepact.core.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Tiny in-app icon set. Glyph geometry follows the Apache-2.0 licensed
 * Material Design Icons; built directly with [ImageVector.Builder] so we don't
 * depend on the frozen material-icons artifacts (see docs/STACK.md, rejected
 * candidates). Icons render at 24dp and tint via LocalContentColor as usual.
 */
object WakeIcons {

    val Add: ImageVector by lazy {
        icon("wake_add", "M19,13H13V19H11V13H5V11H11V5H13V11H19V13Z")
    }

    val Back: ImageVector by lazy {
        icon("wake_back", "M20,11H7.83L13.42,5.41L12,4L4,12L12,20L13.41,18.59L7.83,13H20V11Z")
    }

    val Check: ImageVector by lazy {
        icon("wake_check", "M9,16.17L4.83,12L3.41,13.41L9,19L21,7L19.59,5.59L9,16.17Z")
    }

    val Close: ImageVector by lazy {
        icon(
            "wake_close",
            "M19,6.41L17.59,5L12,10.59L6.41,5L5,6.41L10.59,12L5,17.59L6.41,19L12,13.41L17.59,19L19,17.59L13.41,12L19,6.41Z",
        )
    }

    val Delete: ImageVector by lazy {
        icon(
            "wake_delete",
            "M6,19C6,20.1 6.9,21 8,21H16C17.1,21 18,20.1 18,19V7H6V19M19,4H15.5L14.5,3H9.5L8.5,4H5V6H19V4Z",
        )
    }

    val People: ImageVector by lazy {
        icon(
            "wake_people",
            "M16,11C17.66,11 18.99,9.66 18.99,8C18.99,6.34 17.66,5 16,5C14.34,5 13,6.34 13,8C13,9.66 " +
                "14.34,11 16,11M8,11C9.66,11 10.99,9.66 10.99,8C10.99,6.34 9.66,5 8,5C6.34,5 5,6.34 5,8C5,9.66 " +
                "6.34,11 8,11M8,13C5.67,13 1,14.17 1,16.5V19H15V16.5C15,14.17 10.33,13 8,13M16,13C15.71,13 " +
                "15.38,13.02 15.03,13.05C16.19,13.89 17,15.02 17,16.5V19H23V16.5C23,14.17 18.33,13 16,13Z",
        )
    }

    val Alarm: ImageVector by lazy {
        icon(
            "wake_alarm",
            "M12,22C13.1,22 14,21.1 14,20H10C10,21.1 10.9,22 12,22M18,16V11C18,7.93 16.36,5.36 13.5,4.68V4C13.5," +
                "3.17 12.83,2.5 12,2.5C11.17,2.5 10.5,3.17 10.5,4V4.68C7.63,5.36 6,7.92 6,11V16L4,18V19H20V18L18,16Z",
        )
    }

    val Walk: ImageVector by lazy {
        icon(
            "wake_walk",
            "M13.5,5.5C14.6,5.5 15.5,4.6 15.5,3.5C15.5,2.4 14.6,1.5 13.5,1.5C12.4,1.5 11.5,2.4 11.5,3.5C11.5," +
                "4.6 12.4,5.5 13.5,5.5M9.8,8.9L7,23H9.1L10.9,15L13,17V23H15V15.5L12.9,13.5L13.5,10.5C14.8,12 " +
                "16.8,13 19,13V11C17.1,11 15.5,10 14.7,8.6L13.7,7C13.3,6.4 12.7,6 12,6C11.7,6 11.5,6.1 11.2,6.1L6," +
                "8.3V13H8V9.6L9.8,8.9Z",
        )
    }

    private fun icon(name: String, pathData: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(
            pathData = addPathNodes(pathData),
            fill = SolidColor(Color.Black),
        ).build()
}
