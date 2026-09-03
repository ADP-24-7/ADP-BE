SHELL := /bin/sh

GRADLE_IMAGE ?= gradle:8.14.3-jdk21
COMPOSE ?= docker compose
DOCKER_RUN_GRADLE := docker run --rm \
	-v "$(CURDIR)":/workspace \
	-v adp-be-gradle-cache:/home/gradle/.gradle \
	-w /workspace \
	$(GRADLE_IMAGE) gradle --no-daemon --project-cache-dir /home/gradle/.gradle/build-project-cache
DOCKER_RUN_GRADLE_TEST := docker run --rm --network adp-local \
	-e SPRING_DATASOURCE_URL=jdbc:postgresql://postgres-test:5432/adp \
	-e SPRING_DATASOURCE_USERNAME=adp \
	-e SPRING_DATASOURCE_PASSWORD=adp \
	-e SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=4 \
	-e SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE=0 \
	-e ADP_LOCAL_FIXTURES_ENABLED=true \
	-e ADP_MOCK_RUNTIME_ENABLED=true \
	-e ADP_DATA_ACCESS_PREVIEW_ENABLED=true \
	-e ADP_CONTEXT_PREVIEW_ENABLED=true \
	-v "$(CURDIR)":/workspace \
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

.PHONY: help setup env docker-network postgres-up test-postgres-up test package check run docker-up docker-rebuild docker-down docker-logs docker-ps

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
