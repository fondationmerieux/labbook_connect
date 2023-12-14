REGISTRY_NAME=localhost
IMAGE_NAME=labbook-connect
FULL_IMAGE_NAME=$(REGISTRY_NAME)/$(IMAGE_NAME)
DOCKER_COMMAND=podman
SAVE_DIR=/tmp
DEFAULT_URL_PREFIX=/connect

# TODO
# - read version number somewhere
# - add LOGLEVEL setup
# - add map to storage volume if running with LabBook

DEFAULT_VERSION=1.0.0

# $(info DEFAULT_VERSION=$(DEFAULT_VERSION)

CONTAINER_NAME=labbook_connect
DEVRUN_HTTP=8080
DEVRUN_STORAGE?=$(shell pwd)/devrun_storage
# The Z option tells Podman that two or more <<containers|pods>> share the volume content
DEVRUN_MAP_STORAGE=$(DEVRUN_STORAGE):/storage:Z
DEVRUN_LOG_DIR?=$(shell pwd)/logs
DEVRUN_WORKDIR?=/app
DEVRUN_GENERAL_OPTIONS=--rm --detach --name=$(CONTAINER_NAME) --publish=$(DEVRUN_HTTP):$(DEVRUN_HTTP)
DEVRUN_ENV_OPTIONS=--tz=local --env TZ --env TERM --env LANG
DEVRUN_VOLUME_OPTIONS=\
--volume=$(DEVRUN_MAP_STORAGE) \
--volume=$(DEVRUN_LOG_DIR):$(DEVRUN_WORKDIR)/logs

ifdef VERSION
BUILD_VERSION=$(VERSION)
else
BUILD_VERSION=$(DEFAULT_VERSION)
endif

TAG_NAME=v$(BUILD_VERSION)

ifeq (, $(shell type podman 2> /dev/null))
DOCKER_COMMAND=docker
endif

.PHONY: help
help:
	@echo 'Usage:'
	@echo ''
	@echo '  make devbuild      build image $(FULL_IMAGE_NAME):latest from working directory'
	@echo '  make devclean      remove image $(FULL_IMAGE_NAME):latest'
	@echo '  make devrun        run the application access from http://localhost:$(DEVRUN_HTTP)$(DEFAULT_URL_PREFIX)'
	@echo '  make devstop       stop the application'
	@echo '  make devreload     stop, clean, build and run'

.PHONY: devbuild
devbuild:
	$(DOCKER_COMMAND) build . -t $(FULL_IMAGE_NAME):latest

.PHONY: devclean
devclean:
	$(DOCKER_COMMAND) rmi $(FULL_IMAGE_NAME):latest

.PHONY: devrun
devrun:
	mkdir -p $(DEVRUN_LOG_DIR)
	mkdir -p $(DEVRUN_STORAGE)
	rsync -a ./storage/ $(DEVRUN_STORAGE)
	$(DOCKER_COMMAND) run $(DEVRUN_GENERAL_OPTIONS) $(DEVRUN_ENV_OPTIONS) $(DEVRUN_VOLUME_OPTIONS) $(FULL_IMAGE_NAME):latest

.PHONY: devstop
devstop:
	$(DOCKER_COMMAND) stop $(CONTAINER_NAME)

.PHONY: devreload
devreload: devstop devclean devbuid devrun
