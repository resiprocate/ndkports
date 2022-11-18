
// This is a gradle build script written using the Kotlin DSL:
//  https://docs.gradle.org/current/userguide/kotlin_dsl.html

// To use it, ensure Gradle is in $PATH, for example:
//
//         export PATH=/opt/gradle-7.3.3/bin/:$PATH
//
//     In the reSIProcate repository:
//
//         build/release-tarball.sh && \
//             cp resiprocate-1.13.0~alpha1.tar.gz /tmp/reSIProcate-snapshot.tar.gz
//
//     In this (ndkports fork) repository:
//
//         gradle -PndkPath=${HOME}/Android/Sdk/ndk/23.1.7779620 release publishToMavenLocal-x test

// It is modeled on the Google ndkports build script for curl:
//  https://android.googlesource.com/platform/tools/ndkports/+/refs/heads/master/curl/build.gradle.kts

// When using the NDK, gradle is not a replacement for the existing
// autotools or CMake build system.  Rather, it becomes a wrapper
// around the existing build system.

// In the case of reSIProcate, this gradle script and the associated
// plugins will:
//
// a) bring in dependencies from ndkports, for example, openssl
//
// b) run the autotools build multiple times, once for each
//    target architecture
//
// c) produce an AAR file containing the headers and libraries for
//    other users of reSIProcate to build their own projects

import com.android.ndkports.AutoconfPortTask
import com.android.ndkports.CMakeCompatibleVersion
import com.android.ndkports.CMakePortTask
import com.android.ndkports.PrefabSysrootPlugin

val portVersion = "1.13.0"  // FIXME - can we get this from configure.ac?

group = "org.resiprocate"
version = "$portVersion${rootProject.extra.get("snapshotSuffix")}"

plugins {
    id("maven-publish")
    id("com.android.ndkports.NdkPorts")
    distribution
}

dependencies {
    implementation(project(":openssl"))
}

ndkPorts {
    ndkPath.set(File(project.findProperty("ndkPath") as String))
    // FIXME: can we work from the source tree?
    // FIXME: override Port.kts extractSource method to call git-archive
    source.set(project.file("src.tar.gz"))
    minSdkVersion.set(19)
}

tasks.prefab {
    generator.set(PrefabSysrootPlugin::class.java)
}

val buildTask = tasks.register<CMakePortTask>("buildPort") {
    cmake {

        // From the previous autoconf build:
        // args( // taken from reSIProcate build/android-custom-ndk
        //     "--disable-versioned-soname",
        //     "--enable-android",
        //     "--with-ssl",
        //     // "--enable-ipv6",
        //     "--disable-static"
        // )
        // env("CPPFLAGS", "-fPIC -I$sysroot/include")
        // env("LDFLAGS", "-L$sysroot/lib")

        // The ndkports system generates unstripped libraries for
        // debugging.  If that changes in future, this hack can
        // potentially stop it stripping the libs:
        //env("STRIP", "/bin/true")
        //
        // When gradle / NDK uses the AAR to build a final APK file,
        // it makes a further attempt to strip the libraries and the
        // app's build.gradle needs to include doNotStrip

        // If we want to be sure the libraries are not optimized at all
        // we can add these:
        //env("CFLAGS", "-O0 -g")
        //env("CXXFLAGS", "-O0 -g")

        arg("-DHAVE_CLOCK_GETTIME_MONOTONIC_EXITCODE=1")
        //arg("-DCMAKE_CXX_FLAGS='-fPIC -I$sysroot/include'")
        //arg("-DCMAKE_C_FLAGS='-fPIC -I$sysroot/include'")
        //arg("-DCMAKE_LD_FLAGS=-L$sysroot/lib")
        arg("-DWITH_C_ARES=OFF")
        arg("-DWITH_SSL=ON")
        arg("-DUSE_POPT=OFF")
        arg("-DUSE_SIGCOMP=OFF")
        arg("-DUSE_FMT=OFF")
        arg("-DVERSIONED_SONAME=OFF")
        arg("-DENABLE_ANDROID=ON")
        arg("-DUSE_IPV6=ON")
        arg("-DUSE_DTLS=ON")
        arg("-DPEDANTIC_STACK=OFF")
        arg("-DUSE_MYSQL=OFF")
        arg("-DUSE_SOCI_POSTGRESQL=OFF")
        arg("-DUSE_SOCI_MYSQL=OFF")
        arg("-DUSE_POSTGRESQL=OFF")
        arg("-DUSE_MAXMIND_GEOIP=OFF")
        arg("-DRESIP_HAVE_RADCLI=OFF")
        arg("-DUSE_NETSNMP=OFF")
        arg("-DBUILD_REPRO=OFF")
        arg("-DBUILD_REPRO_DSO_PLUGINS=OFF")
        arg("-DBUILD_RETURN=OFF")
        arg("-DBUILD_REND=OFF")
        arg("-DBUILD_TFM=OFF")
        arg("-DBUILD_ICHAT_GW=OFF")
        arg("-DBUILD_TELEPATHY_CM=OFF")
        arg("-DBUILD_RECON=OFF")
        arg("-DUSE_SRTP1=OFF")
        arg("-DBUILD_RECONSERVER=OFF")
        arg("-DUSE_SIPXTAPI=OFF")
        arg("-DUSE_KURENTO=OFF")
        arg("-DUSE_GSTREAMER=OFF")
        arg("-DUSE_LIBWEBRTC=OFF")
        arg("-DRECON_LOCAL_HW_TESTS=OFF")
        arg("-DDEFAULT_BRIDGE_MAX_IN_OUTPUTS=20")
        arg("-DBUILD_P2P=OFF")
        arg("-DBUILD_PYTHON=OFF")
        arg("-DPYCXX_SRCDIR=/usr/src/CXX")
        arg("-DBUILD_QPID_PROTON=OFF")
        arg("-DRESIP_ASSERT_SYSLOG=ON")
        arg("-DREGENERATE_MEDIA_SAMPLES=OFF")
        arg("-DUSE_NDKPORTS_HACKS=ON")
    }
}

tasks.prefabPackage {
    version.set(CMakeCompatibleVersion.parse(portVersion))
    licensePath.set("COPYING")
    @Suppress("UnstableApiUsage") dependencies.set(
        mapOf(
            "openssl" to "1.1.1s"
        )
    )
    modules {
        create("resipares")
        create("rutil") {
            dependencies.set(
                listOf(
                    "//openssl:crypto", "//openssl:ssl"
                )
            )
        }
        create("resip")
        create("dum")
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["prefab"])
            pom {
                name.set("resiprocate")
                description.set("The ndkports AAR for resiprocate.")
                url.set(
                    "https://android.googlesource.com/platform/tools/ndkports"
                )
                licenses {
                    license {
                        name.set("The reSIProcate License")
                        url.set("https://www.resiprocate.org/License")
                        distribution.set("repo")
                    }
                }
                developers {
                    developer {
                        name.set("reSIProcate developers")
                    }
                }
                scm {
                    url.set("https://github.com/resiprocate/ndkports")
                    connection.set("scm:git:https://github.com/resiprocate/ndkports")
                }
            }
        }
    }
    repositories {
        maven {
            url = uri("${project.buildDir}/repository")
        }
    }
}

distributions {
    main {
        contents {
            from("${project.buildDir}/repository")
            include("**/*.aar")
            include("**/*.pom")
        }
    }
}

tasks {
    distZip {
        dependsOn("publish")
        destinationDirectory.set(File(rootProject.buildDir, "distributions"))
    }
}
