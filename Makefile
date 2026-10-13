IMAGE ?= abhaypratap/mockhub
VERSION ?= $(shell grep '^version' build.gradle.kts | cut -d'"' -f2)

.PHONY: build test ui-dev run docker docker-run clean

build:
	./gradlew build -PwithUi

test:
	./gradlew test uiTest

ui-dev:
	cd ui && npm run dev

run: build
	MOCKHUB_DATA_DIR=./mockhub-data java -jar build/libs/mockhub.jar

docker:
	docker build -t $(IMAGE):$(VERSION) -t $(IMAGE):latest .

docker-run:
	docker run --rm -p 8080:8080 -v mockhub-data:/data $(IMAGE):latest

clean:
	./gradlew clean
	rm -rf ui/dist
