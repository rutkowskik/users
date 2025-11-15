#!/bin/bash

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

DOCKER_REGISTRY="kacperroot"
IMAGE_NAME="users-backend"

echo -e "${GREEN} Build and push backendu${NC}"
echo ""

# Build Maven
echo "Maven build..."
cd ..
mvn clean package -DskipTests

# Tag z datą
TAG=$(date +%Y%m%d-%H%M%S)
FULL_IMAGE="${DOCKER_REGISTRY}/${IMAGE_NAME}:${TAG}"
LATEST_IMAGE="${DOCKER_REGISTRY}/${IMAGE_NAME}:latest"
export FULL_IMAGE

# Build Docker
echo "Docker build..."
docker compose -f docker-compose.yml build --no-cache
docker tag ${FULL_IMAGE} ${LATEST_IMAGE}

# Push
echo "📤 Docker push..."
docker push ${FULL_IMAGE}
docker push ${LATEST_IMAGE}

echo -e "${GREEN}Done!${NC}"
echo "Image: ${FULL_IMAGE}"
