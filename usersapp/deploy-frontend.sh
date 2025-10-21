#!/bin/bash
set -e

IMAGE_NAME="kacperroot/users-frontend:latest"
FRONTEND_DIR="../usersapp"

echo "🧱 Buduję Angular app..."
cd $FRONTEND_DIR
npm install --legacy-peer-deps
npm run build --prod

echo "🐳 Buduję obraz Dockera..."
docker build -t users-frontend:latest .

echo "🏷️  Taguję obraz..."
docker tag users-frontend:latest $IMAGE_NAME

echo "🔑 Logowanie do Docker Hub..."
docker login

echo "📤 Wysyłam obraz do Docker Hub..."
docker push $IMAGE_NAME

echo "✅ Wysłano: $IMAGE_NAME"
