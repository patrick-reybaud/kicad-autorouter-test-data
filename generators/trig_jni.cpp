// SPDX-License-Identifier: GPL-3.0-or-later
// Oracle only. No JNI dependency is introduced into the routing engine.
#include <jni.h>
#include "fdlibm_trig.h"
extern "C" JNIEXPORT jdouble JNICALL
Java_app_freerouting_geometry_planar_NativeTrig_sin( JNIEnv*, jclass, jdouble x )
{
    return FREEROUTING_FDLIBM::Sin( x );
}
extern "C" JNIEXPORT jdouble JNICALL
Java_app_freerouting_geometry_planar_NativeTrig_cos( JNIEnv*, jclass, jdouble x )
{
    return FREEROUTING_FDLIBM::Cos( x );
}
