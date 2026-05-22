import 'package:flutter/material.dart';

import '../../core/constants/app_colors.dart';

class MapPreview extends StatelessWidget {
  const MapPreview({super.key, this.height = 190, this.statusLabel, this.showEta = true, this.estimatedMinutes = 12});

  final double height;
  final String? statusLabel;
  final bool showEta;
  final int estimatedMinutes;

  @override
  Widget build(BuildContext context) {
    return Container(
      height: height,
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(24),
        gradient: const LinearGradient(
          colors: [Color(0xFFFF9B73), Color(0xFF9B4A2F)],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
        boxShadow: const [BoxShadow(color: Color(0x182C2C3A), blurRadius: 18, offset: Offset(0, 8))],
      ),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(24),
        child: Stack(
          children: [
            Positioned.fill(child: CustomPaint(painter: _MapPainter())),
            Positioned.fill(
              child: DecoratedBox(
                decoration: BoxDecoration(
                  gradient: LinearGradient(
                    begin: Alignment.bottomCenter,
                    end: Alignment.topCenter,
                    colors: [Colors.black.withOpacity(.28), Colors.transparent],
                  ),
                ),
              ),
            ),
            if (statusLabel != null)
              Positioned(
                left: 14,
                bottom: 14,
                child: Container(
                  padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 7),
                  decoration: BoxDecoration(color: AppColors.primary, borderRadius: BorderRadius.circular(999)),
                  child: Text(statusLabel!, style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w900)),
                ),
              ),
            if (showEta)
              Positioned(
                left: 18,
                bottom: 18,
                child: Container(
                  padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                  decoration: BoxDecoration(color: Colors.white.withOpacity(.92), borderRadius: BorderRadius.circular(16)),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      const Text('Tempo estimado', style: TextStyle(color: AppColors.onSurfaceVariant, fontWeight: FontWeight.w700)),
                      Text('$estimatedMinutes min', style: const TextStyle(color: AppColors.primary, fontWeight: FontWeight.w800, fontSize: 22)),
                    ],
                  ),
                ),
              ),
            if (showEta)
              Positioned(
                right: 18,
                bottom: 18,
                child: FloatingActionButton.small(
                  heroTag: null,
                  elevation: 0,
                  backgroundColor: AppColors.primary,
                  onPressed: () {},
                  child: const Icon(Icons.navigation_rounded, color: Colors.white),
                ),
              ),
          ],
        ),
      ),
    );
  }
}

class _MapPainter extends CustomPainter {
  @override
  void paint(Canvas canvas, Size size) {
    final roadPaint = Paint()
      ..color = Colors.white.withOpacity(.45)
      ..strokeWidth = 2
      ..style = PaintingStyle.stroke;
    final routePaint = Paint()
      ..color = AppColors.primaryContainer
      ..strokeWidth = 5
      ..strokeCap = StrokeCap.round
      ..style = PaintingStyle.stroke;
    final greenPaint = Paint()..color = const Color(0xFFB8DDBA).withOpacity(.75);

    canvas.drawRect(Rect.fromLTWH(size.width * .62, 0, size.width * .22, size.height), greenPaint);
    for (var i = 0; i < 8; i++) {
      final y = size.height * (i + 1) / 9;
      canvas.drawLine(Offset(0, y), Offset(size.width, y + (i.isEven ? 18 : -18)), roadPaint);
    }
    for (var i = 0; i < 5; i++) {
      final x = size.width * (i + 1) / 6;
      canvas.drawLine(Offset(x, 0), Offset(x + 20, size.height), roadPaint);
    }

    final path = Path()
      ..moveTo(size.width * .32, size.height * .06)
      ..cubicTo(size.width * .42, size.height * .25, size.width * .72, size.height * .32, size.width * .58, size.height * .56)
      ..cubicTo(size.width * .48, size.height * .74, size.width * .60, size.height * .78, size.width * .54, size.height * .93);
    canvas.drawPath(path, routePaint);

    final markerPaint = Paint()..color = AppColors.primaryContainer;
    for (final p in [Offset(size.width * .32, size.height * .06), Offset(size.width * .58, size.height * .56), Offset(size.width * .54, size.height * .93)]) {
      canvas.drawCircle(p, 8, markerPaint);
      canvas.drawCircle(p, 3.5, Paint()..color = Colors.white);
    }
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}
