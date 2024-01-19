# 基础镜像
FROM openjdk:8-jdk-alpine
  
# 指定工作目录
WORKDIR /app
  
# 将 jar 包添加到工作目录，比如 target/buoj-backend-user-service-0.0.1-SNAPSHOT.jar
ADD target/buoj-code-sandbox-0.0.1-SNAPSHOT.jar .
ADD tmpCode/Template /app/tmpCode/
  
# 暴露端口
EXPOSE 8090
  
# 启动命令
ENTRYPOINT ["java","-jar","/app/buoj-code-sandbox-0.0.1-SNAPSHOT.jar","--spring.profiles.active=prod"]