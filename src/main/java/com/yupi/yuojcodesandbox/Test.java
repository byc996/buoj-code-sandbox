package com.yupi.yuojcodesandbox;

import java.math.BigDecimal;


public class Test {

    public static void main(String[] args) {

        double originalNumber = 93.141592653589793;

        // 使用BigDecimal进行精确计算
        BigDecimal bigDecimalNumber = new BigDecimal(originalNumber);
        BigDecimal roundedNumber = bigDecimalNumber.setScale(5, BigDecimal.ROUND_HALF_UP);

        System.out.println(roundedNumber);
    }
}
