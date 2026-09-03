/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: /usr/local/lib/android/sdk/build-tools/35.0.0/aidl -p/usr/local/lib/android/sdk/platforms/android-35/framework.aidl -o/home/runner/work/com.game.reschange/com.game.reschange/app/build/generated/aidl_source_output_dir/debug/out -I/home/runner/work/com.game.reschange/com.game.reschange/app/src/main/aidl -I/home/runner/work/com.game.reschange/com.game.reschange/app/src/debug/aidl -I/home/runner/.gradle/caches/8.11.1/transforms/471e809463c7af0d0b893c1e3f18eac0/transformed/core-1.13.1/aidl -I/home/runner/.gradle/caches/8.11.1/transforms/e5e7a6b6e3950bd1a43c4f6577b0cdb4/transformed/versionedparcelable-1.1.1/aidl -d/tmp/aidl2626923360694180489.d /home/runner/work/com.game.reschange/com.game.reschange/app/src/main/aidl/com/game/reschange/IShellService.aidl
 */
package com.game.reschange;
public interface IShellService extends android.os.IInterface
{
  /** Default implementation for IShellService. */
  public static class Default implements com.game.reschange.IShellService
  {
    @Override public java.lang.String exec(java.lang.String command) throws android.os.RemoteException
    {
      return null;
    }
    // Codigo de transacao fixo que o Shizuku usa para encerrar o
    // servico ao trocar de versao (ver ShellUserService.destroy()).
    @Override public void destroy() throws android.os.RemoteException
    {
    }
    @Override
    public android.os.IBinder asBinder() {
      return null;
    }
  }
  /** Local-side IPC implementation stub class. */
  public static abstract class Stub extends android.os.Binder implements com.game.reschange.IShellService
  {
    /** Construct the stub at attach it to the interface. */
    @SuppressWarnings("this-escape")
    public Stub()
    {
      this.attachInterface(this, DESCRIPTOR);
    }
    /**
     * Cast an IBinder object into an com.game.reschange.IShellService interface,
     * generating a proxy if needed.
     */
    public static com.game.reschange.IShellService asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof com.game.reschange.IShellService))) {
        return ((com.game.reschange.IShellService)iin);
      }
      return new com.game.reschange.IShellService.Stub.Proxy(obj);
    }
    @Override public android.os.IBinder asBinder()
    {
      return this;
    }
    @Override public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException
    {
      java.lang.String descriptor = DESCRIPTOR;
      if (code >= android.os.IBinder.FIRST_CALL_TRANSACTION && code <= android.os.IBinder.LAST_CALL_TRANSACTION) {
        data.enforceInterface(descriptor);
      }
      if (code == INTERFACE_TRANSACTION) {
        reply.writeString(descriptor);
        return true;
      }
      switch (code)
      {
        case TRANSACTION_exec:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          java.lang.String _result = this.exec(_arg0);
          reply.writeNoException();
          reply.writeString(_result);
          break;
        }
        case TRANSACTION_destroy:
        {
          this.destroy();
          reply.writeNoException();
          break;
        }
        default:
        {
          return super.onTransact(code, data, reply, flags);
        }
      }
      return true;
    }
    private static class Proxy implements com.game.reschange.IShellService
    {
      private android.os.IBinder mRemote;
      Proxy(android.os.IBinder remote)
      {
        mRemote = remote;
      }
      @Override public android.os.IBinder asBinder()
      {
        return mRemote;
      }
      public java.lang.String getInterfaceDescriptor()
      {
        return DESCRIPTOR;
      }
      @Override public java.lang.String exec(java.lang.String command) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        java.lang.String _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(command);
          boolean _status = mRemote.transact(Stub.TRANSACTION_exec, _data, _reply, 0);
          _reply.readException();
          _result = _reply.readString();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      // Codigo de transacao fixo que o Shizuku usa para encerrar o
      // servico ao trocar de versao (ver ShellUserService.destroy()).
      @Override public void destroy() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_destroy, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
    }
    static final int TRANSACTION_exec = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
    static final int TRANSACTION_destroy = (android.os.IBinder.FIRST_CALL_TRANSACTION + 16777114);
  }
  /** @hide */
  public static final java.lang.String DESCRIPTOR = "com.game.reschange.IShellService";
  public java.lang.String exec(java.lang.String command) throws android.os.RemoteException;
  // Codigo de transacao fixo que o Shizuku usa para encerrar o
  // servico ao trocar de versao (ver ShellUserService.destroy()).
  public void destroy() throws android.os.RemoteException;
}
