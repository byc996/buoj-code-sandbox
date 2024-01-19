package com.byc.codesandbox;

import cn.hutool.core.io.FileUtil;
import com.byc.codesandbox.model.ExecuteCodeResponse;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.core.DockerClientBuilder;
import com.byc.codesandbox.model.ExecuteCodeRequest;
import com.byc.codesandbox.model.ExecuteMessage;
import com.byc.codesandbox.model.enums.ExecutionStatusEnum;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public abstract class DockerSandboxTemplate {

    private static final String GLOBAL_CODE_DIR_NAME = "tmpCode";

    private static final long TIME_OUT = 5000L;

    private static final String DELIMITER = "MainResult: ";

    DockerClient dockerClient = DockerClientBuilder.getInstance().build();

    /**
     * 1. 把用户代码保存为文件
     *
     * @param code 用户代码
     * @return
     */
    public List<File> saveCodeToFile(String code, String solutionFileName,String mainClass,String mainFileName) {
        String userDir = System.getProperty("user.dir");
        String globalCodePathName = userDir + File.separator + GLOBAL_CODE_DIR_NAME;
        // 判断全局代码目录是否存在
        if (!FileUtil.exist(globalCodePathName)) {
            FileUtil.mkdir(globalCodePathName);
        }
        List<File> files = new ArrayList<>();
        // 把用户的代码隔离存放
        String userCodeParentPath = globalCodePathName + File.separator + UUID.randomUUID();
        String solutionCodePath = userCodeParentPath + File.separator + solutionFileName;
        File solutionFile = FileUtil.writeString(code, solutionCodePath, StandardCharsets.UTF_8);
        files.add(solutionFile);
        String mainCodePath = userCodeParentPath + File.separator + mainFileName;
//        String userCodePath = userCodeParentPath + File.separator + GLOBAL_JAVA_CLASS_NAME;
        File mainClassFile = FileUtil.writeString(mainClass, mainCodePath, StandardCharsets.UTF_8);
        files.add(mainClassFile);
        return files;
    }

    /**
     * 2. 编译代码
     *
     * @param userCodeFile
     * @return
     */
    public ExecuteMessage compileFile(File userCodeFile){
        return null;
    }

    /**
     * 启动容器
     *
     */
    public String createAndStartContainer(String image, String userCodeParentPath) {

        CreateContainerCmd containerCmd = dockerClient.createContainerCmd(image);
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
        System.out.println("启动容器: " +containerId);
        dockerClient.startContainerCmd(containerId).exec();
        return containerId;
    }

    /**
     * 3. 执行文件，获得执行结果列表
     *
     * @param userCodeFile
     * @param inputList
     * @return
     */
    public abstract List<ExecuteMessage> runFile(File userCodeFile, List<String> inputList);

    /**
     * 4. 整理输出结果
     * @param executeMessageList
     * @return
     */
    public ExecuteCodeResponse getOutputResponse(List<ExecuteMessage> executeMessageList) {
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        List<String> outputList = new ArrayList<>();
        executeCodeResponse.setStatus(ExecutionStatusEnum.SUCCESS.getValue());
        // 取用时最大值，便于判断是否超时
        long maxTime = 0;
        long maxMemory = 0;
        StringBuilder stdOut = new StringBuilder();
        for (ExecuteMessage executeMessage : executeMessageList) {
            if (executeMessage.getExitValue() == 0) {
                // split stdout and result
                String msg = executeMessage.getMessage();
                int lastIdx = msg.lastIndexOf(DELIMITER);
                if (lastIdx >= 0) {
                    stdOut.append(msg, 0, lastIdx);
                    outputList.add(msg.substring(lastIdx + DELIMITER.length()));
                } else {
                    outputList.add(msg);
                }
            } else {
                // 执行中存在错误
                executeCodeResponse.setStatus(ExecutionStatusEnum.RUNTIME_ERROR.getValue());
                executeCodeResponse.setMessage(ExecutionStatusEnum.RUNTIME_ERROR.getText());
                executeCodeResponse.setDetailMessage(executeMessage.getErrorMessage());
                break;
            }
            if (executeMessage.getTime() != null) {
                maxTime = Math.max(maxTime, executeMessage.getTime());
            }
            if (executeMessage.getMemory() != null) {
                maxMemory = Math.max(maxMemory, executeMessage.getMemory());
            }
        }
        executeCodeResponse.setOutputList(outputList);
        executeCodeResponse.setTime(maxTime);
        executeCodeResponse.setMemory(maxMemory);
        executeCodeResponse.setStdOut(stdOut.toString());
//        JudgeInfo judgeInfo = new JudgeInfo();
//
//        judgeInfo.setTime(maxTime);
//        // 要借助第三方库来获取内存占用
//        judgeInfo.setMemory(maxMemory);
//        executeCodeResponse.setJudgeInfo(judgeInfo);
        return executeCodeResponse;
    }

    /**
     * 5. 删除文件
     * @param userCodeFile
     * @return
     */
    public boolean deleteFile(File userCodeFile) {
        if (userCodeFile.getParentFile() != null) {
            String userCodeParentPath = userCodeFile.getParent();
            boolean del = FileUtil.del(userCodeParentPath);
            System.out.println("删除临时文件" + (del ? "成功" : "失败"));
            return del;
        }

        return true;
    }

    /**
     * 6. 获取错误响应
     *
     * @param
     * @return
     */
//    private ExecuteCodeResponse getErrorResponse(Exception e) {
//        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
//        executeCodeResponse.setOutputList(new ArrayList<>());
//        executeCodeResponse.setErrorMessage(e.getMessage());
//        // 2表示代码沙箱错误
//        executeCodeResponse.setStatus(2);
////        executeCodeResponse.setJudgeInfo(new JudgeInfo());
//        return executeCodeResponse;
//    }

    public abstract ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest);
}
