package com.github.sherter.googlejavaformatgradleplugin.test


import org.junit.jupiter.api.io.TempDir
import spock.lang.Specification

import java.nio.file.Path

class ProjectTest extends Specification {

    @TempDir Path temporaryFolder
    File rootDir
    Project project

    void setup() {
        rootDir = temporaryFolder.toFile()
        project = new Project(rootDir)
    }

    def 'create file'() {
        when:
        project.createFile(['foo'])

        then:
        def file = new File(rootDir, 'foo')
        file.exists()
        file.readBytes() == [] as byte[]
    }

    def 'create file with non-existing parent directories'() {
        when:
        project.createFile(['foo', 'bar', 'baz'])

        then:
        def file = new File(rootDir, 'foo/bar/baz')
        file.exists()
        file.readBytes() == [] as byte[]
    }

    def 'create file with initial content'() {
        when:
        project.createFile(['foo'], [1, 2, 3] as byte[])

        then:
        def file = new File(rootDir, 'foo')
        file.exists()
        file.readBytes() == [1, 2, 3] as byte[]
    }

    def 'create file with initial string as content'() {
        when:
        project.createFile(['foo'], 'bar')

        then:
        def file = new File(rootDir, 'foo')
        file.exists()
        file.readLines('UTF-8') == ['bar']
    }

}
