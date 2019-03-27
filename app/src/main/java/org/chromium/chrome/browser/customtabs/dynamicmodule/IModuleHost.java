/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Original file: ../../third_party/cct_dynamic_module/src/src/java/org/chromium/chrome/browser/customtabs/dynamicmodule/IModuleHost.aidl
 */
package org.chromium.chrome.browser.customtabs.dynamicmodule;
/** Chrome host that runs custom modules. */
public interface IModuleHost extends android.os.IInterface
{
/** Local-side IPC implementation stub class. */
public static abstract class Stub extends android.os.Binder implements org.chromium.chrome.browser.customtabs.dynamicmodule.IModuleHost
{
private static final java.lang.String DESCRIPTOR = "org.chromium.chrome.browser.customtabs.dynamicmodule.IModuleHost";
/** Construct the stub at attach it to the interface. */
public Stub()
{
this.attachInterface(this, DESCRIPTOR);
}
/**
 * Cast an IBinder object into an org.chromium.chrome.browser.customtabs.dynamicmodule.IModuleHost interface,
 * generating a proxy if needed.
 */
public static org.chromium.chrome.browser.customtabs.dynamicmodule.IModuleHost asInterface(android.os.IBinder obj)
{
if ((obj==null)) {
return null;
}
android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
if (((iin!=null)&&(iin instanceof org.chromium.chrome.browser.customtabs.dynamicmodule.IModuleHost))) {
return ((org.chromium.chrome.browser.customtabs.dynamicmodule.IModuleHost)iin);
}
return new org.chromium.chrome.browser.customtabs.dynamicmodule.IModuleHost.Stub.Proxy(obj);
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
case TRANSACTION_getHostApplicationContext:
{
data.enforceInterface(DESCRIPTOR);
org.chromium.chrome.browser.customtabs.dynamicmodule.IObjectWrapper _result = this.getHostApplicationContext();
reply.writeNoException();
reply.writeStrongBinder((((_result!=null))?(_result.asBinder()):(null)));
return true;
}
case TRANSACTION_getModuleContext:
{
data.enforceInterface(DESCRIPTOR);
org.chromium.chrome.browser.customtabs.dynamicmodule.IObjectWrapper _result = this.getModuleContext();
reply.writeNoException();
reply.writeStrongBinder((((_result!=null))?(_result.asBinder()):(null)));
return true;
}
case TRANSACTION_getHostVersion:
{
data.enforceInterface(DESCRIPTOR);
int _result = this.getHostVersion();
reply.writeNoException();
reply.writeInt(_result);
return true;
}
case TRANSACTION_getMinimumModuleVersion:
{
data.enforceInterface(DESCRIPTOR);
int _result = this.getMinimumModuleVersion();
reply.writeNoException();
reply.writeInt(_result);
return true;
}
}
return super.onTransact(code, data, reply, flags);
}
private static class Proxy implements org.chromium.chrome.browser.customtabs.dynamicmodule.IModuleHost
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
/** Returns the context of this host. */
@Override public org.chromium.chrome.browser.customtabs.dynamicmodule.IObjectWrapper getHostApplicationContext() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
org.chromium.chrome.browser.customtabs.dynamicmodule.IObjectWrapper _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_getHostApplicationContext, _data, _reply, 0);
_reply.readException();
_result = org.chromium.chrome.browser.customtabs.dynamicmodule.IObjectWrapper.Stub.asInterface(_reply.readStrongBinder());
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
/** Returns the context of the custom module. */
@Override public org.chromium.chrome.browser.customtabs.dynamicmodule.IObjectWrapper getModuleContext() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
org.chromium.chrome.browser.customtabs.dynamicmodule.IObjectWrapper _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_getModuleContext, _data, _reply, 0);
_reply.readException();
_result = org.chromium.chrome.browser.customtabs.dynamicmodule.IObjectWrapper.Stub.asInterface(_reply.readStrongBinder());
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
@Override public int getHostVersion() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
int _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_getHostVersion, _data, _reply, 0);
_reply.readException();
_result = _reply.readInt();
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
@Override public int getMinimumModuleVersion() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
int _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_getMinimumModuleVersion, _data, _reply, 0);
_reply.readException();
_result = _reply.readInt();
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
}
static final int TRANSACTION_getHostApplicationContext = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
static final int TRANSACTION_getModuleContext = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
static final int TRANSACTION_getHostVersion = (android.os.IBinder.FIRST_CALL_TRANSACTION + 2);
static final int TRANSACTION_getMinimumModuleVersion = (android.os.IBinder.FIRST_CALL_TRANSACTION + 3);
}
/** Returns the context of this host. */
public org.chromium.chrome.browser.customtabs.dynamicmodule.IObjectWrapper getHostApplicationContext() throws android.os.RemoteException;
/** Returns the context of the custom module. */
public org.chromium.chrome.browser.customtabs.dynamicmodule.IObjectWrapper getModuleContext() throws android.os.RemoteException;
public int getHostVersion() throws android.os.RemoteException;
public int getMinimumModuleVersion() throws android.os.RemoteException;
}
