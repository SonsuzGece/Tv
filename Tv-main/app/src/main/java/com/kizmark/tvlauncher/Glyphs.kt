package com.kizmark.tvlauncher

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp

enum class Glyph(@DrawableRes val resId: Int) {
    GRID(R.drawable.ic_grid),
    SLIDERS(R.drawable.ic_sliders),
    PLAY(R.drawable.ic_play),
    PAUSE(R.drawable.ic_pause),
    NEXT(R.drawable.ic_next),
    PREV(R.drawable.ic_prev),
    WIFI(R.drawable.ic_wifi),
    SPEAKER(R.drawable.ic_speaker),
    IMAGE(R.drawable.ic_image),
    CLOCK(R.drawable.ic_clock),
    DISPLAY(R.drawable.ic_display),
    BAG(R.drawable.ic_bag),
    GEAR(R.drawable.ic_gear),
    TYPE(R.drawable.ic_type),
    MUSIC(R.drawable.ic_music),
    LAYERS(R.drawable.ic_layers),
    BLUETOOTH(R.drawable.ic_bluetooth),
    INPUT(R.drawable.ic_input),
    MOON(R.drawable.ic_moon),
    ACCESS(R.drawable.ic_access),
    BELL(R.drawable.ic_bell),
    POWER(R.drawable.ic_power),
    SEARCH(R.drawable.ic_search),
    CAST(R.drawable.ic_cast),
    MIC(R.drawable.ic_mic),
    HOME(R.drawable.ic_home),
    BOLT(R.drawable.ic_bolt),
    USER(R.drawable.ic_user),
    LEFT(R.drawable.ic_left),
    RIGHT(R.drawable.ic_right),
    INFO(R.drawable.ic_info),
    TRASH(R.drawable.ic_trash),
    MOVE(R.drawable.ic_move),
    PIN(R.drawable.ic_pin),
    RESTART(R.drawable.ic_restart),
    TV(R.drawable.ic_tv),
    EYE_OFF(R.drawable.ic_eye_off),
    CHECK(R.drawable.ic_check),
    ETHERNET(R.drawable.ic_ethernet),
    SHUFFLE(R.drawable.ic_shuffle)
}

@Composable
fun GlyphIcon(glyph: Glyph, size: Dp, color: Color, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(glyph.resId),
        contentDescription = null,
        colorFilter = ColorFilter.tint(color),
        modifier = modifier.size(size)
    )
}
