#!/bin/bash
set -e

# === Konfiguracja ===
APP_NAME="users-backend"
REGISTRY="kacperroot"
IMAGE_NAME="kacperrutkowski/$APP_NAME"
TAG=$(date +%Y%m%d-%H%M)
WAR_FILE="target/users_app.jar"

# === Sprawdzenie MAVEN ===
echo "🔧 Buduję aplikację Maven..."
mvn clean package -DskipTests

# === Sprawdzenie WAR ===
if [ ! -f "$WAR_FILE" ]; then
  echo "❌ Plik WAR nie został znaleziony: $WAR_FILE"
  exit 1
fi

# === Budowanie obrazu Dockera ===
echo "🐳 Buduję obraz Dockera..."
docker build -t $IMAGE_NAME:$TAG -t $IMAGE_NAME:latest -f ../Dockerfile ..

# === Logowanie do rejestru ===
echo "🔑 Logowanie do Docker Registry..."
docker login $REGISTRY

# === Push obrazów ===
echo "⬆️ Wysyłanie obrazów do rejestru..."
docker push $IMAGE_NAME:$TAG
docker push $IMAGE_NAME:latest

# === Podsumowanie ===
echo "✅ Obraz został pomyślnie zbudowany i wysłany:"
echo "   - $IMAGE_NAME:$TAG"
echo "   - $IMAGE_NAME:latest"