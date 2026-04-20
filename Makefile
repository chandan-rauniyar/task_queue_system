# ============================================================
# TASK QUEUE SYSTEM — Makefile
# Run any command with: make <target>
# ============================================================

# ── Infrastructure ──────────────────────────────────────────

## Start all Docker services (Postgres, Redis, Kafka, UIs)
infra-up:
	docker-compose up -d postgres redis zookeeper kafka kafka-ui redis-commander

## Stop all services (data preserved)
infra-down:
	docker-compose down

## Wipe everything including data volumes
infra-clean:
	docker-compose down -v

## Show running containers
infra-status:
	docker-compose ps

## Show logs for a service: make logs s=kafka
logs:
	docker-compose logs -f $(s)

# ── Backend ─────────────────────────────────────────────────

## Run Spring Boot locally (connects to Docker infra)
run:
	cd backend && mvn spring-boot:run

## Build jar
build:
	cd backend && mvn clean package -DskipTests

## Run tests
test:
	cd backend && mvn test

# ── Admin Panel ─────────────────────────────────────────────

## Start React admin panel
admin:
	cd admin-panel && npm start

## Install admin panel dependencies
admin-install:
	cd admin-panel && npm install

# ── Dev shortcuts ────────────────────────────────────────────

## Start everything for development
dev: infra-up
	@echo "Waiting 15s for services to be ready..."
	@sleep 15
	@echo "Infrastructure ready!"
	@echo ""
	@echo "  Spring Boot:    make run         → http://localhost:8080"
	@echo "  Admin panel:    make admin       → http://localhost:3000"
	@echo "  Swagger UI:     http://localhost:8080/api/v1/swagger-ui.html"
	@echo "  Kafka UI:       http://localhost:8090"
	@echo "  Redis UI:       http://localhost:8091"
	@echo "  PostgreSQL:     localhost:5432   user=tquser  pass=tqpass123"

.PHONY: infra-up infra-down infra-clean infra-status logs run build test admin admin-install dev