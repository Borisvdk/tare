package app.tare.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.tare.R

private fun geist(weight: Int) = Font(
    R.font.geist,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Geist for everything functional; numbers use tabular figures so columns line up. */
val Geist = FontFamily(geist(400), geist(500), geist(600), geist(700))

/** Instrument Serif for the few editorial moments (day headline, empty states). */
val InstrumentSerif = FontFamily(
    Font(R.font.instrument_serif),
    Font(R.font.instrument_serif_italic, style = FontStyle.Italic),
)

private const val TABULAR = "tnum"

@Immutable
data class TareType(
    /** The one big number on a screen (calories left). */
    val hero: TextStyle,
    /** Large secondary numbers (weight, sheet amount). */
    val number: TextStyle,
    /** Editorial serif headline ("Today"). */
    val headline: TextStyle,
    val title: TextStyle,
    val body: TextStyle,
    val bodyStrong: TextStyle,
    val label: TextStyle,
    val caption: TextStyle,
    /** Small tabular numbers in rows. */
    val figure: TextStyle,
    val overline: TextStyle,
) {
    val material: Typography
        get() = Typography(
            displayLarge = hero,
            displayMedium = number,
            displaySmall = number.copy(fontSize = 32.sp),
            headlineLarge = headline,
            headlineMedium = headline.copy(fontSize = 30.sp),
            headlineSmall = title.copy(fontSize = 22.sp),
            titleLarge = title.copy(fontSize = 20.sp),
            titleMedium = title,
            titleSmall = bodyStrong,
            bodyLarge = body.copy(fontSize = 16.sp),
            bodyMedium = body,
            bodySmall = caption,
            labelLarge = label.copy(fontSize = 15.sp),
            labelMedium = label,
            labelSmall = caption,
        )
}

val TareTypography = TareType(
    hero = TextStyle(
        fontFamily = Geist,
        fontWeight = FontWeight(600),
        fontSize = 60.sp,
        lineHeight = 60.sp,
        letterSpacing = (-0.045).em,
        fontFeatureSettings = TABULAR,
    ),
    number = TextStyle(
        fontFamily = Geist,
        fontWeight = FontWeight(600),
        fontSize = 40.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.035).em,
        fontFeatureSettings = TABULAR,
    ),
    headline = TextStyle(
        fontFamily = InstrumentSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 38.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.01).em,
    ),
    title = TextStyle(
        fontFamily = Geist,
        fontWeight = FontWeight(600),
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.01).em,
    ),
    body = TextStyle(
        fontFamily = Geist,
        fontWeight = FontWeight(400),
        fontSize = 15.sp,
        lineHeight = 21.sp,
    ),
    bodyStrong = TextStyle(
        fontFamily = Geist,
        fontWeight = FontWeight(500),
        fontSize = 15.sp,
        lineHeight = 21.sp,
    ),
    label = TextStyle(
        fontFamily = Geist,
        fontWeight = FontWeight(500),
        fontSize = 13.sp,
        lineHeight = 17.sp,
    ),
    caption = TextStyle(
        fontFamily = Geist,
        fontWeight = FontWeight(400),
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    figure = TextStyle(
        fontFamily = Geist,
        fontWeight = FontWeight(500),
        fontSize = 15.sp,
        lineHeight = 20.sp,
        fontFeatureSettings = TABULAR,
    ),
    overline = TextStyle(
        fontFamily = Geist,
        fontWeight = FontWeight(600),
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.08.em,
    ),
)
