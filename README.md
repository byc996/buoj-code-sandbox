## 项目介绍

BU OJ后端代码沙箱项目：判题服务调用该代码沙箱，代码沙箱根据用户提交代码和判题用例编译运行代码，并返回执行结果。

**项目地址：** [BU OJ 判题系统](http://oj.bc2996.com/)

### 项目结构

#### 1. buoj-backend-micorservice： [后端微服务](https://github.com/byc996/buoj-backend-microservice)

#### 2. buoj-code-sandbox：目前支持Java和Python语言进行判题



## 核心业务流程

1. 保存用户代码到本地文件
2. 如果为编译型语言，需要对代码文件进行编译
3. 创建临时容器（作为隔离环境，运行代码），并绑定映射代码文件夹
4. 循环测试用例，执行代码
5. 获取输出结果、执行时间、最大所需内存，并返回



## 技术栈

- Java 8
- Spring Boot 2.7.14
- Docker-Java 用于与docker容器进行交互
- Hutool 工具类