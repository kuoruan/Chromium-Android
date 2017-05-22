/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Original file: ../../content/public/android/java/src/org/chromium/content/common/IChildProcessService.aidl
 */
package org.chromium.content.common;
public interface IChildProcessService extends android.os.IInterface
{
/** Local-side IPC implementation stub class. */
public static abstract class Stub extends android.os.Binder implements org.chromium.content.common.IChildProcessService
{
private static final java.lang.String DESCRIPTOR = "org.chromium.content.common.IChildProcessService";
/** Construct the stub at attach it to the interface. */
public Stub()
{
this.attachInterface(this, DESCRIPTOR);
}
/**
 * Cast an IBinder object into an org.chromium.content.common.IChildProcessService interface,
 * generating a proxy if needed.
 */
public static org.chromium.content.common.IChildProcessService asInterface(android.os.IBinder obj)
{
if ((obj==null)) {
return null;
}
android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
if (((iin!=null)&&(iin instanceof org.chromium.content.common.IChildProcessService))) {
return ((org.chromium.content.common.IChildProcessService)iin);
}
return new org.chromium.content.common.IChildProcessService.Stub.Proxy(obj);
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
org.chromium.content.common.IChildProcessCallback _arg1;
_arg1 = org.chromium.content.common.IChildProcessCallback.Stub.asInterface(data.readStrongBinder());
int _result = this.setupConnection(_arg0, _arg1);
reply.writeNoException();
reply.writeInt(_result);
return true;
}
case TRANSACTION_crashIntentionallyForTesting:
{
data.enforceInterface(DESCRIPTOR);
this.crashIntentionallyForTesting();
reply.writeNoException();
return true;
}
}
return super.onTransact(code, data, reply, flags);
}
private static class Proxy implements org.chromium.content.common.IChildProcessService
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
@Override public int setupConnection(android.os.Bundle args, org.chromium.content.common.IChildProcessCallback callback) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
int _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
if ((args!=null)) {
_data.writeInt(1);
args.writeToParcel(_data, 0);
}
else {
_data.writeInt(0);
}
_data.writeStrongBinder((((callback!=null))?(callback.asBinder()):(null)));
mRemote.transact(Stub.TRANSACTION_setupConnection, _data, _reply, 0);
_reply.readException();
_result = _reply.readInt();
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
// Asks the child service to crash so that we can test the termination logic.

@Override public void crashIntentionallyForTesting() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_crashIntentionallyForTesting, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
}
static final int TRANSACTION_bindToCaller = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
static final int TRANSACTION_setupConnection = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
static final int TRANSACTION_crashIntentionallyForTesting = (android.os.IBinder.FIRST_CALL_TRANSACTION + 2);
}
// On the first call to this method, the service will record the calling PID
// and return true. Subsequent calls will only return true if the calling PID
// is the same as the recorded one.

public boolean bindToCaller() throws android.os.RemoteException;
public int setupConnection(android.os.Bundle args, org.chromium.content.common.IChildProcessCallback callback) throws android.os.RemoteException;
// Asks the child service to crash so that we can test the termination logic.

public void crashIntentionallyForTesting() throws android.os.RemoteException;
}
