#!/usr/bin/env bash
set -euo pipefail

flutter create . \
  --project-name pede_aqui_courier_app \
  --org com.pedeaqui \
  --platforms=android,ios,web

flutter pub get
