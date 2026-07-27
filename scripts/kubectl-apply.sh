#!/bin/bash

# Kolory dla outputu
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Konfiguracja
DOCKER_REGISTRY="kacperroot"
IMAGE_NAME="users-backend"
# Sciezki - liczone wzgledem polozenia skryptu, nie katalogu wywolania
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "${SCRIPT_DIR}")"

NAMESPACE="users-app"
KUBECTL_CONTEXT=$(kubectl config current-context 2>/dev/null || echo "")

echo -e "${GREEN}================================${NC}"
echo -e "${GREEN}  DEPLOYMENT USERS-APP${NC}"
echo -e "${GREEN}================================${NC}"
echo ""

# Sprawdzenie czy kubectl jest zainstalowane
if ! command -v kubectl &> /dev/null; then
    echo -e "${RED}❌ kubectl nie jest zainstalowane!${NC}"
    exit 1
fi

# Sprawdzenie kontekstu Kubernetes
if [ -z "$KUBECTL_CONTEXT" ]; then
    echo -e "${RED}❌ Brak aktywnego kontekstu Kubernetes!${NC}"
    exit 1
fi

echo -e "${YELLOW}📋 Aktywny kontekst: ${KUBECTL_CONTEXT}${NC}"
echo ""

# Potwierdzenie przed deploymentem
read -p "Czy chcesz kontynuować deployment? (y/n): " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo -e "${YELLOW}⚠️  Deployment anulowany${NC}"
    exit 0
fi

# Tworzenie namespace jeśli nie istnieje
kubectl get namespace ${NAMESPACE} &> /dev/null
if [ $? -ne 0 ]; then
    echo "📦 Tworzenie namespace ${NAMESPACE}..."
    kubectl apply -f "${ROOT_DIR}"/k8s/namespace.yaml
fi

# Deployment konfiguracji
echo "📝 Applying ConfigMap..."
kubectl apply -f "${ROOT_DIR}"/k8s/configmap.yaml

echo "🔐 Applying Secrets..."
kubectl apply -f "${ROOT_DIR}"/k8s/secrets.yaml

# Deployment bazy danych i cache
echo "🗄️  Deploying PostgreSQL..."
kubectl apply -f "${ROOT_DIR}"/k8s/postgres/postgres-statefulset.yaml

echo "⚡ Deploying Redis..."
kubectl apply -f "${ROOT_DIR}"/k8s/redis/redis-statefulset.yaml

# Czekanie na gotowość baz danych
echo "⏳ Czekanie na gotowość baz danych..."
kubectl wait --for=condition=ready pod -l app=postgres -n ${NAMESPACE} --timeout=120s
kubectl wait --for=condition=ready pod -l app=redis -n ${NAMESPACE} --timeout=120s

# Deployment backendu
echo "🚀 Deploying Backend..."
kubectl apply -f "${ROOT_DIR}"/k8s/backend/backend-deployment.yaml

# Jeśli istnieje HPA dla backendu
if [ -f "${ROOT_DIR}/k8s/backend/backend-hpa.yaml" ]; then
    echo "📊 Applying Backend HPA..."
    kubectl apply -f "${ROOT_DIR}"/k8s/backend/backend-hpa.yaml
fi

# Deployment frontendu (jeśli istnieje)
if [ -f "${ROOT_DIR}/k8s/frontend/frontend-deployment.yaml" ]; then
    echo "🌐 Deploying Frontend..."
    kubectl apply -f "${ROOT_DIR}"/k8s/frontend/frontend-deployment.yaml

    if [ -f "${ROOT_DIR}/k8s/frontend/frontend-hpa.yaml" ]; then
        echo "📊 Applying Frontend HPA..."
        kubectl apply -f "${ROOT_DIR}"/k8s/frontend/frontend-hpa.yaml
    fi
fi

# Ingress
if [ -f "${ROOT_DIR}/k8s/ingress.yaml" ]; then
    echo "🔀 Applying Ingress..."
    kubectl apply -f "${ROOT_DIR}"/k8s/ingress.yaml
fi

echo -e "${GREEN}✅ Manifesty Kubernetes zastosowane${NC}"
echo ""

echo -e "${GREEN}⏳ KROK 5: Czekanie na gotowość deploymentów${NC}"
echo "================================"
kubectl rollout status deployment/users-backend -n ${NAMESPACE} --timeout=300s
if [ $? -ne 0 ]; then
    echo -e "${RED}❌ Timeout podczas deploymentu backendu!${NC}"
    echo "Sprawdź logi: kubectl logs -l app=users-backend -n ${NAMESPACE}"
    exit 1
fi

echo -e "${GREEN}✅ Backend jest gotowy${NC}"
echo ""

echo -e "${GREEN}🏥 KROK 6: Health Check${NC}"
echo "================================"

# Sprawdzanie podów
echo "Pods:"
kubectl get pods -n ${NAMESPACE}
echo ""

# Sprawdzanie serwisów
echo "Services:"
kubectl get svc -n ${NAMESPACE}
echo ""

# Endpoint health check (jeśli port-forward jest dostępny)
BACKEND_POD=$(kubectl get pod -n ${NAMESPACE} -l app=users-backend -o jsonpath="{.items[0].metadata.name}")
if [ ! -z "$BACKEND_POD" ]; then
    echo "🔍 Sprawdzanie health endpoint..."
    kubectl exec -n ${NAMESPACE} ${BACKEND_POD} -- curl -s http://localhost:8080/actuator/health > /dev/null 2>&1
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✅ Backend health check OK${NC}"
    else
        echo -e "${YELLOW}⚠️  Backend health check nie powiódł się (może być OK jeśli endpoint nie istnieje)${NC}"
    fi
fi

echo ""
echo -e "${GREEN}================================${NC}"
echo -e "${GREEN}✅ DEPLOYMENT ZAKOŃCZONY POMYŚLNIE!${NC}"
echo -e "${GREEN}================================${NC}"
echo ""
echo "📊 Użyteczne komendy:"
echo "  - Sprawdź logi: kubectl logs -l app=users-backend -n ${NAMESPACE} -f"
echo "  - Sprawdź pody: kubectl get pods -n ${NAMESPACE}"
echo "  - Port forward: kubectl port-forward svc/backend-service 8081:8081 -n ${NAMESPACE}"
echo "  - Szczegóły deploymentu: kubectl describe deployment users-backend -n ${NAMESPACE}"
echo ""
echo "🏷️  Deployed image: ${FULL_IMAGE}"
