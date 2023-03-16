# WORK IN PROGRESS!!!

## Docker instructions

### Building and pushing the image to Docker Hub

```
docker image rm bodlulu/slack-chatgpt-bot:latest
DOCKER_USERNAME=<your docker hub login> DOCKER_PASSWORD=<your docker hub password> ./gradlew dockerPushImage
```

### Running the image

```
docker pull bodlulu/slack-chatgpt-bot
docker run bodlulu/slack-chatgpt-bot <options>
```
