package com.hbs.site.module.bfm.engine.subprocess;

/**
 * TX模式上下文持有者
 * 用于跨组件传递TX模式状态，禁用重试等干扰事务的行为
 */
public class TxModeHolder {
    private static final ThreadLocal<Boolean> TX_MODE = new ThreadLocal<>();

    public static void setTxMode(boolean isTx) {
        TX_MODE.set(isTx);
    }

    public static boolean isTxMode() {
        return Boolean.TRUE.equals(TX_MODE.get());
    }

    public static void clear() {
        TX_MODE.remove();
    }
}