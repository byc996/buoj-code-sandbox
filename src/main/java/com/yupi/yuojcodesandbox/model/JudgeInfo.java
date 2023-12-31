package com.yupi.yuojcodesandbox.model;

import lombok.Data;

/**
 * 判题信息
 */
@Data
public class JudgeInfo {

    /**
     * 程序执行信息  比如：通过
     */
    private String message;

    /**
     * 程序执行信息  比如：通过
     */
    private String detailMessage;

    /**
     * 程序执行状态  比如：0
     */
    private Integer status;


    /**
     * 消耗内存
     */
    private Long memory;

    /**
     * 消耗时间（KB）
     */
    private Long time;
}
