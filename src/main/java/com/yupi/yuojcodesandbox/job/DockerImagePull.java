package com.yupi.yuojcodesandbox.job;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.PullImageCmd;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.model.PullResponseItem;
import com.github.dockerjava.core.DockerClientBuilder;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
@Component
public class DockerImagePull implements CommandLineRunner {
    @Override
    public void run(String... args) throws Exception {
        // 获取默认的dockerclient
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();
        // 拉取镜像
        String javaImage = "openjdk:8-alpine";
        PullImageCmd pullImageCmd = dockerClient.pullImageCmd(javaImage);
        PullImageResultCallback pullImageResultCallback = new PullImageResultCallback() {
            @Override
            public void onNext(PullResponseItem item) {
//                System.out.println("下载Java镜像：" + item.isPullSuccessIndicated());
                super.onNext(item);
            }
        };
        System.out.println("开始下载Java镜像...");
        pullImageCmd.exec(pullImageResultCallback).awaitCompletion();
        System.out.println("下载完成");
        String pythonImage = "python:3.9-slim";
        pullImageCmd = dockerClient.pullImageCmd(pythonImage);
        System.out.println("开始下载Python镜像...");
        pullImageCmd.exec(pullImageResultCallback).awaitCompletion();
        System.out.println("下载完成");
    }
}
