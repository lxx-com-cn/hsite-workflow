package com.hbs.site.module.bfm.parser;

/**
 * 解析异常
 */
public class ParserException extends Exception {
    public ParserException(String message) {
        super(message);
    }

    public ParserException(String message, Throwable cause) {
        super(message, cause);
    }
}