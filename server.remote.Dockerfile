FROM maven:3.9.9-eclipse-temurin-11 AS build

WORKDIR /workspace

COPY . .

RUN mvn -s settings.xml -pl snowy-web-app -am -DskipTests clean package

FROM eclipse-temurin:11-jre-jammy

ENV TZ=Asia/Shanghai
ENV LANG=C.UTF-8
ENV LC_ALL=C.UTF-8

WORKDIR /app

COPY --from=build /workspace/snowy-web-app/target/snowy-web-app-2.0.0.jar /app/gago-snowy.jar

EXPOSE 82

ENTRYPOINT ["java", "-Xmx2G", "-Xms1G", "-jar", "/app/gago-snowy.jar"]
