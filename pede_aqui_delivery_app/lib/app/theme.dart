import 'package:flutter/material.dart';

import '../core/constants/app_colors.dart';
import '../core/constants/app_spacing.dart';

/// App theme mirrored from the web (`pede-aqui-delivery`):
/// Fraunces (serif) for display/section headers, Plus Jakarta Sans for
/// everything else; ember-orange primary, forest-green secondary chrome,
/// warm cream surfaces.
class AppTheme {
  static const String _display = 'Fraunces';
  static const String _body = 'Plus Jakarta Sans';

  static TextTheme _textTheme({required Color onSurface}) => TextTheme(
        displayLarge: TextStyle(fontFamily: _display, fontWeight: FontWeight.w900, fontSize: 44, height: 1.05, color: onSurface),
        displayMedium: TextStyle(fontFamily: _display, fontWeight: FontWeight.w800, fontSize: 34, height: 1.1, color: onSurface),
        headlineLarge: TextStyle(fontFamily: _display, fontWeight: FontWeight.w800, fontSize: 28, height: 1.15, color: onSurface),
        headlineMedium: TextStyle(fontFamily: _display, fontWeight: FontWeight.w700, fontSize: 22, height: 1.25, color: onSurface),
        headlineSmall: TextStyle(fontFamily: _body, fontWeight: FontWeight.w700, fontSize: 18, height: 1.3, color: onSurface),
        titleLarge: TextStyle(fontFamily: _body, fontWeight: FontWeight.w700, fontSize: 16, height: 1.35, color: onSurface),
        titleMedium: TextStyle(fontFamily: _body, fontWeight: FontWeight.w600, fontSize: 15, height: 1.4, color: onSurface),
        bodyLarge: TextStyle(fontFamily: _body, fontWeight: FontWeight.w400, fontSize: 16, height: 1.5, color: onSurface),
        bodyMedium: TextStyle(fontFamily: _body, fontWeight: FontWeight.w400, fontSize: 14, height: 1.5, color: onSurface),
        bodySmall: TextStyle(fontFamily: _body, fontWeight: FontWeight.w400, fontSize: 12, height: 1.45, color: onSurface),
        labelLarge: TextStyle(fontFamily: _body, fontWeight: FontWeight.w700, fontSize: 14, height: 1.2, color: onSurface),
        labelMedium: TextStyle(fontFamily: _body, fontWeight: FontWeight.w600, fontSize: 12, height: 1.2, letterSpacing: 0.02, color: onSurface),
      );

  static ThemeData light() {
    return _base(
      brightness: Brightness.light,
      scheme: const ColorScheme(
        brightness: Brightness.light,
        primary: AppColors.primary,
        onPrimary: Colors.white,
        primaryContainer: AppColors.primaryContainer,
        onPrimaryContainer: AppColors.onPrimaryFixed,
        secondary: AppColors.secondary,
        onSecondary: Colors.white,
        secondaryContainer: AppColors.secondaryContainer,
        onSecondaryContainer: AppColors.onSecondaryContainer,
        tertiary: AppColors.tertiary,
        onTertiary: Colors.white,
        tertiaryContainer: AppColors.tertiaryContainer,
        onTertiaryContainer: AppColors.onTertiaryContainer,
        error: AppColors.error,
        onError: AppColors.onError,
        errorContainer: AppColors.errorContainer,
        onErrorContainer: AppColors.error,
        surface: AppColors.surface,
        onSurface: AppColors.text,
        onSurfaceVariant: AppColors.mutedText,
        outline: AppColors.outline,
        outlineVariant: AppColors.border,
      ),
      scaffoldBackground: AppColors.background,
      onSurface: AppColors.text,
      mutedText: AppColors.mutedText,
      border: AppColors.border,
      cardColor: AppColors.surfaceContainerLowest,
      inputFill: AppColors.surfaceContainerLowest,
      filledBg: AppColors.primary,
      filledFg: Colors.white,
      outlinedFg: AppColors.secondary,
      focusBorder: AppColors.primary,
    );
  }

  static ThemeData dark() {
    const onSurface = Color(0xFFF1DFDA);
    const darkBorder = Color(0xFF523B35);
    return _base(
      brightness: Brightness.dark,
      scheme: const ColorScheme(
        brightness: Brightness.dark,
        primary: Color(0xFFFFB59B),
        onPrimary: Color(0xFF5E1400),
        primaryContainer: Color(0xFF8A1C00),
        onPrimaryContainer: Color(0xFFFFE3D6),
        secondary: Color(0xFF8FD7A0),
        onSecondary: Color(0xFF09371B),
        secondaryContainer: Color(0xFF1F4D2C),
        onSecondaryContainer: Color(0xFFC9F2D2),
        tertiary: Color(0xFF9ECAFF),
        onTertiary: Color(0xFF003256),
        tertiaryContainer: Color(0xFF004879),
        onTertiaryContainer: Color(0xFFD1E4FF),
        error: Color(0xFFFFB4AB),
        onError: Color(0xFF690005),
        errorContainer: Color(0xFF93000A),
        onErrorContainer: Color(0xFFFFDAD6),
        surface: Color(0xFF1A100E),
        onSurface: onSurface,
        onSurfaceVariant: Color(0xFFD7BDB6),
        outline: Color(0xFFA08A82),
        outlineVariant: darkBorder,
      ),
      scaffoldBackground: const Color(0xFF1A100E),
      onSurface: onSurface,
      mutedText: const Color(0xFFD7BDB6),
      border: darkBorder,
      cardColor: const Color(0xFF2B1F1C),
      inputFill: const Color(0xFF2B1F1C),
      filledBg: const Color(0xFFFFB59B),
      filledFg: const Color(0xFF5E1400),
      outlinedFg: const Color(0xFF8FD7A0),
      focusBorder: const Color(0xFFFFB59B),
    );
  }

  static ThemeData _base({
    required Brightness brightness,
    required ColorScheme scheme,
    required Color scaffoldBackground,
    required Color onSurface,
    required Color mutedText,
    required Color border,
    required Color cardColor,
    required Color inputFill,
    required Color filledBg,
    required Color filledFg,
    required Color outlinedFg,
    required Color focusBorder,
  }) {
    return ThemeData(
      useMaterial3: true,
      colorScheme: scheme,
      scaffoldBackgroundColor: scaffoldBackground,
      fontFamily: _body,
      appBarTheme: AppBarTheme(
        centerTitle: false,
        elevation: 0,
        scrolledUnderElevation: 0,
        backgroundColor: Colors.transparent,
        foregroundColor: onSurface,
        surfaceTintColor: Colors.transparent,
      ),
      cardTheme: CardThemeData(
        color: cardColor,
        elevation: 0,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(AppRadii.card),
          side: BorderSide(color: border, width: 0.5),
        ),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: inputFill,
        contentPadding: const EdgeInsets.symmetric(horizontal: 18, vertical: 16),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(AppRadii.md),
          borderSide: BorderSide(color: border),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(AppRadii.md),
          borderSide: BorderSide(color: border),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(AppRadii.md),
          borderSide: BorderSide(color: focusBorder, width: 2),
        ),
        errorBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(AppRadii.md),
          borderSide: BorderSide(color: scheme.error, width: 1.5),
        ),
        focusedErrorBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(AppRadii.md),
          borderSide: BorderSide(color: scheme.error, width: 2),
        ),
        labelStyle: TextStyle(color: mutedText, fontFamily: _body),
        hintStyle: TextStyle(color: mutedText, fontFamily: _body),
      ),
      textTheme: _textTheme(onSurface: onSurface),
      switchTheme: SwitchThemeData(
        thumbColor: WidgetStateProperty.resolveWith((states) {
          if (states.contains(WidgetState.selected)) return scheme.secondary;
          return scheme.outline;
        }),
        trackColor: WidgetStateProperty.resolveWith((states) {
          if (states.contains(WidgetState.selected)) return scheme.secondaryContainer;
          return border;
        }),
      ),
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(
          backgroundColor: filledBg,
          foregroundColor: filledFg,
          minimumSize: const Size.fromHeight(56),
          textStyle: const TextStyle(fontSize: 16, fontWeight: FontWeight.w800, fontFamily: _body),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AppRadii.md)),
        ),
      ),
      outlinedButtonTheme: OutlinedButtonThemeData(
        style: OutlinedButton.styleFrom(
          foregroundColor: outlinedFg,
          side: BorderSide(color: outlinedFg, width: 2),
          minimumSize: const Size.fromHeight(52),
          textStyle: const TextStyle(fontSize: 15, fontWeight: FontWeight.w800, fontFamily: _body),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AppRadii.md)),
        ),
      ),
    );
  }
}
