package ru.rzk.schedule.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring

/**
 * Пружинная физика в стиле iOS (UIKit spring, без лишнего отскока: dampingRatio = 1f)
 * и плавные кривые для переходов между экранами.
 */
object Motion {
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    fun <T> iosSpring(stiffness: Float = 380f) = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = stiffness,
    )

    fun <T> iosSnappySpring() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 520f,
    )
}
