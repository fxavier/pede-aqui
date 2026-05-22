---
name: Modern African Energy
colors:
  surface: '#fff8f6'
  surface-dim: '#f1d4cd'
  surface-bright: '#fff8f6'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#fff1ed'
  surface-container: '#ffe9e4'
  surface-container-high: '#ffe2db'
  surface-container-highest: '#fadcd5'
  on-surface: '#271814'
  on-surface-variant: '#5b403a'
  inverse-surface: '#3e2c28'
  inverse-on-surface: '#ffede9'
  outline: '#907068'
  outline-variant: '#e4beb5'
  surface-tint: '#b42800'
  primary: '#b02700'
  on-primary: '#ffffff'
  primary-container: '#d8390e'
  on-primary-container: '#fffbff'
  inverse-primary: '#ffb4a2'
  secondary: '#006d3f'
  on-secondary: '#ffffff'
  secondary-container: '#9bf6ba'
  on-secondary-container: '#0e7344'
  tertiary: '#005f9e'
  on-tertiary: '#ffffff'
  tertiary-container: '#0078c6'
  on-tertiary-container: '#fdfcff'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#ffdad2'
  primary-fixed-dim: '#ffb4a2'
  on-primary-fixed: '#3c0700'
  on-primary-fixed-variant: '#8a1c00'
  secondary-fixed: '#9bf6ba'
  secondary-fixed-dim: '#80d9a0'
  on-secondary-fixed: '#00210f'
  on-secondary-fixed-variant: '#00522e'
  tertiary-fixed: '#d1e4ff'
  tertiary-fixed-dim: '#9dcaff'
  on-tertiary-fixed: '#001d35'
  on-tertiary-fixed-variant: '#00497b'
  background: '#fff8f6'
  on-background: '#271814'
  surface-variant: '#fadcd5'
typography:
  display:
    fontFamily: Plus Jakarta Sans
    fontSize: 48px
    fontWeight: '800'
    lineHeight: '1.1'
    letterSpacing: -0.02em
  headline-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 32px
    fontWeight: '700'
    lineHeight: 40px
  headline-lg-mobile:
    fontFamily: Plus Jakarta Sans
    fontSize: 28px
    fontWeight: '700'
    lineHeight: 34px
  headline-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 24px
    fontWeight: '700'
    lineHeight: 32px
  headline-sm:
    fontFamily: Plus Jakarta Sans
    fontSize: 20px
    fontWeight: '600'
    lineHeight: 28px
  body-lg:
    fontFamily: DM Sans
    fontSize: 18px
    fontWeight: '400'
    lineHeight: 28px
  body-md:
    fontFamily: DM Sans
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
  body-sm:
    fontFamily: DM Sans
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
  label-md:
    fontFamily: DM Sans
    fontSize: 14px
    fontWeight: '700'
    lineHeight: 16px
    letterSpacing: 0.01em
  code-otp:
    fontFamily: JetBrains Mono
    fontSize: 24px
    fontWeight: '600'
    lineHeight: 32px
    letterSpacing: 0.1em
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  base: 4px
  xs: 8px
  sm: 12px
  md: 16px
  lg: 24px
  xl: 32px
  xxl: 48px
  gutter: 16px
  margin-mobile: 16px
  margin-desktop: 40px
---

## Brand & Style
The design system is built on a "Modern African Energy" narrative—a fusion of vibrant, sun-drenched warmth with the rhythmic precision of a high-growth delivery marketplace. It rejects clinical corporate coldness in favor of a bold, joyful, and tactile experience.

The visual style is **High-Contrast / Bold** with a touch of **Tactile** warmth. It emphasizes movement, hospitality, and reliability. Surfaces feel substantive and inviting, utilizing a warm off-white foundation to ensure the primary coral-orange and forest-green accents feel energetic rather than overwhelming. The emotional goal is to make every interaction feel like a successful, rhythmic hand-off in a bustling local market.

## Colors
The palette is rooted in earth and sun. **Deep Coral-Orange** is the driver of action, used for primary calls to action and movement-related indicators. **Rich Forest Green** provides a grounded counter-balance, used for secondary actions and trust-building elements.

The background is a soft **Off-White**, reducing eye strain compared to pure white while maintaining a premium, paper-like feel. Text is rendered in **Slate** to maintain high legibility without the harshness of pure black. Warm Golden Yellow is reserved for highlights, badges, and attention-grabbing accents like the OTP entry fields.

## Typography
The typography strategy prioritizes warmth and clarity. **Plus Jakarta Sans** provides a geometric yet friendly voice for headlines, utilizing tight letter-spacing and heavy weights to create a sense of urgency and importance.

**DM Sans** is used for all body copy and interface labels to ensure high legibility on mobile devices across various literacy levels. For technical data—such as order IDs, delivery codes, and confirmation pins—**JetBrains Mono** is used to provide a distinct, "receipt-like" visual rhythm that separates transactional data from narrative content.

## Layout & Spacing
The layout follows a 4px baseline grid. For mobile, use a 4-column fluid grid with 16px margins. For desktop, use a 12-column fixed grid (max-width 1200px) with 24px gutters.

The spacing rhythm is intentional: use **16px (md)** for internal component padding and **24px (lg)** for vertical section spacing. This creates a dense but breathable interface that mimics the organized chaos of a marketplace. Components should be grouped using logical proximity, with consistent 8px gaps between related items in a list.

## Elevation & Depth
Depth is created through **Tonal Layers** and subtle, warm shadows. This design system avoids harsh black shadows, preferring "Umbra" values tinted with the neutral slate color at very low opacities.

- **Level 0 (Base):** Off-white background (#FAFAF7).
- **Level 1 (Cards):** Pure white background with a `0 2px 12px rgba(44, 44, 58, 0.08)` shadow.
- **Level 2 (Modals/Overlays):** Pure white background with a `0 8px 24px rgba(44, 44, 58, 0.12)` shadow.

This creates a "stacked paper" effect where the most important information physically lifts off the page toward the user.

## Shapes
Shapes are generous and approachable. The standard radius is **16px** for cards and large containers, reflecting a friendly, modern aesthetic. Interactive elements like buttons use a slightly sharper **12px** radius to distinguish them as "tools" rather than content containers.

Small elements like checkboxes and input fields should follow a **4px** or **8px** radius to maintain a clean, functional appearance without becoming overly "bubbly."

## Components
- **Buttons:** Primary buttons use Coral-Orange (#E8441A) with white bold text. Secondary buttons use Forest Green (#1A7A4A) as an outline or solid fill. Height is fixed at 48px or 56px for high-touch targets.
- **Confirmation Codes (OTP):** Use JetBrains Mono. Each digit is housed in an individual box with a 2px border. When focused, the box takes a 2px Golden Yellow (#F5A623) border and a soft yellow tint background.
- **Status Badges:** Pill-shaped with 100px radius. Use low-saturation versions of the status colors for the background and high-saturation versions for the text (e.g., light green background with dark green text for "Delivered").
- **Cards:** White background, 16px radius. Content within cards should have 16px padding. Images within cards should have an 8px top-radius to sit flush with the card container.
- **Input Fields:** 12px radius, light gray border (#D1D5DB). On focus, the border transitions to Forest Green (#1A7A4A) to signal a secure, trustworthy entry point.
- **Chips:** Small, 8px radius or pill-shaped, used for categories (e.g., "Jollof", "Fast Food"). Use Forest Green for selected states.