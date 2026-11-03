package ru.rzk.schedule.ui

import androidx.compose.animation.core.CubicBezierEasing

/**
 * Кривые движения из Material 3 («emphasized»): быстрый старт и мягкое замедление при появлении,
 * плавное ускорение при уходе. Именно такие кривые делают переходы «дорогими», а не механическими.
 */
object Motion {
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
}
