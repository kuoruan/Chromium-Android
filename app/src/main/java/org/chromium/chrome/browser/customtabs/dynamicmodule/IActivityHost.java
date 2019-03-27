/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Original file: ../../third_party/cct_dynamic_module/src/src/java/org/chromium/chrome/browser/customtabs/dynamicmodule/IActivityHost.aidl
 */
package org.chromium.chrome.browser.customtabs.dynamicmodule;
/** API to customize the Chrome activity. */
public interface IActivityHost extends android.os.IInterface
{
/** Local-side IPC implementation stub class. */
public static abstract class Stub extends android.os.Binder implements org.chromium.chrome.browser.customtabs.dynamicmodule.IActivityHost
{
private static final java.lang.String DESCRIPTOR = "org.chromium.chrome.browser.customtabs.dynamicmodule.IActivityHost";
/** Construct the stub at attach it to the interface. */
public Stub()
{
this.attachInterface(this, DESCRIPTOR);
}
/**
 * Cast an IBinder object into an org.chromium.chrome.browser.customtabs.dynamicmodule.IActivityHost interface,
 * generating a proxy if needed.
 */
public static org.chromium.chrome.browser.customtabs.dynamicmodule.IActivityHost asInterface(android.os.IBinder obj)
{
if ((obj==null)) {
return null;
}
android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
if (((iin!=null)&&(iin instanceof org.chromium.chrome.browser.customtabs.dynamicmodule.IActivityHost))) {
return ((org.chromium.chrome.browser.customtabs.dynamicmodule.IActivityHost)iin);
}
return new org.chromium.chrome.browser.customtabs.dynamicmodule.IActivityHost.Stub.Proxy(obj);
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
case TRANSACTION_getActivityContext:
{
data.enforceInterface(DESCRIPTOR);
org.chromium.chrome.browser.customtabs.dynamicmodule.IObjectWrapper _result = this.getActivityContext();
reply.writeNoException();
reply.writeStrongBinder((((_result!=null))?(_result.asBinder()):(null)));
return true;
}
case TRANSACTION_setBottomBarView:
{
data.enforceInterface(DESCRIPTOR);
org.chromium.chrome.browser.customtabs.dynamicmodule.IObjectWrapper _arg0;
_arg0 = org.chromium.chrome.browser.customtabs.dynamicmodule.IObjectWrapper.Stub.asInterface(data.readStrongBinder());
this.setBottomBarView(_arg0);
reply.writeNoException();
return true;
}
case TRANSACTION_setOverlayView:
{
data.enforceInterface(DESCRIPTOR);
org.chromium.chrome.browser.customtabs.dynamicmodule.IObjectWrapper _arg0;
_arg0 = org.chromium.chrome.browser.customtabs.dynamicmodule.IObjectWrapper.Stub.asInterface(data.readStrongBinder());
this.setOverlayView(_arg0);
reply.writeNoException();
return true;
}
case TRANSACTION_setBottomBarHeight:
{
data.enforceInterface(DESCRIPTOR);
int _arg0;
_arg0 = data.readInt();
this.setBottomBarHeight(_arg0);
reply.writeNoException();
return true;
}
case TRANSACTION_loadUri:
{
data.enforceInterface(DESCRIPTOR);
android.net.Uri _arg0;
if ((0!=data.readInt())) {
_arg0 = android.net.Uri.CREATOR.createFromParcel(data);
}
else {
_arg0 = null;
}
this.loadUri(_arg0);
reply.writeNoException();
return true;
}
case TRANSACTION_setTopBarView:
{
data.enforceInterface(DESCRIPTOR);
org.chromium.chrome.browser.customtabs.dynamicmodule.IObjectWrapper _arg0;
_arg0 = org.chromium.chrome.browser.customtabs.dynamicmodule.IObjectWrapper.Stub.asInterface(data.readStrongBinder());
this.setTopBarView(_arg0);
reply.writeNoException();
return true;
}
case TRANSACTION_setTopBarHeight:
{
data.enforceInterface(DESCRIPTOR);
int _arg0;
_arg0 = data.readInt();
this.setTopBarHeight(_arg0);
reply.writeNoException();
return true;
}
case TRANSACTION_requestPostMessageChannel:
{
data.enforceInterface(DESCRIPTOR);
android.net.Uri _arg0;
if ((0!=data.readInt())) {
_arg0 = android.net.Uri.CREATOR.createFromParcel(data);
}
else {
_arg0 = null;
}
boolean _result = this.requestPostMessageChannel(_arg0);
reply.writeNoException();
reply.writeInt(((_result)?(1):(0)));
return true;
}
case TRANSACTION_postMessage:
{
data.enforceInterface(DESCRIPTOR);
java.lang.String _arg0;
_arg0 = data.readString();
int _result = this.postMessage(_arg0);
reply.writeNoException();
reply.writeInt(_result);
return true;
}
case TRANSACTION_setTopBarMinHeight:
{
data.enforceInterface(DESCRIPTOR);
int _arg0;
_arg0 = data.readInt();
this.setTopBarMinHeight(_arg0);
reply.writeNoException();
return true;
}
}
return super.onTransact(code, data, reply, flags);
}
private static class Proxy implements org.chromium.chrome.browser.customtabs.dynamicmodule.IActivityHost
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
/** Returns the Context of the Chrome activity. */
@Override public org.chromium.chrome.browser.customtabs.dynamicmodule.IObjectWrapper getActivityContext() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
org.chromium.chrome.browser.customtabs.dynamicmodule.IObjectWrapper _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_getActivityContext, _data, _reply, 0);
_reply.readException();
_result = org.chromium.chrome.browser.customtabs.dynamicmodule.IObjectWrapper.Stub.asInterface(_reply.readStrongBinder());
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
/**
   * Sets the given view as the bottom bar.
   *
   * Compared to the overlay view, the bottom bar is automatically hidden when
   * the user scrolls down on the web content.
   */
@Override public void setBottomBarView(org.chromium.chrome.browser.customtabs.dynamicmodule.IObjectWrapper bottomBarView) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeStrongBinder((((bottomBarView!=null))?(bottomBarView.asBinder()):(null)));
mRemote.transact(Stub.TRANSACTION_setBottomBarView, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
/*
   * Sets the overlay view.
   *
   * This view is always on top of the web content.
   */
@Override public void setOverlayView(org.chromium.chrome.browser.customtabs.dynamicmodule.IObjectWrapper overlayView) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeStrongBinder((((overlayView!=null))?(overlayView.asBinder()):(null)));
mRemote.transact(Stub.TRANSACTION_setOverlayView, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
/**
   * Sets the height of the bottom bar.
   *
   * Chrome uses this to calculate the bottom padding of the web content.
   */
@Override public void setBottomBarHeight(int heightInPx) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeInt(heightInPx);
mRemote.transact(Stub.TRANSACTION_setBottomBarHeight, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
/**
   * Loads a URI in the existing CCT activity.
   *
   * Introduced in API version 3.
   */
@Override public void loadUri(android.net.Uri uri) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
if ((uri!=null)) {
_data.writeInt(1);
uri.writeToParcel(_data, 0);
}
else {
_data.writeInt(0);
}
mRemote.transact(Stub.TRANSACTION_loadUri, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
/**
   * Sets the top bar view in CCT. This will not attempt to hide or remove
   * the CCT header. It should only be called once in the lifecycle of an
   * activity.

   * Introduced in API version 5.
   */
@Override public void setTopBarView(org.chromium.chrome.browser.customtabs.dynamicmodule.IObjectWrapper topBarView) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeStrongBinder((((topBarView!=null))?(topBarView.asBinder()):(null)));
mRemote.transact(Stub.TRANSACTION_setTopBarView, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
/**
   * Sets the height of the top bar for module-managed URLs only. This is needed
   * for CCT to calculate the web content area. It is not applicable to
   * non-module-managed URLs, i.e. landing pages, where the top bar is not
   * shown.
   *
   * @param heightInPx The desired height. Chrome will decide if it can honor
   *        the desired height at runtime.
   *
   * Introduced in API version 7.
   */
@Override public void setTopBarHeight(int heightInPx) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeInt(heightInPx);
mRemote.transact(Stub.TRANSACTION_setTopBarHeight, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
/**
   * Sends a request to create a two-way postMessage channel between the web
   * page in the host activity and the dynamic module.
   *
   * @param postMessageOrigin Identifies the client which is attempting to
   *        establish the connection. For instance, this could be a URI
   *        representation of the package name.
   * @return whether the request was accepted.
   *
   * Introduced in API version 9.
   */
@Override public boolean requestPostMessageChannel(android.net.Uri postMessageOrigin) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
boolean _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
if ((postMessageOrigin!=null)) {
_data.writeInt(1);
postMessageOrigin.writeToParcel(_data, 0);
}
else {
_data.writeInt(0);
}
mRemote.transact(Stub.TRANSACTION_requestPostMessageChannel, _data, _reply, 0);
_reply.readException();
_result = (0!=_reply.readInt());
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
@Override public int postMessage(java.lang.String message) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
int _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeString(message);
mRemote.transact(Stub.TRANSACTION_postMessage, _data, _reply, 0);
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
   * Sets the min height of the top bar for module-managed URLs only. This is
   * needed for sticky top bar elements. It is not applicable to
   * non-module-managed URLs, i.e. landing pages, where the top bar is not
   * shown.
   *
   * @param heightInPx The desired height. Chrome will decide if it can honor
   *        the desired height at runtime.
   *
   * Introduced in API version 8.
   */
@Override public void setTopBarMinHeight(int heightInPx) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeInt(heightInPx);
mRemote.transact(Stub.TRANSACTION_setTopBarMinHeight, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
}
static final int TRANSACTION_getActivityContext = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
static final int TRANSACTION_setBottomBarView = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
static final int TRANSACTION_setOverlayView = (android.os.IBinder.FIRST_CALL_TRANSACTION + 2);
static final int TRANSACTION_setBottomBarHeight = (android.os.IBinder.FIRST_CALL_TRANSACTION + 3);
static final int TRANSACTION_loadUri = (android.os.IBinder.FIRST_CALL_TRANSACTION + 4);
static final int TRANSACTION_setTopBarView = (android.os.IBinder.FIRST_CALL_TRANSACTION + 5);
static final int TRANSACTION_setTopBarHeight = (android.os.IBinder.FIRST_CALL_TRANSACTION + 6);
static final int TRANSACTION_requestPostMessageChannel = (android.os.IBinder.FIRST_CALL_TRANSACTION + 7);
static final int TRANSACTION_postMessage = (android.os.IBinder.FIRST_CALL_TRANSACTION + 8);
static final int TRANSACTION_setTopBarMinHeight = (android.os.IBinder.FIRST_CALL_TRANSACTION + 9);
}
/** Returns the Context of the Chrome activity. */
public org.chromium.chrome.browser.customtabs.dynamicmodule.IObjectWrapper getActivityContext() throws android.os.RemoteException;
/**
   * Sets the given view as the bottom bar.
   *
   * Compared to the overlay view, the bottom bar is automatically hidden when
   * the user scrolls down on the web content.
   */
public void setBottomBarView(org.chromium.chrome.browser.customtabs.dynamicmodule.IObjectWrapper bottomBarView) throws android.os.RemoteException;
/*
   * Sets the overlay view.
   *
   * This view is always on top of the web content.
   */
public void setOverlayView(org.chromium.chrome.browser.customtabs.dynamicmodule.IObjectWrapper overlayView) throws android.os.RemoteException;
/**
   * Sets the height of the bottom bar.
   *
   * Chrome uses this to calculate the bottom padding of the web content.
   */
public void setBottomBarHeight(int heightInPx) throws android.os.RemoteException;
/**
   * Loads a URI in the existing CCT activity.
   *
   * Introduced in API version 3.
   */
public void loadUri(android.net.Uri uri) throws android.os.RemoteException;
/**
   * Sets the top bar view in CCT. This will not attempt to hide or remove
   * the CCT header. It should only be called once in the lifecycle of an
   * activity.

   * Introduced in API version 5.
   */
public void setTopBarView(org.chromium.chrome.browser.customtabs.dynamicmodule.IObjectWrapper topBarView) throws android.os.RemoteException;
/**
   * Sets the height of the top bar for module-managed URLs only. This is needed
   * for CCT to calculate the web content area. It is not applicable to
   * non-module-managed URLs, i.e. landing pages, where the top bar is not
   * shown.
   *
   * @param heightInPx The desired height. Chrome will decide if it can honor
   *        the desired height at runtime.
   *
   * Introduced in API version 7.
   */
public void setTopBarHeight(int heightInPx) throws android.os.RemoteException;
/**
   * Sends a request to create a two-way postMessage channel between the web
   * page in the host activity and the dynamic module.
   *
   * @param postMessageOrigin Identifies the client which is attempting to
   *        establish the connection. For instance, this could be a URI
   *        representation of the package name.
   * @return whether the request was accepted.
   *
   * Introduced in API version 9.
   */
public boolean requestPostMessageChannel(android.net.Uri postMessageOrigin) throws android.os.RemoteException;
public int postMessage(java.lang.String message) throws android.os.RemoteException;
/**
   * Sets the min height of the top bar for module-managed URLs only. This is
   * needed for sticky top bar elements. It is not applicable to
   * non-module-managed URLs, i.e. landing pages, where the top bar is not
   * shown.
   *
   * @param heightInPx The desired height. Chrome will decide if it can honor
   *        the desired height at runtime.
   *
   * Introduced in API version 8.
   */
public void setTopBarMinHeight(int heightInPx) throws android.os.RemoteException;
}
