/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Original file: ../../chrome/android/webapk/libs/common/src/org/chromium/webapk/lib/common/identity_service/IIdentityService.aidl
 */
package org.chromium.webapk.lib.common.identity_service;
/** IdentityService allows browsers to query information about the WebAPK. */
public interface IIdentityService extends android.os.IInterface
{
/** Local-side IPC implementation stub class. */
public static abstract class Stub extends android.os.Binder implements org.chromium.webapk.lib.common.identity_service.IIdentityService
{
private static final java.lang.String DESCRIPTOR = "org.chromium.webapk.lib.common.identity_service.IIdentityService";
/** Construct the stub at attach it to the interface. */
public Stub()
{
this.attachInterface(this, DESCRIPTOR);
}
/**
 * Cast an IBinder object into an org.chromium.webapk.lib.common.identity_service.IIdentityService interface,
 * generating a proxy if needed.
 */
public static org.chromium.webapk.lib.common.identity_service.IIdentityService asInterface(android.os.IBinder obj)
{
if ((obj==null)) {
return null;
}
android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
if (((iin!=null)&&(iin instanceof org.chromium.webapk.lib.common.identity_service.IIdentityService))) {
return ((org.chromium.webapk.lib.common.identity_service.IIdentityService)iin);
}
return new org.chromium.webapk.lib.common.identity_service.IIdentityService.Stub.Proxy(obj);
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
case TRANSACTION_getRuntimeHostBrowserPackageName:
{
data.enforceInterface(DESCRIPTOR);
java.lang.String _result = this.getRuntimeHostBrowserPackageName();
reply.writeNoException();
reply.writeString(_result);
return true;
}
}
return super.onTransact(code, data, reply, flags);
}
private static class Proxy implements org.chromium.webapk.lib.common.identity_service.IIdentityService
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
@Override public java.lang.String getRuntimeHostBrowserPackageName() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
java.lang.String _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_getRuntimeHostBrowserPackageName, _data, _reply, 0);
_reply.readException();
_result = _reply.readString();
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
}
static final int TRANSACTION_getRuntimeHostBrowserPackageName = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
}
public java.lang.String getRuntimeHostBrowserPackageName() throws android.os.RemoteException;
}
