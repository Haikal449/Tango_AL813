LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

# We only want this apk build for tests.
LOCAL_MODULE_TAGS := tests
LOCAL_CERTIFICATE := shared

LOCAL_JAVA_LIBRARIES := android.test.runner
LOCAL_STATIC_JAVA_LIBRARIES := junit-report librobotium4

# Include all test java files.
LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_PACKAGE_NAME := MagicSmokeSelectorTests

LOCAL_INSTRUMENTATION_FOR := MagicSmokeWallpapers

include $(BUILD_PACKAGE)
