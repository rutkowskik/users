#!/bin/bash

# Kolory
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

NAMESPACE="users-app"

show_usage() {
    echo "Usage: $0 [backend|postgres|redis|all] [--follow]"
    echo ""
    echo "Examples:"
    echo "  $0 backend          # Show backend logs"
    echo "  $0 backend --follow # Follow backend logs"
    echo "  $0 all              # Show all logs"
    exit 1
}

COMPONENT=${1:-backend}
FOLLOW=""

if [ "$2" == "--follow" ] || [ "$2" == "-f" ]; then
    FOLLOW="-f"
fi

show_logs() {
    local label=$1
    local name=$2
    
    echo -e "${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${GREEN}📋 Logs: ${name}${NC}"
    echo -e "${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    
    if kubectl get pods -n ${NAMESPACE} -l app=${label} 2>/dev/null | grep -q ${label}; then
        kubectl logs -l app=${label} -n ${NAMESPACE} --tail=100 ${FOLLOW}
    else
        echo -e "${RED}❌ Brak podów z labelką app=${label}${NC}"
    fi
    echo ""
}

case $COMPONENT in
    backend)
        show_logs "users-backend" "Backend"
        ;;
    postgres)
        show_logs "postgres" "PostgreSQL"
        ;;
    redis)
        show_logs "redis" "Redis"
        ;;
    all)
        echo -e "${YELLOW}Pokazuję logi wszystkich komponentów...${NC}"
        echo ""
        show_logs "users-backend" "Backend"
        show_logs "postgres" "PostgreSQL"
        show_logs "redis" "Redis"
        ;;
    *)
        show_usage
        ;;
esac
