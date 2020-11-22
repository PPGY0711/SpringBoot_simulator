package com.jz.simulator.exception;

public class SimulatorException extends Exception{
    private String message;

    public SimulatorException(String message){
        super(message);
        this.message = message;
    }
}
