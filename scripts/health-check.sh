#!/bin/bash

# Kolory
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

NAMESPACE="users-app"

echo -e "${GREEN}🏥 Health Check - Users App${NC}"
echo "================================"
echo ""

# Sprawdzenie namespace
if ! kubectl get namespace ${NAMESPACE} &> /dev/null; then
    echo -e "${RED}❌ Namespace ${NAMESPACE} nie istnieje!${NC}"
    exit 1
fi

# Pods
echo -e "${YELLOW}📦 PODS:${NC}"
kubectl get pods -n ${NAMESPACE} -o wide
echo ""

# Deployments
echo -e "${YELLOW}🚀 DEPLOYMENTS:${NC}"
kubectl get deployments -n ${NAMESPACE}
echo ""

# Services
echo -e "${YELLOW}🔌 SERVICES:${NC}"
kubectl get svc -n ${NAMESPACE}
echo ""

# StatefulSets
echo -e "${YELLOW}📊 STATEFULSETS:${NC}"
kubectl get statefulsets -n ${NAMESPACE}
echo ""

# HPA (jeśli istnieje)
if kubectl get hpa -n ${NAMESPACE} &> /dev/null; then
    echo -e "${YELLOW}📈 HPA:${NC}"
    kubectl get hpa -n ${NAMESPACE}
    echo ""
fi

# Ingress
echo -e "${YELLOW}🔀 INGRESS:${NC}"
kubectl get ingress -n ${NAMESPACE}
echo ""

# Sprawdzenie health endpointów
echo -e "${YELLOW}🩺 Health Endpoints:${NC}"

BACKEND_POD=$(kubectl get pod -n ${NAMESPACE} -l app=users-backend -o jsonpath="{.items[0].metadata.name}" 2>/dev/null)
if [ ! -z "$BACKEND_POD" ]; then
    echo "Backend pod: ${BACKEND_POD}"
    HEALTH=$(kubectl exec -n ${NAMESPACE} ${BACKEND_POD} -- curl -s http://localhost:8080/actuator/health 2>/dev/null)
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✅ Backend health: OK${NC}"
        echo "$HEALTH" | head -n 5
    else
        echo -e "${RED}❌ Backend health: FAILED${NC}"
    fi
else
    echo -e "${YELLOW}⚠️  Brak backendu${NC}"
fi

echo ""
echo -e "${YELLOW}📝 Recent Events:${NC}"
kubectl get events -n ${NAMESPACE} --sort-by='.lastTimestamp' | tail -10
