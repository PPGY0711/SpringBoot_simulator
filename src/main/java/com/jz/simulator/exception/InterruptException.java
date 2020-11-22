package com.jz.simulator.exception;

import lombok.Getter;
import lombok.Setter;

/**
 * 中断异常
 */
@Getter
@Setter
public class InterruptException extends Exception{
    private int intRequest; //中断号
    private short readChar; //读入的字符
    private int readInt;    //读入的整数
    private String readString;  //读入的字符串

    private String message; //异常信息
}
