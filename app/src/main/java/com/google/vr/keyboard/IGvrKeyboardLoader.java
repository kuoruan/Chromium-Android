/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Original file: ../../third_party/gvr-android-keyboard/com/google/vr/keyboard/IGvrKeyboardLoader.aidl
 */
package com.google.vr.keyboard;
/**
 * @hide
 * Provides loading of the GVR keyboard library.
 */
public interface IGvrKeyboardLoader extends android.os.IInterface
{
/** Local-side IPC implementation stub class. */
public static abstract class Stub extends android.os.Binder implements com.google.vr.keyboard.IGvrKeyboardLoader
{
private static final java.lang.String DESCRIPTOR = "com.google.vr.keyboard.IGvrKeyboardLoader";
/** Construct the stub at attach it to the interface. */
public Stub()
{
this.attachInterface(this, DESCRIPTOR);
}
/**
 * Cast an IBinder object into an com.google.vr.keyboard.IGvrKeyboardLoader interface,
 * generating a proxy if needed.
 */
public static com.google.vr.keyboard.IGvrKeyboardLoader asInterface(android.os.IBinder obj)
{
if ((obj==null)) {
return null;
}
android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
if (((iin!=null)&&(iin instanceof com.google.vr.keyboard.IGvrKeyboardLoader))) {
return ((com.google.vr.keyboard.IGvrKeyboardLoader)iin);
}
return new com.google.vr.keyboard.IGvrKeyboardLoader.Stub.Proxy(obj);
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
case TRANSACTION_loadGvrKeyboard:
{
data.enforceInterface(DESCRIPTOR);
long _arg0;
_arg0 = data.readLong();
long _result = this.loadGvrKeyboard(_arg0);
reply.writeNoException();
reply.writeLong(_result);
return true;
}
case TRANSACTION_closeGvrKeyboard:
{
data.enforceInterface(DESCRIPTOR);
long _arg0;
_arg0 = data.readLong();
this.closeGvrKeyboard(_arg0);
reply.writeNoException();
return true;
}
}
return super.onTransact(code, data, reply, flags);
}
private static class Proxy implements com.google.vr.keyboard.IGvrKeyboardLoader
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
/**
   * Attempts to load (dlopen) GVR keyboard library.
   * <p>
   * The library will be loaded only if a matching library can be found
   * that is recent enough. If the library is out-of-date, or cannot be found,
   * 0 will be returned.
   *
   * @param the version of the target library.
   * @return the native handle to the library. 0 if the load fails.
   */
@Override public long loadGvrKeyboard(long version) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
long _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeLong(version);
mRemote.transact(Stub.TRANSACTION_loadGvrKeyboard, _data, _reply, 0);
_reply.readException();
_result = _reply.readLong();
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
/**
   * Closes a library (dlclose).
   *
   * @param nativeLibrary the native handle of the library to be closed.
   */
@Override public void closeGvrKeyboard(long nativeLibrary) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeLong(nativeLibrary);
mRemote.transact(Stub.TRANSACTION_closeGvrKeyboard, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
}
static final int TRANSACTION_loadGvrKeyboard = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
static final int TRANSACTION_closeGvrKeyboard = (android.os.IBinder.FIRST_CALL_TRANSACTION + 2);
}
/**
   * Attempts to load (dlopen) GVR keyboard library.
   * <p>
   * The library will be loaded only if a matching library can be found
   * that is recent enough. If the library is out-of-date, or cannot be found,
   * 0 will be returned.
   *
   * @param the version of the target library.
   * @return the native handle to the library. 0 if the load fails.
   */
public long loadGvrKeyboard(long version) throws android.os.RemoteException;
/**
   * Closes a library (dlclose).
   *
   * @param nativeLibrary the native handle of the library to be closed.
   */
public void closeGvrKeyboard(long nativeLibrary) throws android.os.RemoteException;
}
