/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Original file: ../../third_party/cct_dynamic_module/src/src/java/org/chromium/chrome/browser/customtabs/dynamicmodule/IActivityDelegate.aidl
 */
package org.chromium.chrome.browser.customtabs.dynamicmodule;
/** Runtime providing additional features to a Chrome activity. */
public interface IActivityDelegate extends android.os.IInterface
{
/** Local-side IPC implementation stub class. */
public static abstract class Stub extends android.os.Binder implements org.chromium.chrome.browser.customtabs.dynamicmodule.IActivityDelegate
{
private static final java.lang.String DESCRIPTOR = "org.chromium.chrome.browser.customtabs.dynamicmodule.IActivityDelegate";
/** Construct the stub at attach it to the interface. */
public Stub()
{
this.attachInterface(this, DESCRIPTOR);
}
/**
 * Cast an IBinder object into an org.chromium.chrome.browser.customtabs.dynamicmodule.IActivityDelegate interface,
 * generating a proxy if needed.
 */
public static org.chromium.chrome.browser.customtabs.dynamicmodule.IActivityDelegate asInterface(android.os.IBinder obj)
{
if ((obj==null)) {
return null;
}
android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
if (((iin!=null)&&(iin instanceof org.chromium.chrome.browser.customtabs.dynamicmodule.IActivityDelegate))) {
return ((org.chromium.chrome.browser.customtabs.dynamicmodule.IActivityDelegate)iin);
}
return new org.chromium.chrome.browser.customtabs.dynamicmodule.IActivityDelegate.Stub.Proxy(obj);
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
case TRANSACTION_onCreate:
{
data.enforceInterface(DESCRIPTOR);
android.os.Bundle _arg0;
if ((0!=data.readInt())) {
_arg0 = android.os.Bundle.CREATOR.createFromParcel(data);
}
else {
_arg0 = null;
}
this.onCreate(_arg0);
reply.writeNoException();
return true;
}
case TRANSACTION_onPostCreate:
{
data.enforceInterface(DESCRIPTOR);
android.os.Bundle _arg0;
if ((0!=data.readInt())) {
_arg0 = android.os.Bundle.CREATOR.createFromParcel(data);
}
else {
_arg0 = null;
}
this.onPostCreate(_arg0);
reply.writeNoException();
return true;
}
case TRANSACTION_onStart:
{
data.enforceInterface(DESCRIPTOR);
this.onStart();
reply.writeNoException();
return true;
}
case TRANSACTION_onStop:
{
data.enforceInterface(DESCRIPTOR);
this.onStop();
reply.writeNoException();
return true;
}
case TRANSACTION_onWindowFocusChanged:
{
data.enforceInterface(DESCRIPTOR);
boolean _arg0;
_arg0 = (0!=data.readInt());
this.onWindowFocusChanged(_arg0);
reply.writeNoException();
return true;
}
case TRANSACTION_onSaveInstanceState:
{
data.enforceInterface(DESCRIPTOR);
android.os.Bundle _arg0;
if ((0!=data.readInt())) {
_arg0 = android.os.Bundle.CREATOR.createFromParcel(data);
}
else {
_arg0 = null;
}
this.onSaveInstanceState(_arg0);
reply.writeNoException();
return true;
}
case TRANSACTION_onRestoreInstanceState:
{
data.enforceInterface(DESCRIPTOR);
android.os.Bundle _arg0;
if ((0!=data.readInt())) {
_arg0 = android.os.Bundle.CREATOR.createFromParcel(data);
}
else {
_arg0 = null;
}
this.onRestoreInstanceState(_arg0);
reply.writeNoException();
return true;
}
case TRANSACTION_onResume:
{
data.enforceInterface(DESCRIPTOR);
this.onResume();
reply.writeNoException();
return true;
}
case TRANSACTION_onPause:
{
data.enforceInterface(DESCRIPTOR);
this.onPause();
reply.writeNoException();
return true;
}
case TRANSACTION_onDestroy:
{
data.enforceInterface(DESCRIPTOR);
this.onDestroy();
reply.writeNoException();
return true;
}
case TRANSACTION_onBackPressed:
{
data.enforceInterface(DESCRIPTOR);
boolean _result = this.onBackPressed();
reply.writeNoException();
reply.writeInt(((_result)?(1):(0)));
return true;
}
case TRANSACTION_onBackPressedAsync:
{
data.enforceInterface(DESCRIPTOR);
org.chromium.chrome.browser.customtabs.dynamicmodule.IObjectWrapper _arg0;
_arg0 = org.chromium.chrome.browser.customtabs.dynamicmodule.IObjectWrapper.Stub.asInterface(data.readStrongBinder());
this.onBackPressedAsync(_arg0);
reply.writeNoException();
return true;
}
case TRANSACTION_onNavigationEvent:
{
data.enforceInterface(DESCRIPTOR);
int _arg0;
_arg0 = data.readInt();
android.os.Bundle _arg1;
if ((0!=data.readInt())) {
_arg1 = android.os.Bundle.CREATOR.createFromParcel(data);
}
else {
_arg1 = null;
}
this.onNavigationEvent(_arg0, _arg1);
reply.writeNoException();
return true;
}
case TRANSACTION_onMessageChannelReady:
{
data.enforceInterface(DESCRIPTOR);
this.onMessageChannelReady();
reply.writeNoException();
return true;
}
case TRANSACTION_onPostMessage:
{
data.enforceInterface(DESCRIPTOR);
java.lang.String _arg0;
_arg0 = data.readString();
this.onPostMessage(_arg0);
reply.writeNoException();
return true;
}
case TRANSACTION_onPageMetricEvent:
{
data.enforceInterface(DESCRIPTOR);
java.lang.String _arg0;
_arg0 = data.readString();
long _arg1;
_arg1 = data.readLong();
long _arg2;
_arg2 = data.readLong();
long _arg3;
_arg3 = data.readLong();
this.onPageMetricEvent(_arg0, _arg1, _arg2, _arg3);
reply.writeNoException();
return true;
}
}
return super.onTransact(code, data, reply, flags);
}
private static class Proxy implements org.chromium.chrome.browser.customtabs.dynamicmodule.IActivityDelegate
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
@Override public void onCreate(android.os.Bundle savedInstanceState) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
if ((savedInstanceState!=null)) {
_data.writeInt(1);
savedInstanceState.writeToParcel(_data, 0);
}
else {
_data.writeInt(0);
}
mRemote.transact(Stub.TRANSACTION_onCreate, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
@Override public void onPostCreate(android.os.Bundle savedInstanceState) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
if ((savedInstanceState!=null)) {
_data.writeInt(1);
savedInstanceState.writeToParcel(_data, 0);
}
else {
_data.writeInt(0);
}
mRemote.transact(Stub.TRANSACTION_onPostCreate, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
@Override public void onStart() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_onStart, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
@Override public void onStop() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_onStop, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
@Override public void onWindowFocusChanged(boolean hasFocus) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeInt(((hasFocus)?(1):(0)));
mRemote.transact(Stub.TRANSACTION_onWindowFocusChanged, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
@Override public void onSaveInstanceState(android.os.Bundle outState) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
if ((outState!=null)) {
_data.writeInt(1);
outState.writeToParcel(_data, 0);
}
else {
_data.writeInt(0);
}
mRemote.transact(Stub.TRANSACTION_onSaveInstanceState, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
@Override public void onRestoreInstanceState(android.os.Bundle savedInstanceState) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
if ((savedInstanceState!=null)) {
_data.writeInt(1);
savedInstanceState.writeToParcel(_data, 0);
}
else {
_data.writeInt(0);
}
mRemote.transact(Stub.TRANSACTION_onRestoreInstanceState, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
@Override public void onResume() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_onResume, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
@Override public void onPause() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_onPause, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
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
@Override public boolean onBackPressed() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
boolean _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_onBackPressed, _data, _reply, 0);
_reply.readException();
_result = (0!=_reply.readInt());
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
/**
   * Offers an opportunity to handle the back press event. If it is not handled,
   * the Runnable must be run.
   *
   * Introduced in API version 2.
   */
@Override public void onBackPressedAsync(org.chromium.chrome.browser.customtabs.dynamicmodule.IObjectWrapper notHandledRunnable) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeStrongBinder((((notHandledRunnable!=null))?(notHandledRunnable.asBinder()):(null)));
mRemote.transact(Stub.TRANSACTION_onBackPressedAsync, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
/**
   * Notify module about navigation events.
   * {@see android.support.customtabs.CustomTabsCallback#onNavigationEvent}
   *
   * Introduced in API version 4.
   */
@Override public void onNavigationEvent(int navigationEvent, android.os.Bundle extras) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeInt(navigationEvent);
if ((extras!=null)) {
_data.writeInt(1);
extras.writeToParcel(_data, 0);
}
else {
_data.writeInt(0);
}
mRemote.transact(Stub.TRANSACTION_onNavigationEvent, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
/**
   * Notifies the module that a postMessage channel has been created and is
   * ready for sending and receiving messages.
   *
   * Experimental API.
   */
@Override public void onMessageChannelReady() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_onMessageChannelReady, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
/**
   * Notifies the module about messages posted to it by the web page.
   * @param message The message sent. No guarantees are made about the format.
   *
   * Experimental API.
   */
@Override public void onPostMessage(java.lang.String message) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeString(message);
mRemote.transact(Stub.TRANSACTION_onPostMessage, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
/**
   * Notifies the module of page load metrics, for example time until first
   * contentful paint.
   * @param metricName Name of the page load metric.
   * @param navigationStart Navigation start time in ms as SystemClock.uptimeMillis() value.
   * @param offset offset in ms from navigationStart for the page load metric.
   * @param navigationId the unique id of a navigation this metrics is related to.
   * This parameter is guaranteed to be positive value or zero in case of "no id".
   *
   * Experimental API.
   */
@Override public void onPageMetricEvent(java.lang.String metricName, long navigationStart, long offset, long navigationId) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeString(metricName);
_data.writeLong(navigationStart);
_data.writeLong(offset);
_data.writeLong(navigationId);
mRemote.transact(Stub.TRANSACTION_onPageMetricEvent, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
}
static final int TRANSACTION_onCreate = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
static final int TRANSACTION_onPostCreate = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
static final int TRANSACTION_onStart = (android.os.IBinder.FIRST_CALL_TRANSACTION + 2);
static final int TRANSACTION_onStop = (android.os.IBinder.FIRST_CALL_TRANSACTION + 3);
static final int TRANSACTION_onWindowFocusChanged = (android.os.IBinder.FIRST_CALL_TRANSACTION + 4);
static final int TRANSACTION_onSaveInstanceState = (android.os.IBinder.FIRST_CALL_TRANSACTION + 5);
static final int TRANSACTION_onRestoreInstanceState = (android.os.IBinder.FIRST_CALL_TRANSACTION + 6);
static final int TRANSACTION_onResume = (android.os.IBinder.FIRST_CALL_TRANSACTION + 7);
static final int TRANSACTION_onPause = (android.os.IBinder.FIRST_CALL_TRANSACTION + 8);
static final int TRANSACTION_onDestroy = (android.os.IBinder.FIRST_CALL_TRANSACTION + 9);
static final int TRANSACTION_onBackPressed = (android.os.IBinder.FIRST_CALL_TRANSACTION + 10);
static final int TRANSACTION_onBackPressedAsync = (android.os.IBinder.FIRST_CALL_TRANSACTION + 11);
static final int TRANSACTION_onNavigationEvent = (android.os.IBinder.FIRST_CALL_TRANSACTION + 12);
static final int TRANSACTION_onMessageChannelReady = (android.os.IBinder.FIRST_CALL_TRANSACTION + 13);
static final int TRANSACTION_onPostMessage = (android.os.IBinder.FIRST_CALL_TRANSACTION + 14);
static final int TRANSACTION_onPageMetricEvent = (android.os.IBinder.FIRST_CALL_TRANSACTION + 15);
}
public void onCreate(android.os.Bundle savedInstanceState) throws android.os.RemoteException;
public void onPostCreate(android.os.Bundle savedInstanceState) throws android.os.RemoteException;
public void onStart() throws android.os.RemoteException;
public void onStop() throws android.os.RemoteException;
public void onWindowFocusChanged(boolean hasFocus) throws android.os.RemoteException;
public void onSaveInstanceState(android.os.Bundle outState) throws android.os.RemoteException;
public void onRestoreInstanceState(android.os.Bundle savedInstanceState) throws android.os.RemoteException;
public void onResume() throws android.os.RemoteException;
public void onPause() throws android.os.RemoteException;
public void onDestroy() throws android.os.RemoteException;
public boolean onBackPressed() throws android.os.RemoteException;
/**
   * Offers an opportunity to handle the back press event. If it is not handled,
   * the Runnable must be run.
   *
   * Introduced in API version 2.
   */
public void onBackPressedAsync(org.chromium.chrome.browser.customtabs.dynamicmodule.IObjectWrapper notHandledRunnable) throws android.os.RemoteException;
/**
   * Notify module about navigation events.
   * {@see android.support.customtabs.CustomTabsCallback#onNavigationEvent}
   *
   * Introduced in API version 4.
   */
public void onNavigationEvent(int navigationEvent, android.os.Bundle extras) throws android.os.RemoteException;
/**
   * Notifies the module that a postMessage channel has been created and is
   * ready for sending and receiving messages.
   *
   * Experimental API.
   */
public void onMessageChannelReady() throws android.os.RemoteException;
/**
   * Notifies the module about messages posted to it by the web page.
   * @param message The message sent. No guarantees are made about the format.
   *
   * Experimental API.
   */
public void onPostMessage(java.lang.String message) throws android.os.RemoteException;
/**
   * Notifies the module of page load metrics, for example time until first
   * contentful paint.
   * @param metricName Name of the page load metric.
   * @param navigationStart Navigation start time in ms as SystemClock.uptimeMillis() value.
   * @param offset offset in ms from navigationStart for the page load metric.
   * @param navigationId the unique id of a navigation this metrics is related to.
   * This parameter is guaranteed to be positive value or zero in case of "no id".
   *
   * Experimental API.
   */
public void onPageMetricEvent(java.lang.String metricName, long navigationStart, long offset, long navigationId) throws android.os.RemoteException;
}
