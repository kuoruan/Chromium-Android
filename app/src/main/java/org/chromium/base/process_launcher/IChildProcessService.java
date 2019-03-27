/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Original file: ../../base/android/java/src/org/chromium/base/process_launcher/IChildProcessService.aidl
 */
package org.chromium.base.process_launcher;
public interface IChildProcessService extends android.os.IInterface
{
/** Local-side IPC implementation stub class. */
public static abstract class Stub extends android.os.Binder implements org.chromium.base.process_launcher.IChildProcessService
{
private static final java.lang.String DESCRIPTOR = "org.chromium.base.process_launcher.IChildProcessService";
/** Construct the stub at attach it to the interface. */
public Stub()
{
this.attachInterface(this, DESCRIPTOR);
}
/**
 * Cast an IBinder object into an org.chromium.base.process_launcher.IChildProcessService interface,
 * generating a proxy if needed.
 */
public static org.chromium.base.process_launcher.IChildProcessService asInterface(android.os.IBinder obj)
{
if ((obj==null)) {
return null;
}
android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
if (((iin!=null)&&(iin instanceof org.chromium.base.process_launcher.IChildProcessService))) {
return ((org.chromium.base.process_launcher.IChildProcessService)iin);
}
return new org.chromium.base.process_launcher.IChildProcessService.Stub.Proxy(obj);
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
case TRANSACTION_bindToCaller:
{
data.enforceInterface(DESCRIPTOR);
boolean _result = this.bindToCaller();
reply.writeNoException();
reply.writeInt(((_result)?(1):(0)));
return true;
}
case TRANSACTION_setupConnection:
{
data.enforceInterface(DESCRIPTOR);
android.os.Bundle _arg0;
if ((0!=data.readInt())) {
_arg0 = android.os.Bundle.CREATOR.createFromParcel(data);
}
else {
_arg0 = null;
}
org.chromium.base.process_launcher.IParentProcess _arg1;
_arg1 = org.chromium.base.process_launcher.IParentProcess.Stub.asInterface(data.readStrongBinder());
java.util.List<android.os.IBinder> _arg2;
_arg2 = data.createBinderArrayList();
this.setupConnection(_arg0, _arg1, _arg2);
return true;
}
case TRANSACTION_forceKill:
{
data.enforceInterface(DESCRIPTOR);
this.forceKill();
return true;
}
case TRANSACTION_onMemoryPressure:
{
data.enforceInterface(DESCRIPTOR);
int _arg0;
_arg0 = data.readInt();
this.onMemoryPressure(_arg0);
return true;
}
}
return super.onTransact(code, data, reply, flags);
}
private static class Proxy implements org.chromium.base.process_launcher.IChildProcessService
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
// On the first call to this method, the service will record the calling PID
// and return true. Subsequent calls will only return true if the calling PID
// is the same as the recorded one.

@Override public boolean bindToCaller() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
boolean _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_bindToCaller, _data, _reply, 0);
_reply.readException();
_result = (0!=_reply.readInt());
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
// Sets up the initial IPC channel.

@Override public void setupConnection(android.os.Bundle args, org.chromium.base.process_launcher.IParentProcess parentProcess, java.util.List<android.os.IBinder> clientInterfaces) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
if ((args!=null)) {
_data.writeInt(1);
args.writeToParcel(_data, 0);
}
else {
_data.writeInt(0);
}
_data.writeStrongBinder((((parentProcess!=null))?(parentProcess.asBinder()):(null)));
_data.writeBinderList(clientInterfaces);
mRemote.transact(Stub.TRANSACTION_setupConnection, _data, null, android.os.IBinder.FLAG_ONEWAY);
}
finally {
_data.recycle();
}
}
// Forcefully kills the child process.

@Override public void forceKill() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_forceKill, _data, null, android.os.IBinder.FLAG_ONEWAY);
}
finally {
_data.recycle();
}
}
// Notifies about memory pressure. The argument is MemoryPressureLevel enum.

@Override public void onMemoryPressure(int pressure) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeInt(pressure);
mRemote.transact(Stub.TRANSACTION_onMemoryPressure, _data, null, android.os.IBinder.FLAG_ONEWAY);
}
finally {
_data.recycle();
}
}
}
static final int TRANSACTION_bindToCaller = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
static final int TRANSACTION_setupConnection = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
static final int TRANSACTION_forceKill = (android.os.IBinder.FIRST_CALL_TRANSACTION + 2);
static final int TRANSACTION_onMemoryPressure = (android.os.IBinder.FIRST_CALL_TRANSACTION + 3);
}
// On the first call to this method, the service will record the calling PID
// and return true. Subsequent calls will only return true if the calling PID
// is the same as the recorded one.

public boolean bindToCaller() throws android.os.RemoteException;
// Sets up the initial IPC channel.

public void setupConnection(android.os.Bundle args, org.chromium.base.process_launcher.IParentProcess parentProcess, java.util.List<android.os.IBinder> clientInterfaces) throws android.os.RemoteException;
// Forcefully kills the child process.

public void forceKill() throws android.os.RemoteException;
// Notifies about memory pressure. The argument is MemoryPressureLevel enum.

public void onMemoryPressure(int pressure) throws android.os.RemoteException;
}
