SHELL := /bin/sh

GRADLE_IMAGE ?= gradle:8.14.3-jdk21
DOCKER_RUN_GRADLE := docker run --rm \
	-v "$(CURDIR)":/workspace \
	-v adp-be-gradle-cache:/home/gradle/.gradle \
	-w /workspace \
	$(GRADLE_IMAGE) gradle --no-daemon
DOCKER_RUN_GRADLE_TEST := docker run --rm --network adp-local \
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
	$(GRADLE_IMAGE) gradle --no-daemon

.PHONY: help setup postgres-up test package check run docker-up docker-down

help:
	@printf "%s\n" \
		"ADP-BE commands:" \
		"  make setup       Verify Docker-based Gradle toolchain" \
		"  make postgres-up Start PostgreSQL for local tests" \
		"  make test        Run unit/integration tests" \
		"  make package     Build executable jar" \
		"  make check       Run BE-0 verification" \
		"  make run         Run locally through Docker Gradle image" \
		"  make docker-up   Build and start container" \
		"  make docker-down Stop container"

setup:
	docker --version
	docker run --rm $(GRADLE_IMAGE) gradle --version

postgres-up:
	docker network inspect adp-local >/dev/null 2>&1 || docker network create adp-local
	docker compose up -d postgres

test: postgres-up
	$(DOCKER_RUN_GRADLE_TEST) test

package:
	$(DOCKER_RUN_GRADLE) bootJar

check: test package

run: postgres-up
	$(DOCKER_RUN_GRADLE_TEST) bootRun

docker-up:
	docker network inspect adp-local >/dev/null 2>&1 || docker network create adp-local
	docker compose up --build

docker-down:
	docker compose down
