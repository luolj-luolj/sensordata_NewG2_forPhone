/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
#include <cstring>
#include <jni.h>
#include <cinttypes>
#include <android/log.h>
#include <sensor_data.h>
#include <string>

#define LOGI(...) \
  ((void)__android_log_print(ANDROID_LOG_INFO, "sensordata-libs::", __VA_ARGS__))

/* This is a trivial JNI example where we use a native method
 * to return a new VM String. See the corresponding Java source
 * file located at:
 *
 *   app/src/main/java/com/lenovo/sensordatalibs/MainActivity.java
 */
extern "C"
JNIEXPORT jstring JNICALL
Java_com_lenovo_sensordatalibs_MainActivity_stringFromJNI(JNIEnv *env, jobject thiz) {
    return env->NewStringUTF("Hello from JNI LIBS!");
}
extern "C"
JNIEXPORT void JNICALL
Java_com_lenovo_sensordatalibs_MainActivity_nativeSetUsbFileDescriptor(JNIEnv *env, jobject thiz,
                                                                       jint vid, jint pid, jint fd,
                                                                       jint busnum, jint devaddr,
                                                                       jstring usbfs_str) {
    const char *c_usbfs = env->GetStringUTFChars(usbfs_str, JNI_FALSE);
    setUsbParams(vid, pid, fd, busnum, devaddr, c_usbfs);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_lenovo_sensordatalibs_MainActivity_nativeSetStUfd(JNIEnv *env, jobject thiz, jint vid,
                                                           jint pid, jint fd, jint busnum,
                                                           jint devaddr, jstring usbfs_str) {
    const char *c_usbfs = env->GetStringUTFChars(usbfs_str, JNI_FALSE);
    setStUfd(vid, pid, fd, busnum, devaddr, c_usbfs);
}
extern "C"
JNIEXPORT jint JNICALL
Java_com_lenovo_sensordatalibs_MainActivity_nativeInitialSensorData(JNIEnv *env, jobject thiz) {
    return initial_sensor();
}
extern "C"
JNIEXPORT jint JNICALL
Java_com_lenovo_sensordatalibs_MainActivity_nativeReleaseSensorData(JNIEnv *env, jobject thiz) {
    return release_sensor();
}
extern "C"
JNIEXPORT jint JNICALL
Java_com_lenovo_sensordatalibs_MainActivity_nativeStartSensorData(JNIEnv *env, jobject thiz, jstring who) {
    const char *c_who = env->GetStringUTFChars(who, JNI_FALSE);
    return start_sensor((char*)c_who);

}
extern "C"
JNIEXPORT jint JNICALL
Java_com_lenovo_sensordatalibs_MainActivity_nativeStopSensorData(JNIEnv *env, jobject thiz, jstring who) {
    const char *c_who = env->GetStringUTFChars(who, JNI_FALSE);
    return stop_sensor((char*)c_who);
}