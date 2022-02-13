package org.lsposed.patch.util;

public class JavaLogger extends Logger {

    @Override
    public void d(String msg) {
        if (verbose) System.out.println(msg);
    }

    @Override
    public void i(String msg) {
        System.out.println(msg);
    }

    @Override
    public void e(String msg) {
        System.err.println(msg);
    }
}
