package org.lsposed.patch.util;

public abstract class Logger {

    public boolean verbose = false;

    abstract public void d(String msg);

    abstract public void i(String msg);

    abstract public void e(String msg);
}
