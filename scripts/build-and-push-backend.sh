#!/bin/bash
set -e

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

# Sciezki - liczone wzgledem polozenia skryptu, nie katalogu wywolania
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "${SCRIPT_DIR}")"
BACKEND_DIR="${ROOT_DIR}/backend"

DOCKER_REGISTRY="kacperroot"
IMAGE_NAME="users-backend"

echo -e "${GREEN} Build and push backendu${NC}"
echo ""

# Build Maven
echo "Maven build..."
mvn -f "${BACKEND_DIR}/pom.xml" clean package -DskipTests

# Tag z datą
TAG=$(date +%Y%m%d-%H%M%S)
FULL_IMAGE="${DOCKER_REGISTRY}/${IMAGE_NAME}:${TAG}"
LATEST_IMAGE="${DOCKER_REGISTRY}/${IMAGE_NAME}:latest"
export FULL_IMAGE

# Build Docker
echo "Docker build..."
docker compose -f "${BACKEND_DIR}/docker-compose.yml" build --no-cache
docker tag ${FULL_IMAGE} ${LATEST_IMAGE}

# Push
echo "📤 Docker push..."
docker push ${FULL_IMAGE}
docker push ${LATEST_IMAGE}

echo -e "${GREEN}Done!${NC}"
echo "Image: ${FULL_IMAGE}"
