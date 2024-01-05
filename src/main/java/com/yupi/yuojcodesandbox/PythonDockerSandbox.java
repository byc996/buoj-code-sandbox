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
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class PythonDockerSandbox extends DockerSandboxTemplate{

    private static final String Main_CLASS_NAME = "Main";
    private static final String SOLUTION_CLASS_NAME = "Solution";
    private static final String JAVA_SUFFIX = ".java";
    private static final String PYTHON_SUFFIX = ".py";

    private static final String PYTHON_DOCKER_IMAGE = "python:3.9-slim";

    private static final long TIME_OUT = 5000L;


    @Override
    public List<ExecuteMessage> runFile(File userCodeFile, List<String> inputList) {
        String userCodeParentPath = userCodeFile.getParent();
        // 获取默认的dockerclient
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();

        CreateContainerCmd containerCmd = dockerClient.createContainerCmd(PYTHON_DOCKER_IMAGE);
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
            String[] cmdArray = ArrayUtil.append(new String[]{"python3", "/app/Main.py"}, args);
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
                dockerClient.execStartCmd(execId).exec(execStartResultCallback)
                        .awaitCompletion(TIME_OUT, TimeUnit.MILLISECONDS);
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
//                throw new RuntimeException(e);
                executeMessage.setErrorMessage(e.getMessage());
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
        System.out.println("删除容器完成");
        return executeMessageList;
    }

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        List<String> inputList = executeCodeRequest.getInputList();
        String code = executeCodeRequest.getCode();
        String language = executeCodeRequest.getLanguage();
        String mainClass = executeCodeRequest.getMainClass();
        mainClass = JSONUtil.toBean(mainClass, LanguageConfig.class).getPython();

        // 1. 把用户代码保存为文件
        String solutionFileName = SOLUTION_CLASS_NAME + PYTHON_SUFFIX;
        String mainFileName = Main_CLASS_NAME + PYTHON_SUFFIX;
        File userCodeFile;
        executeCodeResponse.setTime(0L);
        executeCodeResponse.setMemory(0L);
        try {
//            File solutionName = saveCodeToFile(code, solutionFileName);
//            System.out.println("solution: " +solutionName.getAbsolutePath());
            userCodeFile = saveCodeToFile(code, solutionFileName, mainClass, mainFileName).get(1);
            System.out.println("userCodeFile: " +userCodeFile.getAbsolutePath());
        } catch (Exception e) {
            executeCodeResponse.setStatus(ExecutionStatusEnum.SYSTEM_ERROR.getValue());
            executeCodeResponse.setMessage(ExecutionStatusEnum.SYSTEM_ERROR.getText());
            executeCodeResponse.setDetailMessage(e.getMessage());
            return executeCodeResponse;
        }
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
