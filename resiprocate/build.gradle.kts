
// This is a gradle build script written using the Kotlin DSL:
//  https://docs.gradle.org/current/userguide/kotlin_dsl.html

// To use it, ensure Gradle is in $PATH and run
//
//     In the reSIProcate repository:
//
//         build/release-tarball.sh && \
//             cp resiprocate-1.13.0~alpha1.tar.gz /tmp/reSIProcate-snapshot.tar.gz
//
//     In this (ndkports fork) repository:
//
//         gradle -PndkPath=${HOME}/Android/Sdk/ndk/23.1.7779620 release -x test

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
import com.android.ndkports.PrefabSysrootPlugin

val portVersion = "1.13.0"  // FIXME - can we get this from configure.ac?

group = "org.resiprocate"
version = "$portVersion${rootProject.extra.get("snapshotSuffix")}"

plugins {
    id("maven-publish")
    id("com.android.ndkports.NdkPorts")
}

dependencies {
    implementation(project(":openssl"))
}

ndkPorts {
    ndkPath.set(File(project.findProperty("ndkPath") as String))
    // FIXME: can we work from the source tree?
    // FIXME: override Port.kts extractSource method to call git-archive
    source.set(project.file("/tmp/reSIProcate-snapshot.tar.gz"))
    minSdkVersion.set(16)
}

tasks.prefab {
    generator.set(PrefabSysrootPlugin::class.java)
}

tasks.register<AutoconfPortTask>("buildPort") {
    autoconf {
        args( // taken from reSIProcate build/android-custom-ndk
            "--disable-versioned-soname",
            "--enable-android",
            "--with-ssl",
            // "--enable-ipv6",
            "--disable-static"
        )
        env("CPPFLAGS", "-fPIC -I$sysroot/include")
        env("LDFLAGS", "-L$sysroot/lib")

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
    }
}

tasks.prefabPackage {
    version.set(CMakeCompatibleVersion.parse(portVersion))
    licensePath.set("COPYING")
    @Suppress("UnstableApiUsage") dependencies.set(
        mapOf(
            "openssl" to "1.1.1k"
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
            url = uri("${rootProject.buildDir}/repository")
        }
    }
}
