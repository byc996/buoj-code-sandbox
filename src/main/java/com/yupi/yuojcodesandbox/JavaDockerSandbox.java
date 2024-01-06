package com.yupi.yuojcodesandbox;

import cn.hutool.core.date.StopWatch;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.json.JSONUtil;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.InvocationBuilder;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.yupi.yuojcodesandbox.model.ExecuteCodeRequest;
import com.yupi.yuojcodesandbox.model.ExecuteCodeResponse;
import com.yupi.yuojcodesandbox.model.ExecuteMessage;
import com.yupi.yuojcodesandbox.model.LanguageConfig;
import com.yupi.yuojcodesandbox.model.enums.ExecutionStatusEnum;
import com.yupi.yuojcodesandbox.utils.ProcessUtils;
import jdk.nashorn.internal.runtime.regexp.joni.constants.internal.StringType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class JavaDockerSandbox extends DockerSandboxTemplate{

    private static final String Main_CLASS_NAME = "Main";
    private static final String SOLUTION_CLASS_NAME = "Solution";
    private static final String JAVA_SUFFIX = ".java";
    private static final String PYTHON_SUFFIX = ".py";

    private static final String JDK_DOCKER_IMAGE = "openjdk:8-alpine";

    private static final long TIME_OUT = 5000L;

    @Override
    public ExecuteMessage compileFile(File userCodeFile) {
        String userCodeParentPath = userCodeFile.getParent();
        //userCodeFile.getAbsoluteFile()
        String compileCmd = String.format("javac -encoding utf-8 -cp %s %s", userCodeParentPath, userCodeFile.getAbsolutePath());
        System.out.println(compileCmd);
        ExecuteMessage executeMessage = new ExecuteMessage();
        try {
            Process compileProcess = Runtime.getRuntime().exec(compileCmd);
            // 等待程序执行，获取错误码
            executeMessage = ProcessUtils.runProcessAndGetMessage(compileProcess, "compile");
            if (executeMessage.getExitValue() != 0) {
                try {
                    String errMsg = executeMessage.getErrorMessage();
                    System.out.println("errorMessage: " + errMsg);
                    executeMessage.setErrorMessage(errMsg.substring(errMsg.indexOf("Main.java")));
                } catch (Exception e1){}
            }
        } catch (IOException e) {
            executeMessage.setExitValue(-1);
            executeMessage.setErrorMessage(e.getMessage());

//            throw new RuntimeException(e);
//            return getErrorResponse(e);
        }
        return executeMessage;

    }

    @Override
    public List<ExecuteMessage> runFile(File userCodeFile, List<String> inputList) {
        String userCodeParentPath = userCodeFile.getParent();
        // 获取默认的dockerclient
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();


        CreateContainerCmd containerCmd = dockerClient.createContainerCmd(JDK_DOCKER_IMAGE);
        HostConfig hostConfig = new HostConfig();
        hostConfig.withMemory(100 * 1000 * 1000L);
        hostConfig.withMemorySwap(0L);
        hostConfig.withCpuCount(1L);
//        hostConfig.withSecurityOpts(Arrays.asList("seccomp=xxx"));
        hostConfig.setBinds(new Bind(userCodeParentPath, new Volume("/app")));
        CreateContainerResponse createContainerResponse = containerCmd
                .withHostConfig(hostConfig)
                .withNetworkDisabled(true)
                .withReadonlyRootfs(true)
                .withAttachStdin(true)
                .withAttachStdout(true)
                .withAttachStderr(true)
                .withTty(true)
                .exec();
//        System.out.println(createContainerResponse);
        String containerId = createContainerResponse.getId();

        // 启动容器
        dockerClient.startContainerCmd(containerId).exec();

        // docker exec gallant_bhaskara java -cp /app Main 1 3
        // 执行命令并获取结果
        List<ExecuteMessage> executeMessageList = new ArrayList<>();
        for (String inputArgs : inputList) {
            StopWatch stopWatch = new StopWatch();

            String[] args = inputArgs.split(" ");
            String[] cmdArray = ArrayUtil.append(new String[]{"java", "-cp", "/app", "Main"}, args);
            ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(containerId)
                    .withCmd(cmdArray)
                    .withAttachStdin(true)
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .exec();
//            System.out.println("创建执行命令：" + execCreateCmdResponse);
            ExecuteMessage executeMessage = new ExecuteMessage();
//            final String[] message = {null};
//            final String[] errorMessage = {null};
            List<String> messages = new ArrayList<>();
            List<String> errorMessages = new ArrayList<>();
            long time = 0L;
            // 判断是否超时
            final boolean[] timeout = {true};
            String execId = execCreateCmdResponse.getId();
            ExecStartResultCallback execStartResultCallback = new ExecStartResultCallback() {
                @Override
                public void onComplete() {
                    // 如果执行完成，则表示没超时
                    timeout[0] = false;
                    super.onComplete();
                }

                @Override
                public void onError(Throwable throwable) {
                    // 如果执行错误，则表示超时
                    timeout[0] = true;
//                    System.out.println("onError: " + throwable.getMessage());
                    super.onError(throwable);
                }

                @Override
                public void onNext(Frame frame) {
                    StreamType streamType = frame.getStreamType();
                    System.out.println(streamType.toString() + new String(frame.getPayload()));
                    if (StreamType.STDERR.equals(streamType)) {
                        errorMessages.add(new String(frame.getPayload()));
//                        System.out.println("输出错误结果：" + errorMessage[0]);
                    } else if (StreamType.STDOUT.equals(streamType)){
                        messages.add(new String(frame.getPayload()));
//                        String temp = new String(frame.getPayload());
//                        message[0] = temp;
//                        System.out.println("输出结果：" + message[0]);
                    }

                    super.onNext(frame);
                }
            };
//            final long[] maxMemory = {0L};
            Statistics stats;
//            executeMessage.setMemory(maxMemory[0]);
            try {
                stopWatch.start();
                boolean isSuccess = dockerClient.execStartCmd(execId).exec(execStartResultCallback).awaitCompletion(TIME_OUT, TimeUnit.MILLISECONDS);
//                System.out.println("isSuccess: " + isSuccess);
//                if (isSuccess) {
//                    executeMessage.setExitValue(0);
//                } else {
//                    executeMessage.setExitValue(1);
//                }
                stopWatch.stop();
                time = stopWatch.getLastTaskTimeMillis();
                stats = getNextStatistics(dockerClient, containerId);
//                System.out.println(stats);
                executeMessage.setMemory(stats.getMemoryStats().getMaxUsage());

//                callback.close();
            } catch (Exception e) {
                System.out.println("程序执行异常");
                throw new RuntimeException(e);
            }
            String message = String.join("", messages).trim();
            String errorMessage = String.join("", errorMessages).trim();
            executeMessage.setMessage(message);

            executeMessage.setErrorMessage(errorMessage);
            executeMessage.setTime(time);
//            executeMessage.setMemory(maxMemory[0]);
//            executeMessage.setMemory(0L);
            if (StringUtils.isBlank(errorMessage)) {
                executeMessage.setExitValue(0);
            } else {
                executeMessage.setExitValue(1);
            }
            executeMessageList.add(executeMessage);
        }
        System.out.println(executeMessageList);
        // 删除临时容器
        System.out.println("删除容器：" + containerId);
        StopContainerCmd stopContainerCmd = dockerClient.stopContainerCmd(containerId);
        stopContainerCmd.exec();
        RemoveContainerCmd removeContainerCmd = dockerClient.removeContainerCmd(containerId);
        removeContainerCmd.withForce(true).exec();

        return executeMessageList;
    }

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        List<String> inputList = executeCodeRequest.getInputList();
        String code = executeCodeRequest.getCode();
        String language = executeCodeRequest.getLanguage();
        String mainClass = executeCodeRequest.getMainClass();
        mainClass = JSONUtil.toBean(mainClass, LanguageConfig.class).getJava();

        // 1. 把用户代码保存为文件
        String solutionFileName = SOLUTION_CLASS_NAME + JAVA_SUFFIX;
        String mainFileName = Main_CLASS_NAME + JAVA_SUFFIX;
//        File userCodeFile;
        List<File> files;
        executeCodeResponse.setTime(0L);
        executeCodeResponse.setMemory(0L);
        try {
//            File solutionName = saveCodeToFile(code, solutionFileName);
//            System.out.println("solution: " +solutionName.getAbsolutePath());
            files = saveCodeToFile(code, solutionFileName, mainClass, mainFileName);
//            System.out.println("userCodeFile: " +userCodeFile.getAbsolutePath());
        } catch (Exception e) {
            executeCodeResponse.setStatus(ExecutionStatusEnum.SYSTEM_ERROR.getValue());
            executeCodeResponse.setMessage(ExecutionStatusEnum.SYSTEM_ERROR.getText());
            executeCodeResponse.setDetailMessage(e.getMessage());
            return executeCodeResponse;
        }
        // 2. 编译代码，得到class文件
        boolean compileSuccess = true;
        ExecuteMessage compileFileExecuteMessage = new ExecuteMessage();
        for (File file : files) {
            compileFileExecuteMessage = compileFile(file);
            System.out.println("compileFileExecuteMessage: " + compileFileExecuteMessage);
            if (compileFileExecuteMessage.getExitValue() != 0) {
                compileSuccess = false;
                executeCodeResponse.setStatus(ExecutionStatusEnum.COMPILE_ERROR.getValue());
                executeCodeResponse.setMessage(ExecutionStatusEnum.COMPILE_ERROR.getText());
                executeCodeResponse.setDetailMessage(compileFileExecuteMessage.getErrorMessage());
                break;
            }
        }
        File userCodeFile = files.get(1);
        if (compileSuccess) {
//            System.out.println(compileFileExecuteMessage);
//            String containerId = createAndStartContainer(JDK_DOCKER_IMAGE, userCodeFile.getParent());
            // 3. 执行代码，得到输出结果
            List<ExecuteMessage> executeMessageList = runFile(userCodeFile, inputList);
            // 4. 收集整理输出结果
            executeCodeResponse = getOutputResponse(executeMessageList);
            System.out.println(executeCodeResponse);
            // 5. 文件清理
            boolean b = deleteFile(userCodeFile);
            if (!b) {
                log.error("deleteFile error, userCodeFile = {}", userCodeFile.getAbsoluteFile());
            }
        }
//        executeCodeResponse.setQuestionSubmitId(executeCodeRequest.getQuestionSubmitId());

        return executeCodeResponse;
    }

    private Statistics getNextStatistics(DockerClient client, String containerId) {
        InvocationBuilder.AsyncResultCallback<Statistics> callback = new InvocationBuilder.AsyncResultCallback<>();
        client.statsCmd(containerId).exec(callback);
        Statistics stats = null;
        try {
            stats = callback.awaitResult();
            callback.close();
        } catch (RuntimeException | IOException e) {
            // you may want to throw an exception here
            System.out.println("获取统计状态异常");
        }
        return stats; // this may be null or invalid if the container has terminated
    }
}
