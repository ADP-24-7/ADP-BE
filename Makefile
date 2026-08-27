SHELL := /bin/sh

MVN_IMAGE ?= maven:3.9.16-eclipse-temurin-21
DOCKER_RUN_MVN := docker run --rm -v "$(CURDIR)":/workspace -w /workspace $(MVN_IMAGE) mvn

.PHONY: help setup test package check run docker-up docker-down

help:
	@printf "%s\n" \
		"ADP-BE commands:" \
		"  make setup       Verify Docker-based Maven toolchain" \
		"  make test        Run unit/integration tests" \
		"  make package     Build executable jar" \
		"  make check       Run BE-0 verification" \
		"  make run         Run locally through Docker Maven image" \
		"  make docker-up   Build and start container" \
		"  make docker-down Stop container"

setup:
	docker --version
	docker run --rm $(MVN_IMAGE) mvn -version

test:
	$(DOCKER_RUN_MVN) test

package:
	$(DOCKER_RUN_MVN) package

check: test package

run:
	$(DOCKER_RUN_MVN) spring-boot:run

docker-up:
	docker network inspect adp-local >/dev/null 2>&1 || docker network create adp-local
	docker compose up --build

docker-down:
	docker compose down
