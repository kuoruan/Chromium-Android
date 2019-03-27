/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Original file: ../../third_party/cct_dynamic_module/src/src/java/org/chromium/chrome/browser/customtabs/dynamicmodule/IObjectWrapper.aidl
 */
package org.chromium.chrome.browser.customtabs.dynamicmodule;
/**
 * This interface intentionally has no methods, and instances of this should
 * be created from class ObjectWrapper only.  This is used as a way of passing
 * objects that descend from the system classes via AIDL across classloaders
 * without serializing them.
 */
public interface IObjectWrapper extends android.os.IInterface
{
/** Local-side IPC implementation stub class. */
public static abstract class Stub extends android.os.Binder implements org.chromium.chrome.browser.customtabs.dynamicmodule.IObjectWrapper
{
private static final java.lang.String DESCRIPTOR = "org.chromium.chrome.browser.customtabs.dynamicmodule.IObjectWrapper";
/** Construct the stub at attach it to the interface. */
public Stub()
{
this.attachInterface(this, DESCRIPTOR);
}
/**
 * Cast an IBinder object into an org.chromium.chrome.browser.customtabs.dynamicmodule.IObjectWrapper interface,
 * generating a proxy if needed.
 */
public static org.chromium.chrome.browser.customtabs.dynamicmodule.IObjectWrapper asInterface(android.os.IBinder obj)
{
if ((obj==null)) {
return null;
}
android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
if (((iin!=null)&&(iin instanceof org.chromium.chrome.browser.customtabs.dynamicmodule.IObjectWrapper))) {
return ((org.chromium.chrome.browser.customtabs.dynamicmodule.IObjectWrapper)iin);
}
return new org.chromium.chrome.browser.customtabs.dynamicmodule.IObjectWrapper.Stub.Proxy(obj);
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
}
return super.onTransact(code, data, reply, flags);
}
private static class Proxy implements org.chromium.chrome.browser.customtabs.dynamicmodule.IObjectWrapper
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
}
}
}
