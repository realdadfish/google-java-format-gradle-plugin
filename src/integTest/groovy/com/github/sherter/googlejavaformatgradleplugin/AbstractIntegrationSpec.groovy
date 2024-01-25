package com.github.sherter.googlejavaformatgradleplugin

import com.github.sherter.googlejavaformatgradleplugin.format.Gjf
import com.github.sherter.googlejavaformatgradleplugin.test.Project
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.io.TempDir
import spock.lang.Specification
import spock.util.environment.Jvm

import java.nio.file.Path

abstract class AbstractIntegrationSpec extends Specification {

    static final String buildScriptBlock = """\
        |buildscript {
        |  repositories {
        |    mavenLocal()
        |  }
        |  dependencies {
        |    classpath group: 'com.github.sherter.googlejavaformatgradleplugin',
        |               name: 'google-java-format-gradle-plugin',
        |            version: '${GoogleJavaFormatPlugin.PLUGIN_VERSION}'
        |  }
        |}
        |""".stripMargin()

    static final String applyPlugin = """\
        |$buildScriptBlock
        |apply plugin: 'com.github.sherter.google-java-format'
        |""".stripMargin()

    // if a dependency is not in teskit's dependency cache, try to load it from
    // the local repository first, before reaching out to the web
    static final String defaultRepositories = '''\
        |repositories {
        |  mavenLocal()
        |  mavenCentral()
        |}
        |'''.stripMargin()

    // google-java-format targets Java 11 since version 1.8
    static final String downgradeToolVersionIfLatestNotSupportedOnCurrentJvm = new BigDecimal(Jvm.current.javaSpecificationVersion) < BigDecimal.valueOf(11) ? """\
        |googleJavaFormat {
        |  toolVersion = '${Gjf.SUPPORTED_VERSIONS.get(Gjf.SUPPORTED_VERSIONS.indexOf('1.8') - 1)}'
        |}
        |""".stripMargin() : ''

    @TempDir Path temporaryFolder
    GradleRunner runner
    Project project

    def setup() {
        runner = GradleRunner.create()
                .withGradleVersion(System.properties['GRADLE_VERSION'])
                .withProjectDir(temporaryFolder.toFile())
        project = new Project(temporaryFolder.toFile())
        additionalSetup()
    }

    void additionalSetup() {};
}
