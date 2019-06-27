call gradlew build -x test --no-daemon && java -jar -Dspring.profiles.active=dev build\libs\spring-demo2-0.0.1-SNAPSHOT.jar --server.port=8081 
pause