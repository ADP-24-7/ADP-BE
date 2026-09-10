SHELL := /bin/sh

GRADLE_IMAGE ?= gradle:8.14.3-jdk21
COMPOSE ?= docker compose
ADP_DA_ROOT ?= $(CURDIR)/../ADP-DA
DOCKER_RUN_GRADLE := docker run --rm \
	-v "$(CURDIR)":/workspace \
	-v adp-be-gradle-cache:/home/gradle/.gradle \
	-w /workspace \
	$(GRADLE_IMAGE) gradle --no-daemon --project-cache-dir /home/gradle/.gradle/build-project-cache
DOCKER_RUN_GRADLE_TEST := docker run --rm --network adp-local \
	-e SPRING_DATASOURCE_URL=jdbc:postgresql://postgres-test:5432/adp \
	-e SPRING_DATASOURCE_USERNAME=adp \
	-e SPRING_DATASOURCE_PASSWORD=adp \
	-e SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=2 \
	-e SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE=0 \
	-e ADP_LOCAL_FIXTURES_ENABLED=true \
	-e ADP_MOCK_RUNTIME_ENABLED=true \
	-e ADP_DATA_ACCESS_PREVIEW_ENABLED=true \
	-e ADP_CONTEXT_PREVIEW_ENABLED=true \
	-e ADP_DA_ROOT=/adp-da \
	-v "$(CURDIR)":/workspace \
	-v "$(ADP_DA_ROOT)":/adp-da:ro \
	-v adp-be-gradle-cache:/home/gradle/.gradle \
	-w /workspace \
	$(GRADLE_IMAGE) gradle --no-daemon --project-cache-dir /home/gradle/.gradle/test-project-cache
DOCKER_RUN_GRADLE_DEV := docker run --rm --network adp-local \
	-e SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/adp \
	-e SPRING_DATASOURCE_USERNAME=adp \
	-e SPRING_DATASOURCE_PASSWORD=adp \
	-e ADP_LOCAL_FIXTURES_ENABLED=true \
	-e ADP_MOCK_RUNTIME_ENABLED=true \
	-e ADP_DATA_ACCESS_PREVIEW_ENABLED=true \
	-e ADP_CONTEXT_PREVIEW_ENABLED=true \
	-v "$(CURDIR)":/workspace \
	-v adp-be-gradle-cache:/home/gradle/.gradle \
	-w /workspace \
	$(GRADLE_IMAGE) gradle --no-daemon --project-cache-dir /home/gradle/.gradle/dev-run-project-cache

.PHONY: help setup env docker-network postgres-up test-postgres-up test package check run docker-up docker-rebuild docker-down docker-logs docker-ps ai-eval-e2e digital-asset-e2e ncp-artifact-ingest-e2e

help:
	@printf "%s\n" \
		"ADP-BE commands:" \
		"  make setup       Prepare shared Docker dev environment" \
		"  make env         Create .env from .env.example if missing" \
		"  make docker-network Ensure shared adp-local Docker network exists" \
		"  make postgres-up Start PostgreSQL for local tests" \
		"  make test-postgres-up Start clean PostgreSQL for tests" \
		"  make test        Run unit/integration tests" \
		"  make package     Build executable jar" \
		"  make check       Run test and package verification" \
		"  make run         Run bootRun through Docker Gradle image" \
		"  make docker-up   Start BE, FE, DA, Docs and PostgreSQL dev stack" \
		"  make docker-rebuild Rebuild and start the full dev stack" \
		"  make docker-logs Follow full dev stack logs" \
		"  make docker-ps   Show full dev stack containers" \
		"  make ai-eval-e2e Run the explicitly confirmed real three-model Evaluation and export the DA Bundle" \
		"  make digital-asset-e2e Run DA PR #31 six-case fixtures through the real local Runtime path" \
		"  make ncp-artifact-ingest-e2e Read the DA Bundle from NCP and ingest it through the BE API" \
		"  make docker-down Stop full dev stack"

setup: env docker-network
	docker --version
	docker run --rm $(GRADLE_IMAGE) gradle --version

env:
	@if [ ! -f .env ]; then cp .env.example .env; fi

docker-network:
	docker network inspect adp-local >/dev/null 2>&1 || docker network create adp-local

postgres-up: env docker-network
	$(COMPOSE) up -d postgres

test-postgres-up: docker-network
	$(COMPOSE) rm -sf postgres-test
	$(COMPOSE) up -d postgres-test

test: test-postgres-up
	@test -d "$(ADP_DA_ROOT)/03_digital_asset/artifacts/local_product_e2e_v1" || \
		{ printf '%s\n' "ADP-DA PR #31 fixtures were not found under $(ADP_DA_ROOT)" >&2; exit 1; }
	$(DOCKER_RUN_GRADLE_TEST) test; status=$$?; $(COMPOSE) rm -sf postgres-test; exit $$status

package:
	$(DOCKER_RUN_GRADLE) bootJar

check: test package

run: postgres-up
	$(DOCKER_RUN_GRADLE_DEV) bootRun

docker-up: env docker-network
	$(COMPOSE) up -d --build

docker-rebuild: env docker-network
	$(COMPOSE) build --no-cache
	$(COMPOSE) up -d

docker-down:
	$(COMPOSE) down

docker-logs:
	$(COMPOSE) logs -f

docker-ps:
	$(COMPOSE) ps

ai-eval-e2e:
	./scripts/run-ai-evaluation-e2e.sh

digital-asset-e2e: test-postgres-up
	@test -d "$(ADP_DA_ROOT)/03_digital_asset/artifacts/local_product_e2e_v1" || \
		{ printf '%s\n' "ADP-DA PR #31 fixtures were not found under $(ADP_DA_ROOT)" >&2; exit 1; }
	$(DOCKER_RUN_GRADLE_TEST) test --tests com.adp.gateway.digitalasset.DigitalAssetLocalProductE2ETests; \
		status=$$?; $(COMPOSE) rm -sf postgres-test; exit $$status

ncp-artifact-ingest-e2e: docker-network
	./scripts/run-ncp-artifact-ingest-e2e.sh
