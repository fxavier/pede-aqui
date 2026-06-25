import 'package:flutter/material.dart';

/// Brand palette mirrored from the web app (`pede-aqui-delivery`).
/// Values are the exact hex equivalents of the web CSS HSL tokens
/// (see pede-aqui-delivery/src/index.css and tailwind.config.js).
/// Identity = ember orange + forest green + warm cream.
class AppColors {
  // ── Brand ──────────────────────────────────────────────
  /// Forest green — primary chrome (nav, "Comprar" CTA, badges).
  static const Color forest = Color(0xFF1A2F1D);
  static const Color forestDark = Color(0xFF111F13);
  static const Color forestLight = Color(0xFF2A4A2E);
  /// Ember orange — brand accent (cart badge, hero emphasis).
  static const Color ember = Color(0xFFE8430C);

  // ── Primary (web --primary: hsl(17 93% 47%)) ───────────
  static const Color primary = Color(0xFFE74708);
  static const Color primaryContainer = Color(0xFFC73C06);
  static const Color primaryFixed = Color(0xFFFFE3D6);
  static const Color primaryFixedDim = Color(0xFFFFC1A8);
  static const Color onPrimaryFixed = Color(0xFF3C0700);
  static const Color onPrimaryFixedVariant = Color(0xFF8A1C00);
  static const Color primarySoft = Color(0xFFFFE3D6);

  // ── Surfaces (web --background cream / --card white) ────
  static const Color background = Color(0xFFFCF8F3);
  static const Color surface = Color(0xFFFFFFFF);
  static const Color surfaceContainer = Color(0xFFF5EFE7);
  static const Color surfaceContainerLow = Color(0xFFFAF5EE);
  static const Color surfaceContainerLowest = Color(0xFFFFFFFF);
  static const Color surfaceContainerHigh = Color(0xFFF0EAE0);
  static const Color surfaceContainerHighest = Color(0xFFEBE4D8);
  static const Color surfaceSoft = Color(0xFFFAF5EE);

  // ── Text (web --foreground / --muted-foreground) ───────
  static const Color text = Color(0xFF0F241C);
  static const Color mutedText = Color(0xFF6C7F77);
  static const Color border = Color(0xFFE4DFD8);
  static const Color outline = Color(0xFF9AA39C);

  // ── Secondary chrome = forest family ───────────────────
  static const Color secondary = forest;
  static const Color secondaryContainer = Color(0xFFE7F2EA);
  static const Color onSecondaryContainer = forestDark;

  // ── Success greens (web Tailwind green-500/600/100) ────
  static const Color green = Color(0xFF16A34A);
  static const Color greenSoft = Color(0xFFDCFCE7);

  // ── Accent (web --accent: amber) + star ambers ─────────
  static const Color accent = Color(0xFFFBBB18);
  static const Color amber = Color(0xFFFBBB18);
  static const Color amber300 = Color(0xFFFCD34D);
  static const Color amber600 = Color(0xFFD97706);

  // ── Info / tertiary (barely used) ──────────────────────
  static const Color tertiary = Color(0xFF005F9E);
  static const Color tertiaryContainer = Color(0xFFD1E4FF);
  static const Color onTertiaryContainer = Color(0xFF001D35);
  static const Color blue = Color(0xFF005F9E);

  // ── Semantic ───────────────────────────────────────────
  static const Color warning = Color(0xFFF5A623);
  static const Color error = Color(0xFFEF4343);
  static const Color onError = Color(0xFFFFFFFF);
  static const Color errorContainer = Color(0xFFFFDAD6);

  /// Base tint for warm shadows (web rgba(60,30,5,…)).
  static const Color warmShadowBase = Color(0xFF3C1E05);
}
