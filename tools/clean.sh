#!/bin/sh

PRO_DIR="$(dirname $0)/../"

rm -rf ${PRO_DIR}/app/libs/* \
	${PRO_DIR}/app/src/main/aidl/* \
	${PRO_DIR}/app/src/main/assets/* \
	${PRO_DIR}/app/src/main/java/* \
	${PRO_DIR}/app/src/main/jniLibs/* \
	${PRO_DIR}/app/src/main/res/*

rm -rf ${PRO_DIR}/customtabs/src/main/res/*

rm -rf ${PRO_DIR}/components/autofill/src/main/res/* \
	${PRO_DIR}/components/components_base/src/main/res/* \
	${PRO_DIR}/components/embedder_support/src/main/res/*

rm -rf ${PRO_DIR}/content/src/main/res/*

rm -rf ${PRO_DIR}/data_chart/src/main/res/* \
	${PRO_DIR}/data_chart/src/main/java/*

rm -rf ${PRO_DIR}/media/src/main/res/* \
	${PRO_DIR}/media/src/main/java/*

rm -rf ${PRO_DIR}/download/src/main/res/*

rm -rf ${PRO_DIR}/ui/src/main/res/*

rm -rf ${PRO_DIR}/splash/src/main/res/*

rm -rf ${PRO_DIR}/autofill_assistant/src/main/res/*

rm -rf ${PRO_DIR}/feed/src/main/res/*

rm -rf ${PRO_DIR}/feed/*_res

rm -rf ${PRO_DIR}/res_base/*
