#
# Copyright (C) 2014 MediaTek Inc.
# Modification based on code covered by the mentioned copyright
# and/or permission notice(s).
#
#
# Copyright (C) 2014 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# This tool is prebuilt if we're doing an app-only build.
ifeq ($(TARGET_BUILD_APPS)$(filter true,$(TARGET_BUILD_PDK)),)

# ==========================================================
# Setup some common variables for the different build
# targets here.
# ==========================================================
LOCAL_PATH:= $(call my-dir)

aaptMain := Main.cpp
aaptSources := \
    AaptAssets.cpp \
    AaptConfig.cpp \
    AaptUtil.cpp \
    AaptXml.cpp \
    ApkBuilder.cpp \
    Command.cpp \
    CrunchCache.cpp \
    FileFinder.cpp \
    Images.cpp \
    Package.cpp \
    pseudolocalize.cpp \
    qsort_r_compat.c \
    Resource.cpp \
    ResourceFilter.cpp \
    ResourceIdCache.cpp \
    ResourceTable.cpp \
    SourcePos.cpp \
    StringPool.cpp \
    WorkQueue.cpp \
    XMLNode.cpp \
    ZipEntry.cpp \
    ZipFile.cpp

aaptTests := \
    tests/AaptConfig_test.cpp \
    tests/AaptGroupEntry_test.cpp \
    tests/ResourceFilter_test.cpp

aaptCIncludes := \
    external/libpng \
    external/zlib

aaptHostLdLibs :=
aaptHostStaticLibs := \
    libandroidfw \
    libpng \
    liblog \
    libutils \
    libcutils \
    libexpat \
    libziparchive-host
# DTS2014102105499 yinwenshuai/00211458 20141105 begin
aaptHostStaticLibs += \
    libandroidfw.huawei
# DTS2014102105499 yinwenshuai/00211458 20141105 end

aaptCFlags := -DAAPT_VERSION=\"$(BUILD_NUMBER)\"

ifeq ($(HOST_OS),linux)
    aaptHostLdLibs += -lrt -ldl -lpthread
endif

# Statically link libz for MinGW (Win SDK under Linux),
# and dynamically link for all others.
ifneq ($(strip $(USE_MINGW)),)
    aaptHostStaticLibs += libz
else
    aaptHostLdLibs += -lz
endif

# ==========================================================
# Build the host static library: libaapt
# ==========================================================
include $(CLEAR_VARS)

LOCAL_MODULE := libaapt
LOCAL_CFLAGS += -Wno-format-y2k -DSTATIC_ANDROIDFW_FOR_TOOLS $(aaptCFlags)
LOCAL_CPPFLAGS += $(aaptCppFlags)
ifeq (darwin,$(HOST_OS))
LOCAL_CFLAGS += -D_DARWIN_UNLIMITED_STREAMS
endif
LOCAL_C_INCLUDES += $(aaptCIncludes)
LOCAL_SRC_FILES := $(aaptSources)

include $(BUILD_HOST_STATIC_LIBRARY)

ifeq ($(strip $(MTK_DFO_RESOLUTION_SUPPORT)),yes)
LOCAL_CFLAGS += -DMTK_FOR_HOST_BUILD_AAPT
endif

# ==========================================================
# Build the host executable: aapt
# ==========================================================
include $(CLEAR_VARS)

LOCAL_MODULE := aapt
LOCAL_CFLAGS += $(aaptCFlags)
LOCAL_CPPFLAGS += $(aaptCppFlags)
LOCAL_LDLIBS += $(aaptHostLdLibs)
LOCAL_SRC_FILES := $(aaptMain)
LOCAL_STATIC_LIBRARIES += libaapt $(aaptHostStaticLibs)

include $(BUILD_HOST_EXECUTABLE)


# ==========================================================
# Build the host tests: libaapt_tests
# ==========================================================
include $(CLEAR_VARS)

LOCAL_MODULE := libaapt_tests
LOCAL_CFLAGS += $(aaptCFlags)
LOCAL_CPPFLAGS += $(aaptCppFlags)
LOCAL_LDLIBS += $(aaptHostLdLibs)
LOCAL_SRC_FILES += $(aaptTests)
LOCAL_C_INCLUDES += $(LOCAL_PATH)
LOCAL_STATIC_LIBRARIES += libaapt $(aaptHostStaticLibs)

include $(BUILD_HOST_NATIVE_TEST)


# ==========================================================
# Build the device executable: aapt
# ==========================================================
ifneq ($(SDK_ONLY),true)
include $(CLEAR_VARS)

LOCAL_MODULE := aapt
LOCAL_CFLAGS += $(aaptCFlags)
LOCAL_SRC_FILES := $(aaptSources) $(aaptMain)
LOCAL_C_INCLUDES += \
    $(aaptCIncludes) \
    bionic \
    external/stlport/stlport
LOCAL_SHARED_LIBRARIES := \
    libandroidfw \
    libutils \
    libcutils \
    libpng \
    liblog \
    libz
LOCAL_STATIC_LIBRARIES := \
    libstlport_static \
    libexpat_static \
    libandroidfw.huawei
# DTS2014102105499 yinwenshuai/00211458 20141105 begin
#LOCAL_STATIC_LIBRARIES += \
#    libandroidfw.huawei
# DTS2014102105499 yinwenshuai/00211458 20141105 end

include $(BUILD_EXECUTABLE)

endif # Not SDK_ONLY

endif # No TARGET_BUILD_APPS or TARGET_BUILD_PDK
