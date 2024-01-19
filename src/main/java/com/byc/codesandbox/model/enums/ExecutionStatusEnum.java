package com.byc.codesandbox.model.enums;

import org.apache.commons.lang3.ObjectUtils;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public enum ExecutionStatusEnum {

    SUCCESS("通过", 0),
    COMPILE_ERROR("编译错误", 2),
    RUNTIME_ERROR("运行错误", 3),
//    OUTPUT_LIMIT_EXCEEDED("输出过长", 6),
    SYSTEM_ERROR("系统错误", 7),
    PRESENTATION_ERROR("展示错误", 8),
    WAITING("等待中", 9),
    DANGEROUS_OPERATION("危险操作", 10);

    private final Integer value;

    private final String text;


    ExecutionStatusEnum(String text, Integer value) {
        this.text = text;
        this.value = value;
    }

    /**
     * 获取值列表
     *
     * @return
     */
    public static List<Integer> getValues() {
        return Arrays.stream(values()).map(item -> item.value).collect(Collectors.toList());
    }

    /**
     * 根据 value 获取枚举
     *
     * @param value
     * @return
     */
    public static ExecutionStatusEnum getEnumByValue(Integer value) {
        if (ObjectUtils.isEmpty(value)) {
            return null;
        }
        for (ExecutionStatusEnum anEnum : ExecutionStatusEnum.values()) {
            if (anEnum.value.equals(value)) {
                return anEnum;
            }
        }
        return null;
    }

    public Integer getValue() {
        return value;
    }

    public String getText() {
        return text;
    }
}
