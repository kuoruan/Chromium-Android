/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Original file: ../../base/android/java/src/org/chromium/base/process_launcher/IParentProcess.aidl
 */
package org.chromium.base.process_launcher;
public interface IParentProcess extends android.os.IInterface
{
/** Local-side IPC implementation stub class. */
public static abstract class Stub extends android.os.Binder implements org.chromium.base.process_launcher.IParentProcess
{
private static final java.lang.String DESCRIPTOR = "org.chromium.base.process_launcher.IParentProcess";
/** Construct the stub at attach it to the interface. */
public Stub()
{
this.attachInterface(this, DESCRIPTOR);
}
/**
 * Cast an IBinder object into an org.chromium.base.process_launcher.IParentProcess interface,
 * generating a proxy if needed.
 */
public static org.chromium.base.process_launcher.IParentProcess asInterface(android.os.IBinder obj)
{
if ((obj==null)) {
return null;
}
android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
if (((iin!=null)&&(iin instanceof org.chromium.base.process_launcher.IParentProcess))) {
return ((org.chromium.base.process_launcher.IParentProcess)iin);
}
return new org.chromium.base.process_launcher.IParentProcess.Stub.Proxy(obj);
}
@Override public android.os.IBinder asBinder()
{
return this;
}
@Override public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException
{
switch (code)
{
case INTERFACE_TRANSACTION:
{
reply.writeString(DESCRIPTOR);
return true;
}
case TRANSACTION_sendPid:
{
data.enforceInterface(DESCRIPTOR);
int _arg0;
_arg0 = data.readInt();
this.sendPid(_arg0);
return true;
}
case TRANSACTION_reportCleanExit:
{
data.enforceInterface(DESCRIPTOR);
this.reportCleanExit();
reply.writeNoException();
return true;
}
}
return super.onTransact(code, data, reply, flags);
}
private static class Proxy implements org.chromium.base.process_launcher.IParentProcess
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
// Sends the child pid to the parent process. This will be called before any
// third-party code is loaded, and will be a no-op after the first call.

@Override public void sendPid(int pid) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeInt(pid);
mRemote.transact(Stub.TRANSACTION_sendPid, _data, null, android.os.IBinder.FLAG_ONEWAY);
}
finally {
_data.recycle();
}
}
// Tells the parent proces the child exited cleanly. Not oneway to ensure
// the browser receives the message before child exits.

@Override public void reportCleanExit() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_reportCleanExit, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
}
static final int TRANSACTION_sendPid = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
static final int TRANSACTION_reportCleanExit = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
}
// Sends the child pid to the parent process. This will be called before any
// third-party code is loaded, and will be a no-op after the first call.

public void sendPid(int pid) throws android.os.RemoteException;
// Tells the parent proces the child exited cleanly. Not oneway to ensure
// the browser receives the message before child exits.

public void reportCleanExit() throws android.os.RemoteException;
}
