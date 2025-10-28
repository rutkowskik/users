#!/bin/bash
set -e

# Pobierz wersję z argumentu lub użyj znacznika czasu jako domyślnej wersji
VERSION=${1:-$(date +%Y%m%d-%H%M%S)}
IMAGE_NAME="kacperroot/users-frontend"
FRONTEND_DIR="../usersapp"

echo "🧱 Buduję Angular app..."
cd $FRONTEND_DIR
npm install --legacy-peer-deps
npm run build -- --configuration=production

echo "🐳 Buduję obraz Dockera..."
docker build -t users-frontend:latest .

echo "🏷️  Taguję obraz..."
# Tag z wersją
docker tag users-frontend:latest $IMAGE_NAME:$VERSION
# Tag latest
docker tag users-frontend:latest $IMAGE_NAME:latest

echo "🔑 Logowanie do Docker Hub..."
docker login

echo "📤 Wysyłam obraz do Docker Hub..."
# Wyślij tag z wersją
docker push $IMAGE_NAME:$VERSION
# Wyślij tag latest
docker push $IMAGE_NAME:latest

echo "✅ Wysłano:"
echo "   - $IMAGE_NAME:$VERSION"
echo "   - $IMAGE_NAME:latest"
