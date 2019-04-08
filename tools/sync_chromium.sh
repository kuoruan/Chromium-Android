#!/bin/bash

set -e

PRO_DIR="$(pwd)/Chromium"
BASE_DIR="/root/chromium/src"
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
	mkdir -p ${MODULES_DIR}/components/{autofill,components_base,embedder_support}/src/main/res

	local components="${BASE_DIR}/components"
	cp -r ${components}/autofill/android/java/src/* \
		${components}/background_task_scheduler/android/java/src/* \
		${components}/bookmarks/common/android/java/src/* \
		${components}/crash/android/java/src/* \
		${components}/dom_distiller/content/browser/android/java/src/* \
		${components}/dom_distiller/core/android/java/src/* \
		${components}/download/internal/background_service/android/java/src/* \
		${components}/download/network/android/java/src/* \
		${components}/embedder_support/android/java/src/* \
		${components}/feature_engagement/internal/android/java/src/* \
		${components}/feature_engagement/public/android/java/src/* \
		${components}/gcm_driver/android/java/src/* \
		${components}/gcm_driver/instance_id/android/java/src/* \
		${components}/invalidation/impl/android/java/src/* \
		${components}/language/android/java/src/* \
		${components}/location/android/java/src/* \
		${components}/minidump_uploader/android/java/src/* \
		${components}/module_installer/android/java/src-common/* \
		${components}/module_installer/android/java/src-impl/* \
		${components}/navigation_interception/android/java/src/* \
		${components}/offline_items_collection/core/android/java/src/* \
		${components}/omnibox/browser/android/java/src/* \
		${components}/payments/content/android/java/src/* \
		${components}/policy/android/java/src/* \
		${components}/safe_browsing/android/java/src/* \
		${components}/signin/core/browser/android/java/src/* \
		${components}/spellcheck/browser/android/java/src/* \
		${components}/sync/android/java/src/* \
		${components}/url_formatter/android/java/src/* \
		${components}/variations/android/java/src/* \
		${components}/version_info/android/java/src/* \
		${components}/viz/service/java/src/* \
		${components}/web_restrictions/browser/java/src/* \
		"${APP_DIR}/src/main/java"

	cp -r ${RELEASE_DIR}/gen/components/version_info/android/java/* \
		"${APP_DIR}/src/main/java"

	cp -r ${components}/autofill/android/java/res/* \
		${RELEASE_DIR}/gen/components/autofill/android/autofill_strings_grd_grit_output/* \
		"${MODULES_DIR}/components/autofill/src/main/res"

	cp -r ${components}/embedder_support/android/java/res/* \
		${RELEASE_DIR}/gen/components/embedder_support/android/web_contents_delegate_strings_grd_grit_output/* \
		"${MODULES_DIR}/components/embedder_support/src/main/res"

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
		${BASE_DIR}/media/base/android/java/res/* \
		"${MODULES_DIR}/media/src/main/res"
}

sync_download() {
        mkdir -p ${MODULES_DIR}/download/src/main/res

	cp -r ${BASE_DIR}/chrome/android/java/res_download/* \
		"${MODULES_DIR}/download/src/main/res"
}

sync_autofill_assistant() {
	mkdir -p ${MODULES_DIR}/autofill_assistant/src/main/res

	cp -r ${BASE_DIR}/chrome/android/java/res_autofill_assistant/* \
		"${MODULES_DIR}/autofill_assistant/src/main/res"
}

sync_customtabs() {
	mkdir -p ${MODULES_DIR}/customtabs/src/main/res

	cp -r ${BASE_DIR}/third_party/custom_tabs_client/src/customtabs/src/* \
		"${APP_DIR}/src/main/java"

	cp -r ${BASE_DIR}/third_party/custom_tabs_client/src/customtabs/res/* \
		"${MODULES_DIR}/customtabs/src/main/res"
}

sync_splash() {
       mkdir -p ${MODULES_DIR}/splash/src/main/res

       cp -r ${BASE_DIR}/chrome/android/webapk/libs/common/res_splash/* \
	       "${MODULES_DIR}/splash/src/main/res"
}

sync_feed() {
	mkdir -p ${MODULES_DIR}/feed/{shared_res,shared_public_res,basic_res,basic_view_res,piet_res}

	cp -r ${BASE_DIR}/third_party/feed/src/src/main/java/com/google/android/libraries/feed/sharedstream/res/* \
	"${MODULES_DIR}/feed/shared_res"

	cp -r ${BASE_DIR}/third_party/feed/src/src/main/java/com/google/android/libraries/feed/sharedstream/publicapi/menumeasurer/res/* \
	"${MODULES_DIR}/feed/shared_public_res"

	cp -r ${BASE_DIR}/third_party/feed/src/src/main/java/com/google/android/libraries/feed/basicstream/res/* \
	"${MODULES_DIR}/feed/basic_res"

	cp -r ${BASE_DIR}/third_party/feed/src/src/main/java/com/google/android/libraries/feed/basicstream/internal/viewholders/res/* \
	"${MODULES_DIR}/feed/basic_view_res"

	cp -r ${BASE_DIR}/third_party/feed/src/src/main/java/com/google/android/libraries/feed/piet/res/* \
	"${MODULES_DIR}/feed/piet_res"
}

sync_aidl() {
        local custom_tabs_aidl="${APP_DIR}/src/main/aidl/android/support/customtabs"
        mkdir -p "$custom_tabs_aidl"
        mv -f ${APP_DIR}/src/main/java/android/support/customtabs/*.aidl \
		"$custom_tabs_aidl"

        local custom_tabs_trusted_aidl="${APP_DIR}/src/main/aidl/android/support/customtabs/trusted"
        mkdir -p "$custom_tabs_trusted_aidl"
        mv -f ${APP_DIR}/src/main/java/android/support/customtabs/trusted/*.aidl \
                "$custom_tabs_trusted_aidl"
}

sync_chrome() {
	mkdir -p ${APP_DIR}/{src/main/{java,res,aidl},libs}
	local src_dir="${APP_DIR}/src/main/java"
	local res_dir="${APP_DIR}/src/main/res"

	cp -r ${BASE_DIR}/base/android/java/src/* \
		${BASE_DIR}/build/android/buildhooks/java/* \
		${BASE_DIR}/chrome/android/feed/core/java/src/* \
		${BASE_DIR}/chrome/android/java/src/* \
		${BASE_DIR}/chrome/android/third_party/compositor_animator/java/src/* \
		${BASE_DIR}/chrome/android/webapk/libs/client/src/* \
		${BASE_DIR}/chrome/android/webapk/libs/common/src/* \
		${BASE_DIR}/device/bluetooth/android/java/src/* \
		${BASE_DIR}/device/gamepad/android/java/src/* \
		${BASE_DIR}/device/usb/android/java/src/* \
		${BASE_DIR}/device/vr/android/java/src/* \
		${BASE_DIR}/media/base/android/java/src/* \
		${BASE_DIR}/media/capture/content/android/java/src/* \
		${BASE_DIR}/media/capture/video/android/java/src/* \
		${BASE_DIR}/media/midi/java/src/* \
		${BASE_DIR}/mojo/public/java/base/src/* \
		${BASE_DIR}/mojo/public/java/bindings/src/* \
		${BASE_DIR}/mojo/public/java/system/src/* \
		${BASE_DIR}/net/android/java/src/* \
		${BASE_DIR}/printing/android/java/src/* \
		${BASE_DIR}/services/data_decoder/public/cpp/android/java/src/* \
		${BASE_DIR}/services/device/android/java/src/* \
		${BASE_DIR}/services/device/battery/android/java/src/* \
		${BASE_DIR}/services/device/generic_sensor/android/java/src/* \
		${BASE_DIR}/services/device/geolocation/android/java/src/* \
		${BASE_DIR}/services/device/nfc/android/java/src/* \
		${BASE_DIR}/services/device/public/java/src/* \
		${BASE_DIR}/services/device/screen_orientation/android/java/src/* \
		${BASE_DIR}/services/device/time_zone_monitor/android/java/src/* \
		${BASE_DIR}/services/device/vibration/android/java/src/* \
		${BASE_DIR}/services/device/wake_lock/power_save_blocker/android/java/src/* \
		${BASE_DIR}/services/media_session/public/cpp/android/java/src/* \
		${BASE_DIR}/services/service_manager/public/java/src/* \
		${BASE_DIR}/services/shape_detection/android/java/src/* \
		${BASE_DIR}/third_party/android_protobuf/src/java/src/device/main/java/* \
		${BASE_DIR}/third_party/android_protobuf/src/java/src/main/java/* \
		${BASE_DIR}/third_party/android_swipe_refresh/java/src/* \
		${BASE_DIR}/third_party/cct_dynamic_module/src/src/java/* \
		${BASE_DIR}/third_party/cacheinvalidation/src/java/* \
		${BASE_DIR}/third_party/feed/src/src/main/java/* \
		${BASE_DIR}/third_party/gif_player/src/* \
		${BASE_DIR}/third_party/protobuf/java/core/src/main/java/* \
		"$src_dir"

	cp -r ${RELEASE_DIR}/gradle/chrome/android/chrome_public_apk/extracted-srcjars/* \
		"$src_dir"

	cp -r ${RELEASE_DIR}/gen/chrome/android/templates/org/* \
		"$src_dir/org"

	mkdir -p ${PRO_DIR}/res_base

	cp -r ${BASE_DIR}/chrome/android/java/res/* \
		${RELEASE_DIR}/gen/chrome/android/chrome_strings_grd_grit_output/* \
	       "${PRO_DIR}/res_base"

	cp -r  ${BASE_DIR}/chrome/android/java/res_chromium/* \
		"$res_dir"

	cp -r ${BASE_DIR}/chrome/android/java/res_vr/* "$res_dir"

	cp -r ${RELEASE_DIR}/gen/chrome/java/res/* \
		${RELEASE_DIR}/gen/chrome/android/templates/chrome_version_xml/res/* \
		${RELEASE_DIR}/gradle/chrome/android/chrome_public_apk/extracted-res/xml \
		"$res_dir"

	cp "${RELEASE_DIR}/gen/chrome/android/chrome_public_apk/AndroidManifest.xml" \
		"${APP_DIR}/src/main"

	cp -r ${RELEASE_DIR}/gen/chrome/app/policy/android/* \
		"$res_dir"

	cp -r \
		${RELEASE_DIR}/gen/chrome/android/chrome_java/generated_java/* \
		${RELEASE_DIR}/gen/base/base_build_config_gen/java_cpp_template/* \
		${RELEASE_DIR}/gen/net/android/net_errors_java/java_cpp_template/* \
		${RELEASE_DIR}/gen/base/base_java/generated_java/* \
		"$src_dir"
}

sync_assets() {
	local asset_dir="${APP_DIR}/src/main/assets"
	mkdir -p "$asset_dir"
	mkdir -p "${asset_dir}/locales"

	cp ${RELEASE_DIR}/*.dat \
		${RELEASE_DIR}/natives_blob.bin \
		${RELEASE_DIR}/gen/chrome/android/chrome_apk_paks/*.pak \
		${RELEASE_DIR}/gen/chrome/android/chrome_public_apk_unwind_assets/* \
		"$asset_dir"

	cp ${RELEASE_DIR}/gen/chrome/android/chrome_apk_paks/locales/{en-US,zh-CN,zh-TW}.pak \
		"${asset_dir}/locales"
	cp ${RELEASE_DIR}/snapshot_blob.bin "$asset_dir"/snapshot_blob_32.bin
}

sync_libs() {
	mkdir -p "${APP_DIR}/libs"

	cp ${BASE_DIR}/third_party/google_android_play_core/*.aar \
		${RELEASE_DIR}/lib.java/third_party/android_tools/gcm.jar \
		"${APP_DIR}/libs"
}

sync_jniLibs() {
	local jni_libs_dir="${APP_DIR}/src/main/jniLibs/armeabi-v7a"
	mkdir -p "$jni_libs_dir"
	cp ${RELEASE_DIR}/*.so "$jni_libs_dir"
}

clean_project() {
	rm -rf ${APP_DIR}/src/main/java/org/org \
		${APP_DIR}/src/main/java/org/src \
		${APP_DIR}/src/main/java/org/com \
		${APP_DIR}/src/main/java/com/google/protobuf \
		${APP_DIR}/src/main/java/org/chromium/chrome/browser/MonochromeApplication.java \
		${APP_DIR}/src/main/java/org/chromium/chrome/browser/preferences/password/PasswordEntryEditorPreference.java \
		${APP_DIR}/src/main/java/org/chromium/components/embedder_support/media \
		${APP_DIR}/src/main/java/org/chromium/chrome/browser/offlinepages/evaluation \
		${APP_DIR}/src/main/java/{src,test,templates}

	local feed_dir="${APP_DIR}/src/main/java/com/google/android/libraries/feed"
	find "$feed_dir" -regextype "posix-egrep" -regex ".*/(testing|test_data|res)" -type d -print0 | \
		xargs -0 rm -rf

	find "$feed_dir" -regextype "posix-egrep" -regex ".*/AndroidManifest.xml" -type f -print0 | \
		xargs -0 rm -f

	local del_files="README|OWNERS|COPYING|BUILD|.*\.template|R\.java|.*\.stamp|.*stamp\.d|.*\.py|.*\.flags"
	find "$PRO_DIR" -regextype "posix-egrep" -regex ".*/(${del_files})" -type f -print0 | \
		xargs -0 rm -f

	local langs="am|ar|bg|ca|cs|da|de|el|en-rGB|es|es-rUS|fa|fi|fr|hi|hr|hu|in|it|iw"
	langs="$langs|ja|ko|lt|lv|nb|nl|pl|pt-rBR|pt-rPT|ro|ru|sk|sl|sr|sv|sw|th|tl|tr|uk|vi"
	find "$PRO_DIR" -regextype "posix-egrep" -regex ".*values-($langs)" -print0 | xargs -0 rm -rf

	find "${PRO_DIR}/res_base" -regextype "posix-egrep" -regex ".*/app_icon\.png" -type f -print0 | xargs -0 rm -f

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
	sync_customtabs
	sync_download
	sync_splash
	sync_autofill_assistant
	sync_feed
	sync_aidl

	sync_assets
	sync_libs
	sync_jniLibs

	clean_project
	# NativeLibraries
}

do_sync
