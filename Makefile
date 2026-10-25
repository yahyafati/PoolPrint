# Pool Print: common tasks. `make help` lists them.
#
# Release is the default everywhere. Add `BUILD=debug` for debug variants, e.g. `make install-phone BUILD=debug`.
# Several devices attached? Pick one with `make install-phone DEVICE=<serial>` (see `make devices`).

BUILD ?= release
DEVICE ?=

GRADLE := ./gradlew
ADB := adb $(if $(DEVICE),-s $(DEVICE))
APP_ID := com.yahyafati.poolprint
MAC_APP_NAME := Pool Print.app
INSTALL_DIR := /Applications

ifeq ($(BUILD),release)
  ANDROID_ASSEMBLE := :androidApp:assembleRelease
  ANDROID_INSTALL := :androidApp:installRelease
  ANDROID_UNINSTALL := :androidApp:uninstallRelease
  APK := androidApp/build/outputs/apk/release/androidApp-release.apk
  DESKTOP_APP_TASK := :desktopApp:createReleaseDistributable
  DESKTOP_DMG_TASK := :desktopApp:packageReleaseDmg
  DESKTOP_APP := desktopApp/build/compose/binaries/main-release/app/$(MAC_APP_NAME)
  DESKTOP_DMG_DIR := desktopApp/build/compose/binaries/main-release/dmg
else ifeq ($(BUILD),debug)
  ANDROID_ASSEMBLE := :androidApp:assembleDebug
  ANDROID_INSTALL := :androidApp:installDebug
  ANDROID_UNINSTALL := :androidApp:uninstallDebug
  APK := androidApp/build/outputs/apk/debug/androidApp-debug.apk
  DESKTOP_APP_TASK := :desktopApp:createDistributable
  DESKTOP_DMG_TASK := :desktopApp:packageDmg
  DESKTOP_APP := desktopApp/build/compose/binaries/main/app/$(MAC_APP_NAME)
  DESKTOP_DMG_DIR := desktopApp/build/compose/binaries/main/dmg
else
  $(error BUILD must be 'release' or 'debug', got '$(BUILD)')
endif

# Gradle's installXxx task picks the device from ANDROID_SERIAL.
ifneq ($(DEVICE),)
  export ANDROID_SERIAL := $(DEVICE)
endif

.DEFAULT_GOAL := help
.PHONY: help all build android desktop dmg install install-phone install-pc install-apk uninstall-phone uninstall-pc \
        launch-phone run-pc run-pc-dev hot-reload test test-android test-all ssh-server devices clean

help: ## Show this help
	@echo "BUILD=$(BUILD)  (override with BUILD=debug)"
	@echo
	@grep -E '^[a-zA-Z_-]+:.*## ' $(MAKEFILE_LIST) | awk -F':.*## ' '{printf "  \033[1m%-16s\033[0m %s\n", $$1, $$2}'

# --- Build -------------------------------------------------------------------------------------------------------

all: android desktop ## Build the Android APK and the macOS app

build: all

android: ## Build the Android APK
	$(GRADLE) $(ANDROID_ASSEMBLE)
	@echo "APK: $(APK)"

desktop: ## Build the macOS .app bundle
	$(GRADLE) $(DESKTOP_APP_TASK)
	@echo "App: $(DESKTOP_APP)"

dmg: ## Build the macOS .dmg installer
	$(GRADLE) $(DESKTOP_DMG_TASK)
	@echo "DMG: $$(ls $(DESKTOP_DMG_DIR)/*.dmg)"

# --- Install -----------------------------------------------------------------------------------------------------

install: install-phone install-pc ## Install on the phone and on this Mac

install-phone: ## Build and install on the connected Android device (adb)
	@$(ADB) get-state >/dev/null 2>&1 || { echo "No Android device found. Check 'make devices'."; exit 1; }
	$(GRADLE) $(ANDROID_INSTALL)
	@$(MAKE) --no-print-directory launch-phone

install-apk: android ## Install the already-built APK with adb (no Gradle install task)
	$(ADB) install -r "$(APK)"

install-pc: desktop ## Build and install the app into /Applications on this Mac
	@if pgrep -f "$(MAC_APP_NAME)" >/dev/null; then echo "Quitting running Pool Print..."; osascript -e 'quit app "Pool Print"' || true; sleep 1; fi
	rm -rf "$(INSTALL_DIR)/$(MAC_APP_NAME)"
	cp -R "$(DESKTOP_APP)" "$(INSTALL_DIR)/"
	@echo "Installed $(INSTALL_DIR)/$(MAC_APP_NAME)"

uninstall-phone: ## Remove the app from the connected Android device
	$(ADB) uninstall $(APP_ID)

uninstall-pc: ## Remove the app from /Applications
	rm -rf "$(INSTALL_DIR)/$(MAC_APP_NAME)"

# --- Run ---------------------------------------------------------------------------------------------------------

launch-phone: ## Start the already-installed app on the Android device
	$(ADB) shell monkey -p $(APP_ID) -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1

run-pc: ## Run the macOS app from Gradle (follows BUILD)
ifeq ($(BUILD),release)
	$(GRADLE) :desktopApp:runRelease
else
	$(GRADLE) :desktopApp:run
endif

hot-reload: ## Run the desktop app with Compose hot reload
	$(GRADLE) :desktopApp:hotRun --auto

# --- Test and misc -----------------------------------------------------------------------------------------------

test: ## Shared tests on the JVM
	$(GRADLE) :shared:jvmTest

test-android: ## Shared tests on the Android host (local JVM)
	$(GRADLE) :shared:testAndroidHostTest

test-all: test test-android ## Run every test suite

ssh-server: ## Standalone test SSH server on port 2222 (emulator: 10.0.2.2)
	$(GRADLE) :shared:runTestSshServer

devices: ## List attached Android devices
	adb devices -l

clean: ## Gradle clean
	$(GRADLE) clean
