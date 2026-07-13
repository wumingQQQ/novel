FROM eclipse-temurin:21-jre

WORKDIR /app

ARG MODULE

COPY artifacts/${MODULE}.jar /app/app.jar

ENV TZ=Asia/Shanghai

ENTRYPOINT ["java", "-Duser.timezone=Asia/Shanghai", "-jar", "/app/app.jar"]
