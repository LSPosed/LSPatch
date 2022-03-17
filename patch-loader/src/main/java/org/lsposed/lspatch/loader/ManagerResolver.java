package org.lsposed.lspatch.loader;

import static org.lsposed.lspatch.share.Constants.MANAGER_PACKAGE_NAME;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;

import org.lsposed.lspatch.manager.IManagerService;
import org.lsposed.lspd.models.Module;

import java.util.List;

public class ManagerResolver {
    private static final Uri PROVIDER = Uri.parse("content://" + MANAGER_PACKAGE_NAME + ".provider");

    private final IManagerService service;

    public ManagerResolver(Context context) throws RemoteException {
        try {
            Bundle back = context.getContentResolver().call(PROVIDER, "getBinder", null, null);
            service = IManagerService.Stub.asInterface(back.getBinder("binder"));
            if (service == null) throw new RemoteException("Binder is null");
        } catch (Throwable t) {
            var e = new RemoteException("Failed to get manager binder");
            e.setStackTrace(new StackTraceElement[0]);
            e.addSuppressed(t);
            throw e;
        }
    }

    public List<Module> getModules() throws RemoteException {
        return service.getModules();
    }
}
