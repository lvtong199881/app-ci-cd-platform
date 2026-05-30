FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# 复制构建产物
COPY build/libs/*.jar app.jar

# Fly.io 需要应用监听 0.0.0.0:8080
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "-Dserver.address=0.0.0.0", "app.jar"]