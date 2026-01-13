FROM amazoncorretto:17-alpine
# 명확하게 build/libs 폴더 아래의 jar 파일을 지정
# (bootJar 명령으로 생성된 파일만 가져오도록 패턴 수정)
COPY build/libs/*-SNAPSHOT.jar app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]