import 'package:flutter/material.dart';

class Category {
  const Category({
    required this.id,
    required this.name,
    required this.icon,
    required this.color,
  });

  final String id;
  final String name;
  final IconData icon;
  final Color color;
}

class Vendor {
  const Vendor({
    required this.id,
    required this.name,
    required this.category,
    required this.description,
    required this.distanceKm,
    required this.deliveryMinutes,
    required this.rating,
    required this.deliveryFee,
    required this.isOpen,
    required this.coverEmoji,
  });

  final String id;
  final String name;
  final String category;
  final String description;
  final double distanceKm;
  final int deliveryMinutes;
  final double rating;
  final double deliveryFee;
  final bool isOpen;
  final String coverEmoji;
}

class Product {
  const Product({
    required this.id,
    required this.vendorId,
    required this.name,
    required this.description,
    required this.price,
    required this.category,
    required this.emoji,
    this.badge,
  });

  final String id;
  final String vendorId;
  final String name;
  final String description;
  final double price;
  final String category;
  final String emoji;
  final String? badge;
}
