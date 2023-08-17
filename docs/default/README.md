---
sidebar_position: 1
title: Ezlib
description: Runtime library/dependency loader & relocator for Java in a single class
---

Welcome to Ezlib wiki, here you will find information about the use of this lightweight library.

## Introduction

Ezib is a library created to provide an easy way to load dependencies of Java projects at runtime, including support for package relocation.

## Requirements

*  Minimum **Java 8**

## Dependency

![version](https://img.shields.io/github/v/tag/saicone/ezlib?label=current%20version&style=for-the-badge)

Ezlib it's completely shadeable in your project.

So you can copy the [Ezlib class](https://github.com/saicone/ezlib/blob/master/src/main/java/com/saicone/ezlib/Ezlib.java) or add it as implementación.

```mdx-code-block
import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

<Tabs>
<TabItem value="groovy" label="build.gradle" default>

```groovy
plugins {
    id 'com.github.johnrengelman.shadow' version '8.1.1'
}

repositories {
    maven { url 'https://jitpack.io' }
}

// Use only ezlib
dependencies {
    implementation 'com.saicone.ezlib:ezlib:VERSION'
}

// Use ezlib loader instead
dependencies {
    implementation 'com.saicone.ezlib:loader:VERSION'
    // Use annotations
    compileOnly 'com.saicone.ezlib:annotations:VERSION'
    annotationProcessor 'com.saicone.ezlib:annotations:VERSION'
}

jar.dependsOn (shadowJar)

shadowJar {
    relocate 'com.saicone.ezlib', project.group + '.ezlib'
}
```

</TabItem>
<TabItem value="kotlin" label="build.gradle.kts">

```kotlin
plugins {
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

repositories {
    maven("https://jitpack.io")
}

// Use only ezlib
dependencies {
    implementation("com.saicone.ezlib:ezlib:VERSION")
}

// Use ezlib loader instead
dependencies {
    implementation("com.saicone.ezlib:loader:VERSION")
    // Use annotations
    compileOnly("com.saicone.ezlib:annotations:VERSION")
    annotationProcessor("com.saicone.ezlib:annotations:VERSION")
}

tasks {
    jar {
        dependsOn(tasks.shadowJar)
    }

    shadowJar {
        relocate("com.saicone.ezlib", "${project.group}.ezlib")
    }
}
```

</TabItem>
<TabItem value="maven" label="pom.xml">

```xml
<repositories>
    <repository>
        <id>Jitpack</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependencies>
    <!-- Use ezlib -->
    <dependency>
        <groupId>com.saicone.ezlib</groupId>
        <artifactId>ezlib</artifactId>
        <version>VERSION</version>
        <scope>compile</scope>
    </dependency>
    <!-- Use ezlib loader -->
    <dependency>
        <groupId>com.saicone.ezlib</groupId>
        <artifactId>loader</artifactId>
        <version>VERSION</version>
        <scope>compile</scope>
    </dependency>
    <!-- Use annotations -->
    <dependency>
        <groupId>com.saicone.ezlib</groupId>
        <artifactId>annotations</artifactId>
        <version>VERSION</version>
        <scope>provided</scope>
    </dependency>
</dependencies>

<build>
    <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-shade-plugin</artifactId>
        <version>3.3.0</version>
        <configuration>
            <artifactSet>
                <includes>
                    <include>com.saicone.ezlib:ezlib</include>
                    <include>com.saicone.ezlib:loader</include>
                </includes>
            </artifactSet>
            <relocations>
                <relocation>
                    <pattern>com.saicone.ezlib</pattern>
                    <shadedPattern>${project.groupId}.ezlib</shadedPattern>
                </relocation>
            </relocations>
        </configuration>
        <executions>
            <execution>
                <phase>package</phase>
                <goals>
                    <goal>shade</goal>
                </goals>
            </execution>
        </executions>
    </plugin>
</build>
```

</TabItem>
</Tabs>
