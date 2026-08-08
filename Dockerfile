FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app

# Install Python 3 + libraries needed by translate.py and tts.py
RUN apt-get update && \
    apt-get install -y python3 python3-pip && \
    ln -s /usr/bin/python3 /usr/bin/python && \
    pip3 install --break-system-packages --no-cache-dir deep-translator gTTS && \
    rm -rf /var/lib/apt/lists/*

COPY --from=build /app/target/*.jar app.jar
COPY translate.py /app/translate.py
COPY tts.py /app/tts.py

EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]