FROM openjdk:8-jdk-alpine
EXPOSE 8080
VOLUME /tmp
ARG JAR_FILE
ADD ${JAR_FILE} app.jar
ENV JAVA_OPTS=""
ENTRYPOINT ["java","$JAVA_OPTS","-Djava.security.egd=file:/dev/./urandom","-jar","/app.jar"]