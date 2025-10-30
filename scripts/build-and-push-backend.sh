#!/bin/bash

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

DOCKER_REGISTRY="kacperroot"
IMAGE_NAME="users-backend"

echo -e "${GREEN}🔨 Build and push backendu${NC}"
echo ""

# Build Maven
echo "Maven build..."
mvn clean package -DskipTests
if [ $? -ne 0 ]; then
    echo -e "${RED}Error during Maven build!${NC}"
    exit 1
fi

# Tag z datą
TAG=$(date +%Y%m%d-%H%M%S)
FULL_IMAGE="${DOCKER_REGISTRY}/${IMAGE_NAME}:${TAG}"
LATEST_IMAGE="${DOCKER_REGISTRY}/${IMAGE_NAME}:latest"

# Build Docker
echo "🐳 Docker build..."
docker build -t ${FULL_IMAGE} .
docker tag ${FULL_IMAGE} ${LATEST_IMAGE}

if [ $? -ne 0 ]; then
    echo -e "${RED} Error during Docker build!${NC}"
    exit 1
fi

# Push
echo "📤 Docker push..."
docker push ${FULL_IMAGE}
docker push ${LATEST_IMAGE}

if [ $? -ne 0 ]; then
    echo -e "${RED}❌ Error during push!${NC}"
    exit 1
fi

echo -e "${GREEN}✅ Done!${NC}"
echo "Image: ${FULL_IMAGE}"
