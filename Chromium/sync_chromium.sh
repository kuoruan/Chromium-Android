#!/bin/bash

set -e

PRO_DIR="$(pwd)/Project"
BASE_DIR="/root/build/src"
RELEASE_DIR="${BASE_DIR}/out/Release"
APP_DIR="${PRO_DIR}/app"
MODULES_DIR="${PRO_DIR}"

sync_ui() {
	mkdir -p ${MODULES_DIR}/ui/src/main/res

	cp -r ${BASE_DIR}/ui/android/java/src/* \
		"${APP_DIR}/src/main/java"

	cp -r ${BASE_DIR}/ui/android/java/res/* \
		${RELEASE_DIR}/gen/ui/android/ui_strings_grd_grit_output/* \
		"${MODULES_DIR}/ui/src/main/res"
}

sync_components() {
	mkdir -p ${MODULES_DIR}/components/{autofill,components_base,web_contents_delegate}/src/main/res

	local components="${BASE_DIR}/components"
	cp -r ${components}/autofill/android/java/src/* \
		${components}/background_task_scheduler/android/java/src/* \
		${components}/bookmarks/common/android/java/src/* \
		${components}/crash/android/java/src/* \
		${components}/dom_distiller/content/browser/android/java/src/* \
		${components}/dom_distiller/core/android/java/src/* \
		${components}/download/internal/android/java/src/* \
		${components}/feature_engagement/internal/android/java/src/* \
		${components}/feature_engagement/public/android/java/src/* \
		${components}/gcm_driver/android/java/src/* \
		${components}/gcm_driver/instance_id/android/java/src/* \
		${components}/invalidation/impl/android/java/src/* \
		${components}/location/android/java/src/* \
		${components}/minidump_uploader/android/java/src/* \
		${components}/navigation_interception/android/java/src/* \
		${components}/ntp_tiles/android/java/src/* \
		${components}/offline_items_collection/core/android/java/src/* \
		${components}/payments/content/android/java/src/* \
		${components}/policy/android/java/src/* \
		${components}/safe_browsing/android/java/src/* \
		${components}/signin/core/browser/android/java/src/* \
		${components}/spellcheck/browser/android/java/src/* \
		${components}/sync/android/java/src/* \
		${components}/url_formatter/android/java/src/* \
		${components}/variations/android/java/src/* \
		${components}/web_contents_delegate_android/java/src/* \
		${components}/web_restrictions/browser/java/src/* \
		"${APP_DIR}/src/main/java"

	cp -r ${components}/autofill/android/java/res/* \
		${RELEASE_DIR}/gen/components/autofill/android/autofill_strings_grd_grit_output/* \
		"${MODULES_DIR}/components/autofill/src/main/res"

	cp -r ${components}/web_contents_delegate_android/java/res/* \
		${RELEASE_DIR}/gen/components/web_contents_delegate_android/web_contents_delegate_android_strings_grd_grit_output/* \
		"${MODULES_DIR}/components/web_contents_delegate/src/main/res"

	cp -r ${RELEASE_DIR}/gen/components/strings/java/res/* \
		"${MODULES_DIR}/components/components_base/src/main/res"
}

sync_content() {
	mkdir -p ${MODULES_DIR}/content/src/main/res

	cp -r ${BASE_DIR}/content/public/android/java/src/* \
		"${APP_DIR}/src/main/java"

	cp -r ${BASE_DIR}/content/public/android/java/res/* \
		${RELEASE_DIR}/gen/content/public/android/content_strings_grd_grit_output/* \
		"${MODULES_DIR}/content/src/main/res"

	# local aidl_i="${APP_DIR}/src/main/aidl/org/chromium"
	# mkdir -p "$aidl_i"
	#
	# mv ${APP_DIR}/src/main/java/org/chromium/*.aidl "$aidl_i"
	#
	# local aidl_j="${APP_DIR}/src/main/aidl/org/chromium/content/common"
	# mkdir -p "$aidl_j"
	#
	# mv ${APP_DIR}/src/main/java/org/chromium/content/common/*.aidl "$aidl_j"

	# find ${APP_DIR}/src/main/java/org/chromium -name "*.aidl" -type f -print0 | xargs -0 rm -f
}

sync_data_chart() {
	mkdir -p ${MODULES_DIR}/data_chart/src/main/{java,res}

	cp -r ${BASE_DIR}/third_party/android_data_chart/java/src/* \
		"${MODULES_DIR}/data_chart/src/main/java"

	cp -r ${BASE_DIR}/third_party/android_data_chart/java/res/* \
		"${MODULES_DIR}/data_chart/src/main/res"
}

sync_media() {
	mkdir -p ${MODULES_DIR}/media/src/main/{java,res}

	cp -r ${BASE_DIR}/third_party/android_media/java/src/* \
		"${MODULES_DIR}/media/src/main/java"
	cp -r ${BASE_DIR}/third_party/android_media/java/res/* \
		"${MODULES_DIR}/media/src/main/res"
}

sync_chrome() {
	mkdir -p ${APP_DIR}/{src/main/{java,res,aidl},libs}
	local src_dir="${APP_DIR}/src/main/java"
	local res_dir="${APP_DIR}/src/main/res"

	cp -r ${BASE_DIR}/base/android/java/src/* \
		${BASE_DIR}/build/android/buildhooks/java/* \
		${BASE_DIR}/chrome/android/java/src/* \
        ${BASE_DIR}/chrome/android/third_party/compositor_animator/java/src/* \
		${BASE_DIR}/chrome/android/third_party/widget_bottomsheet_base/java/src/* \
		${BASE_DIR}/chrome/android/webapk/libs/client/src/* \
		${BASE_DIR}/chrome/android/webapk/libs/common/src/* \
		${BASE_DIR}/chrome/android/webapk/libs/runtime_library/src/* \
		${BASE_DIR}/device/bluetooth/android/java/src/* \
		${BASE_DIR}/device/gamepad/android/java/src/* \
		${BASE_DIR}/device/geolocation/android/java/src/* \
		${BASE_DIR}/device/sensors/android/java/src/* \
		${BASE_DIR}/device/usb/android/java/src/* \
		${BASE_DIR}/device/vr/android/java/src/* \
		${BASE_DIR}/media/base/android/java/src/* \
		${BASE_DIR}/media/capture/content/android/java/src/* \
		${BASE_DIR}/media/capture/video/android/java/src/* \
		${BASE_DIR}/media/midi/java/src/* \
		${BASE_DIR}/mojo/android/system/src/* \
		${BASE_DIR}/mojo/public/java/bindings/src/* \
		${BASE_DIR}/mojo/public/java/system/src/* \
		${BASE_DIR}/net/android/java/src/* \
		${BASE_DIR}/printing/android/java/src/* \
        ${BASE_DIR}/services/data_decoder/public/cpp/android/java/src/* \
		${BASE_DIR}/services/device/android/java/src/* \
		${BASE_DIR}/services/device/battery/android/java/src/* \
		${BASE_DIR}/services/device/generic_sensor/android/java/src/* \
		${BASE_DIR}/services/device/nfc/android/java/src/* \
		${BASE_DIR}/services/device/public/java/src/* \
		${BASE_DIR}/services/device/screen_orientation/android/java/src/* \
		${BASE_DIR}/services/device/time_zone_monitor/android/java/src/* \
		${BASE_DIR}/services/device/vibration/android/java/src/* \
		${BASE_DIR}/services/device/wake_lock/power_save_blocker/android/java/src/* \
		${BASE_DIR}/services/service_manager/public/java/src/* \
		${BASE_DIR}/services/shape_detection/android/java/src/* \
		${BASE_DIR}/third_party/android_protobuf/src/java/src/device/main/java/* \
		${BASE_DIR}/third_party/android_protobuf/src/java/src/main/java/* \
		${BASE_DIR}/third_party/android_swipe_refresh/java/src/* \
		${BASE_DIR}/third_party/cacheinvalidation/src/java/* \
		${BASE_DIR}/third_party/custom_tabs_client/src/customtabs/src/* \
		${BASE_DIR}/third_party/gif_player/src/* \
		"$src_dir"

	cp -r ${RELEASE_DIR}/gradle/chrome/android/chrome_public_apk/extracted-srcjars/* \
		"$src_dir"

	cp -r ${BASE_DIR}/chrome/android/java/res/* \
		${BASE_DIR}/chrome/android/java/res_chromium/* \
		${BASE_DIR}/media/base/android/java/res/* \
		${RELEASE_DIR}/gen/chrome/java/res/* \
		${RELEASE_DIR}/gen/chrome/android/chrome_strings_grd_grit_output/* \
		${RELEASE_DIR}/gradle/chrome/android/chrome_public_apk/extracted-res/xml \
		"$res_dir"

	cp "${RELEASE_DIR}/gen/chrome/android/chrome_public_apk/AndroidManifest.xml" \
		"${APP_DIR}/src/main"

	cp -r ${RELEASE_DIR}/gen/chrome/app/policy/android/* \
		"$res_dir"

	cp -r \
		${RELEASE_DIR}/gen/base/base_build_config_gen/java_cpp_template/* \
		${RELEASE_DIR}/gen/net/android/net_errors_java/java_cpp_template/* \
		"$src_dir"

	local custom_tabs_aidl="${APP_DIR}/src/main/aidl/android/support/customtabs"
	mkdir -p "$custom_tabs_aidl"
	mv -f ${APP_DIR}/src/main/java/android/support/customtabs/*.aidl \
		"$custom_tabs_aidl"
}

sync_assets() {
	local asset_dir="${APP_DIR}/src/main/assets"
	mkdir -p "$asset_dir"

	cp ${RELEASE_DIR}/*.pak \
		${RELEASE_DIR}/*.dat \
		${RELEASE_DIR}/natives_blob.bin \
		${RELEASE_DIR}/locales/{en-US,zh-CN,zh-TW}.pak \
		"$asset_dir"
	cp ${RELEASE_DIR}/snapshot_blob.bin "$asset_dir"/snapshot_blob_32.bin
}

sync_libs() {
	local lib="${APP_DIR}/libs"
	mkdir -p "${lib}"

	cp ${RELEASE_DIR}/lib.java/third_party/android_tools/support/gcm.jar "$lib"
}

sync_jniLibs() {
	local jni_libs_dir="${APP_DIR}/src/main/jniLibs/armeabi-v7a"
	mkdir -p "$jni_libs_dir"
	cp ${RELEASE_DIR}/*.so "$jni_libs_dir"
}

clean_project() {
	local del_files="README|OWNERS|.*\.template|R\.java|.*\.stamp|.*stamp\.d"
	find "$PRO_DIR" -regextype "posix-egrep" -regex ".*/(${del_files})" -type f -print0 | \
		xargs -0 rm -f

	local langs="am|ar|bg|ca|cs|da|de|el|en-rGB|es|es-rUS|fa|fi|fr|hi|hr|hu|in|it|iw"
	langs="$langs|ja|ko|lt|lv|nb|nl|pl|pt-rBR|pt-rPT|ro|ru|sk|sl|sr|sv|sw|th|tl|tr|uk|vi"
	find "$PRO_DIR" -regextype "posix-egrep" -regex ".*values-($langs)" -print0 | xargs -0 rm -rf

	local aidls
	aidls=$(find "${APP_DIR}/src/main/aidl" -name "*.aidl" -type f)
	local j_file;
	for aidl in $aidls; do
		j_file="$(basename "$aidl" ".aidl")"
		find "${APP_DIR}/src/main/java" -name "${j_file}.java" -type f -print0 | xargs -0 rm -f
	done

	local empty_dirs;
	while :; do
		empty_dirs="$(find "$PRO_DIR" -type d -empty)"
		if [ -n "$empty_dirs" ]; then
			echo "$empty_dirs" | xargs rm -rf
		else
			break
		fi
	done
}

do_sync() {
	rm -rf "$PRO_DIR"
	sync_chrome
	sync_ui
	sync_media
	sync_data_chart
	sync_components
	sync_content

	sync_assets
	sync_libs
	sync_jniLibs

	clean_project

	# NativeLibraries
}

do_sync
