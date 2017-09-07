/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Original file: ../../chrome/android/java/src/org/chromium/chrome/browser/photo_picker/IDecoderServiceCallback.aidl
 */
package org.chromium.chrome.browser.photo_picker;
/**
 * This interface is used to communicate the results of an image decoding
 * request.
 */
public interface IDecoderServiceCallback extends android.os.IInterface
{
/** Local-side IPC implementation stub class. */
public static abstract class Stub extends android.os.Binder implements org.chromium.chrome.browser.photo_picker.IDecoderServiceCallback
{
private static final java.lang.String DESCRIPTOR = "org.chromium.chrome.browser.photo_picker.IDecoderServiceCallback";
/** Construct the stub at attach it to the interface. */
public Stub()
{
this.attachInterface(this, DESCRIPTOR);
}
/**
 * Cast an IBinder object into an org.chromium.chrome.browser.photo_picker.IDecoderServiceCallback interface,
 * generating a proxy if needed.
 */
public static org.chromium.chrome.browser.photo_picker.IDecoderServiceCallback asInterface(android.os.IBinder obj)
{
if ((obj==null)) {
return null;
}
android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
if (((iin!=null)&&(iin instanceof org.chromium.chrome.browser.photo_picker.IDecoderServiceCallback))) {
return ((org.chromium.chrome.browser.photo_picker.IDecoderServiceCallback)iin);
}
return new org.chromium.chrome.browser.photo_picker.IDecoderServiceCallback.Stub.Proxy(obj);
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
case TRANSACTION_onDecodeImageDone:
{
data.enforceInterface(DESCRIPTOR);
android.os.Bundle _arg0;
if ((0!=data.readInt())) {
_arg0 = android.os.Bundle.CREATOR.createFromParcel(data);
}
else {
_arg0 = null;
}
this.onDecodeImageDone(_arg0);
return true;
}
}
return super.onTransact(code, data, reply, flags);
}
private static class Proxy implements org.chromium.chrome.browser.photo_picker.IDecoderServiceCallback
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
  * Called when decoding is done.
  * @param payload The results of the image decoding request, including the
  *                decoded bitmap.
  */
@Override public void onDecodeImageDone(android.os.Bundle payload) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
if ((payload!=null)) {
_data.writeInt(1);
payload.writeToParcel(_data, 0);
}
else {
_data.writeInt(0);
}
mRemote.transact(Stub.TRANSACTION_onDecodeImageDone, _data, null, android.os.IBinder.FLAG_ONEWAY);
}
finally {
_data.recycle();
}
}
}
static final int TRANSACTION_onDecodeImageDone = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
}
/**
  * Called when decoding is done.
  * @param payload The results of the image decoding request, including the
  *                decoded bitmap.
  */
public void onDecodeImageDone(android.os.Bundle payload) throws android.os.RemoteException;
}
