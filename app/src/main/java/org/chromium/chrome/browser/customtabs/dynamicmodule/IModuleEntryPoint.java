/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Original file: ../../third_party/cct_dynamic_module/src/src/java/org/chromium/chrome/browser/customtabs/dynamicmodule/IModuleEntryPoint.aidl
 */
package org.chromium.chrome.browser.customtabs.dynamicmodule;
/** Entry point for a dynamic module. */
public interface IModuleEntryPoint extends android.os.IInterface
{
/** Local-side IPC implementation stub class. */
public static abstract class Stub extends android.os.Binder implements org.chromium.chrome.browser.customtabs.dynamicmodule.IModuleEntryPoint
{
private static final java.lang.String DESCRIPTOR = "org.chromium.chrome.browser.customtabs.dynamicmodule.IModuleEntryPoint";
/** Construct the stub at attach it to the interface. */
public Stub()
{
this.attachInterface(this, DESCRIPTOR);
}
/**
 * Cast an IBinder object into an org.chromium.chrome.browser.customtabs.dynamicmodule.IModuleEntryPoint interface,
 * generating a proxy if needed.
 */
public static org.chromium.chrome.browser.customtabs.dynamicmodule.IModuleEntryPoint asInterface(android.os.IBinder obj)
{
if ((obj==null)) {
return null;
}
android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
if (((iin!=null)&&(iin instanceof org.chromium.chrome.browser.customtabs.dynamicmodule.IModuleEntryPoint))) {
return ((org.chromium.chrome.browser.customtabs.dynamicmodule.IModuleEntryPoint)iin);
}
return new org.chromium.chrome.browser.customtabs.dynamicmodule.IModuleEntryPoint.Stub.Proxy(obj);
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
case TRANSACTION_init:
{
data.enforceInterface(DESCRIPTOR);
org.chromium.chrome.browser.customtabs.dynamicmodule.IModuleHost _arg0;
_arg0 = org.chromium.chrome.browser.customtabs.dynamicmodule.IModuleHost.Stub.asInterface(data.readStrongBinder());
this.init(_arg0);
reply.writeNoException();
return true;
}
case TRANSACTION_getModuleVersion:
{
data.enforceInterface(DESCRIPTOR);
int _result = this.getModuleVersion();
reply.writeNoException();
reply.writeInt(_result);
return true;
}
case TRANSACTION_getMinimumHostVersion:
{
data.enforceInterface(DESCRIPTOR);
int _result = this.getMinimumHostVersion();
reply.writeNoException();
reply.writeInt(_result);
return true;
}
case TRANSACTION_createActivityDelegate:
{
data.enforceInterface(DESCRIPTOR);
org.chromium.chrome.browser.customtabs.dynamicmodule.IActivityHost _arg0;
_arg0 = org.chromium.chrome.browser.customtabs.dynamicmodule.IActivityHost.Stub.asInterface(data.readStrongBinder());
org.chromium.chrome.browser.customtabs.dynamicmodule.IActivityDelegate _result = this.createActivityDelegate(_arg0);
reply.writeNoException();
reply.writeStrongBinder((((_result!=null))?(_result.asBinder()):(null)));
return true;
}
case TRANSACTION_onDestroy:
{
data.enforceInterface(DESCRIPTOR);
this.onDestroy();
reply.writeNoException();
return true;
}
case TRANSACTION_onBundleReceived:
{
data.enforceInterface(DESCRIPTOR);
org.chromium.chrome.browser.customtabs.dynamicmodule.IObjectWrapper _arg0;
_arg0 = org.chromium.chrome.browser.customtabs.dynamicmodule.IObjectWrapper.Stub.asInterface(data.readStrongBinder());
this.onBundleReceived(_arg0);
reply.writeNoException();
return true;
}
}
return super.onTransact(code, data, reply, flags);
}
private static class Proxy implements org.chromium.chrome.browser.customtabs.dynamicmodule.IModuleEntryPoint
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
/** Called by Chrome to perform module initialization. */
@Override public void init(org.chromium.chrome.browser.customtabs.dynamicmodule.IModuleHost moduleHost) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeStrongBinder((((moduleHost!=null))?(moduleHost.asBinder()):(null)));
mRemote.transact(Stub.TRANSACTION_init, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
@Override public int getModuleVersion() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
int _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_getModuleVersion, _data, _reply, 0);
_reply.readException();
_result = _reply.readInt();
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
@Override public int getMinimumHostVersion() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
int _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_getMinimumHostVersion, _data, _reply, 0);
_reply.readException();
_result = _reply.readInt();
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
/**
   * Called when an enhanced activity is started.
   *
   * @throws IllegalStateException if the hosted application is not created.
   */
@Override public org.chromium.chrome.browser.customtabs.dynamicmodule.IActivityDelegate createActivityDelegate(org.chromium.chrome.browser.customtabs.dynamicmodule.IActivityHost activityHost) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
org.chromium.chrome.browser.customtabs.dynamicmodule.IActivityDelegate _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeStrongBinder((((activityHost!=null))?(activityHost.asBinder()):(null)));
mRemote.transact(Stub.TRANSACTION_createActivityDelegate, _data, _reply, 0);
_reply.readException();
_result = org.chromium.chrome.browser.customtabs.dynamicmodule.IActivityDelegate.Stub.asInterface(_reply.readStrongBinder());
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
/** Called by Chrome when the module is destroyed. */
@Override public void onDestroy() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_onDestroy, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
/**
   * Called by Chrome when a bundle for the module is received.
   *
   * Introduced in API version 6.
   */
@Override public void onBundleReceived(org.chromium.chrome.browser.customtabs.dynamicmodule.IObjectWrapper args) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeStrongBinder((((args!=null))?(args.asBinder()):(null)));
mRemote.transact(Stub.TRANSACTION_onBundleReceived, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
}
static final int TRANSACTION_init = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
static final int TRANSACTION_getModuleVersion = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
static final int TRANSACTION_getMinimumHostVersion = (android.os.IBinder.FIRST_CALL_TRANSACTION + 2);
static final int TRANSACTION_createActivityDelegate = (android.os.IBinder.FIRST_CALL_TRANSACTION + 3);
static final int TRANSACTION_onDestroy = (android.os.IBinder.FIRST_CALL_TRANSACTION + 4);
static final int TRANSACTION_onBundleReceived = (android.os.IBinder.FIRST_CALL_TRANSACTION + 5);
}
/** Called by Chrome to perform module initialization. */
public void init(org.chromium.chrome.browser.customtabs.dynamicmodule.IModuleHost moduleHost) throws android.os.RemoteException;
public int getModuleVersion() throws android.os.RemoteException;
public int getMinimumHostVersion() throws android.os.RemoteException;
/**
   * Called when an enhanced activity is started.
   *
   * @throws IllegalStateException if the hosted application is not created.
   */
public org.chromium.chrome.browser.customtabs.dynamicmodule.IActivityDelegate createActivityDelegate(org.chromium.chrome.browser.customtabs.dynamicmodule.IActivityHost activityHost) throws android.os.RemoteException;
/** Called by Chrome when the module is destroyed. */
public void onDestroy() throws android.os.RemoteException;
/**
   * Called by Chrome when a bundle for the module is received.
   *
   * Introduced in API version 6.
   */
public void onBundleReceived(org.chromium.chrome.browser.customtabs.dynamicmodule.IObjectWrapper args) throws android.os.RemoteException;
}
